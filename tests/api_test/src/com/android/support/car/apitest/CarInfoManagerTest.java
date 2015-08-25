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
package com.android.support.car.apitest;

import android.content.ComponentName;
import android.os.IBinder;
import android.os.Looper;
import android.support.car.Car;
import android.support.car.CarInfoManager;
import android.support.car.ServiceConnectionListener;
import android.test.AndroidTestCase;
import android.util.Log;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CarInfoManagerTest extends AndroidTestCase {
    private static final long DEFAULT_WAIT_TIMEOUT_MS = 3000;

    private static final String TAG = CarInfoManagerTest.class.getSimpleName();

    private final Semaphore mConnectionWait = new Semaphore(0);

    private Car mCar;
    private CarInfoManager mCarInfoManager;

    private final ServiceConnectionListener mConnectionListener = new ServiceConnectionListener() {

        @Override
        public void onServiceSuspended(int cause) {
            assertMainThread();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            assertMainThread();
        }

        @Override
        public void onServiceConnectionFailed(int cause) {
            assertMainThread();
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            assertMainThread();
            mConnectionWait.release();
        }
    };

    private void assertMainThread() {
        assertTrue(Looper.getMainLooper().isCurrentThread());
    }
    private void waitForConnection(long timeoutMs) throws InterruptedException {
        mConnectionWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mCar = new Car(getContext(), mConnectionListener, null);
        mCar.connect();
        waitForConnection(DEFAULT_WAIT_TIMEOUT_MS);
        mCarInfoManager =
                (CarInfoManager) mCar.getCarManager(Car.INFO_SERVICE);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mCar.disconnect();
    }

    public void testManufactuter() throws Exception {
        String name = mCarInfoManager.getString(CarInfoManager.KEY_MANUFACTURER);
        assertNotNull(name);
        Log.i(TAG, CarInfoManager.KEY_MANUFACTURER + ":" + name);
        try {
            Float v = mCarInfoManager.getFloat(CarInfoManager.KEY_MANUFACTURER);
            fail("type check failed");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            Integer v = mCarInfoManager.getInt(CarInfoManager.KEY_MANUFACTURER);
            fail("type check failed");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testNoSuchInfo() throws Exception {
        final String NO_SUCH_NAME = "no-such-information-available";
        try {
            String name = mCarInfoManager.getString(NO_SUCH_NAME);
            fail("wrong param check");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            Integer intValue = mCarInfoManager.getInt(NO_SUCH_NAME);
            fail("wrong param check");
        } catch (IllegalArgumentException e) {
            //expected
        }
        try {
            Float floatValue = mCarInfoManager.getFloat(NO_SUCH_NAME);
            fail("wrong param check");
        } catch (IllegalArgumentException e) {
            //expected
        }
    }
}
