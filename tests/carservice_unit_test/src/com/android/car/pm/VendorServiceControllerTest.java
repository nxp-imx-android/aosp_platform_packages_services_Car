/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.pm;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.car.userlib.CarUserManagerHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.car.CarLocalServices;
import com.android.car.user.CarUserService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class VendorServiceControllerTest {
    private VendorServiceController mController;
    private static final Long DEFAULT_TIMEOUT_MS = 1000L;

    private static final int FG_USER_ID = 13;

    private static final String SERVICE_BIND_ALL_USERS_ASAP = "com.andorid.car/.AllUsersService";
    private static final String SERVICE_BIND_FG_USER_UNLOCKED = "com.andorid.car/.ForegroundUsers";
    private static final String SERVICE_START_SYSTEM_UNLOCKED = "com.andorid.car/.SystemUser";

    private static final String[] FAKE_SERVICES = new String[] {
            SERVICE_BIND_ALL_USERS_ASAP + "#bind=bind,user=all,trigger=asap",
            SERVICE_BIND_FG_USER_UNLOCKED + "#bind=bind,user=foreground,trigger=userUnlocked",
            SERVICE_START_SYSTEM_UNLOCKED + "#bind=start,user=system,trigger=userUnlocked"
    };

    @Mock
    private Resources mResources;

    @Mock
    private UserManager mUserManager;

    private MockitoSession mSession;
    private ServiceLauncherContext mContext;
    private CarUserManagerHelper mUserManagerHelper;
    private CarUserService mCarUserService;

    @Before
    public void setUp() {
        mSession = mockitoSession()
                .initMocks(this)
                .strictness(Strictness.LENIENT)
                .spyStatic(ActivityManager.class)
                .startMocking();
        mContext = new ServiceLauncherContext(ApplicationProvider.getApplicationContext());
        mUserManagerHelper = Mockito.spy(new CarUserManagerHelper(mContext));
        mCarUserService = new CarUserService(mContext, mUserManagerHelper, mUserManager,
                ActivityManager.getService(), 2 /* max running users */);
        CarLocalServices.addService(CarUserService.class, mCarUserService);

        mController = new VendorServiceController(mContext, Looper.getMainLooper());

        UserInfo persistentFgUser = new UserInfo(FG_USER_ID, "persistent user", 0);
        when(mUserManager.getUserInfo(FG_USER_ID)).thenReturn(persistentFgUser);

        // Let's pretend system is not fully loaded, current user is system.
        when(ActivityManager.getCurrentUser()).thenReturn(UserHandle.USER_SYSTEM);
        // ..and by default all users are locked
        mockUserUnlock(UserHandle.USER_ALL, false /* unlock */);
        when(mResources.getStringArray(com.android.car.R.array.config_earlyStartupServices))
                .thenReturn(FAKE_SERVICES);
    }

    @After
    public void tearDown() {
        CarLocalServices.removeServiceForTest(CarUserService.class);
        mSession.finishMocking();
    }

    @Test
    public void init_nothingConfigured() {
        when(mResources.getStringArray(com.android.car.R.array.config_earlyStartupServices))
                .thenReturn(new String[0]);

        mController.init();

        mContext.verifyNoMoreServiceLaunches();
    }

    @Test
    public void init_systemUser() throws InterruptedException {
        mController.init();

        Thread.sleep(100);

        mContext.assertBoundService(SERVICE_BIND_ALL_USERS_ASAP);
        mContext.verifyNoMoreServiceLaunches();
    }

    @Test
    public void systemUserUnlocked() {
        mController.init();
        mContext.reset();

        // Unlock system user
        mockUserUnlock(UserHandle.USER_SYSTEM, true);
        runOnMainThread(() -> mCarUserService.setUserLockStatus(UserHandle.USER_SYSTEM, true));

        mContext.assertStartedService(SERVICE_START_SYSTEM_UNLOCKED);
        mContext.verifyNoMoreServiceLaunches();
    }

    @Test
    public void fgUserUnlocked() {
        mController.init();
        mContext.reset();

        // Switch user to foreground
        when(ActivityManager.getCurrentUser()).thenReturn(FG_USER_ID);
        runOnMainThread(() -> mCarUserService.onSwitchUser(FG_USER_ID));

        // Expect only services with ASAP trigger to be started
        mContext.assertBoundService(SERVICE_BIND_ALL_USERS_ASAP);
        mContext.verifyNoMoreServiceLaunches();

        // Unlock foreground user
        mockUserUnlock(FG_USER_ID, true);
        runOnMainThread(() -> mCarUserService.setUserLockStatus(FG_USER_ID, true));

        mContext.assertBoundService(SERVICE_BIND_FG_USER_UNLOCKED);
        mContext.verifyNoMoreServiceLaunches();
    }

    private void runOnMainThread(Runnable r) {
        Handler.getMain().runWithScissors(r, DEFAULT_TIMEOUT_MS);
    }

    private void mockUserUnlock(int userId, boolean unlock) {
        if (UserHandle.USER_ALL == userId) {
            when(mUserManager.isUserUnlockingOrUnlocked(any())).thenReturn(unlock);
            when(mUserManager.isUserUnlockingOrUnlocked(anyInt())).thenReturn(unlock);
        } else {
            when(mUserManager.isUserUnlockingOrUnlocked(userId)).thenReturn(unlock);
            when(mUserManager.isUserUnlockingOrUnlocked(UserHandle.of(userId))).thenReturn(unlock);
        }
    }

    /** Overrides framework behavior to succeed on binding/starting processes. */
    public class ServiceLauncherContext extends ContextWrapper {
        private List<Intent> mBoundIntents = new ArrayList<>();
        private List<Intent> mStartedServicesIntents = new ArrayList<>();

        ServiceLauncherContext(Context base) {
            super(base);
        }

        @Override
        public ComponentName startServiceAsUser(Intent service, UserHandle user) {
            mStartedServicesIntents.add(service);
            return service.getComponent();
        }

        @Override
        public boolean bindServiceAsUser(Intent service, ServiceConnection conn, int flags,
                Handler handler, UserHandle user) {
            mBoundIntents.add(service);
            conn.onServiceConnected(service.getComponent(), null);
            return true;
        }

        @Override
        public boolean bindServiceAsUser(Intent service, ServiceConnection conn,
                int flags, UserHandle user) {
            return bindServiceAsUser(service, conn, flags, null, user);
        }

        @Override
        public Resources getResources() {
            return mResources;
        }

        void assertBoundService(String service) {
            assertThat(mBoundIntents).hasSize(1);
            assertThat(mBoundIntents.get(0).getComponent())
                    .isEqualTo(ComponentName.unflattenFromString(service));
            mBoundIntents.clear();
        }

        void assertStartedService(String service) {
            assertThat(mStartedServicesIntents).hasSize(1);
            assertThat(mStartedServicesIntents.get(0).getComponent())
                    .isEqualTo(ComponentName.unflattenFromString(service));
            mStartedServicesIntents.clear();
        }

        void verifyNoMoreServiceLaunches() {
            assertThat(mStartedServicesIntents).isEmpty();
            assertThat(mBoundIntents).isEmpty();
        }

        void reset() {
            mStartedServicesIntents.clear();
            mBoundIntents.clear();

        }

        @Override
        public Object getSystemService(String name) {
            if (Context.USER_SERVICE.equals(name)) {
                return mUserManager;
            } else {
                return super.getSystemService(name);
            }
        }
    }
}
