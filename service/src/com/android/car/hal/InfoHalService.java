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

import android.car.CarInfoManager;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyType;
import android.os.Bundle;
import android.util.Log;

import com.android.car.CarLog;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class InfoHalService extends HalServiceBase {

    private final VehicleHal mHal;
    private Bundle mBasicInfo = new Bundle();

    public InfoHalService(VehicleHal hal) {
        mHal = hal;
    }

    @Override
    public void init() {
        //nothing to do
    }

    @Override
    public synchronized void release() {
        mBasicInfo = new Bundle();
    }

    @Override
    public synchronized Collection<VehiclePropConfig> takeSupportedProperties(
            Collection<VehiclePropConfig> allProperties) {
        List<VehiclePropConfig> supported = new LinkedList<>();
        for (VehiclePropConfig p: allProperties) {
            switch (p.prop) {
                case VehicleProperty.INFO_MAKE:
                    readPropertyToBundle(p.prop, CarInfoManager.BASIC_INFO_KEY_MANUFACTURER);
                    break;
                case VehicleProperty.INFO_MODEL:
                    readPropertyToBundle(p.prop, CarInfoManager.BASIC_INFO_KEY_MODEL);
                    break;
                case VehicleProperty.INFO_MODEL_YEAR:
                    readPropertyToBundle(p.prop, CarInfoManager.BASIC_INFO_KEY_MODEL_YEAR);
                    break;
                case VehicleProperty.INFO_FUEL_CAPACITY:
                    readPropertyToBundle(p.prop, CarInfoManager.BASIC_INFO_FUEL_CAPACITY);
                    break;
                case VehicleProperty.INFO_FUEL_TYPE:
                    readPropertyToBundle(p.prop, CarInfoManager.BASIC_INFO_FUEL_TYPES);
                    break;
                case VehicleProperty.INFO_EV_BATTERY_CAPACITY:
                    readPropertyToBundle(p.prop, CarInfoManager.BASIC_INFO_EV_BATTERY_CAPACITY);
                    break;
                case VehicleProperty.INFO_EV_CONNECTOR_TYPE:
                    readPropertyToBundle(p.prop, CarInfoManager.BASIC_INFO_EV_CONNECTOR_TYPES);
                    break;
                default: // not supported
                    break;
            }
        }
        return supported;
    }

    private void readPropertyToBundle(int prop, String key) {
        try {
            int propType =  prop & VehiclePropertyType.MASK;

            switch(propType) {
                case VehiclePropertyType.STRING:
                    mBasicInfo.putString(key, mHal.get(String.class, prop));
                    break;
                case VehiclePropertyType.FLOAT:
                    mBasicInfo.putFloat(key, mHal.get(float.class, prop));
                    break;
                case VehiclePropertyType.INT32:
                    mBasicInfo.putInt(key, mHal.get(int.class, prop));
                    break;
                case VehiclePropertyType.INT32_VEC:
                    mBasicInfo.putIntArray(key, mHal.get(int[].class, prop));
                    break;
                default: // not supported
                    throw(new IllegalArgumentException("Property type " + propType + " is not" +
                        "supported"));
            }
        } catch (PropertyTimeoutException e) {
            Log.e(CarLog.TAG_INFO, "Unable to read property", e);
        }
    }

    @Override
    public void handleHalEvents(List<VehiclePropValue> values) {
        for (VehiclePropValue v : values) {
            logUnexpectedEvent(v.prop);
        }
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("*InfoHal*");
        writer.println("**BasicInfo:" + mBasicInfo);
    }

    public synchronized Bundle getBasicInfo() {
        return mBasicInfo;
    }

    private void logUnexpectedEvent(int property) {
       Log.w(CarLog.TAG_INFO, "unexpected HAL event for property 0x" +
               Integer.toHexString(property));
    }
}
