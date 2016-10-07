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

package android.support.car;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.car.content.pm.CarPackageManagerEmbedded;
import android.support.car.hardware.CarSensorManagerEmbedded;
import android.support.car.media.CarAudioManagerEmbedded;
import android.support.car.navigation.CarNavigationStatusManagerEmbedded;
import java.util.LinkedList;

/**
 * Default CarServiceLoader for system with built-in car service (=embedded).
 * @hide
 */
public class CarServiceLoaderEmbedded extends CarServiceLoader {

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            getConnectionCallback().onServiceConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            getConnectionCallback().onServiceDisconnected();
        }
    };

    private final android.car.Car mEmbeddedCar;
    private final LinkedList<CarConnectionCallback> mCarConnectionCallback =
            new LinkedList<>();
    private final CallbackHandler mHandler;

    /** @hide */
    CarServiceLoaderEmbedded(Car car, Context context, ServiceConnectionCallback
            serviceConnectionCallback,
            Handler handler) {
        super(context, serviceConnectionCallback, handler);
        mEmbeddedCar = android.car.Car.createCar(context, mServiceConnection, handler);
        mHandler = new CallbackHandler(car, handler.getLooper());
    }

    @Override
    public void connect() throws IllegalStateException {
        mEmbeddedCar.connect();
    }

    @Override
    public void disconnect() {
        mEmbeddedCar.disconnect();
    }

    @Override
    public boolean isConnectedToCar() {
        // for embedded, connected to service means connected to car.
        return mEmbeddedCar.isConnected();
    }

    @Override
    public int getCarConnectionType() throws CarNotConnectedException {
        @android.support.car.Car.ConnectionType
        int carConnectionType = mEmbeddedCar.getCarConnectionType();
        return carConnectionType;
    }

    @Override
    public void registerCarConnectionCallback(final CarConnectionCallback listener)
            throws CarNotConnectedException {
        synchronized (this) {
            mCarConnectionCallback.add(listener);
        }
        // car service connection is checked when this is called. So just dispatch it.
        mHandler.dispatchCarConnectionCall(listener, getCarConnectionType());
    }

    @Override
    public void unregisterCarConnectionCallback(CarConnectionCallback listener) {
        synchronized (this) {
            mCarConnectionCallback.remove(listener);
        }
    }

    @Override
    public Object getCarManager(String serviceName) throws CarNotConnectedException {
        Object manager;
        try {
            manager = mEmbeddedCar.getCarManager(serviceName);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }

        if (manager == null) {
            return null;
        }
        // For publicly available versions, return wrapper version.
        switch (serviceName) {
            case Car.AUDIO_SERVICE:
                return new CarAudioManagerEmbedded(manager);
            case Car.SENSOR_SERVICE:
                return new CarSensorManagerEmbedded(manager, getContext());
            case Car.INFO_SERVICE:
                return new CarInfoManagerEmbedded(manager);
            case Car.APP_FOCUS_SERVICE:
                return new CarAppFocusManagerEmbedded(manager);
            case Car.PACKAGE_SERVICE:
                return new CarPackageManagerEmbedded(manager);
            case Car.CAR_NAVIGATION_SERVICE:
                return new CarNavigationStatusManagerEmbedded(manager);
            default:
                return manager;
        }
    }

    private static class CallbackHandler extends Handler {

        private static final int MSG_DISPATCH_CAR_CONNECTION = 0;
        private final Car mCar;

        private CallbackHandler(Car car, Looper looper) {
            super(looper);
            mCar = car;
        }

        private void dispatchCarConnectionCall(CarConnectionCallback listener, int connectionType) {
            sendMessage(obtainMessage(MSG_DISPATCH_CAR_CONNECTION, connectionType, 0, listener));
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_DISPATCH_CAR_CONNECTION:
                    CarConnectionCallback listener = (CarConnectionCallback) msg.obj;
                    listener.onConnected(mCar, msg.arg1);
                    break;
            }
        }
    }
}
