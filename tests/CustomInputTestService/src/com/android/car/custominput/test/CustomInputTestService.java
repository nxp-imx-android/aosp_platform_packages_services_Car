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
package com.android.car.custominput.test;

import android.annotation.NonNull;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.car.Car;
import android.car.input.CarInputManager;
import android.car.input.CustomInputEvent;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.util.List;

/**
 * This service is a reference implementation to be used as an example on how to define and handle
 * HW_CUSTOM_INPUT events.
 */
// TODO(b/12219669): Rename this to CustomInputSampleService
public class CustomInputTestService extends Service implements
        CarInputManager.CarInputCaptureCallback {

    private static final String TAG = CustomInputTestService.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final String CHANNEL_ID = CustomInputTestService.class.getSimpleName();
    private static final int FOREGROUND_ID = 1;

    private Car mCar;
    private CarInputManager mCarInputManager;
    private CustomInputEventListener mEventHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground();
    }

    private void startForeground() {
        // TODO(b/12219669): Start this service from carservice, then the code in below
        //     won't be needed.
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                CHANNEL_ID,
                NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
        Notification notification = new Notification.Builder(getApplicationContext(), CHANNEL_ID)
                .setContentTitle("CustomInputTestService")
                .setContentText("Processing...")
                .setSmallIcon(R.drawable.custom_input_ref_service)
                .build();
        startForeground(FOREGROUND_ID, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        connectToCarService();
        return START_STICKY;
    }

    private void connectToCarService() {
        if (mCar != null && mCar.isConnected()) {
            Log.w(TAG, "Ignoring request to connect against car service");
            return;
        }
        Log.i(TAG, "Connecting against car service");
        mCar = Car.createCar(this, /* handler= */ null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (car, ready) -> {
                    mCar = car;
                    if (ready) {
                        mCarInputManager =
                                (CarInputManager) mCar.getCarManager(Car.CAR_INPUT_SERVICE);
                        mCarInputManager.requestInputEventCapture(this,
                                CarInputManager.TARGET_DISPLAY_TYPE_MAIN,
                                new int[]{CarInputManager.INPUT_TYPE_CUSTOM_INPUT_EVENT},
                                CarInputManager.CAPTURE_REQ_FLAGS_ALLOW_DELAYED_GRANT);
                    }
                });
        mEventHandler = new CustomInputEventListener(getApplicationContext(), this);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "Service destroyed");
        }
        if (mCarInputManager != null) {
            mCarInputManager.releaseInputEventCapture(CarInputManager.TARGET_DISPLAY_TYPE_MAIN);
        }
        if (mCar != null) {
            mCar.disconnect();
            mCar = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCustomInputEvents(int targetDisplayId, @NonNull List<CustomInputEvent> events) {
        for (CustomInputEvent event : events) {
            mEventHandler.handle(targetDisplayId, event);
        }
    }
}
