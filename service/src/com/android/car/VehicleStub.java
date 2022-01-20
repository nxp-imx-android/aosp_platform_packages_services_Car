/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.hardware.automotive.vehicle.SubscribeOptions;
import android.os.RemoteException;
import android.os.ServiceSpecificException;

import com.android.car.hal.HalClientCallback;
import com.android.car.hal.HalPropConfig;
import com.android.car.hal.HalPropValue;
import com.android.car.hal.HalPropValueBuilder;

/**
 * VehicleStub represents an IVehicle service interface in either AIDL or legacy HIDL version. It
 * exposes common interface so that the client does not need to care about which version the
 * underlying IVehicle service is in.
 */
public abstract class VehicleStub {
    /** VehicleStubCallback is either an AIDL or a HIDL callback. */
    public interface VehicleStubCallback {
        /**
         *  Get the callback interface for AIDL backend.
         */
        android.hardware.automotive.vehicle.IVehicleCallback getAidlCallback();
        /**
         * Get the callback interface for HIDL backend.
         */
        android.hardware.automotive.vehicle.V2_0.IVehicleCallback.Stub getHidlCallback();
    }

    /**
     * Create a new VehicleStub to connect to Vehicle HAL.
     *
     * Create a new VehicleStub to connect to Vehicle HAL according to which backend (AIDL or HIDL)
     * is available. Caller must call isValid to check the returned {@code VehicleStub} before using
     * it.
     *
     * @return a vehicle stub to connect to Vehicle HAL.
     */
    public static VehicleStub newVehicleStub() {
        VehicleStub stub = new AidlVehicleStub();
        if (stub.isValid()) {
            return stub;
        }

        Slogf.w(CarLog.TAG_SERVICE, "No AIDL vehicle HAL found, fall back to HIDL version");
        return new HidlVehicleStub();
    }

    /**
     * Gets a HalPropValueBuilder that could be used to build a HalPropValue.
     *
     * @return a builder to build HalPropValue.
     */
    public abstract HalPropValueBuilder getHalPropValueBuilder();

    /**
     * Returns whether this vehicle stub is connecting to a valid vehicle HAL.
     *
     * @return Whether this vehicle stub is connecting to a valid vehicle HAL.
     */
    public abstract boolean isValid();

    /**
     * Gets the interface descriptor for the connecting vehicle HAL.
     *
     * @return the interface descriptor.
     * @throws IllegalStateException If unable to get the descriptor.
     */
    public abstract String getInterfaceDescriptor() throws IllegalStateException;

    /**
     * Register a death recipient that would be called when vehicle HAL died.
     *
     * @param recipient A death recipient.
     * @throws IllegalStateException If unable to register the death recipient.
     */
    public abstract void linkToDeath(IVehicleDeathRecipient recipient) throws IllegalStateException;

    /**
     * Unlink a previously linked death recipient.
     *
     * @param recipient A previously linked death recipient.
     */
    public abstract void unlinkToDeath(IVehicleDeathRecipient recipient);

    /**
     * Get all property configs.
     *
     * @return All the property configs.
     * @throws RemoteException if the remote operation fails.
     * @throws ServiceSpecificException if VHAL returns service specific error.
     */
    public abstract HalPropConfig[] getAllPropConfigs()
            throws RemoteException, ServiceSpecificException;

    /**
     * Subscribe to a property.
     *
     * @param callback The VehicleStubCallback that would be called for subscribe events.
     * @param options The list of subscribe options.
     * @throws RemoteException if the remote operation fails.
     * @throws ServiceSpecificException if VHAL returns service specific error.
     */
    public abstract void subscribe(VehicleStubCallback callback,
            SubscribeOptions[] options) throws RemoteException, ServiceSpecificException;

    /**
     * Unsubscribe to a property.
     *
     * @param callback The previously subscribed callback to unsubscribe.
     * @param prop The ID for the property to unsubscribe.
     * @throws RemoteException if the remote operation fails.
     * @throws ServiceSpecificException if VHAL returns service specific error.
     */
    public abstract void unsubscribe(VehicleStubCallback callback, int prop)
            throws RemoteException, ServiceSpecificException;

    /**
     * Get a new {@code VehicleStubCallback} that could be used to subscribe/unsubscribe.
     *
     * @param callback A callback that could be used to receive events.
     * @return a {@code VehicleStubCallback} that could be passed to subscribe/unsubscribe.
     */
    public abstract VehicleStubCallback newCallback(HalClientCallback callback);

    /**
     * Get a property.
     *
     * @param requestedPropValue The property to get.
     * @return The vehicle property value.
     * @throws RemoteException if the remote operation fails.
     * @throws ServiceSpecificException if VHAL returns service specific error.
     */
    @Nullable
    public abstract HalPropValue get(HalPropValue requestedPropValue)
            throws RemoteException, ServiceSpecificException;

    /**
     * Set a property.
     *
     * @param propValue The property to set.
     * @throws RemoteException if the remote operation fails.
     * @throws ServiceSpecificException if VHAL returns service specific error.
     */
    public abstract void set(HalPropValue propValue)
            throws RemoteException, ServiceSpecificException;
}
