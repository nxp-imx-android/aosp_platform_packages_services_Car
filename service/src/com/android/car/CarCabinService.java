/*
 * Copyright (C) 2016 The Android Open Source Project
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
import android.content.Context;

import com.android.car.hal.CabinHalService;

public class CarCabinService extends CarPropertyServiceBase {
	private final static boolean DBG = false;

    public CarCabinService(Context context, CabinHalService cabinHal) {
        super(context, cabinHal, Car.PERMISSION_ADJUST_CAR_CABIN, DBG, CarLog.TAG_CABIN);
    }
}
