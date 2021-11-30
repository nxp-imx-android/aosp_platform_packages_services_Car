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


import android.annotation.NonNull;
import android.car.builtin.util.Slogf;
import android.hardware.automotive.vehicle.VehiclePropError;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Common interface for all HAL service like sensor HAL.
 * Each HAL service is connected with XyzService supporting XyzManager,
 * and will translate HAL data into car api specific format.
 */
public abstract class HalServiceBase {

    private static final String MY_TAG = HalServiceBase.class.getSimpleName();

    /** For dispatching events. Kept here to avoid alloc every time */
    private final ArrayList<HalPropValue> mDispatchList = new ArrayList<>(1);

    static final int NOT_SUPPORTED_PROPERTY = -1;

    /**
     * Whether this class has been migrated to support AIDL VHAL backend.
     *
     * TODO(b/205774940): Remove when all HalServices supports AIDL.
     */
    protected boolean mAidlSupported = true;

    /**
     * Returns whether this class has been migrated to support AIDL VHAL backend.
     *
     * TODO(b/205774940): Remove when all HalServices supports AIDL.
     */
    public boolean isAidlSupported() {
        return mAidlSupported;
    }

    public List<HalPropValue> getDispatchList() {
        return mDispatchList;
    }

    /** initialize */
    public abstract void init();

    /** release and stop operation */
    public abstract void release();

    /**
     * Returns all property IDs this HalService can support. If return value is empty,
     * {@link #isSupportedProperty(int)} is used to query support for each property.
     */
    @NonNull
    public abstract int[] getAllSupportedProperties();

    /**
     * Checks if given {@code propId} is supported.
     */
    public boolean isSupportedProperty(int propId) {
        for (int supported: getAllSupportedProperties()) {
            if (propId == supported) {
                return true;
            }
        }
        return false;
    }

    /**
     * Takes the passed properties
     *
     * Only called when isAidlSupported is false.
     * @deprecated TODO(b/205774940): Remove this after we migrate all the usages.
     */
    @Deprecated
    public void takePropertiesDeprecated(
            @NonNull Collection<android.hardware.automotive.vehicle.V2_0.VehiclePropConfig>
            properties) {
        return;
    }

    /**
     * Takes the passed properties. Passed properties are a subset of properties returned from
     * {@link #getAllSupportedProperties()} and are supported in the current device.
     *
     * @param properties properties that are available in this device. This is guaranteed to be
     *                   supported by the HalService as the list is filtered with
     *                   {@link #getAllSupportedProperties()} or {@link #isSupportedProperty(int)}.
     *                   It can be empty if no property is available.
     */
    public void takeProperties(@NonNull Collection<HalPropConfig> properties) {
        return;
    }

    /**
     * Handles property changes from HAL.
     *
     * Only called when isAidlSupported is false.
     * @deprecated TODO(b/205774940): Remove this after we migrate all the usages.
     */
    @Deprecated
    public void onHalEventsDeprecated(
            List<android.hardware.automotive.vehicle.V2_0.VehiclePropValue> values) {
        return;
    }

    /**
     * Handles property changes from HAL.
     */
    public void onHalEvents(List<HalPropValue> values) {
        return;
    }

    /**
     * Handles errors and pass error codes  when setting properties.
     *
     * Only called when isAidlSupported is false.
     * @deprecated TODO(b/205774940): Remove this after we migrate all the usages.
     */
    @Deprecated
    public void onPropertySetErrorDeprecated(int property, int area, int errorCode) {
        Slogf.d(MY_TAG, getClass().getSimpleName() + ".onPropertySetError(): property=" + property
                + ", area=" + area + " , errorCode = " + errorCode);
    }

    /**
     * Handles errors and pass error codes  when setting properties.
     */
    public void onPropertySetError(ArrayList<VehiclePropError> errors) {
        for (VehiclePropError error : errors) {
            Slogf.d(MY_TAG, getClass().getSimpleName() + ".onPropertySetError(): property="
                    + error.propId + ", area=" + error.areaId + " , errorCode = "
                    + error.errorCode);
        }
    }

    /**
     * Dumps HAL service related info to the writer passed as parameter.
     */
    public abstract void dump(PrintWriter writer);

    /**
     * Helper class that maintains bi-directional mapping between manager's property
     * Id (public or system API) and vehicle HAL property Id.
     *
     * <p>This class is supposed to be immutable. Use {@link #create(int[])} factory method to
     * instantiate this class.
     */
    static class ManagerToHalPropIdMap {
        private final BidirectionalSparseIntArray mMap;

        /**
         * Creates {@link ManagerToHalPropIdMap} for provided [manager prop Id, hal prop Id] pairs.
         *
         * <p> The input array should have an odd number of elements.
         */
        static ManagerToHalPropIdMap create(int... mgrToHalPropIds) {
            return new ManagerToHalPropIdMap(BidirectionalSparseIntArray.create(mgrToHalPropIds));
        }

        private ManagerToHalPropIdMap(BidirectionalSparseIntArray map) {
            mMap = map;
        }

        int getHalPropId(int managerPropId) {
            return mMap.getValue(managerPropId, NOT_SUPPORTED_PROPERTY);
        }

        int getManagerPropId(int halPropId) {
            return mMap.getKey(halPropId, NOT_SUPPORTED_PROPERTY);
        }
    }
}
