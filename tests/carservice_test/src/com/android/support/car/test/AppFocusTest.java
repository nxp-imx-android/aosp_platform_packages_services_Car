/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.support.car.test;

import android.support.car.Car;
import android.support.car.CarAppFocusManager;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import com.android.car.test.MockedCarTestBase;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@MediumTest
public class AppFocusTest extends MockedCarTestBase {
    private static final String TAG = AppFocusTest.class.getSimpleName();
    private static final long DEFAULT_WAIT_TIMEOUT_MS = 1000;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        getVehicleHalEmulator().start();
    }

    public void testFocusChange() throws Exception {
        CarAppFocusManager manager = (CarAppFocusManager) getSupportCar().getCarManager(
                Car.APP_FOCUS_SERVICE);
        FocusChangeListener listener = new FocusChangeListener();
        FocusOwnershipChangeListerner ownershipListener = new FocusOwnershipChangeListerner();
        manager.registerFocusListener(listener, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        manager.registerFocusListener(listener, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);
        manager.requestAppFocus(ownershipListener, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        listener.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, true);
        manager.requestAppFocus(ownershipListener, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);
        listener.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, true);
        manager.abandonAppFocus(ownershipListener, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        listener.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, false);
        manager.abandonAppFocus(ownershipListener, CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND);
        listener.waitForFocusChangeAndAssert(DEFAULT_WAIT_TIMEOUT_MS,
                CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND, false);
        manager.unregisterFocusListener(listener);
    }

    private class FocusChangeListener implements CarAppFocusManager.AppFocusChangeListener {
        private int mLastChangeAppType;
        private boolean mLastChangeAppActive;
        private final Semaphore mChangeWait = new Semaphore(0);

        public boolean waitForFocusChangeAndAssert(long timeoutMs, int expectedAppType,
                boolean expectedAppActive) throws Exception {
            if (!mChangeWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                return false;
            }
            assertEquals(expectedAppType, mLastChangeAppType);
            assertEquals(expectedAppActive, mLastChangeAppActive);
            return true;
        }

        @Override
        public void onAppFocusChange(int appType, boolean active) {
            Log.i(TAG, "onAppFocusChange appType=" + appType + " active=" + active);
            mLastChangeAppType = appType;
            mLastChangeAppActive = active;
            mChangeWait.release();
        }
    }

    private class FocusOwnershipChangeListerner
            implements CarAppFocusManager.AppFocusOwnershipChangeListener {
        private int mLastLossEvent;
        private final Semaphore mLossEventWait = new Semaphore(0);

        public boolean waitForOwnershipLossAndAssert(long timeoutMs, int expectedLossAppType)
                throws Exception {
            if (!mLossEventWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                return false;
            }
            assertEquals(expectedLossAppType, mLastLossEvent);
            return true;
        }

        @Override
        public void onAppFocusOwnershipLoss(int appType) {
            Log.i(TAG, "onAppFocusOwnershipLoss " + appType);
            mLastLossEvent = appType;
            mLossEventWait.release();
        }
    }
}
