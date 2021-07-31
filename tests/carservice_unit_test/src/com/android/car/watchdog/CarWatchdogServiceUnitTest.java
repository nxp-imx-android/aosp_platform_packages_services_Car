/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.watchdog;

import static android.automotive.watchdog.internal.ResourceOveruseActionType.KILLED;
import static android.automotive.watchdog.internal.ResourceOveruseActionType.NOT_KILLED;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmGetAliveUsers;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmGetAllUsers;
import static android.car.watchdog.CarWatchdogManager.TIMEOUT_CRITICAL;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.ActivityThread;
import android.automotive.watchdog.ResourceType;
import android.automotive.watchdog.internal.ApplicationCategoryType;
import android.automotive.watchdog.internal.ComponentType;
import android.automotive.watchdog.internal.GarageMode;
import android.automotive.watchdog.internal.ICarWatchdog;
import android.automotive.watchdog.internal.ICarWatchdogServiceForSystem;
import android.automotive.watchdog.internal.PackageIdentifier;
import android.automotive.watchdog.internal.PackageInfo;
import android.automotive.watchdog.internal.PackageIoOveruseStats;
import android.automotive.watchdog.internal.PackageMetadata;
import android.automotive.watchdog.internal.PackageResourceOveruseAction;
import android.automotive.watchdog.internal.PerStateIoOveruseThreshold;
import android.automotive.watchdog.internal.ResourceSpecificConfiguration;
import android.automotive.watchdog.internal.StateType;
import android.automotive.watchdog.internal.UidType;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.watchdog.CarWatchdogManager;
import android.car.watchdog.ICarWatchdogServiceCallback;
import android.car.watchdog.IResourceOveruseListener;
import android.car.watchdog.IoOveruseAlertThreshold;
import android.car.watchdog.IoOveruseConfiguration;
import android.car.watchdog.IoOveruseStats;
import android.car.watchdog.PackageKillableState;
import android.car.watchdog.PerStateBytes;
import android.car.watchdog.ResourceOveruseConfiguration;
import android.car.watchdog.ResourceOveruseStats;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.SparseArray;

import com.android.car.CarServiceUtils;

import com.google.common.truth.Correspondence;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * <p>This class contains unit tests for the {@link CarWatchdogService}.
 */
@RunWith(MockitoJUnitRunner.class)
public class CarWatchdogServiceUnitTest extends AbstractExtendedMockitoTestCase {
    private static final String CAR_WATCHDOG_DAEMON_INTERFACE = "carwatchdogd_system";
    private static final int MAX_WAIT_TIME_MS = 3000;
    private static final int INVALID_SESSION_ID = -1;

    @Mock private Context mMockContext;
    @Mock private PackageManager mMockPackageManager;
    @Mock private UserManager mMockUserManager;
    @Mock private IBinder mMockBinder;
    @Mock private ICarWatchdog mMockCarWatchdogDaemon;

    private CarWatchdogService mCarWatchdogService;
    private ICarWatchdogServiceForSystem mWatchdogServiceForSystemImpl;
    private IBinder.DeathRecipient mCarWatchdogDaemonBinderDeathRecipient;
    private BroadcastReceiver mBroadcastReceiver;
    private final SparseArray<String> mGenericPackageNameByUid = new SparseArray<>();
    private final SparseArray<List<String>> mPackagesBySharedUid = new SparseArray<>();
    private final ArrayMap<String, android.content.pm.PackageInfo> mPmPackageInfoByUserPackage =
            new ArrayMap<>();

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder builder) {
        builder
            .spyStatic(ServiceManager.class)
            .spyStatic(Binder.class)
            .spyStatic(ActivityThread.class);
    }

    /**
     * Initialize all of the objects with the @Mock annotation.
     */
    @Before
    public void setUpMocks() throws Exception {
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockContext.getPackageName()).thenReturn(
                CarWatchdogServiceUnitTest.class.getCanonicalName());
        mCarWatchdogService = new CarWatchdogService(mMockContext);
        mockWatchdogDaemon();
        setupUsers();
        mCarWatchdogService.init();
        mWatchdogServiceForSystemImpl = registerCarWatchdogService();
        captureBroadcastReceiver();
        captureDaemonBinderDeathRecipient();
        mockPackageManager();
    }

    @Test
    public void testCarWatchdogServiceHealthCheck() throws Exception {
        mWatchdogServiceForSystemImpl.checkIfAlive(123456, TIMEOUT_CRITICAL);
        verify(mMockCarWatchdogDaemon,
                timeout(MAX_WAIT_TIME_MS)).tellCarWatchdogServiceAlive(
                eq(mWatchdogServiceForSystemImpl), any(int[].class), eq(123456));
    }

    @Test
    public void testRegisterClient() throws Exception {
        TestClient client = new TestClient();
        mCarWatchdogService.registerClient(client, TIMEOUT_CRITICAL);
        mWatchdogServiceForSystemImpl.checkIfAlive(123456, TIMEOUT_CRITICAL);
        // Checking client health is asynchronous, so wait at most 1 second.
        int repeat = 10;
        while (repeat > 0) {
            int sessionId = client.getLastSessionId();
            if (sessionId != INVALID_SESSION_ID) {
                return;
            }
            SystemClock.sleep(100L);
            repeat--;
        }
        assertThat(client.getLastSessionId()).isNotEqualTo(INVALID_SESSION_ID);
    }

    @Test
    public void testUnregisterUnregisteredClient() throws Exception {
        TestClient client = new TestClient();
        mCarWatchdogService.registerClient(client, TIMEOUT_CRITICAL);
        mCarWatchdogService.unregisterClient(client);
        mWatchdogServiceForSystemImpl.checkIfAlive(123456, TIMEOUT_CRITICAL);
        assertThat(client.getLastSessionId()).isEqualTo(INVALID_SESSION_ID);
    }

    @Test
    public void testGoodClientHealthCheck() throws Exception {
        testClientHealthCheck(new TestClient(), 0);
    }

    @Test
    public void testBadClientHealthCheck() throws Exception {
        testClientHealthCheck(new BadTestClient(), 1);
    }

    @Test
    public void testGarageModeStateChangeToOn() throws Exception {
        mBroadcastReceiver.onReceive(mMockContext,
                new Intent().setAction(CarWatchdogService.ACTION_GARAGE_MODE_ON));
        verify(mMockCarWatchdogDaemon)
                .notifySystemStateChange(
                        eq(StateType.GARAGE_MODE), eq(GarageMode.GARAGE_MODE_ON), eq(-1));
    }

    @Test
    public void testGarageModeStateChangeToOff() throws Exception {
        mBroadcastReceiver.onReceive(mMockContext,
                new Intent().setAction(CarWatchdogService.ACTION_GARAGE_MODE_OFF));
        verify(mMockCarWatchdogDaemon)
                .notifySystemStateChange(
                        eq(StateType.GARAGE_MODE), eq(GarageMode.GARAGE_MODE_OFF), eq(-1));
    }

    @Test
    public void testGetResourceOveruseStats() throws Exception {
        int uid = Binder.getCallingUid();
        injectPackageInfos(Collections.singletonList(
                constructPackageManagerPackageInfo(
                        mMockContext.getPackageName(), uid, null, ApplicationInfo.FLAG_SYSTEM, 0)));

        SparseArray<PackageIoOveruseStats> packageIoOveruseStatsByUid =
                injectIoOveruseStatsForPackages(
                        mGenericPackageNameByUid, /* killablePackages= */new ArraySet<>(),
                        /* shouldNotifyPackages= */new ArraySet<>());

        ResourceOveruseStats expectedStats =
                constructResourceOveruseStats(
                        uid, mMockContext.getPackageName(),
                        packageIoOveruseStatsByUid.get(uid).ioOveruseStats);

        ResourceOveruseStats actualStats = mCarWatchdogService.getResourceOveruseStats(
                CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        assertWithMessage("Expected: " + expectedStats.toString() + "\nActual: "
                + actualStats.toString())
                .that(ResourceOveruseStatsSubject.isEquals(actualStats, expectedStats)).isTrue();
    }

    @Test
    public void testGetResourceOveruseStatsForSharedUid() throws Exception {
        int sharedUid = Binder.getCallingUid();
        injectPackageInfos(Collections.singletonList(
                constructPackageManagerPackageInfo(
                        mMockContext.getPackageName(), sharedUid, "system_shared_package",
                        ApplicationInfo.FLAG_SYSTEM, 0)));

        SparseArray<PackageIoOveruseStats> packageIoOveruseStatsByUid =
                injectIoOveruseStatsForPackages(
                        mGenericPackageNameByUid, /* killablePackages= */new ArraySet<>(),
                        /* shouldNotifyPackages= */new ArraySet<>());

        ResourceOveruseStats expectedStats =
                constructResourceOveruseStats(sharedUid, "shared:system_shared_package",
                        packageIoOveruseStatsByUid.get(sharedUid).ioOveruseStats);

        ResourceOveruseStats actualStats = mCarWatchdogService.getResourceOveruseStats(
                CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        assertWithMessage("Expected: " + expectedStats.toString() + "\nActual: "
                + actualStats.toString())
                .that(ResourceOveruseStatsSubject.isEquals(actualStats, expectedStats)).isTrue();
    }

    @Test
    public void testFailsGetResourceOveruseStatsWithInvalidArgs() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogService.getResourceOveruseStats(/* resourceOveruseFlag= */0,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));

        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogService.getResourceOveruseStats(
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO, /* maxStatsPeriod= */0));
    }

    @Test
    public void testGetAllResourceOveruseStatsWithNoMinimum() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("third_party_package", 1103456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 1201278, null)));

        List<PackageIoOveruseStats> packageIoOveruseStats = Arrays.asList(
                constructPackageIoOveruseStats(1103456,
                        /* shouldNotify= */false,
                        constructInternalIoOveruseStats(/* killableOnOveruse= */true,
                                /* remainingWriteBytes= */constructPerStateBytes(20, 20, 20),
                                /* writtenBytes= */constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */2)),
                constructPackageIoOveruseStats(1201278,
                        /* shouldNotify= */false,
                        constructInternalIoOveruseStats(/* killableOnOveruse= */false,
                                /* remainingWriteBytes= */constructPerStateBytes(450, 120, 340),
                                /* writtenBytes= */constructPerStateBytes(5000, 6000, 9000),
                                /* totalOveruses= */2)));
        mWatchdogServiceForSystemImpl.latestIoOveruseStats(packageIoOveruseStats);

        List<ResourceOveruseStats> expectedStats = Arrays.asList(
                constructResourceOveruseStats(1103456, "third_party_package",
                        packageIoOveruseStats.get(0).ioOveruseStats),
                constructResourceOveruseStats(1201278, "vendor_package.critical",
                        packageIoOveruseStats.get(1).ioOveruseStats));

        List<ResourceOveruseStats> actualStats = mCarWatchdogService.getAllResourceOveruseStats(
                CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO, /* minimumStatsFlag= */0,
                CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        ResourceOveruseStatsSubject.assertThat(actualStats)
                .containsExactlyElementsIn(expectedStats);
    }

    @Test
    public void testGetAllResourceOveruseStatsForSharedPackage() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "vendor_package.A", 1103456, "vendor_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.B", 1103456, "vendor_shared_package"),
                constructPackageManagerPackageInfo(
                        "system_package.C", 1201000, "system_shared_package"),
                constructPackageManagerPackageInfo(
                        "system_package.D", 1201000, "system_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.A", 1303456, "vendor_shared_package"),
                constructPackageManagerPackageInfo(
                        "vendor_package.B", 1303456, "vendor_shared_package")));

        List<PackageIoOveruseStats> packageIoOveruseStats = Arrays.asList(
                constructPackageIoOveruseStats(1103456,
                        /* shouldNotify= */false,
                        constructInternalIoOveruseStats(/* killableOnOveruse= */true,
                                /* remainingWriteBytes= */constructPerStateBytes(20, 20, 20),
                                /* writtenBytes= */constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */2)),
                constructPackageIoOveruseStats(1201000,
                        /* shouldNotify= */false,
                        constructInternalIoOveruseStats(/* killableOnOveruse= */false,
                                /* remainingWriteBytes= */constructPerStateBytes(450, 120, 340),
                                /* writtenBytes= */constructPerStateBytes(5000, 6000, 9000),
                                /* totalOveruses= */0)),
                constructPackageIoOveruseStats(1303456,
                        /* shouldNotify= */false,
                        constructInternalIoOveruseStats(/* killableOnOveruse= */true,
                                /* remainingWriteBytes= */constructPerStateBytes(40, 50, 60),
                                /* writtenBytes= */constructPerStateBytes(80, 170, 260),
                                /* totalOveruses= */1)));

        mWatchdogServiceForSystemImpl.latestIoOveruseStats(packageIoOveruseStats);

        List<ResourceOveruseStats> expectedStats = Arrays.asList(
                constructResourceOveruseStats(1103456, "shared:vendor_shared_package",
                        packageIoOveruseStats.get(0).ioOveruseStats),
                constructResourceOveruseStats(1201278, "shared:system_shared_package",
                        packageIoOveruseStats.get(1).ioOveruseStats),
                constructResourceOveruseStats(1303456, "shared:vendor_shared_package",
                        packageIoOveruseStats.get(2).ioOveruseStats));

        List<ResourceOveruseStats> actualStats = mCarWatchdogService.getAllResourceOveruseStats(
                CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO, /* minimumStatsFlag= */0,
                CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        ResourceOveruseStatsSubject.assertThat(actualStats)
                .containsExactlyElementsIn(expectedStats);
    }

    @Test
    public void testFailsGetAllResourceOveruseStatsWithInvalidArgs() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogService.getAllResourceOveruseStats(0, /* minimumStatsFlag= */0,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));

        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogService.getAllResourceOveruseStats(
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        CarWatchdogManager.FLAG_MINIMUM_STATS_IO_1_MB
                                | CarWatchdogManager.FLAG_MINIMUM_STATS_IO_100_MB,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));

        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogService.getAllResourceOveruseStats(
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO, /* minimumStatsFlag= */1 << 5,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));

        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogService.getAllResourceOveruseStats(
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO, /* minimumStatsFlag= */0,
                        /* maxStatsPeriod= */0));
    }

    @Test
    public void testGetAllResourceOveruseStatsWithMinimum() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("third_party_package", 1103456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 1201278, null)));

        List<PackageIoOveruseStats> packageIoOveruseStats = Arrays.asList(
                constructPackageIoOveruseStats(1103456,
                        /* shouldNotify= */false,
                        constructInternalIoOveruseStats(/* killableOnOveruse= */true,
                                /* remainingWriteBytes= */constructPerStateBytes(20, 20, 20),
                                /* writtenBytes= */constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */2)),
                constructPackageIoOveruseStats(1201278,
                        /* shouldNotify= */false,
                        constructInternalIoOveruseStats(/* killableOnOveruse= */false,
                                /* remainingWriteBytes= */constructPerStateBytes(450, 120, 340),
                                /* writtenBytes= */constructPerStateBytes(7000000, 6000, 9000),
                                /* totalOveruses= */2)));
        mWatchdogServiceForSystemImpl.latestIoOveruseStats(packageIoOveruseStats);

        List<ResourceOveruseStats> expectedStats = Collections.singletonList(
                constructResourceOveruseStats(1201278, "vendor_package.critical",
                        packageIoOveruseStats.get(1).ioOveruseStats));

        List<ResourceOveruseStats> actualStats = mCarWatchdogService.getAllResourceOveruseStats(
                CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                CarWatchdogManager.FLAG_MINIMUM_STATS_IO_1_MB,
                CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        ResourceOveruseStatsSubject.assertThat(actualStats)
                .containsExactlyElementsIn(expectedStats);
    }

    @Test
    public void testGetResourceOveruseStatsForUserPackage() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("third_party_package", 1103456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 1201278, null)));

        List<PackageIoOveruseStats> packageIoOveruseStats = Arrays.asList(
                constructPackageIoOveruseStats(1103456,
                        /* shouldNotify= */false,
                        constructInternalIoOveruseStats(/* killableOnOveruse= */true,
                                /* remainingWriteBytes= */constructPerStateBytes(20, 20, 20),
                                /* writtenBytes= */constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */2)),
                constructPackageIoOveruseStats(1201278,
                        /* shouldNotify= */false,
                        constructInternalIoOveruseStats(/* killableOnOveruse= */false,
                                /* remainingWriteBytes= */constructPerStateBytes(450, 120, 340),
                                /* writtenBytes= */constructPerStateBytes(500, 600, 900),
                                /* totalOveruses= */2)));
        mWatchdogServiceForSystemImpl.latestIoOveruseStats(packageIoOveruseStats);

        ResourceOveruseStats expectedStats =
                constructResourceOveruseStats(1201278, "vendor_package.critical",
                        packageIoOveruseStats.get(1).ioOveruseStats);

        ResourceOveruseStats actualStats =
                mCarWatchdogService.getResourceOveruseStatsForUserPackage(
                        "vendor_package.critical", new UserHandle(12),
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        assertWithMessage("Expected: " + expectedStats.toString() + "\nActual: "
                + actualStats.toString())
                .that(ResourceOveruseStatsSubject.isEquals(actualStats, expectedStats)).isTrue();
    }
    @Test
    public void testGetResourceOveruseStatsForUserPackageWithSharedUids() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "third_party_package", 1103456, "vendor_shared_package"),
                constructPackageManagerPackageInfo(
                        "vendor_package", 1103456, "vendor_shared_package"),
                constructPackageManagerPackageInfo("system_package", 1101100,
                        "shared_system_package")));

        SparseArray<PackageIoOveruseStats> packageIoOveruseStatsByUid =
                injectIoOveruseStatsForPackages(
                        mGenericPackageNameByUid, /* killablePackages= */new ArraySet<>(
                                Collections.singleton("shared:vendor_shared_package")),
                        /* shouldNotifyPackages= */new ArraySet<>());

        ResourceOveruseStats expectedStats =
                constructResourceOveruseStats(1103456, "shared:vendor_shared_package",
                        packageIoOveruseStatsByUid.get(1103456).ioOveruseStats);

        ResourceOveruseStats actualStats =
                mCarWatchdogService.getResourceOveruseStatsForUserPackage(
                        "vendor_package", new UserHandle(11),
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        assertWithMessage("Expected: " + expectedStats.toString() + "\nActual: "
                + actualStats.toString())
                .that(ResourceOveruseStatsSubject.isEquals(actualStats, expectedStats)).isTrue();
    }

    @Test
    public void testFailsGetResourceOveruseStatsForUserPackageWithInvalidArgs() throws Exception {
        assertThrows(NullPointerException.class,
                () -> mCarWatchdogService.getResourceOveruseStatsForUserPackage(
                        /* packageName= */null, new UserHandle(10),
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));

        assertThrows(NullPointerException.class,
                () -> mCarWatchdogService.getResourceOveruseStatsForUserPackage("some.package",
                        /* userHandle= */null, CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));

        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogService.getResourceOveruseStatsForUserPackage("some.package",
                        UserHandle.ALL, CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));

        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogService.getResourceOveruseStatsForUserPackage("some.package",
                        new UserHandle(10), /* resourceOveruseFlag= */0,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));

        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogService.getResourceOveruseStatsForUserPackage("some.package",
                        new UserHandle(10), CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        /* maxStatsPeriod= */0));
    }

    @Test
    public void testAddResourceOveruseListenerThrowsWithInvalidFlag() throws Exception {
        IResourceOveruseListener mockListener = createMockResourceOveruseListener();
        assertThrows(IllegalArgumentException.class, () -> {
            mCarWatchdogService.addResourceOveruseListener(0, mockListener);
        });
    }

    @Test
    public void testResourceOveruseListener() throws Exception {
        mGenericPackageNameByUid.put(Binder.getCallingUid(), mMockContext.getPackageName());

        IResourceOveruseListener mockListener = createMockResourceOveruseListener();
        IBinder mockBinder = mockListener.asBinder();

        mCarWatchdogService.addResourceOveruseListener(CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                mockListener);

        verify(mockBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        injectIoOveruseStatsForPackages(
                mGenericPackageNameByUid, /* killablePackages= */new ArraySet<>(),
                /* shouldNotifyPackages= */new ArraySet<>(
                        Collections.singleton(mMockContext.getPackageName())));

        verify(mockListener).onOveruse(any());

        mCarWatchdogService.removeResourceOveruseListener(mockListener);

        verify(mockListener, atLeastOnce()).asBinder();
        verify(mockBinder).unlinkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        injectIoOveruseStatsForPackages(
                mGenericPackageNameByUid, /* killablePackages= */new ArraySet<>(),
                /* shouldNotifyPackages= */new ArraySet<>(
                        Collections.singletonList(mMockContext.getPackageName())));

        verifyNoMoreInteractions(mockListener);
    }

    @Test
    public void testDuplicateAddResourceOveruseListener() throws Exception {
        mGenericPackageNameByUid.put(Binder.getCallingUid(), mMockContext.getPackageName());

        IResourceOveruseListener mockListener = createMockResourceOveruseListener();
        IBinder mockBinder = mockListener.asBinder();

        mCarWatchdogService.addResourceOveruseListener(CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                mockListener);

        assertThrows(IllegalStateException.class,
                () -> mCarWatchdogService.addResourceOveruseListener(
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO, mockListener));

        verify(mockBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        mCarWatchdogService.removeResourceOveruseListener(mockListener);

        verify(mockListener, atLeastOnce()).asBinder();
        verify(mockBinder).unlinkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        verifyNoMoreInteractions(mockListener);
    }

    @Test
    public void testAddMultipleResourceOveruseListeners() throws Exception {
        mGenericPackageNameByUid.put(Binder.getCallingUid(), mMockContext.getPackageName());

        IResourceOveruseListener firstMockListener = createMockResourceOveruseListener();
        IBinder firstMockBinder = firstMockListener.asBinder();
        IResourceOveruseListener secondMockListener = createMockResourceOveruseListener();
        IBinder secondMockBinder = secondMockListener.asBinder();

        mCarWatchdogService.addResourceOveruseListener(CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                firstMockListener);
        mCarWatchdogService.addResourceOveruseListener(CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                secondMockListener);

        verify(firstMockBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
        verify(secondMockBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        injectIoOveruseStatsForPackages(
                mGenericPackageNameByUid, /* killablePackages= */new ArraySet<>(),
                /* shouldNotifyPackages= */new ArraySet<>(
                        Collections.singleton(mMockContext.getPackageName())));

        verify(firstMockListener).onOveruse(any());

        mCarWatchdogService.removeResourceOveruseListener(firstMockListener);

        verify(firstMockListener, atLeastOnce()).asBinder();
        verify(firstMockBinder).unlinkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        injectIoOveruseStatsForPackages(
                mGenericPackageNameByUid, /* killablePackages= */new ArraySet<>(),
                /* shouldNotifyPackages= */new ArraySet<>(
                        Collections.singletonList(mMockContext.getPackageName())));

        verify(secondMockListener, times(2)).onOveruse(any());

        mCarWatchdogService.removeResourceOveruseListener(secondMockListener);

        verify(secondMockListener, atLeastOnce()).asBinder();
        verify(secondMockBinder).unlinkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        injectIoOveruseStatsForPackages(
                mGenericPackageNameByUid, /* killablePackages= */new ArraySet<>(),
                /* shouldNotifyPackages= */new ArraySet<>(
                        Collections.singletonList(mMockContext.getPackageName())));

        verifyNoMoreInteractions(firstMockListener);
        verifyNoMoreInteractions(secondMockListener);
    }

    @Test
    public void testAddResourceOveruseListenerForSystemThrowsWithInvalidFlag() throws Exception {
        IResourceOveruseListener mockListener = createMockResourceOveruseListener();
        assertThrows(IllegalArgumentException.class, () -> {
            mCarWatchdogService.addResourceOveruseListenerForSystem(0, mockListener);
        });
    }

    @Test
    public void testResourceOveruseListenerForSystem() throws Exception {
        int callingUid = Binder.getCallingUid();
        mGenericPackageNameByUid.put(callingUid, "system_package.critical");

        IResourceOveruseListener mockListener = createMockResourceOveruseListener();
        mCarWatchdogService.addResourceOveruseListenerForSystem(
                CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO, mockListener);

        IBinder mockBinder = mockListener.asBinder();
        verify(mockBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        List<PackageIoOveruseStats> packageIoOveruseStats = Collections.singletonList(
                constructPackageIoOveruseStats(callingUid, /* shouldNotify= */true,
                        constructInternalIoOveruseStats(/* killableOnOveruse= */true,
                                /* remainingWriteBytes= */constructPerStateBytes(20, 20, 20),
                                /* writtenBytes= */constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */2)));

        mWatchdogServiceForSystemImpl.latestIoOveruseStats(packageIoOveruseStats);

        verify(mockListener).onOveruse(any());

        mCarWatchdogService.removeResourceOveruseListenerForSystem(mockListener);

        verify(mockListener, atLeastOnce()).asBinder();
        verify(mockBinder).unlinkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        mWatchdogServiceForSystemImpl.latestIoOveruseStats(packageIoOveruseStats);

        verifyNoMoreInteractions(mockListener);
    }

    @Test
    public void testSetKillablePackageAsUser() throws Exception {
        mockUmGetAliveUsers(mMockUserManager, 11, 12);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("third_party_package", 1103456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 1101278, null),
                constructPackageManagerPackageInfo("third_party_package", 1203456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 1201278, null)));

        UserHandle userHandle = new UserHandle(11);
        mCarWatchdogService.setKillablePackageAsUser("third_party_package", userHandle,
                /* isKillable= */ false);
        mCarWatchdogService.setKillablePackageAsUser("vendor_package.critical",
                userHandle, /* isKillable= */ false);

        PackageKillableStateSubject.assertThat(
                mCarWatchdogService.getPackageKillableStatesAsUser(UserHandle.ALL)).containsExactly(
                new PackageKillableState("third_party_package", 11,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("vendor_package.critical", 11,
                        PackageKillableState.KILLABLE_STATE_NEVER),
                new PackageKillableState("third_party_package", 12,
                        PackageKillableState.KILLABLE_STATE_YES),
                new PackageKillableState("vendor_package.critical", 12,
                        PackageKillableState.KILLABLE_STATE_NEVER));

        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogService.setKillablePackageAsUser("vendor_package.critical",
                        userHandle, /* isKillable= */ true));

        mockUmGetAliveUsers(mMockUserManager, 11, 12, 13);
        injectPackageInfos(Collections.singletonList(
                constructPackageManagerPackageInfo("third_party_package", 1303456, null)));

        PackageKillableStateSubject.assertThat(
                mCarWatchdogService.getPackageKillableStatesAsUser(UserHandle.ALL)).containsExactly(
                new PackageKillableState("third_party_package", 11,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("vendor_package.critical", 11,
                        PackageKillableState.KILLABLE_STATE_NEVER),
                new PackageKillableState("third_party_package", 12,
                        PackageKillableState.KILLABLE_STATE_YES),
                new PackageKillableState("vendor_package.critical", 12,
                        PackageKillableState.KILLABLE_STATE_NEVER),
                new PackageKillableState("third_party_package", 13,
                        PackageKillableState.KILLABLE_STATE_YES));
    }

    @Test
    public void testSetKillablePackageAsUserWithSharedUids() throws Exception {
        mockUmGetAliveUsers(mMockUserManager, 11, 12);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "third_party_package.A", 1103456, "third_party_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "third_party_package.B", 1103456, "third_party_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "third_party_package.C", 1101356, "third_party_shared_package.B"),
                constructPackageManagerPackageInfo(
                        "third_party_package.D", 1101356, "third_party_shared_package.B")));

        UserHandle userHandle = new UserHandle(11);
        mCarWatchdogService.setKillablePackageAsUser("third_party_package.A", userHandle,
                /* isKillable= */ false);

        PackageKillableStateSubject.assertThat(
                mCarWatchdogService.getPackageKillableStatesAsUser(UserHandle.ALL)).containsExactly(
                new PackageKillableState("third_party_package.A", 11,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("third_party_package.B", 11,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("third_party_package.C", 11,
                        PackageKillableState.KILLABLE_STATE_YES),
                new PackageKillableState("third_party_package.D", 11,
                        PackageKillableState.KILLABLE_STATE_YES));

        mCarWatchdogService.setKillablePackageAsUser("third_party_package.B", userHandle,
                /* isKillable= */ true);
        mCarWatchdogService.setKillablePackageAsUser("third_party_package.C", userHandle,
                /* isKillable= */ false);

        PackageKillableStateSubject.assertThat(
                mCarWatchdogService.getPackageKillableStatesAsUser(UserHandle.ALL)).containsExactly(
                new PackageKillableState("third_party_package.A", 11,
                        PackageKillableState.KILLABLE_STATE_YES),
                new PackageKillableState("third_party_package.B", 11,
                        PackageKillableState.KILLABLE_STATE_YES),
                new PackageKillableState("third_party_package.C", 11,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("third_party_package.D", 11,
                        PackageKillableState.KILLABLE_STATE_NO));
    }

    @Test
    public void testSetKillablePackageAsUserForAllUsers() throws Exception {
        mockUmGetAliveUsers(mMockUserManager, 11, 12);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("third_party_package", 1103456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 1101278, null),
                constructPackageManagerPackageInfo("third_party_package", 1203456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 1201278, null)));

        mCarWatchdogService.setKillablePackageAsUser("third_party_package", UserHandle.ALL,
                /* isKillable= */ false);
        mCarWatchdogService.setKillablePackageAsUser("vendor_package.critical",
                UserHandle.ALL, /* isKillable= */ false);

        PackageKillableStateSubject.assertThat(
                mCarWatchdogService.getPackageKillableStatesAsUser(UserHandle.ALL)).containsExactly(
                new PackageKillableState("third_party_package", 11,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("vendor_package.critical", 11,
                        PackageKillableState.KILLABLE_STATE_NEVER),
                new PackageKillableState("third_party_package", 12,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("vendor_package.critical", 12,
                        PackageKillableState.KILLABLE_STATE_NEVER));

        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogService.setKillablePackageAsUser("vendor_package.critical",
                        UserHandle.ALL, /* isKillable= */ true));

        mockUmGetAliveUsers(mMockUserManager, 11, 12, 13);
        injectPackageInfos(Collections.singletonList(
                constructPackageManagerPackageInfo("third_party_package", 1303456, null)));

        PackageKillableStateSubject.assertThat(
                mCarWatchdogService.getPackageKillableStatesAsUser(UserHandle.ALL)).containsExactly(
                new PackageKillableState("third_party_package", 11,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("vendor_package.critical", 11,
                        PackageKillableState.KILLABLE_STATE_NEVER),
                new PackageKillableState("third_party_package", 12,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("vendor_package.critical", 12,
                        PackageKillableState.KILLABLE_STATE_NEVER),
                new PackageKillableState("third_party_package", 13,
                        PackageKillableState.KILLABLE_STATE_NO));
    }

    @Test
    public void testSetKillablePackageAsUsersForAllUsersWithSharedUids() throws Exception {
        mockUmGetAliveUsers(mMockUserManager, 11, 12);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "third_party_package.A", 1103456, "third_party_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "third_party_package.B", 1103456, "third_party_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "third_party_package.C", 1101356, "third_party_shared_package.B"),
                constructPackageManagerPackageInfo(
                        "third_party_package.D", 1101356, "third_party_shared_package.B"),
                constructPackageManagerPackageInfo(
                        "third_party_package.A", 1203456, "third_party_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "third_party_package.B", 1203456, "third_party_shared_package.A")));

        mCarWatchdogService.setKillablePackageAsUser("third_party_package.A", UserHandle.ALL,
                /* isKillable= */ false);

        PackageKillableStateSubject.assertThat(
                mCarWatchdogService.getPackageKillableStatesAsUser(UserHandle.ALL)).containsExactly(
                new PackageKillableState("third_party_package.A", 11,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("third_party_package.B", 11,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("third_party_package.C", 11,
                        PackageKillableState.KILLABLE_STATE_YES),
                new PackageKillableState("third_party_package.D", 11,
                        PackageKillableState.KILLABLE_STATE_YES),
                new PackageKillableState("third_party_package.A", 12,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("third_party_package.B", 12,
                        PackageKillableState.KILLABLE_STATE_NO));

        mockUmGetAliveUsers(mMockUserManager, 11, 12, 13);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "third_party_package.A", 1303456, "third_party_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "third_party_package.B", 1303456, "third_party_shared_package.A")));

        PackageKillableStateSubject.assertThat(
                mCarWatchdogService.getPackageKillableStatesAsUser(new UserHandle(13)))
                .containsExactly(
                new PackageKillableState("third_party_package.A", 13,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("third_party_package.B", 13,
                        PackageKillableState.KILLABLE_STATE_NO));
    }

    @Test
    public void testGetPackageKillableStatesAsUser() throws Exception {
        mockUmGetAliveUsers(mMockUserManager, 11, 12);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("third_party_package", 1103456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 1101278, null),
                constructPackageManagerPackageInfo("third_party_package", 1203456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 1201278, null)));

        PackageKillableStateSubject.assertThat(
                mCarWatchdogService.getPackageKillableStatesAsUser(new UserHandle(11)))
                .containsExactly(
                        new PackageKillableState("third_party_package", 11,
                                PackageKillableState.KILLABLE_STATE_YES),
                        new PackageKillableState("vendor_package.critical", 11,
                                PackageKillableState.KILLABLE_STATE_NEVER));
    }

    @Test
    public void testGetPackageKillableStatesAsUserWithSharedUids() throws Exception {
        mockUmGetAliveUsers(mMockUserManager, 11, 12);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "system_package.A", 1103456, "vendor_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "vendor_package.B", 1103456, "vendor_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "third_party_package.C", 1105678, "third_party_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.D", 1105678, "third_party_shared_package"),
                constructPackageManagerPackageInfo(
                        "system_package.A", 1203456, "vendor_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "vendor_package.B", 1203456, "vendor_shared_package.A")));

        PackageKillableStateSubject.assertThat(
                mCarWatchdogService.getPackageKillableStatesAsUser(new UserHandle(11)))
                .containsExactly(
                        new PackageKillableState("system_package.A", 11,
                                PackageKillableState.KILLABLE_STATE_NEVER),
                        new PackageKillableState("vendor_package.B", 11,
                                PackageKillableState.KILLABLE_STATE_NEVER),
                        new PackageKillableState("third_party_package.C", 11,
                                PackageKillableState.KILLABLE_STATE_YES),
                        new PackageKillableState("third_party_package.D", 11,
                                PackageKillableState.KILLABLE_STATE_YES));
    }

    @Test
    public void testGetPackageKillableStatesAsUserForAllUsers() throws Exception {
        mockUmGetAliveUsers(mMockUserManager, 11, 12);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("third_party_package", 1103456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 1101278, null),
                constructPackageManagerPackageInfo("third_party_package", 1203456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 1201278, null)));

        PackageKillableStateSubject.assertThat(
                mCarWatchdogService.getPackageKillableStatesAsUser(UserHandle.ALL)).containsExactly(
                new PackageKillableState("third_party_package", 11,
                        PackageKillableState.KILLABLE_STATE_YES),
                new PackageKillableState("vendor_package.critical", 11,
                        PackageKillableState.KILLABLE_STATE_NEVER),
                new PackageKillableState("third_party_package", 12,
                        PackageKillableState.KILLABLE_STATE_YES),
                new PackageKillableState("vendor_package.critical", 12,
                        PackageKillableState.KILLABLE_STATE_NEVER));
    }

    @Test
    public void testGetPackageKillableStatesAsUserForAllUsersWithSharedUids() throws Exception {
        mockUmGetAliveUsers(mMockUserManager, 11, 12);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "system_package.A", 1103456, "vendor_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "vendor_package.B", 1103456, "vendor_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "third_party_package.C", 1105678, "third_party_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.D", 1105678, "third_party_shared_package"),
                constructPackageManagerPackageInfo(
                        "system_package.A", 1203456, "vendor_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "vendor_package.B", 1203456, "vendor_shared_package.A")));

        PackageKillableStateSubject.assertThat(
                mCarWatchdogService.getPackageKillableStatesAsUser(UserHandle.ALL))
                .containsExactly(
                        new PackageKillableState("system_package.A", 11,
                                PackageKillableState.KILLABLE_STATE_NEVER),
                        new PackageKillableState("vendor_package.B", 11,
                                PackageKillableState.KILLABLE_STATE_NEVER),
                        new PackageKillableState("third_party_package.C", 11,
                                PackageKillableState.KILLABLE_STATE_YES),
                        new PackageKillableState("third_party_package.D", 11,
                                PackageKillableState.KILLABLE_STATE_YES),
                        new PackageKillableState("system_package.A", 12,
                                PackageKillableState.KILLABLE_STATE_NEVER),
                        new PackageKillableState("vendor_package.B", 12,
                                PackageKillableState.KILLABLE_STATE_NEVER));
    }

    @Test
    public void testSetResourceOveruseConfigurations() throws Exception {
        assertThat(mCarWatchdogService.setResourceOveruseConfigurations(
                sampleResourceOveruseConfigurations(), CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO))
                .isEqualTo(CarWatchdogManager.RETURN_CODE_SUCCESS);

        InternalResourceOveruseConfigurationSubject
                .assertThat(captureOnSetResourceOveruseConfigurations())
                .containsExactlyElementsIn(sampleInternalResourceOveruseConfigurations());
    }

    @Test
    public void testSetResourceOveruseConfigurationsRetriedWithDisconnectedDaemon()
            throws Exception {
        crashWatchdogDaemon();

        assertThat(mCarWatchdogService.setResourceOveruseConfigurations(
                sampleResourceOveruseConfigurations(), CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO))
                .isEqualTo(CarWatchdogManager.RETURN_CODE_SUCCESS);

        restartWatchdogDaemonAndAwait();

        InternalResourceOveruseConfigurationSubject
                .assertThat(captureOnSetResourceOveruseConfigurations())
                .containsExactlyElementsIn(sampleInternalResourceOveruseConfigurations());
    }

    @Test
    public void testSetResourceOveruseConfigurationsRetriedWithRepeatedRemoteException()
            throws Exception {
        CountDownLatch crashLatch = new CountDownLatch(2);
        doAnswer(args -> {
            crashWatchdogDaemon();
            crashLatch.countDown();
            throw new RemoteException();
        }).when(mMockCarWatchdogDaemon).updateResourceOveruseConfigurations(anyList());

        assertThat(mCarWatchdogService.setResourceOveruseConfigurations(
                sampleResourceOveruseConfigurations(), CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO))
                .isEqualTo(CarWatchdogManager.RETURN_CODE_SUCCESS);

        restartWatchdogDaemonAndAwait();

        /*
         * Wait until the daemon is crashed again during the latest
         * updateResourceOveruseConfigurations call so the test is deterministic.
         */
        crashLatch.await(MAX_WAIT_TIME_MS, TimeUnit.MILLISECONDS);

        /* The below final restart should set the resource overuse configurations successfully. */
        List<android.automotive.watchdog.internal.ResourceOveruseConfiguration> actualConfigs =
                new ArrayList<>();
        doAnswer(args -> {
            List<android.automotive.watchdog.internal.ResourceOveruseConfiguration> configs =
                    args.getArgument(0);
            synchronized (actualConfigs) {
                actualConfigs.addAll(configs);
                actualConfigs.notify();
            }
            return null;
        }).when(mMockCarWatchdogDaemon).updateResourceOveruseConfigurations(anyList());

        restartWatchdogDaemonAndAwait();

        /* Wait until latest updateResourceOveruseConfigurations call is issued on reconnection. */
        synchronized (actualConfigs) {
            actualConfigs.wait(MAX_WAIT_TIME_MS);
            InternalResourceOveruseConfigurationSubject.assertThat(actualConfigs)
                    .containsExactlyElementsIn(sampleInternalResourceOveruseConfigurations());
        }

        verify(mMockCarWatchdogDaemon, times(3)).updateResourceOveruseConfigurations(anyList());
    }

    @Test
    public void testFailsSetResourceOveruseConfigurationsWithPendingRequest()
            throws Exception {
        crashWatchdogDaemon();

        assertThat(mCarWatchdogService.setResourceOveruseConfigurations(
                sampleResourceOveruseConfigurations(), CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO))
                .isEqualTo(CarWatchdogManager.RETURN_CODE_SUCCESS);

        assertThrows(IllegalStateException.class,
                () -> mCarWatchdogService.setResourceOveruseConfigurations(
                        sampleResourceOveruseConfigurations(),
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO));
    }

    @Test
    public void testFailsSetResourceOveruseConfigurationsOnInvalidArgs() throws Exception {
        assertThrows(NullPointerException.class,
                () -> mCarWatchdogService.setResourceOveruseConfigurations(null,
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO));

        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogService.setResourceOveruseConfigurations(new ArrayList<>(),
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO));

        List<ResourceOveruseConfiguration> resourceOveruseConfigs = Collections.singletonList(
                sampleResourceOveruseConfigurationBuilder(ComponentType.SYSTEM,
                        sampleIoOveruseConfigurationBuilder(ComponentType.SYSTEM).build())
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogService.setResourceOveruseConfigurations(resourceOveruseConfigs,
                        0));
    }

    @Test
    public void testFailsSetResourceOveruseConfigurationsOnDuplicateComponents() throws Exception {
        ResourceOveruseConfiguration config =
                sampleResourceOveruseConfigurationBuilder(ComponentType.SYSTEM,
                        sampleIoOveruseConfigurationBuilder(ComponentType.SYSTEM).build()).build();
        List<ResourceOveruseConfiguration> resourceOveruseConfigs = Arrays.asList(config, config);
        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogService.setResourceOveruseConfigurations(resourceOveruseConfigs,
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO));
    }

    @Test
    public void testFailsSetResourceOveruseConfigurationsOnZeroComponentLevelIoOveruseThresholds()
            throws Exception {
        List<ResourceOveruseConfiguration> resourceOveruseConfigs =
                Collections.singletonList(
                        sampleResourceOveruseConfigurationBuilder(ComponentType.SYSTEM,
                                sampleIoOveruseConfigurationBuilder(ComponentType.SYSTEM)
                                        .setComponentLevelThresholds(new PerStateBytes(200, 0, 200))
                                        .build())
                                .build());
        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogService.setResourceOveruseConfigurations(resourceOveruseConfigs,
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO));
    }

    @Test
    public void testFailsSetResourceOveruseConfigurationsOnEmptyIoOveruseSystemWideThresholds()
            throws Exception {
        List<ResourceOveruseConfiguration> resourceOveruseConfigs =
                Collections.singletonList(
                        sampleResourceOveruseConfigurationBuilder(ComponentType.SYSTEM,
                                sampleIoOveruseConfigurationBuilder(ComponentType.SYSTEM)
                                        .setSystemWideThresholds(new ArrayList<>())
                                        .build())
                                .build());
        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogService.setResourceOveruseConfigurations(resourceOveruseConfigs,
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO));
    }

    @Test
    public void testFailsSetResourceOveruseConfigurationsOnIoOveruseInvalidSystemWideThreshold()
            throws Exception {
        List<ResourceOveruseConfiguration> resourceOveruseConfigs = new ArrayList<>();
        resourceOveruseConfigs.add(sampleResourceOveruseConfigurationBuilder(ComponentType.SYSTEM,
                sampleIoOveruseConfigurationBuilder(ComponentType.SYSTEM)
                        .setSystemWideThresholds(Collections.singletonList(
                                new IoOveruseAlertThreshold(30, 0)))
                        .build())
                .build());
        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogService.setResourceOveruseConfigurations(
                        resourceOveruseConfigs,
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO));

        resourceOveruseConfigs.set(0,
                sampleResourceOveruseConfigurationBuilder(ComponentType.SYSTEM,
                        sampleIoOveruseConfigurationBuilder(ComponentType.SYSTEM)
                                .setSystemWideThresholds(Collections.singletonList(
                                        new IoOveruseAlertThreshold(0, 300)))
                                .build())
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogService.setResourceOveruseConfigurations(
                        resourceOveruseConfigs,
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO));
    }

    @Test
    public void testFailsSetResourceOveruseConfigurationsOnNullIoOveruseConfiguration()
            throws Exception {
        List<ResourceOveruseConfiguration> resourceOveruseConfigs = Collections.singletonList(
                sampleResourceOveruseConfigurationBuilder(ComponentType.SYSTEM, null).build());
        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogService.setResourceOveruseConfigurations(resourceOveruseConfigs,
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO));
    }

    @Test
    public void testGetResourceOveruseConfigurations() throws Exception {
        when(mMockCarWatchdogDaemon.getResourceOveruseConfigurations())
                .thenReturn(sampleInternalResourceOveruseConfigurations());

        List<ResourceOveruseConfiguration> actualConfigs =
                mCarWatchdogService.getResourceOveruseConfigurations(
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO);

        ResourceOveruseConfigurationSubject.assertThat(actualConfigs)
                .containsExactlyElementsIn(sampleResourceOveruseConfigurations());
    }

    @Test
    public void testGetResourceOveruseConfigurationsWithDisconnectedDaemon() throws Exception {
        crashWatchdogDaemon();

        assertThrows(IllegalStateException.class,
                () -> mCarWatchdogService.getResourceOveruseConfigurations(
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO));

        verify(mMockCarWatchdogDaemon, never()).getResourceOveruseConfigurations();
    }

    @Test
    public void testGetResourceOveruseConfigurationsWithReconnectedDaemon() throws Exception {
        /*
         * Emulate daemon crash and restart during the get request. The below get request should be
         * waiting for daemon connection before the first call to ServiceManager.getService. But to
         * make sure the test is deterministic emulate daemon restart only on the second call to
         * ServiceManager.getService.
         */
        doReturn(null)
                .doReturn(mMockBinder)
                .when(() -> ServiceManager.getService(CAR_WATCHDOG_DAEMON_INTERFACE));
        mCarWatchdogDaemonBinderDeathRecipient.binderDied();

        when(mMockCarWatchdogDaemon.getResourceOveruseConfigurations())
                .thenReturn(sampleInternalResourceOveruseConfigurations());

        List<ResourceOveruseConfiguration> actualConfigs =
                mCarWatchdogService.getResourceOveruseConfigurations(
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO);

        ResourceOveruseConfigurationSubject.assertThat(actualConfigs)
                .containsExactlyElementsIn(sampleResourceOveruseConfigurations());
    }

    @Test
    public void testConcurrentSetGetResourceOveruseConfigurationsWithReconnectedDaemon()
            throws Exception {
        /*
         * Emulate daemon crash and restart during the get and set requests. The below get request
         * should be waiting for daemon connection before the first call to
         * ServiceManager.getService. But to make sure the test is deterministic emulate daemon
         * restart only on the second call to ServiceManager.getService.
         */
        doReturn(null)
                .doReturn(mMockBinder)
                .when(() -> ServiceManager.getService(CAR_WATCHDOG_DAEMON_INTERFACE));
        mCarWatchdogDaemonBinderDeathRecipient.binderDied();

        /* Capture and respond with the configuration received in the set request. */
        List<android.automotive.watchdog.internal.ResourceOveruseConfiguration> internalConfigs =
                new ArrayList<>();
        doAnswer(args -> {
            List<android.automotive.watchdog.internal.ResourceOveruseConfiguration> configs =
                    args.getArgument(0);
            internalConfigs.addAll(configs);
            return null;
        }).when(mMockCarWatchdogDaemon).updateResourceOveruseConfigurations(anyList());
        when(mMockCarWatchdogDaemon.getResourceOveruseConfigurations()).thenReturn(internalConfigs);

        /* Start a set request that will become pending and a blocking get request. */
        List<ResourceOveruseConfiguration> setConfigs = sampleResourceOveruseConfigurations();
        assertThat(mCarWatchdogService.setResourceOveruseConfigurations(
                setConfigs, CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO))
                .isEqualTo(CarWatchdogManager.RETURN_CODE_SUCCESS);

        List<ResourceOveruseConfiguration> getConfigs =
                mCarWatchdogService.getResourceOveruseConfigurations(
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO);

        ResourceOveruseConfigurationSubject.assertThat(getConfigs)
                .containsExactlyElementsIn(setConfigs);
    }

    @Test
    public void testFailsGetResourceOveruseConfigurationsOnInvalidArgs() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogService.getResourceOveruseConfigurations(0));
    }

    @Test
    public void testLatestIoOveruseStats() throws Exception {
        int criticalSysPkgUid = Binder.getCallingUid();
        int nonCriticalSysPkgUid = getUid(1056);
        int nonCriticalVndrPkgUid = getUid(2564);
        int thirdPartyPkgUid = getUid(2044);

        mCarWatchdogService.setResourceOveruseKillingDelay(1000);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "system_package.critical", criticalSysPkgUid, null),
                constructPackageManagerPackageInfo(
                        "system_package.non_critical", nonCriticalSysPkgUid, null),
                constructPackageManagerPackageInfo(
                        "vendor_package.non_critical", nonCriticalVndrPkgUid, null),
                constructPackageManagerPackageInfo(
                        "third_party_package", thirdPartyPkgUid, null)));

        IResourceOveruseListener mockSystemListener = createMockResourceOveruseListener();
        mCarWatchdogService.addResourceOveruseListenerForSystem(
                CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO, mockSystemListener);

        IResourceOveruseListener mockListener = createMockResourceOveruseListener();
        mCarWatchdogService.addResourceOveruseListener(
                CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO, mockListener);

        IPackageManager packageManagerService = Mockito.spy(ActivityThread.getPackageManager());
        when(ActivityThread.getPackageManager()).thenReturn(packageManagerService);
        mockApplicationEnabledSettingAccessors(packageManagerService);

        List<PackageIoOveruseStats> packageIoOveruseStats = Arrays.asList(
                /* Overuse occurred but cannot be killed/disabled. */
                constructPackageIoOveruseStats(criticalSysPkgUid, /* shouldNotify= */true,
                        constructInternalIoOveruseStats(/* killableOnOveruse= */false,
                                /* remainingWriteBytes= */constructPerStateBytes(0, 0, 0),
                                /* writtenBytes= */constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */2)),
                /* No overuse occurred but should be notified. */
                constructPackageIoOveruseStats(nonCriticalSysPkgUid, /* shouldNotify= */true,
                        constructInternalIoOveruseStats(/* killableOnOveruse= */true,
                                /* remainingWriteBytes= */constructPerStateBytes(20, 30, 40),
                                /* writtenBytes= */constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */2)),
                /* Neither overuse occurred nor be notified. */
                constructPackageIoOveruseStats(nonCriticalVndrPkgUid, /* shouldNotify= */false,
                        constructInternalIoOveruseStats(/* killableOnOveruse= */true,
                                /* remainingWriteBytes= */constructPerStateBytes(200, 300, 400),
                                /* writtenBytes= */constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */2)),
                /* Overuse occurred and can be killed/disabled. */
                constructPackageIoOveruseStats(thirdPartyPkgUid, /* shouldNotify= */true,
                        constructInternalIoOveruseStats(/* killableOnOveruse= */true,
                                /* remainingWriteBytes= */constructPerStateBytes(0, 0, 0),
                                /* writtenBytes= */constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */2)));

        mWatchdogServiceForSystemImpl.latestIoOveruseStats(packageIoOveruseStats);

        /*
         * Handling of packages that exceed I/O thresholds is done on the main thread. To ensure
         * the handling completes before verification, wait for the message to be posted on the
         * main thread and execute an empty block on the main thread.
         */
        delayedRunOnMainSync(() -> {}, /* delayMillis= */2000);

        List<ResourceOveruseStats> expectedStats = new ArrayList<>();

        expectedStats.add(constructResourceOveruseStats(criticalSysPkgUid,
                "system_package.critical", packageIoOveruseStats.get(0).ioOveruseStats));

        verifyOnOveruseCalled(expectedStats, mockListener);

        expectedStats.add(constructResourceOveruseStats(nonCriticalSysPkgUid,
                "system_package.non_critical", packageIoOveruseStats.get(1).ioOveruseStats));

        expectedStats.add(constructResourceOveruseStats(thirdPartyPkgUid,
                "third_party_package",
                packageIoOveruseStats.get(3).ioOveruseStats));

        verifyOnOveruseCalled(expectedStats, mockSystemListener);

        verify(packageManagerService).getApplicationEnabledSetting(
                eq("third_party_package"),
                eq(UserHandle.getUserId(thirdPartyPkgUid)));
        verify(packageManagerService).setApplicationEnabledSetting(
                eq("third_party_package"), anyInt(), anyInt(),
                eq(UserHandle.getUserId(thirdPartyPkgUid)), anyString());

        List<PackageResourceOveruseAction> expectedActions = Arrays.asList(
                constructPackageResourceOveruseAction(
                        "system_package.critical",
                        criticalSysPkgUid, new int[]{ResourceType.IO}, NOT_KILLED),
                constructPackageResourceOveruseAction(
                        "third_party_package",
                        thirdPartyPkgUid, new int[]{ResourceType.IO}, KILLED));
        verifyActionsTakenOnResourceOveruse(expectedActions);
    }

    @Test
    public void testLatestIoOveruseStatsWithSharedUid() throws Exception {
        int criticalSysSharedUid = Binder.getCallingUid();
        int nonCriticalVndrSharedUid = getUid(2564);
        int thirdPartySharedUid = getUid(2044);

        mCarWatchdogService.setResourceOveruseKillingDelay(1000);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "system_package.A", criticalSysSharedUid, "system_shared_package"),
                constructPackageManagerPackageInfo(
                        "system_package.B", criticalSysSharedUid, "system_shared_package"),
                constructPackageManagerPackageInfo("vendor_package.non_critical",
                        nonCriticalVndrSharedUid, "vendor_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.A", thirdPartySharedUid, "third_party_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.B", thirdPartySharedUid, "third_party_shared_package")
        ));

        IResourceOveruseListener mockSystemListener = createMockResourceOveruseListener();
        mCarWatchdogService.addResourceOveruseListenerForSystem(
                CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO, mockSystemListener);

        IResourceOveruseListener mockListener = createMockResourceOveruseListener();
        mCarWatchdogService.addResourceOveruseListener(
                CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO, mockListener);

        IPackageManager packageManagerService = Mockito.spy(ActivityThread.getPackageManager());
        when(ActivityThread.getPackageManager()).thenReturn(packageManagerService);
        mockApplicationEnabledSettingAccessors(packageManagerService);

        List<PackageIoOveruseStats> packageIoOveruseStats = Arrays.asList(
                /* Overuse occurred but cannot be killed/disabled. */
                constructPackageIoOveruseStats(criticalSysSharedUid, /* shouldNotify= */true,
                        constructInternalIoOveruseStats(/* killableOnOveruse= */false,
                                /* remainingWriteBytes= */constructPerStateBytes(0, 0, 0),
                                /* writtenBytes= */constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */2)),
                /* No overuse occurred but should be notified. */
                constructPackageIoOveruseStats(nonCriticalVndrSharedUid, /* shouldNotify= */true,
                        constructInternalIoOveruseStats(/* killableOnOveruse= */true,
                                /* remainingWriteBytes= */constructPerStateBytes(200, 300, 400),
                                /* writtenBytes= */constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */2)),
                /* Overuse occurred and can be killed/disabled. */
                constructPackageIoOveruseStats(thirdPartySharedUid, /* shouldNotify= */true,
                        constructInternalIoOveruseStats(/* killableOnOveruse= */true,
                                /* remainingWriteBytes= */constructPerStateBytes(0, 0, 0),
                                /* writtenBytes= */constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */2)));

        mWatchdogServiceForSystemImpl.latestIoOveruseStats(packageIoOveruseStats);

        /*
         * Handling of packages that exceed I/O thresholds is done on the main thread. To ensure
         * the handling completes before verification, wait for the message to be posted on the
         * main thread and execute an empty block on the main thread.
         */
        delayedRunOnMainSync(() -> {}, /* delayMillis= */2000);

        List<ResourceOveruseStats> expectedStats = new ArrayList<>();

        expectedStats.add(constructResourceOveruseStats(criticalSysSharedUid,
                "shared:system_shared_package", packageIoOveruseStats.get(0).ioOveruseStats));

        verifyOnOveruseCalled(expectedStats, mockListener);

        expectedStats.add(constructResourceOveruseStats(nonCriticalVndrSharedUid,
                "shared:vendor_shared_package", packageIoOveruseStats.get(1).ioOveruseStats));

        expectedStats.add(constructResourceOveruseStats(thirdPartySharedUid,
                "shared:third_party_shared_package",
                packageIoOveruseStats.get(2).ioOveruseStats));

        verifyOnOveruseCalled(expectedStats, mockSystemListener);

        verify(packageManagerService).getApplicationEnabledSetting(
                eq("third_party_package.A"),
                eq(UserHandle.getUserId(thirdPartySharedUid)));
        verify(packageManagerService).getApplicationEnabledSetting(
                eq("third_party_package.B"),
                eq(UserHandle.getUserId(thirdPartySharedUid)));
        verify(packageManagerService).setApplicationEnabledSetting(
                eq("third_party_package.A"), anyInt(), anyInt(),
                eq(UserHandle.getUserId(thirdPartySharedUid)), anyString());
        verify(packageManagerService).setApplicationEnabledSetting(
                eq("third_party_package.B"), anyInt(), anyInt(),
                eq(UserHandle.getUserId(thirdPartySharedUid)), anyString());

        List<PackageResourceOveruseAction> expectedActions = Arrays.asList(
                constructPackageResourceOveruseAction(
                        "shared:system_shared_package",
                        criticalSysSharedUid, new int[]{ResourceType.IO}, NOT_KILLED),
                constructPackageResourceOveruseAction(
                        "shared:third_party_shared_package",
                        thirdPartySharedUid, new int[]{ResourceType.IO}, KILLED));
        verifyActionsTakenOnResourceOveruse(expectedActions);
    }

    @Test
    public void testLatestIoOveruseStatsWithUserOptedOutPackage() throws Exception {
        // TODO(b/170741935): Test that the user opted out package is not killed on overuse.
    }

    @Test
    public void testLatestIoOveruseStatsWithRecurringOveruse() throws Exception {
        /*
         * TODO(b/170741935): Test that non-critical packages are killed on recurring overuse
         *  regardless of user settings.
         */
    }

    @Test
    public void testResetResourceOveruseStats() throws Exception {
        mGenericPackageNameByUid.put(Binder.getCallingUid(), mMockContext.getPackageName());
        mGenericPackageNameByUid.put(1101278, "vendor_package.critical");
        injectIoOveruseStatsForPackages(
                mGenericPackageNameByUid, /* killablePackages= */new ArraySet<>(),
                /* shouldNotifyPackages= */new ArraySet<>());

        mWatchdogServiceForSystemImpl.resetResourceOveruseStats(
                Collections.singletonList(mMockContext.getPackageName()));

        ResourceOveruseStats actualStats = mCarWatchdogService.getResourceOveruseStats(
                CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        ResourceOveruseStats expectedStats = new ResourceOveruseStats.Builder(
                mMockContext.getPackageName(),
                UserHandle.getUserHandleForUid(Binder.getCallingUid())).build();

        assertWithMessage("Expected: " + expectedStats.toString() + "\nActual: "
                + actualStats.toString())
                .that(ResourceOveruseStatsSubject.isEquals(actualStats, expectedStats)).isTrue();
    }

    @Test
    public void testGetPackageInfosForUids() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "system_package.A", 6001, null, ApplicationInfo.FLAG_SYSTEM, 0),
                constructPackageManagerPackageInfo(
                        "vendor_package.B", 5100, null, 0, ApplicationInfo.PRIVATE_FLAG_OEM),
                constructPackageManagerPackageInfo(
                        "vendor_package.C", 1345678, null, 0, ApplicationInfo.PRIVATE_FLAG_ODM),
                constructPackageManagerPackageInfo("third_party_package.D", 120056, null)));

        int[] uids = new int[]{6001, 5100, 120056, 1345678};
        List<PackageInfo> actualPackageInfos = mWatchdogServiceForSystemImpl.getPackageInfosForUids(
                uids, new ArrayList<>());

        List<PackageInfo> expectedPackageInfos = Arrays.asList(
                constructPackageInfo("system_package.A", 6001, new ArrayList<>(),
                        UidType.NATIVE, ComponentType.SYSTEM, ApplicationCategoryType.OTHERS),
                constructPackageInfo("vendor_package.B", 5100, new ArrayList<>(),
                        UidType.NATIVE, ComponentType.VENDOR, ApplicationCategoryType.OTHERS),
                constructPackageInfo("third_party_package.D", 120056, new ArrayList<>(),
                        UidType.APPLICATION, ComponentType.THIRD_PARTY,
                        ApplicationCategoryType.OTHERS),
                constructPackageInfo("vendor_package.C", 1345678, new ArrayList<>(),
                        UidType.APPLICATION, ComponentType.VENDOR,
                        ApplicationCategoryType.OTHERS));

        assertPackageInfoEquals(actualPackageInfos, expectedPackageInfos);
    }

    @Test
    public void testGetPackageInfosWithSharedUids() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("system_package.A", 6050,
                        "system_shared_package", ApplicationInfo.FLAG_UPDATED_SYSTEM_APP, 0),
                constructPackageManagerPackageInfo("system_package.B", 110035,
                        "vendor_shared_package", 0, ApplicationInfo.PRIVATE_FLAG_PRODUCT),
                constructPackageManagerPackageInfo("vendor_package.C", 110035,
                        "vendor_shared_package", 0, ApplicationInfo.PRIVATE_FLAG_VENDOR),
                constructPackageManagerPackageInfo(
                        "third_party_package.D", 6050, "system_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.E", 110035, "vendor_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.F", 120078, "third_party_shared_package")));

        int[] uids = new int[]{6050, 110035, 120056, 120078};
        List<PackageInfo> actualPackageInfos = mWatchdogServiceForSystemImpl.getPackageInfosForUids(
                uids, new ArrayList<>());

        List<PackageInfo> expectedPackageInfos = Arrays.asList(
                constructPackageInfo("shared:system_shared_package", 6050,
                        Arrays.asList("system_package.A", "third_party_package.D"),
                        UidType.NATIVE, ComponentType.SYSTEM, ApplicationCategoryType.OTHERS),
                constructPackageInfo("shared:vendor_shared_package", 110035,
                        Arrays.asList("vendor_package.C", "system_package.B",
                                "third_party_package.E"), UidType.APPLICATION,
                        ComponentType.VENDOR, ApplicationCategoryType.OTHERS),
                constructPackageInfo("shared:third_party_shared_package", 120078,
                        Collections.singletonList("third_party_package.F"),
                        UidType.APPLICATION,  ComponentType.THIRD_PARTY,
                        ApplicationCategoryType.OTHERS));

        assertPackageInfoEquals(actualPackageInfos, expectedPackageInfos);
    }

    @Test
    public void testGetPackageInfosForUidsWithVendorPackagePrefixes() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "vendor_package.A", 110034, null, 0, ApplicationInfo.PRIVATE_FLAG_PRODUCT),
                constructPackageManagerPackageInfo("vendor_pkg.B", 110035,
                        "vendor_shared_package", ApplicationInfo.FLAG_SYSTEM, 0),
                constructPackageManagerPackageInfo(
                        "third_party_package.C", 110035, "vendor_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.D", 110035, "vendor_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.F", 120078, "third_party_shared_package"),
                constructPackageManagerPackageInfo("vndr_pkg.G", 126345, "vendor_package.shared",
                        ApplicationInfo.FLAG_SYSTEM, 0),
                /*
                 * A 3p package pretending to be a vendor package because 3p packages won't have the
                 * required flags.
                 */
                constructPackageManagerPackageInfo("vendor_package.imposter", 123456, null, 0, 0)));

        int[] uids = new int[]{110034, 110035, 120078, 126345, 123456};
        List<PackageInfo> actualPackageInfos = mWatchdogServiceForSystemImpl.getPackageInfosForUids(
                uids, Arrays.asList("vendor_package.", "vendor_pkg.", "shared:vendor_package."));

        List<PackageInfo> expectedPackageInfos = Arrays.asList(
                constructPackageInfo("vendor_package.A", 110034, new ArrayList<>(),
                        UidType.APPLICATION, ComponentType.VENDOR, ApplicationCategoryType.OTHERS),
                constructPackageInfo("shared:vendor_shared_package", 110035,
                        Arrays.asList("vendor_pkg.B", "third_party_package.C",
                                "third_party_package.D"), UidType.APPLICATION,
                        ComponentType.VENDOR, ApplicationCategoryType.OTHERS),
                constructPackageInfo("shared:third_party_shared_package", 120078,
                        Collections.singletonList("third_party_package.F"), UidType.APPLICATION,
                        ComponentType.THIRD_PARTY, ApplicationCategoryType.OTHERS),
                constructPackageInfo("shared:vendor_package.shared", 126345,
                        Collections.singletonList("vndr_pkg.G"), UidType.APPLICATION,
                        ComponentType.VENDOR, ApplicationCategoryType.OTHERS),
                constructPackageInfo("vendor_package.imposter", 123456,
                        new ArrayList<>(), UidType.APPLICATION, ComponentType.THIRD_PARTY,
                        ApplicationCategoryType.OTHERS));

        assertPackageInfoEquals(actualPackageInfos, expectedPackageInfos);
    }

    @Test
    public void testGetPackageInfosForUidsWithMissingApplicationInfos() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "vendor_package.A", 110034, null, 0, ApplicationInfo.PRIVATE_FLAG_OEM),
                constructPackageManagerPackageInfo("vendor_package.B", 110035,
                        "vendor_shared_package", 0, ApplicationInfo.PRIVATE_FLAG_VENDOR),
                constructPackageManagerPackageInfo(
                        "third_party_package.C", 110035, "vendor_shared_package")));

        BiConsumer<Integer, String> addPackageToSharedUid = (uid, packageName) -> {
            List<String> packages = mPackagesBySharedUid.get(uid);
            if (packages == null) {
                packages = new ArrayList<>();
            }
            packages.add(packageName);
            mPackagesBySharedUid.put(uid, packages);
        };

        addPackageToSharedUid.accept(110035, "third_party.package.G");
        mGenericPackageNameByUid.put(120056, "third_party.package.H");
        mGenericPackageNameByUid.put(120078, "shared:third_party_shared_package");
        addPackageToSharedUid.accept(120078, "third_party_package.I");


        int[] uids = new int[]{110034, 110035, 120056, 120078};

        List<PackageInfo> actualPackageInfos = mWatchdogServiceForSystemImpl.getPackageInfosForUids(
                uids, new ArrayList<>());

        List<PackageInfo> expectedPackageInfos = Arrays.asList(
                constructPackageInfo("vendor_package.A", 110034, new ArrayList<>(),
                        UidType.APPLICATION, ComponentType.VENDOR, ApplicationCategoryType.OTHERS),
                constructPackageInfo("shared:vendor_shared_package", 110035,
                        Arrays.asList("vendor_package.B", "third_party_package.C",
                                "third_party.package.G"),
                        UidType.APPLICATION, ComponentType.VENDOR, ApplicationCategoryType.OTHERS),
                constructPackageInfo("third_party.package.H", 120056, new ArrayList<>(),
                        UidType.APPLICATION, ComponentType.UNKNOWN,
                        ApplicationCategoryType.OTHERS),
                constructPackageInfo("shared:third_party_shared_package", 120078,
                        Collections.singletonList("third_party_package.I"),
                        UidType.APPLICATION, ComponentType.UNKNOWN,
                        ApplicationCategoryType.OTHERS));

        assertPackageInfoEquals(actualPackageInfos, expectedPackageInfos);
    }

    @Test
    public void testSetProcessHealthCheckEnabled() throws Exception {
        mCarWatchdogService.controlProcessHealthCheck(true);

        verify(mMockCarWatchdogDaemon).controlProcessHealthCheck(eq(true));
    }

    @Test
    public void testSetProcessHealthCheckEnabledWithDisconnectedDaemon() throws Exception {
        crashWatchdogDaemon();

        assertThrows(IllegalStateException.class,
                () -> mCarWatchdogService.controlProcessHealthCheck(false));

        verify(mMockCarWatchdogDaemon, never()).controlProcessHealthCheck(anyBoolean());
    }

    private void mockWatchdogDaemon() {
        when(mMockBinder.queryLocalInterface(anyString())).thenReturn(mMockCarWatchdogDaemon);
        when(mMockCarWatchdogDaemon.asBinder()).thenReturn(mMockBinder);
        doReturn(mMockBinder).when(() -> ServiceManager.getService(CAR_WATCHDOG_DAEMON_INTERFACE));
    }

    private void mockPackageManager() throws Exception {
        mGenericPackageNameByUid.clear();
        mPackagesBySharedUid.clear();
        mPmPackageInfoByUserPackage.clear();
        when(mMockPackageManager.getNamesForUids(any())).thenAnswer(args -> {
            int[] uids = args.getArgument(0);
            String[] names = new String[uids.length];
            for (int i = 0; i < uids.length; ++i) {
                names[i] = mGenericPackageNameByUid.get(uids[i], null);
            }
            return names;
        });
        when(mMockPackageManager.getPackagesForUid(anyInt())).thenAnswer(args -> {
            int uid = args.getArgument(0);
            List<String> packages = mPackagesBySharedUid.get(uid);
            return packages.toArray(new String[0]);
        });
        when(mMockPackageManager.getApplicationInfoAsUser(anyString(), anyInt(), anyInt()))
                .thenAnswer(args -> {
                    int userId = args.getArgument(2);
                    String userPackageId = userId + ":" + args.getArgument(0);
                    android.content.pm.PackageInfo packageInfo =
                            mPmPackageInfoByUserPackage.get(userPackageId);
                    if (packageInfo == null) {
                        throw new PackageManager.NameNotFoundException(
                                "User package id '" + userPackageId + "' not found");
                    }
                    return packageInfo.applicationInfo;
                });
        when(mMockPackageManager.getPackageInfoAsUser(anyString(), anyInt(), anyInt())).thenAnswer(
                args -> {
                    int userId = args.getArgument(2);
                    String userPackageId = userId + ":" + args.getArgument(0);
                    android.content.pm.PackageInfo packageInfo =
                            mPmPackageInfoByUserPackage.get(userPackageId);
                    if (packageInfo == null) {
                        throw new PackageManager.NameNotFoundException(
                                "User package id '" + userPackageId + "' not found");
                    }
                    return packageInfo;
                });
        when(mMockPackageManager.getInstalledPackagesAsUser(anyInt(), anyInt())).thenAnswer(
                args -> {
                    int userId = args.getArgument(1);
                    List<android.content.pm.PackageInfo> packageInfos = new ArrayList<>();
                    for (android.content.pm.PackageInfo packageInfo :
                            mPmPackageInfoByUserPackage.values()) {
                        if (UserHandle.getUserId(packageInfo.applicationInfo.uid) == userId) {
                            packageInfos.add(packageInfo);
                        }
                    }
                    return packageInfos;
                });
    }

    private void captureBroadcastReceiver() {
        ArgumentCaptor<BroadcastReceiver> receiverArgumentCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mMockContext)
                .registerReceiverForAllUsers(receiverArgumentCaptor.capture(), any(), any(), any());
        mBroadcastReceiver = receiverArgumentCaptor.getValue();
        assertWithMessage("Broadcast receiver must be non-null").that(mBroadcastReceiver)
                .isNotEqualTo(null);
    }

    private void captureDaemonBinderDeathRecipient() throws Exception {
        ArgumentCaptor<IBinder.DeathRecipient> deathRecipientCaptor =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);
        verify(mMockBinder, timeout(MAX_WAIT_TIME_MS).atLeastOnce())
                .linkToDeath(deathRecipientCaptor.capture(), anyInt());
        mCarWatchdogDaemonBinderDeathRecipient = deathRecipientCaptor.getValue();
    }

    public void crashWatchdogDaemon() {
        doReturn(null).when(() -> ServiceManager.getService(CAR_WATCHDOG_DAEMON_INTERFACE));
        mCarWatchdogDaemonBinderDeathRecipient.binderDied();
    }

    public void restartWatchdogDaemonAndAwait() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(args -> {
            latch.countDown();
            return null;
        }).when(mMockBinder).linkToDeath(any(), anyInt());
        mockWatchdogDaemon();
        latch.await(MAX_WAIT_TIME_MS, TimeUnit.MILLISECONDS);
    }

    private void setupUsers() {
        when(mMockContext.getSystemService(Context.USER_SERVICE)).thenReturn(mMockUserManager);
        mockUmGetAllUsers(mMockUserManager, new UserInfo[0]);
    }

    private ICarWatchdogServiceForSystem registerCarWatchdogService() throws Exception {
        /* Registering to daemon is done on the main thread. To ensure the registration completes
         * before verification, execute an empty block on the main thread.
         */
        CarServiceUtils.runOnMainSync(() -> {});

        ArgumentCaptor<ICarWatchdogServiceForSystem> watchdogServiceForSystemImplCaptor =
                ArgumentCaptor.forClass(ICarWatchdogServiceForSystem.class);
        verify(mMockCarWatchdogDaemon, atLeastOnce()).registerCarWatchdogService(
                watchdogServiceForSystemImplCaptor.capture());
        return watchdogServiceForSystemImplCaptor.getValue();
    }

    private void testClientHealthCheck(TestClient client, int badClientCount) throws Exception {
        mCarWatchdogService.registerClient(client, TIMEOUT_CRITICAL);
        mWatchdogServiceForSystemImpl.checkIfAlive(123456, TIMEOUT_CRITICAL);
        ArgumentCaptor<int[]> notRespondingClients = ArgumentCaptor.forClass(int[].class);
        verify(mMockCarWatchdogDaemon, timeout(MAX_WAIT_TIME_MS)).tellCarWatchdogServiceAlive(
                eq(mWatchdogServiceForSystemImpl), notRespondingClients.capture(), eq(123456));
        assertThat(notRespondingClients.getValue().length).isEqualTo(0);
        mWatchdogServiceForSystemImpl.checkIfAlive(987654, TIMEOUT_CRITICAL);
        verify(mMockCarWatchdogDaemon, timeout(MAX_WAIT_TIME_MS)).tellCarWatchdogServiceAlive(
                eq(mWatchdogServiceForSystemImpl), notRespondingClients.capture(), eq(987654));
        assertThat(notRespondingClients.getValue().length).isEqualTo(badClientCount);
    }

    private List<android.automotive.watchdog.internal.ResourceOveruseConfiguration>
            captureOnSetResourceOveruseConfigurations() throws Exception {
        ArgumentCaptor<List<android.automotive.watchdog.internal.ResourceOveruseConfiguration>>
                resourceOveruseConfigurationsCaptor = ArgumentCaptor.forClass(List.class);
        verify(mMockCarWatchdogDaemon, timeout(MAX_WAIT_TIME_MS))
                .updateResourceOveruseConfigurations(resourceOveruseConfigurationsCaptor.capture());
        return resourceOveruseConfigurationsCaptor.getValue();
    }

    private SparseArray<PackageIoOveruseStats> injectIoOveruseStatsForPackages(
            SparseArray<String> genericPackageNameByUid, Set<String> killablePackages,
            Set<String> shouldNotifyPackages) throws Exception {
        SparseArray<PackageIoOveruseStats> packageIoOveruseStatsByUid = new SparseArray<>();
        List<PackageIoOveruseStats> packageIoOveruseStats = new ArrayList<>();
        for (int i = 0; i < genericPackageNameByUid.size(); ++i) {
            String name = genericPackageNameByUid.valueAt(i);
            int uid = genericPackageNameByUid.keyAt(i);
            PackageIoOveruseStats stats = constructPackageIoOveruseStats(uid,
                    shouldNotifyPackages.contains(name),
                    constructInternalIoOveruseStats(killablePackages.contains(name),
                            /* remainingWriteBytes= */constructPerStateBytes(20, 20, 20),
                            /* writtenBytes= */constructPerStateBytes(100, 200, 300),
                            /* totalOveruses= */2));
            packageIoOveruseStatsByUid.put(uid, stats);
            packageIoOveruseStats.add(stats);
        }
        mWatchdogServiceForSystemImpl.latestIoOveruseStats(packageIoOveruseStats);
        return packageIoOveruseStatsByUid;
    }

    private void injectPackageInfos(
            List<android.content.pm.PackageInfo> packageInfos) {
        for (android.content.pm.PackageInfo packageInfo : packageInfos) {
            String genericPackageName = packageInfo.packageName;
            int uid = packageInfo.applicationInfo.uid;
            int userId = UserHandle.getUserId(uid);
            if (packageInfo.sharedUserId != null) {
                genericPackageName =
                        PackageInfoHandler.SHARED_PACKAGE_PREFIX + packageInfo.sharedUserId;
                List<String> packages = mPackagesBySharedUid.get(uid);
                if (packages == null) {
                    packages = new ArrayList<>();
                }
                packages.add(packageInfo.packageName);
                mPackagesBySharedUid.put(uid, packages);
            }
            String userPackageId = userId + ":" + packageInfo.packageName;
            assertWithMessage("Duplicate package infos provided for user package id: "
                    + userPackageId).that(mPmPackageInfoByUserPackage.containsKey(userPackageId))
                    .isFalse();
            assertWithMessage("Mismatch generic package names for the same uid '"
                    + uid + "'").that(mGenericPackageNameByUid.get(uid, genericPackageName))
                    .isEqualTo(genericPackageName);
            mPmPackageInfoByUserPackage.put(userPackageId, packageInfo);
            mGenericPackageNameByUid.put(uid, genericPackageName);
        }
    }

    private void mockApplicationEnabledSettingAccessors(IPackageManager pm) throws Exception {
        doReturn(COMPONENT_ENABLED_STATE_ENABLED).when(pm)
                .getApplicationEnabledSetting(anyString(), eq(UserHandle.myUserId()));

        doNothing().when(pm).setApplicationEnabledSetting(anyString(), anyInt(),
                anyInt(), eq(UserHandle.myUserId()), anyString());
    }

    private void verifyActionsTakenOnResourceOveruse(List<PackageResourceOveruseAction> expected)
            throws Exception {
        ArgumentCaptor<List<PackageResourceOveruseAction>> resourceOveruseActionsCaptor =
                ArgumentCaptor.forClass((Class) List.class);

        verify(mMockCarWatchdogDaemon,
                timeout(MAX_WAIT_TIME_MS).times(2)).actionTakenOnResourceOveruse(
                resourceOveruseActionsCaptor.capture());
        List<PackageResourceOveruseAction> actual = new ArrayList<>();
        for (List<PackageResourceOveruseAction> actions :
                resourceOveruseActionsCaptor.getAllValues()) {
            actual.addAll(actions);
        }

        assertThat(actual).comparingElementsUsing(
                Correspondence.from(
                        CarWatchdogServiceUnitTest::isPackageResourceOveruseActionEquals,
                        "is overuse action equal to")).containsExactlyElementsIn(expected);
    }

    public static boolean isPackageResourceOveruseActionEquals(PackageResourceOveruseAction actual,
            PackageResourceOveruseAction expected) {
        return isEquals(actual.packageIdentifier, expected.packageIdentifier)
                && Arrays.equals(actual.resourceTypes, expected.resourceTypes)
                && actual.resourceOveruseActionType == expected.resourceOveruseActionType;
    }

    private static void verifyOnOveruseCalled(List<ResourceOveruseStats> expectedStats,
            IResourceOveruseListener mockListener) throws Exception {
        ArgumentCaptor<ResourceOveruseStats> resourceOveruseStatsCaptor =
                ArgumentCaptor.forClass(ResourceOveruseStats.class);

        verify(mockListener, times(expectedStats.size()))
                .onOveruse(resourceOveruseStatsCaptor.capture());

        ResourceOveruseStatsSubject.assertThat(resourceOveruseStatsCaptor.getAllValues())
                .containsExactlyElementsIn(expectedStats);
    }

    private static int getUid(int appId) {
        return UserHandle.getUid(UserHandle.myUserId(), appId);
    }

    private static List<ResourceOveruseConfiguration> sampleResourceOveruseConfigurations() {
        return Arrays.asList(
                sampleResourceOveruseConfigurationBuilder(ComponentType.SYSTEM,
                        sampleIoOveruseConfigurationBuilder(ComponentType.SYSTEM).build()).build(),
                sampleResourceOveruseConfigurationBuilder(ComponentType.VENDOR,
                        sampleIoOveruseConfigurationBuilder(ComponentType.VENDOR).build()).build(),
                sampleResourceOveruseConfigurationBuilder(ComponentType.THIRD_PARTY,
                        sampleIoOveruseConfigurationBuilder(ComponentType.THIRD_PARTY).build())
                        .build());
    }

    private static List<android.automotive.watchdog.internal.ResourceOveruseConfiguration>
            sampleInternalResourceOveruseConfigurations() {
        return Arrays.asList(
                sampleInternalResourceOveruseConfiguration(ComponentType.SYSTEM,
                        sampleInternalIoOveruseConfiguration(ComponentType.SYSTEM)),
                sampleInternalResourceOveruseConfiguration(ComponentType.VENDOR,
                        sampleInternalIoOveruseConfiguration(ComponentType.VENDOR)),
                sampleInternalResourceOveruseConfiguration(ComponentType.THIRD_PARTY,
                        sampleInternalIoOveruseConfiguration(ComponentType.THIRD_PARTY)));
    }

    private static ResourceOveruseConfiguration.Builder sampleResourceOveruseConfigurationBuilder(
            int componentType, IoOveruseConfiguration ioOveruseConfig) {
        String prefix = WatchdogPerfHandler.toComponentTypeStr(componentType);
        List<String> safeToKill = Arrays.asList(prefix + "_package.A", prefix + "_pkg.B");
        List<String> vendorPrefixes = Arrays.asList(prefix + "_package", prefix + "_pkg");
        Map<String, String> pkgToAppCategory = new ArrayMap<>();
        pkgToAppCategory.put(prefix + "_package.A", "android.car.watchdog.app.category.MEDIA");
        ResourceOveruseConfiguration.Builder configBuilder =
                new ResourceOveruseConfiguration.Builder(componentType, safeToKill,
                        vendorPrefixes, pkgToAppCategory);
        configBuilder.setIoOveruseConfiguration(ioOveruseConfig);
        return configBuilder;
    }

    private static IoOveruseConfiguration.Builder sampleIoOveruseConfigurationBuilder(
            int componentType) {
        String prefix = WatchdogPerfHandler.toComponentTypeStr(componentType);
        PerStateBytes componentLevelThresholds = new PerStateBytes(
                /* foregroundModeBytes= */10, /* backgroundModeBytes= */20,
                /* garageModeBytes= */30);
        Map<String, PerStateBytes> packageSpecificThresholds = new ArrayMap<>();
        packageSpecificThresholds.put(prefix + "_package.A", new PerStateBytes(
                /* foregroundModeBytes= */40, /* backgroundModeBytes= */50,
                /* garageModeBytes= */60));

        Map<String, PerStateBytes> appCategorySpecificThresholds = new ArrayMap<>();
        appCategorySpecificThresholds.put(
                ResourceOveruseConfiguration.APPLICATION_CATEGORY_TYPE_MEDIA,
                new PerStateBytes(/* foregroundModeBytes= */100, /* backgroundModeBytes= */200,
                        /* garageModeBytes= */300));
        appCategorySpecificThresholds.put(
                ResourceOveruseConfiguration.APPLICATION_CATEGORY_TYPE_MAPS,
                new PerStateBytes(/* foregroundModeBytes= */1100, /* backgroundModeBytes= */2200,
                        /* garageModeBytes= */3300));

        List<IoOveruseAlertThreshold> systemWideThresholds = Collections.singletonList(
                new IoOveruseAlertThreshold(/* durationInSeconds= */10,
                        /* writtenBytesPerSecond= */200));

        return new IoOveruseConfiguration.Builder(componentLevelThresholds,
                packageSpecificThresholds, appCategorySpecificThresholds, systemWideThresholds);
    }

    private static android.automotive.watchdog.internal.ResourceOveruseConfiguration
            sampleInternalResourceOveruseConfiguration(int componentType,
            android.automotive.watchdog.internal.IoOveruseConfiguration ioOveruseConfig) {
        String prefix = WatchdogPerfHandler.toComponentTypeStr(componentType);
        android.automotive.watchdog.internal.ResourceOveruseConfiguration config =
                new android.automotive.watchdog.internal.ResourceOveruseConfiguration();
        config.componentType = componentType;
        config.safeToKillPackages = Arrays.asList(prefix + "_package.A", prefix + "_pkg.B");
        config.vendorPackagePrefixes = Arrays.asList(prefix + "_package", prefix + "_pkg");

        PackageMetadata metadata = new PackageMetadata();
        metadata.packageName = prefix + "_package.A";
        metadata.appCategoryType = ApplicationCategoryType.MEDIA;
        config.packageMetadata = Collections.singletonList(metadata);

        ResourceSpecificConfiguration resourceSpecificConfig = new ResourceSpecificConfiguration();
        resourceSpecificConfig.setIoOveruseConfiguration(ioOveruseConfig);
        config.resourceSpecificConfigurations = Collections.singletonList(resourceSpecificConfig);

        return config;
    }

    private static android.automotive.watchdog.internal.IoOveruseConfiguration
            sampleInternalIoOveruseConfiguration(int componentType) {
        String prefix = WatchdogPerfHandler.toComponentTypeStr(componentType);
        android.automotive.watchdog.internal.IoOveruseConfiguration config =
                new android.automotive.watchdog.internal.IoOveruseConfiguration();
        config.componentLevelThresholds = constructPerStateIoOveruseThreshold(prefix,
                /* fgBytes= */10, /* bgBytes= */20, /* gmBytes= */30);
        config.packageSpecificThresholds = Collections.singletonList(
                constructPerStateIoOveruseThreshold(prefix + "_package.A", /* fgBytes= */40,
                        /* bgBytes= */50, /* gmBytes= */60));
        config.categorySpecificThresholds = Arrays.asList(
                constructPerStateIoOveruseThreshold(
                        WatchdogPerfHandler.INTERNAL_APPLICATION_CATEGORY_TYPE_MEDIA,
                        /* fgBytes= */100, /* bgBytes= */200, /* gmBytes= */300),
                constructPerStateIoOveruseThreshold(
                        WatchdogPerfHandler.INTERNAL_APPLICATION_CATEGORY_TYPE_MAPS,
                        /* fgBytes= */1100, /* bgBytes= */2200, /* gmBytes= */3300));
        config.systemWideThresholds = Collections.singletonList(
                constructInternalIoOveruseAlertThreshold(/* duration= */10, /* writeBPS= */200));
        return config;
    }

    private static PerStateIoOveruseThreshold constructPerStateIoOveruseThreshold(String name,
            long fgBytes, long bgBytes, long gmBytes) {
        PerStateIoOveruseThreshold threshold = new PerStateIoOveruseThreshold();
        threshold.name = name;
        threshold.perStateWriteBytes = new android.automotive.watchdog.PerStateBytes();
        threshold.perStateWriteBytes.foregroundBytes = fgBytes;
        threshold.perStateWriteBytes.backgroundBytes = bgBytes;
        threshold.perStateWriteBytes.garageModeBytes = gmBytes;
        return threshold;
    }

    private static android.automotive.watchdog.internal.IoOveruseAlertThreshold
            constructInternalIoOveruseAlertThreshold(long duration, long writeBPS) {
        android.automotive.watchdog.internal.IoOveruseAlertThreshold threshold =
                new android.automotive.watchdog.internal.IoOveruseAlertThreshold();
        threshold.durationInSeconds = duration;
        threshold.writtenBytesPerSecond = writeBPS;
        return threshold;
    }

    private static PackageIoOveruseStats constructPackageIoOveruseStats(int uid,
            boolean shouldNotify, android.automotive.watchdog.IoOveruseStats ioOveruseStats) {
        PackageIoOveruseStats stats = new PackageIoOveruseStats();
        stats.uid = uid;
        stats.shouldNotify = shouldNotify;
        stats.ioOveruseStats = ioOveruseStats;
        return stats;
    }

    private static ResourceOveruseStats constructResourceOveruseStats(int uid, String packageName,
            android.automotive.watchdog.IoOveruseStats internalIoOveruseStats) {
        IoOveruseStats ioOveruseStats =
                WatchdogPerfHandler.toIoOveruseStatsBuilder(internalIoOveruseStats)
                        .setKillableOnOveruse(internalIoOveruseStats.killableOnOveruse).build();

        return new ResourceOveruseStats.Builder(packageName, UserHandle.getUserHandleForUid(uid))
                .setIoOveruseStats(ioOveruseStats).build();
    }

    private static android.automotive.watchdog.IoOveruseStats constructInternalIoOveruseStats(
            boolean killableOnOveruse,
            android.automotive.watchdog.PerStateBytes remainingWriteBytes,
            android.automotive.watchdog.PerStateBytes writtenBytes, int totalOveruses) {
        android.automotive.watchdog.IoOveruseStats stats =
                new android.automotive.watchdog.IoOveruseStats();
        stats.killableOnOveruse = killableOnOveruse;
        stats.remainingWriteBytes = remainingWriteBytes;
        stats.writtenBytes = writtenBytes;
        stats.totalOveruses = totalOveruses;
        return stats;
    }

    private static android.automotive.watchdog.PerStateBytes constructPerStateBytes(long fgBytes,
            long bgBytes, long gmBytes) {
        android.automotive.watchdog.PerStateBytes perStateBytes =
                new android.automotive.watchdog.PerStateBytes();
        perStateBytes.foregroundBytes = fgBytes;
        perStateBytes.backgroundBytes = bgBytes;
        perStateBytes.garageModeBytes = gmBytes;
        return perStateBytes;
    }

    private static PackageResourceOveruseAction constructPackageResourceOveruseAction(
            String packageName, int uid, int[] resourceTypes, int resourceOveruseActionType) {
        PackageResourceOveruseAction action = new PackageResourceOveruseAction();
        action.packageIdentifier = new PackageIdentifier();
        action.packageIdentifier.name = packageName;
        action.packageIdentifier.uid = uid;
        action.resourceTypes = resourceTypes;
        action.resourceOveruseActionType = resourceOveruseActionType;
        return action;
    }

    private static void delayedRunOnMainSync(Runnable action, long delayMillis)
            throws InterruptedException {
        AtomicBoolean isComplete = new AtomicBoolean();
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            action.run();
            synchronized (action) {
                isComplete.set(true);
                action.notifyAll();
            }
        }, delayMillis);
        synchronized (action) {
            while (!isComplete.get()) {
                action.wait();
            }
        }
    }

    private class TestClient extends ICarWatchdogServiceCallback.Stub {
        protected int mLastSessionId = INVALID_SESSION_ID;

        @Override
        public void onCheckHealthStatus(int sessionId, int timeout) {
            mLastSessionId = sessionId;
            mCarWatchdogService.tellClientAlive(this, sessionId);
        }

        @Override
        public void onPrepareProcessTermination() {
        }

        public int getLastSessionId() {
            return mLastSessionId;
        }
    }

    private final class BadTestClient extends TestClient {
        @Override
        public void onCheckHealthStatus(int sessionId, int timeout) {
            mLastSessionId = sessionId;
            // This client doesn't respond to CarWatchdogService.
        }
    }

    private static IResourceOveruseListener createMockResourceOveruseListener() {
        IResourceOveruseListener listener = mock(IResourceOveruseListener.Stub.class);
        when(listener.asBinder()).thenCallRealMethod();
        return listener;
    }

    private static PackageInfo constructPackageInfo(String packageName, int uid,
            List<String> sharedUidPackages, int uidType, int componentType, int appCategoryType) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageIdentifier = new PackageIdentifier();
        packageInfo.packageIdentifier.name = packageName;
        packageInfo.packageIdentifier.uid = uid;
        packageInfo.uidType = uidType;
        packageInfo.sharedUidPackages = sharedUidPackages;
        packageInfo.componentType = componentType;
        packageInfo.appCategoryType = appCategoryType;

        return packageInfo;
    }

    private static ApplicationInfo constructApplicationInfo(int flags, int privateFlags) {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.flags = flags;
        applicationInfo.privateFlags = privateFlags;
        return applicationInfo;
    }

    private static String toString(List<PackageInfo> packageInfos) {
        StringBuilder builder = new StringBuilder();
        for (PackageInfo packageInfo : packageInfos) {
            builder = toString(builder, packageInfo).append('\n');
        }
        return builder.toString();
    }

    private static StringBuilder toString(StringBuilder builder, PackageInfo packageInfo) {
        if (packageInfo == null) {
            return builder.append("Null package info\n");
        }
        builder.append("Package name: '").append(packageInfo.packageIdentifier.name)
                .append("', UID: ").append(packageInfo.packageIdentifier.uid).append('\n')
                .append("Owned packages: ");
        if (packageInfo.sharedUidPackages != null) {
            for (int i = 0; i < packageInfo.sharedUidPackages.size(); ++i) {
                builder.append('\'').append(packageInfo.sharedUidPackages.get(i)).append('\'');
                if (i < packageInfo.sharedUidPackages.size() - 1) {
                    builder.append(", ");
                }
            }
            builder.append('\n');
        } else {
            builder.append("Null");
        }
        builder.append("Component type: ").append(packageInfo.componentType).append('\n')
                .append("Application category type: ").append(packageInfo.appCategoryType).append(
                '\n');

        return builder;
    }

    private static void assertPackageInfoEquals(List<PackageInfo> actual,
            List<PackageInfo> expected) throws Exception {
        assertWithMessage("Package infos for UIDs:\nExpected: %s\nActual: %s",
                toString(expected), toString(actual)).that(actual).comparingElementsUsing(
                Correspondence.from(CarWatchdogServiceUnitTest::isPackageInfoEquals,
                        "is package info equal to")).containsExactlyElementsIn(expected);
    }

    private static boolean isPackageInfoEquals(PackageInfo lhs, PackageInfo rhs) {
        return isEquals(lhs.packageIdentifier, rhs.packageIdentifier)
                && lhs.sharedUidPackages.containsAll(rhs.sharedUidPackages)
                && lhs.componentType == rhs.componentType
                && lhs.appCategoryType == rhs.appCategoryType;
    }

    private static boolean isEquals(PackageIdentifier lhs, PackageIdentifier rhs) {
        return lhs.name.equals(rhs.name) && lhs.uid == rhs.uid;
    }

    private static android.content.pm.PackageInfo constructPackageManagerPackageInfo(
            String packageName, int uid, String sharedUserId) {
        if (packageName.startsWith("system")) {
            return constructPackageManagerPackageInfo(
                    packageName, uid, sharedUserId, ApplicationInfo.FLAG_SYSTEM, 0);
        }
        if (packageName.startsWith("vendor")) {
            return constructPackageManagerPackageInfo(
                    packageName, uid, sharedUserId, ApplicationInfo.FLAG_SYSTEM,
                    ApplicationInfo.PRIVATE_FLAG_OEM);
        }
        return constructPackageManagerPackageInfo(packageName, uid, sharedUserId, 0, 0);
    }

    private static android.content.pm.PackageInfo constructPackageManagerPackageInfo(
            String packageName, int uid, String sharedUserId, int flags, int privateFlags) {
        android.content.pm.PackageInfo packageInfo = new android.content.pm.PackageInfo();
        packageInfo.packageName = packageName;
        packageInfo.sharedUserId = sharedUserId;
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.packageName = packageName;
        packageInfo.applicationInfo.uid = uid;
        packageInfo.applicationInfo.flags = flags;
        packageInfo.applicationInfo.privateFlags = privateFlags;
        return packageInfo;
    }
}
