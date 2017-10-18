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
package com.android.car;

import android.car.test.CarTestManager;
import android.car.test.CarTestManagerBinderWrapper;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.automotive.vehicle.V2_0.VehicleDrivingStatus;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyAccess;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyChangeMode;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.test.AndroidTestCase;
import android.util.Log;

import android.util.SparseArray;

import com.android.car.systeminterface.DisplayInterface;
import com.android.car.systeminterface.IOInterface;
import com.android.car.systeminterface.StorageMonitoringInterface;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.systeminterface.SystemInterface.Builder;
import com.android.car.systeminterface.SystemStateInterface;
import com.android.car.systeminterface.TimeInterface;
import com.android.car.systeminterface.WakeLockInterface;
import com.android.car.test.utils.TemporaryDirectory;
import com.android.car.vehiclehal.VehiclePropValueBuilder;
import com.android.car.vehiclehal.test.MockedVehicleHal;
import com.android.car.vehiclehal.test.MockedVehicleHal.DefaultPropertyHandler;
import com.android.car.vehiclehal.test.MockedVehicleHal.StaticPropertyHandler;
import com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler;
import com.android.car.vehiclehal.test.VehiclePropConfigBuilder;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * Base class for testing with mocked vehicle HAL (=car).
 * It is up to each app to start emulation by getMockedVehicleHal().start() as there will be
 * per test set up that should be done before starting.
 */
public class MockedCarTestBase extends AndroidTestCase {
    private static final String TAG = MockedCarTestBase.class.getSimpleName();
    static final long DEFAULT_WAIT_TIMEOUT_MS = 3000;
    static final long SHORT_WAIT_TIMEOUT_MS = 500;

    private android.car.Car mCar;
    private ICarImpl mCarImpl;
    private MockedVehicleHal mMockedVehicleHal;
    private SystemInterface mFakeSystemInterface;
    private final MockIOInterface mMockIOInterface = new MockIOInterface();

    private final Semaphore mWaitForMain = new Semaphore(0);
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private final Map<VehiclePropConfigBuilder, VehicleHalPropertyHandler> mHalConfig =
            new HashMap<>();
    private final SparseArray<VehiclePropConfigBuilder> mPropToConfigBuilder = new SparseArray<>();

    private static final IBinder mCarServiceToken = new Binder();
    private static boolean mRealCarServiceReleased = false;

    protected synchronized MockedVehicleHal createMockedVehicleHal() {
        return new MockedVehicleHal();
    }

    protected synchronized MockedVehicleHal getMockedVehicleHal() {
        return mMockedVehicleHal;
    }

    protected synchronized SystemInterface getFakeSystemInterface() {
        return mFakeSystemInterface;
    }

    protected synchronized void configureMockedHal() {
    }

    protected synchronized SystemInterface.Builder getSystemInterfaceBuilder() {
        return Builder.newSystemInterface()
                .withSystemStateInterface(new MockSystemStateInterface())
                .withDisplayInterface(new MockDisplayInterface())
                .withIOInterface(mMockIOInterface)
                .withStorageMonitoringInterface(new MockStorageMonitoringInterface())
                .withTimeInterface(new MockTimeInterface())
                .withWakeLockInterface(new MockWakeLockInterface());
    }

    protected synchronized void configureFakeSystemInterface() {}

    @Override
    protected synchronized void setUp() throws Exception {
        super.setUp();

        releaseRealCarService(getContext());

        mMockedVehicleHal = createMockedVehicleHal();
        addProperty(VehicleProperty.DRIVING_STATUS,
                VehiclePropValueBuilder.newBuilder(VehicleProperty.DRIVING_STATUS)
                        .addIntValue(VehicleDrivingStatus.UNRESTRICTED)
                        .build());
        configureMockedHal();

        mFakeSystemInterface = getSystemInterfaceBuilder().build();
        configureFakeSystemInterface();

        Context context = getCarServiceContext();
        mCarImpl = new ICarImpl(context, mMockedVehicleHal, mFakeSystemInterface,
                null /* error notifier */);

        initMockedHal(false /* no need to release */);

        mCar = new android.car.Car(context, mCarImpl, null /* handler */);
    }

    @Override
    protected synchronized void tearDown() throws Exception {
        super.tearDown();

        mCar.disconnect();
        mCarImpl.release();

        mMockIOInterface.tearDown();
    }

    protected Context getCarServiceContext() throws NameNotFoundException {
        return getContext()
                .createPackageContext("com.android.car", Context.CONTEXT_IGNORE_SECURITY);
    }

    protected synchronized void reinitializeMockedHal() {
        initMockedHal(true /* release */);
    }

    private synchronized void initMockedHal(boolean release) {
        if (release) {
            mCarImpl.release();
        }

        for (Map.Entry<VehiclePropConfigBuilder, VehicleHalPropertyHandler> entry
                : mHalConfig.entrySet()) {
            mMockedVehicleHal.addProperty(entry.getKey().build(), entry.getValue());
        }
        mHalConfig.clear();
        mCarImpl.init();
    }

    protected synchronized VehiclePropConfigBuilder addProperty(int propertyId,
            VehicleHalPropertyHandler propertyHandler) {
        VehiclePropConfigBuilder builder = VehiclePropConfigBuilder.newBuilder(propertyId);
        setConfigBuilder(builder, propertyHandler);
        return builder;
    }

    protected synchronized VehiclePropConfigBuilder addProperty(int propertyId) {
        VehiclePropConfigBuilder builder = VehiclePropConfigBuilder.newBuilder(propertyId);
        setConfigBuilder(builder, new DefaultPropertyHandler(builder.build(), null));
        return builder;
    }

    protected synchronized VehiclePropConfigBuilder addProperty(int propertyId,
            VehiclePropValue value) {
        VehiclePropConfigBuilder builder = VehiclePropConfigBuilder.newBuilder(propertyId);
        setConfigBuilder(builder, new DefaultPropertyHandler(builder.build(), value));
        return builder;
    }

    protected synchronized VehiclePropConfigBuilder addStaticProperty(int propertyId,
            VehiclePropValue value) {
        VehiclePropConfigBuilder builder = VehiclePropConfigBuilder.newBuilder(propertyId)
                .setChangeMode(VehiclePropertyChangeMode.STATIC)
                .setAccess(VehiclePropertyAccess.READ);

        setConfigBuilder(builder, new StaticPropertyHandler(value));
        return builder;
    }

    private void setConfigBuilder(VehiclePropConfigBuilder builder,
            VehicleHalPropertyHandler propertyHandler) {
        int propId = builder.build().prop;

        // Override previous property config if exists.
        VehiclePropConfigBuilder prevBuilder = mPropToConfigBuilder.get(propId);
        if (prevBuilder != null) {
            mHalConfig.remove(prevBuilder);
        }
        mPropToConfigBuilder.put(propId, builder);
        mHalConfig.put(builder, propertyHandler);
    }

    protected synchronized android.car.Car getCar() {
        return mCar;
    }

    protected void runOnMain(final Runnable r) {
        mMainHandler.post(r);
    }

    protected void runOnMainSync(final Runnable r) throws Exception {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                r.run();
                mWaitForMain.release();
            }
        });
        mWaitForMain.acquire();
    }

    public static <T> void assertArrayEquals(T[] expected, T[] actual) {
        if (!Arrays.equals(expected, actual)) {
            fail("expected:<" + Arrays.toString(expected) +
                    "> but was:<" + Arrays.toString(actual) + ">");
        }
    }

    public static void assertArrayEquals(int[] expected, int[] actual) {
        if (!Arrays.equals(expected, actual)) {
            fail("expected:<" + Arrays.toString(expected) +
                    "> but was:<" + Arrays.toString(actual) + ">");
        }
    }

    /*
     * In order to eliminate interfering with real car service we will disable it. It will be
     * enabled back in CarTestService when mCarServiceToken will go away (tests finish).
     */
    private synchronized static void releaseRealCarService(Context context) throws Exception {
        if (mRealCarServiceReleased) {
            return;  // We just want to release it once.
        }

        mRealCarServiceReleased = true;  // To make sure it was called once.

        Object waitForConnection = new Object();
        android.car.Car car = android.car.Car.createCar(context, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                synchronized (waitForConnection) {
                    waitForConnection.notify();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) { }
        });

        car.connect();
        synchronized (waitForConnection) {
            if (!car.isConnected()) {
                waitForConnection.wait(DEFAULT_WAIT_TIMEOUT_MS);
            }
        }

        if (car.isConnected()) {
            Log.i(TAG, "Connected to real car service");
            CarTestManagerBinderWrapper binderWrapper =
                    (CarTestManagerBinderWrapper) car.getCarManager(android.car.Car.TEST_SERVICE);
            assertNotNull(binderWrapper);

            CarTestManager mgr = new CarTestManager(binderWrapper.binder);
            mgr.stopCarService(mCarServiceToken);
        }
    }

    static final class MockDisplayInterface implements DisplayInterface {

        @Override
        public void setDisplayState(boolean on) {}

        @Override
        public void startDisplayStateMonitoring(CarPowerManagementService service) {}

        @Override
        public void stopDisplayStateMonitoring() {}
    }

    static final class MockIOInterface implements IOInterface {
        private TemporaryDirectory mFilesDir = null;

        @Override
        public File getFilesDir() {
            if (mFilesDir == null) {
                try {
                    mFilesDir = new TemporaryDirectory(TAG);
                } catch (IOException e) {
                    Log.e(TAG, "failed to create temporary directory", e);
                    fail("failed to create temporary directory. exception was: " + e);
                }
            }
            return mFilesDir.getDirectory();
        }

        public void tearDown() {
            if (mFilesDir != null) {
                try {
                    mFilesDir.close();
                } catch (Exception e) {
                    Log.w(TAG, "could not remove temporary directory", e);
                }
            }
        }
    }

    static final class MockStorageMonitoringInterface implements StorageMonitoringInterface {}

    static final class MockSystemStateInterface implements SystemStateInterface {
        @Override
        public void shutdown() {}

        @Override
        public void enterDeepSleep(int wakeupTimeSec) {}

        @Override
        public void scheduleActionForBootCompleted(Runnable action, Duration delay) {}
    }

    static final class MockTimeInterface implements TimeInterface {

        @Override
        public void scheduleAction(Runnable r, long delayMs) {}

        @Override
        public void cancelAllActions() {}
    }

    static final class MockWakeLockInterface implements WakeLockInterface {

        @Override
        public void releaseAllWakeLocks() {}

        @Override
        public void switchToPartialWakeLock() {}

        @Override
        public void switchToFullWakeLock() {}
    }

}
