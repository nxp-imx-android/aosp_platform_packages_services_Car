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

import android.car.hardware.hvac.CarHvacManager;

import com.android.car.vehiclenetwork.VehicleNetworkConsts;

public class HvacHalService extends PropertyHalServiceBase {
    private static final boolean DBG = false;
    private static final String TAG = "HvacHalService";

    private final ManagerToHalPropIdMap mMgrHalPropIdMap = ManagerToHalPropIdMap.create(new int[] {
           CarHvacManager.ID_MIRROR_DEFROSTER_ON,
           VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_SIDE_MIRROR_HEAT,

           CarHvacManager.ID_STEERING_WHEEL_TEMP,
           VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_STEERING_WHEEL_TEMP,

           CarHvacManager.ID_OUTSIDE_AIR_TEMP,
           VehicleNetworkConsts.VEHICLE_PROPERTY_ENV_OUTSIDE_TEMPERATURE,

           CarHvacManager.ID_TEMPERATURE_UNITS,
           VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_TEMPERATURE_UNITS,

           CarHvacManager.ID_ZONED_TEMP_SETPOINT,
           VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_TEMPERATURE_SET,

           CarHvacManager.ID_ZONED_TEMP_ACTUAL,
           VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_TEMPERATURE_CURRENT,

           CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT,
           VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_FAN_SPEED,

           CarHvacManager.ID_ZONED_FAN_SPEED_RPM,
           VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_ACTUAL_FAN_SPEED_RPM,

           CarHvacManager.ID_ZONED_FAN_POSITION_AVAILABLE,
           VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_FAN_DIRECTION_AVAILABLE,

           CarHvacManager.ID_ZONED_FAN_POSITION,
           VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_FAN_DIRECTION,

           CarHvacManager.ID_ZONED_SEAT_TEMP,
           VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_SEAT_TEMPERATURE,

           CarHvacManager.ID_ZONED_AC_ON,
           VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_AC_ON,

           CarHvacManager.ID_ZONED_AUTOMATIC_MODE_ON,
           VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_AUTO_ON,

           CarHvacManager.ID_ZONED_AIR_RECIRCULATION_ON,
           VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_RECIRC_ON,

           CarHvacManager.ID_ZONED_MAX_AC_ON,
           VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_MAX_AC_ON,

           CarHvacManager.ID_ZONED_DUAL_ZONE_ON,
           VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_DUAL_ON,

           CarHvacManager.ID_ZONED_MAX_DEFROST_ON,
           VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_MAX_DEFROST_ON,

           CarHvacManager.ID_ZONED_HVAC_POWER_ON,
           VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_POWER_ON,

           CarHvacManager.ID_WINDOW_DEFROSTER_ON,
           VehicleNetworkConsts.VEHICLE_PROPERTY_HVAC_DEFROSTER
    });

    public HvacHalService(VehicleHal vehicleHal) {
        super(vehicleHal, TAG, DBG);
    }

    // Convert the HVAC public API property ID to HAL property ID
    @Override
    protected int managerToHalPropId(int hvacPropId) {
        return mMgrHalPropIdMap.getHalPropId(hvacPropId);
    }

    // Convert he HAL specific property ID to HVAC public API
    @Override
    protected int halToManagerPropId(int halPropId) {
        return mMgrHalPropIdMap.getManagerPropId(halPropId);
    }
}
