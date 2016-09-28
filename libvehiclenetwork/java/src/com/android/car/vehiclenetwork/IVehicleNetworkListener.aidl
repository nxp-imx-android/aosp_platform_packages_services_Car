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

package com.android.car.vehiclenetwork;

import com.android.car.vehiclenetwork.VehiclePropValuesParcelable;
import com.android.car.vehiclenetwork.VehiclePropValueParcelable;

/**
 * Listener for vehicle network service. Intentionally both way as this is supposed to be
 * used between system components. Making this one way brings ordering issue.
 * @hide
 */
interface IVehicleNetworkListener {
    void onVehicleNetworkEvents(in VehiclePropValuesParcelable values) = 0;
    void onHalError(int errorCode, int property, int operation)        = 1;
    void onHalRestart(boolean inMocking)                               = 2;
    void onPropertySet(in VehiclePropValueParcelable value)            = 3;
    //TODO add specialized onVehicleNetworkEvents for byte array for efficiency
}
