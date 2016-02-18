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

import android.car.Car;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.util.Log;

import com.android.car.hal.VehicleHal;


/**
 *  When system boots up, start car service.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.w(CarLog.TAG_SERVICE, "Starting...");
        VehicleHal hal = VehicleHal.getInstance();
        Intent carServiceintent = new Intent();
        carServiceintent.setPackage(context.getPackageName());
        carServiceintent.setAction(Car.CAR_SERVICE_INTERFACE_NAME);
        context.startServiceAsUser(carServiceintent, new UserHandle(0));
    }
}
