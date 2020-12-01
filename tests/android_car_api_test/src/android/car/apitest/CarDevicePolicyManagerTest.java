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
package android.car.apitest;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.eventually;

import static com.google.common.truth.Truth.assertWithMessage;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.car.Car;
import android.car.admin.CarDevicePolicyManager;
import android.car.admin.CreateUserResult;
import android.car.admin.RemoveUserResult;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.PowerManager;
import android.os.UserHandle;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;

public final class CarDevicePolicyManagerTest extends CarApiTestBase {

    private static final String TAG = CarDevicePolicyManagerTest.class.getSimpleName();

    private CarDevicePolicyManager mCarDpm;
    private DevicePolicyManager mDpm;
    private KeyguardManager mKeyguardManager;
    private PowerManager mPowerManager;

    @Before
    public void setManager() throws Exception {
        mCarDpm = getCarService(Car.CAR_DEVICE_POLICY_SERVICE);
        Context context = getContext();
        mDpm = context.getSystemService(DevicePolicyManager.class);
        mKeyguardManager = context.getSystemService(KeyguardManager.class);
        mPowerManager = context.getSystemService(PowerManager.class);
    }

    @Test
    public void testRemoveUser() throws Exception {
        UserInfo user = createUser("CarDevicePolicyManagerTest.testRemoveUser");
        Log.d(TAG, "removing user " + user.toFullString());

        RemoveUserResult result = mCarDpm.removeUser(user.getUserHandle());
        Log.d(TAG, "result: " + result);

        assertWithMessage("Failed to remove user%s: %s", user.toFullString(), result)
                .that(result.isSuccess()).isTrue();
    }

    @Test
    public void testRemoveUser_currentUserSetEphemeral() throws Exception {
        int startUser = ActivityManager.getCurrentUser();
        UserInfo user = createUser(
                "CarDevicePolicyManagerTest.testRemoveUser_currentUserSetEphemeral");
        Log.d(TAG, "switching to user " + user.toFullString());
        switchUser(user.id);

        Log.d(TAG, "removing user " + user.toFullString());
        RemoveUserResult result = mCarDpm.removeUser(user.getUserHandle());

        assertWithMessage("Failed to set ephemeral %s: %s", user.toFullString(), result)
                .that(result.getStatus())
                .isEqualTo(RemoveUserResult.STATUS_SUCCESS_SET_EPHEMERAL);

        assertWithMessage("User should still exist: %s", user).that(hasUser(user.id)).isTrue();
        assertWithMessage("User should be set as ephemeral: %s", user)
                .that(getUser(user.id).isEphemeral())
                .isTrue();

        // Switch back to the starting user.
        Log.d(TAG, "switching to user " + startUser);
        switchUser(startUser);

        // User is removed once switch is complete
        Log.d(TAG, "waiting for user to be removed: " + user);
        waitForUserRemoval(user.id);
        assertWithMessage("User should have been removed after switch: %s", user)
                .that(hasUser(user.id))
                .isFalse();
    }

    @Test
    public void testCreateUser() throws Exception {
        assertCanAddUser();

        String name = "CarDevicePolicyManagerTest.testCreateUser";
        int type = CarDevicePolicyManager.USER_TYPE_REGULAR;
        Log.d(TAG, "creating new user with name " + name + " and type " + type);

        CreateUserResult result = mCarDpm.createUser(name, type);
        Log.d(TAG, "result: " + result);
        UserHandle user = result.getUserHandle();

        try {
            assertWithMessage("Failed to create user named %s and type %s: %s", name, type,
                    result).that(result.isSuccess()).isTrue();
        } finally {
            if (user != null) {
                removeUser(user.getIdentifier());
            }
        }
    }

    @Test
    public void testLockNow_safe() throws Exception {
        lockNowTest(/* safe= */ true);
    }

    @Test
    public void testLockNow_unsafe() throws Exception {
        lockNowTest(/* safe= */ false);
    }

    // lockNow() is safe regardless of the UXR state
    private void lockNowTest(boolean safe) throws Exception {

        assertScreenOn();

        runSecureDeviceTest(()-> {
            setDpmSafety(safe);

            try {
                mDpm.lockNow();

                assertLockedEventually();
                assertScreenOn();
            } finally {
                setDpmSafety(/* safe= */ true);
            }
        });
    }

    private void runSecureDeviceTest(@NonNull Runnable test) {
        unlockDevice();
        setUserPin(1234);

        try {
            test.run();
        } finally {
            resetUserPin(1234);
        }
    }

    private void unlockDevice() {
        runShellCommand("input keyevent KEYCODE_POWER");
        runShellCommand("input keyevent KEYCODE_WAKEUP");
        runShellCommand("wm dismiss-keyguard");
        assertUnLockedEventually();
    }

    private void setUserPin(int pin) {
        runShellCommand("locksettings set-pin %d", pin);
    }

    private void resetUserPin(int oldPin) {
        runShellCommand("locksettings clear --old %d", oldPin);
    }

    private void assertUnlocked() {
        assertWithMessage("device is locked").that(mKeyguardManager.isDeviceLocked()).isFalse();
        assertWithMessage("keyguard is locked").that(mKeyguardManager.isKeyguardLocked()).isFalse();
    }

    private void assertUnLockedEventually() {
        eventually(() -> assertUnlocked());
    }

    private void assertLocked() {
        assertDeviceSecure();
        assertWithMessage("device is unlocked").that(mKeyguardManager.isDeviceLocked())
                .isTrue();
        assertWithMessage("keyguard is unlocked").that(mKeyguardManager.isKeyguardLocked())
                .isTrue();
    }

    private void assertLockedEventually() {
        eventually(() -> assertLocked());
    }

    private void assertDeviceSecure() {
        assertWithMessage("device is not secure / user credentials not set")
                .that(mKeyguardManager.isDeviceSecure()).isTrue();
    }

    private void assertScreenOn() {
        assertWithMessage("screen is off").that(mPowerManager.isInteractive()).isTrue();
    }

    private void setDpmSafety(boolean safe) {
        requireNonUserBuild();
        String state = safe ? "park" : "drive";
        runShellCommand("cmd car_service emulate-driving-state %s", state);
    }
}
