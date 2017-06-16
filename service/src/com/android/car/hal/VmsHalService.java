/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.car.CarServiceUtils.toByteArray;
import static java.lang.Integer.toHexString;

import android.car.VehicleAreaType;
import android.car.annotation.FutureFeature;
import android.car.vms.IVmsSubscriberClient;
import android.car.vms.VmsLayer;
import android.car.vms.VmsAssociatedLayer;
import android.car.vms.VmsLayerDependency;
import android.car.vms.VmsLayersOffering;
import android.car.vms.VmsSubscriptionState;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_1.VehicleProperty;
import android.hardware.automotive.vehicle.V2_1.VmsBaseMessageIntegerValuesIndex;
import android.hardware.automotive.vehicle.V2_1.VmsMessageType;
import android.hardware.automotive.vehicle.V2_1.VmsOfferingMessageIntegerValuesIndex;
import android.hardware.automotive.vehicle.V2_1.VmsSimpleMessageIntegerValuesIndex;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import com.android.car.CarLog;
import com.android.car.VmsLayersAvailability;
import com.android.car.VmsPublishersInfo;
import com.android.car.VmsRouting;
import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This is a glue layer between the VehicleHal and the VmsService. It sends VMS properties back and
 * forth.
 */
@FutureFeature
public class VmsHalService extends HalServiceBase {

    private static final boolean DBG = true;
    private static final int HAL_PROPERTY_ID = VehicleProperty.VEHICLE_MAP_SERVICE;
    private static final String TAG = "VmsHalService";

    private final static List<Integer> AVAILABILITY_MESSAGE_TYPES = Collections.unmodifiableList(
            Arrays.asList(
                    VmsMessageType.AVAILABILITY_RESPONSE,
                    VmsMessageType.AVAILABILITY_CHANGE));

    private boolean mIsSupported = false;
    private CopyOnWriteArrayList<VmsHalPublisherListener> mPublisherListeners =
            new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<VmsHalSubscriberListener> mSubscriberListeners =
            new CopyOnWriteArrayList<>();

    private final IBinder mHalPublisherToken = new Binder();
    private final VehicleHal mVehicleHal;

    private final Object mLock = new Object();
    private final VmsRouting mRouting = new VmsRouting();
    @GuardedBy("mLock")
    private final Map<IBinder, VmsLayersOffering> mOfferings = new HashMap<>();
    @GuardedBy("mLock")
    private final VmsLayersAvailability mAvailableLayers = new VmsLayersAvailability();
    private final VmsPublishersInfo mPublishersInfo = new VmsPublishersInfo();

    /**
     * The VmsPublisherService implements this interface to receive data from the HAL.
     */
    public interface VmsHalPublisherListener {
        void onChange(VmsSubscriptionState subscriptionState);
    }

    /**
     * The VmsSubscriberService implements this interface to receive data from the HAL.
     */
    public interface VmsHalSubscriberListener {
        // Notify listener on a data Message.
        void onDataMessage(VmsLayer layer, byte[] payload);

        // Notify listener on a change in available layers.
        void onLayersAvaiabilityChange(List<VmsAssociatedLayer> availableLayers);
    }

    /**
     * The VmsService implements this interface to receive data from the HAL.
     */
    protected VmsHalService(VehicleHal vehicleHal) {
        mVehicleHal = vehicleHal;
        if (DBG) {
            Log.d(TAG, "started VmsHalService!");
        }
    }

    public void addPublisherListener(VmsHalPublisherListener listener) {
        mPublisherListeners.add(listener);
    }

    public void addSubscriberListener(VmsHalSubscriberListener listener) {
        mSubscriberListeners.add(listener);
    }

    public void removePublisherListener(VmsHalPublisherListener listener) {
        mPublisherListeners.remove(listener);
    }

    public void removeSubscriberListener(VmsHalSubscriberListener listener) {
        mSubscriberListeners.remove(listener);
    }

    public void addSubscription(IVmsSubscriberClient listener, VmsLayer layer) {
        boolean firstSubscriptionForLayer = false;
        synchronized (mLock) {
            // Check if publishers need to be notified about this change in subscriptions.
            firstSubscriptionForLayer = !mRouting.hasLayerSubscriptions(layer);

            // Add the listeners subscription to the layer
            mRouting.addSubscription(listener, layer);
        }
        if (firstSubscriptionForLayer) {
            notifyPublishers(layer, true);
        }
    }

    public void removeSubscription(IVmsSubscriberClient listener, VmsLayer layer) {
        boolean layerHasSubscribers = true;
        synchronized (mLock) {
            if (!mRouting.hasLayerSubscriptions(layer)) {
                Log.i(TAG, "Trying to remove a layer with no subscription: " + layer);
                return;
            }

            // Remove the listeners subscription to the layer
            mRouting.removeSubscription(listener, layer);

            // Check if publishers need to be notified about this change in subscriptions.
            layerHasSubscribers = mRouting.hasLayerSubscriptions(layer);
        }
        if (!layerHasSubscribers) {
            notifyPublishers(layer, false);
        }
    }

    public void addSubscription(IVmsSubscriberClient listener) {
        synchronized (mLock) {
            mRouting.addSubscription(listener);
        }
    }

    public void removeSubscription(IVmsSubscriberClient listener) {
        synchronized (mLock) {
            mRouting.removeSubscription(listener);
        }
    }

    public void removeDeadListener(IVmsSubscriberClient listener) {
        synchronized (mLock) {
            mRouting.removeDeadListener(listener);
        }
    }

    public Set<IVmsSubscriberClient> getListeners(VmsLayer layer) {
        synchronized (mLock) {
            return mRouting.getListeners(layer);
        }
    }

    public Set<IVmsSubscriberClient> getAllListeners() {
        synchronized (mLock) {
            return mRouting.getAllListeners();
        }
    }

    public boolean isHalSubscribed(VmsLayer layer) {
        synchronized (mLock) {
            return mRouting.isHalSubscribed(layer);
        }
    }

    public VmsSubscriptionState getSubscriptionState() {
        synchronized (mLock) {
            return mRouting.getSubscriptionState();
        }
    }

    /**
     * Assigns an idempotent ID for publisherInfo and stores it. The idempotency in this case means
     * that the same publisherInfo will always, within a trip of the vehicle, return the same ID.
     * The publisherInfo should be static for a binary and should only change as part of a software
     * update. The publisherInfo is a serialized proto message which VMS clients can interpret.
     */
    public int getPublisherStaticId(byte[] publisherInfo) {
        if (DBG) {
            Log.i(TAG, "Getting publisher static ID");
        }
        synchronized (mLock) {
            return mPublishersInfo.getIdForInfo(publisherInfo);
        }
    }

    public byte[] getPublisherInfo(int publisherId) {
        if (DBG) {
            Log.i(TAG, "Getting information for publisher ID: " + publisherId);
        }
        synchronized (mLock) {
            return mPublishersInfo.getPublisherInfo(publisherId);
        }
    }

    public void addHalSubscription(VmsLayer layer) {
        boolean firstSubscriptionForLayer = true;
        synchronized (mLock) {
            // Check if publishers need to be notified about this change in subscriptions.
            firstSubscriptionForLayer = !mRouting.hasLayerSubscriptions(layer);

            // Add the listeners subscription to the layer
            mRouting.addHalSubscription(layer);
        }
        if (firstSubscriptionForLayer) {
            notifyPublishers(layer, true);
        }
    }

    public void removeHalSubscription(VmsLayer layer) {
        boolean layerHasSubscribers = true;
        synchronized (mLock) {
            if (!mRouting.hasLayerSubscriptions(layer)) {
                Log.i(TAG, "Trying to remove a layer with no subscription: " + layer);
                return;
            }

            // Remove the listeners subscription to the layer
            mRouting.removeHalSubscription(layer);

            // Check if publishers need to be notified about this change in subscriptions.
            layerHasSubscribers = mRouting.hasLayerSubscriptions(layer);
        }
        if (!layerHasSubscribers) {
            notifyPublishers(layer, false);
        }
    }

    public boolean containsListener(IVmsSubscriberClient listener) {
        synchronized (mLock) {
            return mRouting.containsListener(listener);
        }
    }

    public void setPublisherLayersOffering(IBinder publisherToken, VmsLayersOffering offering) {
        Set<VmsAssociatedLayer> availableLayers = Collections.EMPTY_SET;
        synchronized (mLock) {
            updateOffering(publisherToken, offering);
            availableLayers = mAvailableLayers.getAvailableLayers();
        }
        notifyOfAvailabilityChange(availableLayers);
    }

    public Set<VmsAssociatedLayer> getAvailableLayers() {
        //TODO(b/36872877): wrap available layers in VmsAvailabilityState similar to VmsSubscriptionState.
        synchronized (mLock) {
            return mAvailableLayers.getAvailableLayers();
        }
    }

    /**
     * Notify all the publishers and the HAL on subscription changes regardless of who triggered
     * the change.
     *
     * @param layer          layer which is being subscribed to or unsubscribed from.
     * @param hasSubscribers indicates if the notification is for subscription or unsubscription.
     */
    private void notifyPublishers(VmsLayer layer, boolean hasSubscribers) {
        // notify the HAL
        setSubscriptionRequest(layer, hasSubscribers);

        // Notify the App publishers
        for (VmsHalPublisherListener listener : mPublisherListeners) {
            // Besides the list of layers, also a timestamp is provided to the clients.
            // They should ignore any notification with a timestamp that is older than the most
            // recent timestamp they have seen.
            listener.onChange(getSubscriptionState());
        }
    }

    /**
     * Notify all the subscribers and the HAL on layers availability change.
     *
     * @param availableLayers the layers which publishers claim they made publish.
     */
    private void notifyOfAvailabilityChange(Set<VmsAssociatedLayer> availableLayers) {
        // notify the HAL
        notifyAvailabilityChangeToHal(availableLayers);

        // Notify the App subscribers
        for (VmsHalSubscriberListener listener : mSubscriberListeners) {
            listener.onLayersAvaiabilityChange(new ArrayList<>(availableLayers));
        }
    }

    @Override
    public void init() {
        if (DBG) {
            Log.d(TAG, "init()");
        }
        if (mIsSupported) {
            mVehicleHal.subscribeProperty(this, HAL_PROPERTY_ID);
        }
    }

    @Override
    public void release() {
        if (DBG) {
            Log.d(TAG, "release()");
        }
        if (mIsSupported) {
            mVehicleHal.unsubscribeProperty(this, HAL_PROPERTY_ID);
        }
        mPublisherListeners.clear();
        mSubscriberListeners.clear();
    }

    @Override
    public Collection<VehiclePropConfig> takeSupportedProperties(
            Collection<VehiclePropConfig> allProperties) {
        List<VehiclePropConfig> taken = new LinkedList<>();
        for (VehiclePropConfig p : allProperties) {
            if (p.prop == HAL_PROPERTY_ID) {
                taken.add(p);
                mIsSupported = true;
                if (DBG) {
                    Log.d(TAG, "takeSupportedProperties: " + toHexString(p.prop));
                }
                break;
            }
        }
        return taken;
    }

    /**
     * Consumes/produces HAL messages. The format of these messages is defined in:
     * hardware/interfaces/automotive/vehicle/2.1/types.hal
     */
    @Override
    public void handleHalEvents(List<VehiclePropValue> values) {
        if (DBG) {
            Log.d(TAG, "Handling a VMS property change");
        }
        for (VehiclePropValue v : values) {
            ArrayList<Integer> vec = v.value.int32Values;
            int messageType = vec.get(VmsBaseMessageIntegerValuesIndex.VMS_MESSAGE_TYPE);

            if (DBG) {
                Log.d(TAG, "Handling VMS message type: " + messageType);
            }
            switch (messageType) {
                case VmsMessageType.DATA:
                    handleDataEvent(vec, toByteArray(v.value.bytes));
                    break;
                case VmsMessageType.SUBSCRIBE:
                    handleSubscribeEvent(vec);
                    break;
                case VmsMessageType.UNSUBSCRIBE:
                    handleUnsubscribeEvent(vec);
                    break;
                case VmsMessageType.OFFERING:
                    handleOfferingEvent(vec);
                    break;
                case VmsMessageType.AVAILABILITY_REQUEST:
                    handleHalAvailabilityRequestEvent();
                    break;
                case VmsMessageType.SUBSCRIPTIONS_REQUEST:
                    handleSubscriptionRequestEvent();
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected message type: " + messageType);
            }
        }
    }

    private VmsLayer parseVmsLayerFromSimpleMessageIntegerValues(List<Integer> integerValues) {
        return new VmsLayer(integerValues.get(VmsSimpleMessageIntegerValuesIndex.VMS_LAYER_ID),
                integerValues.get(VmsSimpleMessageIntegerValuesIndex.VMS_LAYER_VERSION),
                integerValues.get(VmsSimpleMessageIntegerValuesIndex.VMS_LAYER_SUB_TYPE));
    }

    /**
     * Data message format:
     * <ul>
     * <li>Message type.
     * <li>Layer id.
     * <li>Layer version.
     * <li>Payload.
     * </ul>
     */
    private void handleDataEvent(List<Integer> integerValues, byte[] payload) {
        VmsLayer vmsLayer = parseVmsLayerFromSimpleMessageIntegerValues(integerValues);
        if (DBG) {
            Log.d(TAG,
                    "Handling a data event for Layer: " + vmsLayer);
        }

        // Send the message.
        for (VmsHalSubscriberListener listener : mSubscriberListeners) {
            listener.onDataMessage(vmsLayer, payload);
        }
    }

    /**
     * Subscribe message format:
     * <ul>
     * <li>Message type.
     * <li>Layer id.
     * <li>Layer version.
     * </ul>
     */
    private void handleSubscribeEvent(List<Integer> integerValues) {
        VmsLayer vmsLayer = parseVmsLayerFromSimpleMessageIntegerValues(integerValues);
        if (DBG) {
            Log.d(TAG,
                    "Handling a subscribe event for Layer: " + vmsLayer);
        }
        addHalSubscription(vmsLayer);
    }

    /**
     * Unsubscribe message format:
     * <ul>
     * <li>Message type.
     * <li>Layer id.
     * <li>Layer version.
     * </ul>
     */
    private void handleUnsubscribeEvent(List<Integer> integerValues) {
        VmsLayer vmsLayer = parseVmsLayerFromSimpleMessageIntegerValues(integerValues);
        if (DBG) {
            Log.d(TAG,
                    "Handling an unsubscribe event for Layer: " + vmsLayer);
        }
        removeHalSubscription(vmsLayer);
    }

    private static int NUM_INTEGERS_IN_VMS_LAYER = 3;

    private VmsLayer parseVmsLayerFromIndex(List<Integer> integerValues, int index) {
        return new VmsLayer(integerValues.get(index++),
                integerValues.get(index++),
                integerValues.get(index++));
    }

    /**
     * Offering message format:
     * <ul>
     * <li>Message type.
     * <li>Publisher ID.
     * <li>Number of offerings.
     * <li>Each offering consists of:
     * <ul>
     * <li>Layer id.
     * <li>Layer version.
     * <li>Number of layer dependencies.
     * <li>Layer type/subtype/version.
     * </ul>
     * </ul>
     */
    private void handleOfferingEvent(List<Integer> integerValues) {
        int publisherId = integerValues.get(VmsOfferingMessageIntegerValuesIndex.PUBLISHER_ID);
        int numLayersDependencies =
                integerValues.get(VmsOfferingMessageIntegerValuesIndex.VMS_NUMBER_OF_LAYERS_DEPENDENCIES);
        int idx = VmsOfferingMessageIntegerValuesIndex.FIRST_DEPENDENCIES_INDEX;

        List<VmsLayerDependency> offeredLayers = new ArrayList<>();

        // An offering is layerId, LayerVersion, LayerType, NumDeps, <LayerId, LayerVersion> X NumDeps.
        for (int i = 0; i < numLayersDependencies; i++) {
            VmsLayer offeredLayer = parseVmsLayerFromIndex(integerValues, idx);
            idx += NUM_INTEGERS_IN_VMS_LAYER;

            int numDependenciesForLayer = integerValues.get(idx++);
            if (numDependenciesForLayer == 0) {
                offeredLayers.add(new VmsLayerDependency(offeredLayer));
            } else {
                Set<VmsLayer> dependencies = new HashSet<>();

                for (int j = 0; j < numDependenciesForLayer; j++) {
                    VmsLayer dependantLayer = parseVmsLayerFromIndex(integerValues, idx);
                    idx += NUM_INTEGERS_IN_VMS_LAYER;
                    dependencies.add(dependantLayer);
                }
                offeredLayers.add(new VmsLayerDependency(offeredLayer, dependencies));
            }
        }
        // Store the HAL offering.
        VmsLayersOffering offering = new VmsLayersOffering(offeredLayers, publisherId);
        synchronized (mLock) {
            updateOffering(mHalPublisherToken, offering);
        }
    }

    /**
     * Availability message format:
     * <ul>
     * <li>Message type.
     * <li>Number of layers.
     * <li>Layer type/subtype/version.
     * </ul>
     */
    private void handleHalAvailabilityRequestEvent() {
        synchronized (mLock) {
            Collection<VmsAssociatedLayer> availableLayers = mAvailableLayers.getAvailableLayers();
            VehiclePropValue vehiclePropertyValue =
                    toAvailabilityUpdateVehiclePropValue(
                            availableLayers,
                            VmsMessageType.AVAILABILITY_RESPONSE);

            setPropertyValue(vehiclePropertyValue);
        }
    }

    /**
     * VmsSubscriptionRequestFormat:
     * <ul>
     * <li>Message type.
     * </ul>
     * <p>
     * VmsSubscriptionResponseFormat:
     * <ul>
     * <li>Message type.
     * <li>Sequence number.
     * <li>Number of layers.
     * <li>Layer type/subtype/version.
     * </ul>
     */
    private void handleSubscriptionRequestEvent() {
        VmsSubscriptionState subscription = getSubscriptionState();
        VehiclePropValue vehicleProp =
                toTypedVmsVehiclePropValue(VmsMessageType.SUBSCRIPTIONS_RESPONSE);
        VehiclePropValue.RawValue v = vehicleProp.value;
        v.int32Values.add(subscription.getSequenceNumber());
        List<VmsLayer> layers = subscription.getLayers();
        v.int32Values.add(layers.size());
        for (VmsLayer layer : layers) {
            v.int32Values.add(layer.getId());
            v.int32Values.add(layer.getVersion());
            v.int32Values.add(layer.getSubType());
        }
        setPropertyValue(vehicleProp);
    }

    private void updateOffering(IBinder publisherToken, VmsLayersOffering offering) {
        Set<VmsAssociatedLayer> availableLayers = Collections.EMPTY_SET;
        synchronized (mLock) {
            mOfferings.put(publisherToken, offering);

            // Update layers availability.
            mAvailableLayers.setPublishersOffering(mOfferings.values());

            availableLayers = mAvailableLayers.getAvailableLayers();
        }
        notifyOfAvailabilityChange(availableLayers);
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println(TAG);
        writer.println("VmsProperty " + (mIsSupported ? "" : "not") + " supported.");
    }

    /**
     * Updates the VMS HAL property with the given value.
     *
     * @param layer          layer data to update the hal property.
     * @param hasSubscribers if it is a subscribe or unsubscribe message.
     * @return true if the call to the HAL to update the property was successful.
     */
    public boolean setSubscriptionRequest(VmsLayer layer, boolean hasSubscribers) {
        VehiclePropValue vehiclePropertyValue = toTypedVmsVehiclePropValueWithLayer(
                hasSubscribers ? VmsMessageType.SUBSCRIBE : VmsMessageType.UNSUBSCRIBE, layer);
        return setPropertyValue(vehiclePropertyValue);
    }

    public boolean setDataMessage(VmsLayer layer, byte[] payload) {
        VehiclePropValue vehiclePropertyValue =
                toTypedVmsVehiclePropValueWithLayer(VmsMessageType.DATA, layer);
        VehiclePropValue.RawValue v = vehiclePropertyValue.value;
        v.bytes.ensureCapacity(payload.length);
        for (byte b : payload) {
            v.bytes.add(b);
        }
        return setPropertyValue(vehiclePropertyValue);
    }

    public boolean notifyAvailabilityChangeToHal(Collection<VmsAssociatedLayer> availableLayers) {
        VehiclePropValue vehiclePropertyValue =
                toAvailabilityUpdateVehiclePropValue(
                        availableLayers,
                        VmsMessageType.AVAILABILITY_CHANGE);

        return setPropertyValue(vehiclePropertyValue);
    }

    public boolean setPropertyValue(VehiclePropValue vehiclePropertyValue) {
        try {
            mVehicleHal.set(vehiclePropertyValue);
            return true;
        } catch (PropertyTimeoutException e) {
            Log.e(CarLog.TAG_PROPERTY, "set, property not ready 0x" + toHexString(HAL_PROPERTY_ID));
        }
        return false;
    }

    private static VehiclePropValue toTypedVmsVehiclePropValue(int messageType) {
        VehiclePropValue vehicleProp = new VehiclePropValue();
        vehicleProp.prop = HAL_PROPERTY_ID;
        vehicleProp.areaId = VehicleAreaType.VEHICLE_AREA_TYPE_NONE;
        VehiclePropValue.RawValue v = vehicleProp.value;

        v.int32Values.add(messageType);
        return vehicleProp;
    }

    /**
     * Creates a {@link VehiclePropValue}
     */
    private static VehiclePropValue toTypedVmsVehiclePropValueWithLayer(
            int messageType, VmsLayer layer) {
        VehiclePropValue vehicleProp = toTypedVmsVehiclePropValue(messageType);
        VehiclePropValue.RawValue v = vehicleProp.value;
        v.int32Values.add(layer.getId());
        v.int32Values.add(layer.getVersion());
        v.int32Values.add(layer.getSubType());
        return vehicleProp;
    }

    private static VehiclePropValue toAvailabilityUpdateVehiclePropValue(
            Collection<VmsAssociatedLayer> availableAssociatedLayers, int messageType) {

        if (!AVAILABILITY_MESSAGE_TYPES.contains(messageType)) {
            throw new IllegalArgumentException("Unsupported availability type: " + messageType);
        }
        VehiclePropValue vehicleProp =
                toTypedVmsVehiclePropValue(messageType);
        populateAvailabilityPropValueFields(availableAssociatedLayers, vehicleProp);
        return vehicleProp;

    }

    private static void populateAvailabilityPropValueFields(
            Collection<VmsAssociatedLayer> availableAssociatedLayers,
            VehiclePropValue vehicleProp) {
        VehiclePropValue.RawValue v = vehicleProp.value;
        int numLayers = availableAssociatedLayers.size();
        v.int32Values.add(numLayers);
        for (VmsAssociatedLayer layer : availableAssociatedLayers) {
            v.int32Values.add(layer.getVmsLayer().getId());
            v.int32Values.add(layer.getVmsLayer().getSubType());
            v.int32Values.add(layer.getVmsLayer().getVersion());
            v.int32Values.add(layer.getPublisherIds().size());
            for (int publisherId : layer.getPublisherIds()) {
                v.int32Values.add(publisherId);
            }
        }
    }
}
