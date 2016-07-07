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
package com.android.car.hal;

import android.car.hardware.hvac.CarHvacManager.HvacPropertyId;

import com.android.car.vehiclenetwork.VehicleNetworkConsts;

public class HvacHalService extends PropertyHalServiceBase {
    private static final boolean DBG = true;
    private static final String TAG = "HvacHalService";

    public HvacHalService(VehicleHal vehicleHal) {
        super(vehicleHal, TAG, DBG);
    }

    // Convert the HVAC public API property ID to HAL property ID
    @Override
    protected int managerToHalPropId(int hvacPropId) {
        switch (hvacPropId) {
            case HvacPropertyId.ZONED_FAN_SPEED_SETPOINT:
                return VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_FAN_SPEED;
            case HvacPropertyId.ZONED_FAN_POSITION:
                return VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_FAN_DIRECTION;
            case HvacPropertyId.OUTSIDE_AIR_TEMP:
                return VehicleNetworkConsts.VEHICLE_PROPERTY_ENV_OUTSIDE_TEMPERATURE;
            case HvacPropertyId.ZONED_TEMP_ACTUAL:
                return VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_TEMPERATURE_CURRENT;
            case HvacPropertyId.ZONED_TEMP_SETPOINT:
                return VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_TEMPERATURE_SET;
            case HvacPropertyId.WINDOW_DEFROSTER_ON:
                return VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_DEFROSTER;
            case HvacPropertyId.ZONED_AC_ON:
                return VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_AC_ON;
            case HvacPropertyId.ZONED_AUTOMATIC_MODE_ON:
                return VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_AUTO_ON;
            case HvacPropertyId.ZONED_AIR_RECIRCULATION_ON:
                return VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_RECIRC_ON;
            case HvacPropertyId.ZONED_MAX_AC_ON:
                return VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_MAX_AC_ON;
            case HvacPropertyId.ZONED_DUAL_ZONE_ON:
                return VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_DUAL_ON;
            case HvacPropertyId.ZONED_MAX_DEFROST_ON:
                return VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_MAX_DEFROST_ON;
            default:
                throw new IllegalArgumentException("hvacPropId " + hvacPropId + " not supported");
        }
    }

    // Convert he HAL specific property ID to HVAC public API
    @Override
    protected int halToManagerPropId(int halPropId) {
        switch (halPropId) {
            case VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_FAN_SPEED:
                return HvacPropertyId.ZONED_FAN_SPEED_SETPOINT;
            case VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_FAN_DIRECTION:
                return HvacPropertyId.ZONED_FAN_POSITION;
            case VehicleNetworkConsts.VEHICLE_PROPERTY_ENV_OUTSIDE_TEMPERATURE:
                return HvacPropertyId.OUTSIDE_AIR_TEMP;
            case VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_TEMPERATURE_CURRENT:
                return HvacPropertyId.ZONED_TEMP_ACTUAL;
            case VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_TEMPERATURE_SET:
                return HvacPropertyId.ZONED_TEMP_SETPOINT;
            case VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_DEFROSTER:
                return HvacPropertyId.WINDOW_DEFROSTER_ON;
            case VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_AC_ON:
                return HvacPropertyId.ZONED_AC_ON;
            case VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_AUTO_ON:
                return HvacPropertyId.ZONED_AUTOMATIC_MODE_ON;
            case VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_RECIRC_ON:
                return HvacPropertyId.ZONED_AIR_RECIRCULATION_ON;
            case VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_MAX_AC_ON:
                return HvacPropertyId.ZONED_MAX_AC_ON;
            case VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_DUAL_ON:
                return HvacPropertyId.ZONED_DUAL_ZONE_ON;
            case VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_MAX_DEFROST_ON:
                return HvacPropertyId.ZONED_MAX_DEFROST_ON;
            default:
                throw new IllegalArgumentException("halPropId " + halPropId + " not supported");
        }
    }
}
