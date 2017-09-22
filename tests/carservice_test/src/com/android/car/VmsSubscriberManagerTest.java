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

package com.android.car;

import static org.junit.Assume.assumeTrue;

import android.car.Car;
import android.car.VehicleAreaType;
import android.car.vms.VmsAssociatedLayer;
import android.car.vms.VmsLayer;
import android.car.vms.VmsSubscriberManager;
import android.car.vms.VmsSubscriberManager.VmsSubscriberClientCallback;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyAccess;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyChangeMode;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.hardware.automotive.vehicle.V2_0.VmsMessageType;
import android.os.SystemClock;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;
import com.android.car.vehiclehal.VehiclePropValueBuilder;
import com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@MediumTest
public class VmsSubscriberManagerTest extends MockedCarTestBase {
    private static final String TAG = "VmsSubscriberManagerTest";
    private static final int PUBLISHER_ID = 17;
    private static final int WRONG_PUBLISHER_ID = 26;
    private static final Set<Integer> PUBLISHERS_LIST = new HashSet<Integer>(Arrays.asList(PUBLISHER_ID));

    private static final int SUBSCRIPTION_LAYER_ID = 2;
    private static final int SUBSCRIPTION_LAYER_VERSION = 3;
    private static final int MOCK_PUBLISHER_LAYER_SUBTYPE = 444;
    private static final VmsLayer SUBSCRIPTION_LAYER = new VmsLayer(SUBSCRIPTION_LAYER_ID,
            MOCK_PUBLISHER_LAYER_SUBTYPE,
            SUBSCRIPTION_LAYER_VERSION);
    private static final VmsAssociatedLayer SUBSCRIPTION_ASSOCIATED_LAYER =
            new VmsAssociatedLayer(SUBSCRIPTION_LAYER, PUBLISHERS_LIST);

    private static final int SUBSCRIPTION_DEPENDANT_LAYER_ID_1 = 4;
    private static final int SUBSCRIPTION_DEPENDANT_LAYER_VERSION_1 = 5;
    private static final VmsLayer SUBSCRIPTION_DEPENDANT_LAYER_1 =
            new VmsLayer(SUBSCRIPTION_DEPENDANT_LAYER_ID_1,
                    MOCK_PUBLISHER_LAYER_SUBTYPE,
                    SUBSCRIPTION_DEPENDANT_LAYER_VERSION_1);

    private static final VmsAssociatedLayer SUBSCRIPTION_DEPENDANT_ASSOCIATED_LAYER_1 =
            new VmsAssociatedLayer(SUBSCRIPTION_DEPENDANT_LAYER_1, PUBLISHERS_LIST);

    private static final int SUBSCRIPTION_DEPENDANT_LAYER_ID_2 = 6;
    private static final int SUBSCRIPTION_DEPENDANT_LAYER_VERSION_2 = 7;
    private static final VmsLayer SUBSCRIPTION_DEPENDANT_LAYER_2 =
            new VmsLayer(SUBSCRIPTION_DEPENDANT_LAYER_ID_2,
                    MOCK_PUBLISHER_LAYER_SUBTYPE,
                    SUBSCRIPTION_DEPENDANT_LAYER_VERSION_2);

    private static final VmsAssociatedLayer SUBSCRIPTION_DEPENDANT_ASSOCIATED_LAYER_2 =
            new VmsAssociatedLayer(SUBSCRIPTION_DEPENDANT_LAYER_2, PUBLISHERS_LIST);

    private static final int SUBSCRIPTION_UNSUPPORTED_LAYER_ID = 100;
    private static final int SUBSCRIPTION_UNSUPPORTED_LAYER_VERSION = 200;


    private HalHandler mHalHandler;
    // Used to block until the HAL property is updated in HalHandler.onPropertySet.
    private Semaphore mHalHandlerSemaphore;
    // Used to block until a value is propagated to the TestClientCallback.onVmsMessageReceived.
    private Semaphore mSubscriberSemaphore;

    @Override
    protected synchronized void configureMockedHal() {
        mHalHandler = new HalHandler();
        addProperty(VehicleProperty.VEHICLE_MAP_SERVICE, mHalHandler)
                .setChangeMode(VehiclePropertyChangeMode.ON_CHANGE)
                .setAccess(VehiclePropertyAccess.READ_WRITE)
                .setSupportedAreas(VehicleAreaType.VEHICLE_AREA_TYPE_NONE);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mSubscriberSemaphore = new Semaphore(0);
        mHalHandlerSemaphore = new Semaphore(0);
    }

    @Override
    protected synchronized void tearDown() throws Exception {
        super.tearDown();
    }

    // Test injecting a value in the HAL and verifying it propagates to a subscriber.
    public void testSubscribe() throws Exception {
        VmsSubscriberManager vmsSubscriberManager = (VmsSubscriberManager) getCar().getCarManager(
                Car.VMS_SUBSCRIBER_SERVICE);
        TestClientCallback clientCallback = new TestClientCallback();
        vmsSubscriberManager.registerClientCallback(clientCallback);
        vmsSubscriberManager.subscribe(SUBSCRIPTION_LAYER);

        // Inject a value and wait for its callback in TestClientCallback.onVmsMessageReceived.
        VehiclePropValue v = VehiclePropValueBuilder.newBuilder(VehicleProperty.VEHICLE_MAP_SERVICE)
                .setAreaId(VehicleAreaType.VEHICLE_AREA_TYPE_NONE)
                .setTimestamp(SystemClock.elapsedRealtimeNanos())
                .build();
        v.value.int32Values.add(VmsMessageType.DATA); // MessageType
        v.value.int32Values.add(SUBSCRIPTION_LAYER_ID);
        v.value.int32Values.add(MOCK_PUBLISHER_LAYER_SUBTYPE);
        v.value.int32Values.add(SUBSCRIPTION_LAYER_VERSION);
        v.value.int32Values.add(PUBLISHER_ID);
        v.value.bytes.add((byte) 0xa);
        v.value.bytes.add((byte) 0xb);
        assertEquals(0, mSubscriberSemaphore.availablePermits());

        getMockedVehicleHal().injectEvent(v);
        assertTrue(mSubscriberSemaphore.tryAcquire(2L, TimeUnit.SECONDS));
        assertEquals(SUBSCRIPTION_LAYER, clientCallback.getLayer());
        byte[] expectedPayload = {(byte) 0xa, (byte) 0xb};
        assertTrue(Arrays.equals(expectedPayload, clientCallback.getPayload()));
    }


    // Test injecting a value in the HAL and verifying it propagates to a subscriber.
    public void testSubscribeToPublisher() throws Exception {
        VmsSubscriberManager vmsSubscriberManager = (VmsSubscriberManager) getCar().getCarManager(
                Car.VMS_SUBSCRIBER_SERVICE);
        TestClientCallback clientCallback = new TestClientCallback();
        vmsSubscriberManager.registerClientCallback(clientCallback);
        vmsSubscriberManager.subscribe(SUBSCRIPTION_LAYER, PUBLISHER_ID);

        // Inject a value and wait for its callback in TestClientCallback.onVmsMessageReceived.
        VehiclePropValue v = VehiclePropValueBuilder.newBuilder(VehicleProperty.VEHICLE_MAP_SERVICE)
                .setAreaId(VehicleAreaType.VEHICLE_AREA_TYPE_NONE)
                .setTimestamp(SystemClock.elapsedRealtimeNanos())
                .build();
        v.value.int32Values.add(VmsMessageType.DATA); // MessageType
        v.value.int32Values.add(SUBSCRIPTION_LAYER_ID);
        v.value.int32Values.add(MOCK_PUBLISHER_LAYER_SUBTYPE);
        v.value.int32Values.add(SUBSCRIPTION_LAYER_VERSION);
        v.value.int32Values.add(WRONG_PUBLISHER_ID);
        v.value.bytes.add((byte) 0xa);
        v.value.bytes.add((byte) 0xb);
        assertEquals(0, mSubscriberSemaphore.availablePermits());

        getMockedVehicleHal().injectEvent(v);

        assertFalse(mSubscriberSemaphore.tryAcquire(2L, TimeUnit.SECONDS));
    }

    // Test injecting a value in the HAL and verifying it propagates to a subscriber.
    public void testSubscribeFromPublisher() throws Exception {
        VmsSubscriberManager vmsSubscriberManager = (VmsSubscriberManager) getCar().getCarManager(
                Car.VMS_SUBSCRIBER_SERVICE);
        TestClientCallback clientCallback = new TestClientCallback();
        vmsSubscriberManager.registerClientCallback(clientCallback);
        vmsSubscriberManager.subscribe(SUBSCRIPTION_LAYER, PUBLISHER_ID);

        // Inject a value and wait for its callback in TestClientCallback.onVmsMessageReceived.
        VehiclePropValue v = VehiclePropValueBuilder.newBuilder(VehicleProperty.VEHICLE_MAP_SERVICE)
                .setAreaId(VehicleAreaType.VEHICLE_AREA_TYPE_NONE)
                .setTimestamp(SystemClock.elapsedRealtimeNanos())
                .build();
        v.value.int32Values.add(VmsMessageType.DATA); // MessageType
        v.value.int32Values.add(SUBSCRIPTION_LAYER_ID);
        v.value.int32Values.add(MOCK_PUBLISHER_LAYER_SUBTYPE); //<-
        v.value.int32Values.add(SUBSCRIPTION_LAYER_VERSION);
        v.value.int32Values.add(PUBLISHER_ID);
        v.value.bytes.add((byte) 0xa);
        v.value.bytes.add((byte) 0xb);
        assertEquals(0, mSubscriberSemaphore.availablePermits());

        getMockedVehicleHal().injectEvent(v);
        assertTrue(mSubscriberSemaphore.tryAcquire(2L, TimeUnit.SECONDS));
        assertEquals(SUBSCRIPTION_LAYER, clientCallback.getLayer());
        byte[] expectedPayload = {(byte) 0xa, (byte) 0xb};
        assertTrue(Arrays.equals(expectedPayload, clientCallback.getPayload()));
    }

    // Test injecting a value in the HAL and verifying it does not propagate to a subscriber.
    public void testUnsubscribe() throws Exception {
        VmsSubscriberManager vmsSubscriberManager = (VmsSubscriberManager) getCar().getCarManager(
                Car.VMS_SUBSCRIBER_SERVICE);
        TestClientCallback clientCallback = new TestClientCallback();
        vmsSubscriberManager.registerClientCallback(clientCallback);
        vmsSubscriberManager.subscribe(SUBSCRIPTION_LAYER);
        vmsSubscriberManager.unsubscribe(SUBSCRIPTION_LAYER);

        // Inject a value and wait for its callback in TestClientCallback.onVmsMessageReceived.
        VehiclePropValue v = VehiclePropValueBuilder.newBuilder(VehicleProperty.VEHICLE_MAP_SERVICE)
                .setAreaId(VehicleAreaType.VEHICLE_AREA_TYPE_NONE)
                .setTimestamp(SystemClock.elapsedRealtimeNanos())
                .build();
        v.value.int32Values.add(VmsMessageType.DATA); // MessageType
        v.value.int32Values.add(SUBSCRIPTION_LAYER_ID);
        v.value.int32Values.add(MOCK_PUBLISHER_LAYER_SUBTYPE);
        v.value.int32Values.add(SUBSCRIPTION_LAYER_VERSION);
        v.value.int32Values.add(PUBLISHER_ID);
        v.value.bytes.add((byte) 0xa);
        v.value.bytes.add((byte) 0xb);
        assertEquals(0, mSubscriberSemaphore.availablePermits());

        getMockedVehicleHal().injectEvent(v);
        assertFalse(mSubscriberSemaphore.tryAcquire(2L, TimeUnit.SECONDS));
    }

    // Test injecting a value in the HAL and verifying it does not propagate to a subscriber.
    public void testSubscribeFromWrongPublisher() throws Exception {
        VmsSubscriberManager vmsSubscriberManager = (VmsSubscriberManager) getCar().getCarManager(
                Car.VMS_SUBSCRIBER_SERVICE);
        TestClientCallback clientCallback = new TestClientCallback();
        vmsSubscriberManager.registerClientCallback(clientCallback);
        vmsSubscriberManager.subscribe(SUBSCRIPTION_LAYER, PUBLISHER_ID);

        // Inject a value and wait for its callback in TestClientCallback.onVmsMessageReceived.
        VehiclePropValue v = VehiclePropValueBuilder.newBuilder(VehicleProperty.VEHICLE_MAP_SERVICE)
                .setAreaId(VehicleAreaType.VEHICLE_AREA_TYPE_NONE)
                .setTimestamp(SystemClock.elapsedRealtimeNanos())
                .build();
        v.value.int32Values.add(VmsMessageType.DATA); // MessageType
        v.value.int32Values.add(SUBSCRIPTION_LAYER_ID);
        v.value.int32Values.add(MOCK_PUBLISHER_LAYER_SUBTYPE);
        v.value.int32Values.add(SUBSCRIPTION_LAYER_VERSION);
        v.value.int32Values.add(WRONG_PUBLISHER_ID);
        v.value.bytes.add((byte) 0xa);
        v.value.bytes.add((byte) 0xb);
        assertEquals(0, mSubscriberSemaphore.availablePermits());

        getMockedVehicleHal().injectEvent(v);
        assertFalse(mSubscriberSemaphore.tryAcquire(2L, TimeUnit.SECONDS));
    }

    // Test injecting a value in the HAL and verifying it does not propagate to a subscriber.
    public void testUnsubscribeFromPublisher() throws Exception {
        VmsSubscriberManager vmsSubscriberManager = (VmsSubscriberManager) getCar().getCarManager(
                Car.VMS_SUBSCRIBER_SERVICE);
        TestClientCallback clientCallback = new TestClientCallback();
        vmsSubscriberManager.registerClientCallback(clientCallback);
        vmsSubscriberManager.subscribe(SUBSCRIPTION_LAYER, PUBLISHER_ID);
        vmsSubscriberManager.unsubscribe(SUBSCRIPTION_LAYER, PUBLISHER_ID);

        // Inject a value and wait for its callback in TestClientCallback.onVmsMessageReceived.
        VehiclePropValue v = VehiclePropValueBuilder.newBuilder(VehicleProperty.VEHICLE_MAP_SERVICE)
                .setAreaId(VehicleAreaType.VEHICLE_AREA_TYPE_NONE)
                .setTimestamp(SystemClock.elapsedRealtimeNanos())
                .build();
        v.value.int32Values.add(VmsMessageType.DATA); // MessageType
        v.value.int32Values.add(SUBSCRIPTION_LAYER_ID);
        v.value.int32Values.add(MOCK_PUBLISHER_LAYER_SUBTYPE);
        v.value.int32Values.add(SUBSCRIPTION_LAYER_VERSION);
        v.value.int32Values.add(PUBLISHER_ID);
        v.value.bytes.add((byte) 0xa);
        v.value.bytes.add((byte) 0xb);
        assertEquals(0, mSubscriberSemaphore.availablePermits());

        getMockedVehicleHal().injectEvent(v);
        assertFalse(mSubscriberSemaphore.tryAcquire(2L, TimeUnit.SECONDS));
    }


    // Test injecting a value in the HAL and verifying it propagates to a subscriber.
    public void testSubscribeAll() throws Exception {
        VmsSubscriberManager vmsSubscriberManager = (VmsSubscriberManager) getCar().getCarManager(
                Car.VMS_SUBSCRIBER_SERVICE);
        TestClientCallback clientCallback = new TestClientCallback();
        vmsSubscriberManager.registerClientCallback(clientCallback);
        vmsSubscriberManager.startMonitoring();

        // Inject a value and wait for its callback in TestClientCallback.onVmsMessageReceived.
        VehiclePropValue v = VehiclePropValueBuilder.newBuilder(VehicleProperty.VEHICLE_MAP_SERVICE)
                .setAreaId(VehicleAreaType.VEHICLE_AREA_TYPE_NONE)
                .setTimestamp(SystemClock.elapsedRealtimeNanos())
                .build();
        v.value.int32Values.add(VmsMessageType.DATA); // MessageType
        v.value.int32Values.add(SUBSCRIPTION_LAYER_ID);
        v.value.int32Values.add(MOCK_PUBLISHER_LAYER_SUBTYPE);
        v.value.int32Values.add(SUBSCRIPTION_LAYER_VERSION);
        v.value.int32Values.add(PUBLISHER_ID);
        v.value.bytes.add((byte) 0xa);
        v.value.bytes.add((byte) 0xb);
        assertEquals(0, mSubscriberSemaphore.availablePermits());

        getMockedVehicleHal().injectEvent(v);
        assertTrue(mSubscriberSemaphore.tryAcquire(2L, TimeUnit.SECONDS));
        assertEquals(SUBSCRIPTION_LAYER, clientCallback.getLayer());
        byte[] expectedPayload = {(byte) 0xa, (byte) 0xb};
        assertTrue(Arrays.equals(expectedPayload, clientCallback.getPayload()));
    }

    // Test injecting a value in the HAL and verifying it propagates to a subscriber.
    public void testSimpleAvailableLayers() throws Exception {
        VmsSubscriberManager vmsSubscriberManager = (VmsSubscriberManager) getCar().getCarManager(
                Car.VMS_SUBSCRIBER_SERVICE);
        TestClientCallback clientCallback = new TestClientCallback();
        vmsSubscriberManager.registerClientCallback(clientCallback);

        // Inject a value and wait for its callback in TestClientCallback.onLayersAvailabilityChanged.
        VehiclePropValue v = VehiclePropValueBuilder.newBuilder(VehicleProperty.VEHICLE_MAP_SERVICE)
                .setAreaId(VehicleAreaType.VEHICLE_AREA_TYPE_NONE)
                .setTimestamp(SystemClock.elapsedRealtimeNanos())
                .build();
        /*
        Offering:
        Layer             | Dependency
        ===============================
        (2, 3, 444), [17] | {}

        Expected availability:
        {(2, 3, 444 [17])}
         */
        v.value.int32Values.addAll(
                Arrays.asList(
                        VmsMessageType.OFFERING, // MessageType
                        PUBLISHER_ID,
                        1, // Number of offered layers

                        SUBSCRIPTION_LAYER_ID,
                        MOCK_PUBLISHER_LAYER_SUBTYPE,
                        SUBSCRIPTION_LAYER_VERSION,
                        0 // number of dependencies for layer
                )
        );

        assertEquals(0, mSubscriberSemaphore.availablePermits());

        getMockedVehicleHal().injectEvent(v);
        assertTrue(mSubscriberSemaphore.tryAcquire(2L, TimeUnit.SECONDS));
        List<VmsAssociatedLayer> expectedAvailableLayers =
                new ArrayList<>(Arrays.asList(SUBSCRIPTION_ASSOCIATED_LAYER));
        assertTrue(expectedAvailableLayers.containsAll(clientCallback.getAvailableLayers()));
        assertEquals(expectedAvailableLayers.size(), clientCallback.getAvailableLayers().size());
    }

    // Test injecting a value in the HAL and verifying it propagates to a subscriber after it has
    // subscribed to a layer.
    public void testSimpleAvailableLayersAfterSubscription() throws Exception {
        VmsSubscriberManager vmsSubscriberManager = (VmsSubscriberManager) getCar().getCarManager(
                Car.VMS_SUBSCRIBER_SERVICE);
        TestClientCallback clientCallback = new TestClientCallback();
        vmsSubscriberManager.registerClientCallback(clientCallback);
        vmsSubscriberManager.subscribe(SUBSCRIPTION_LAYER);

        // Inject a value and wait for its callback in TestClientCallback.onLayersAvailabilityChanged.
        VehiclePropValue v = VehiclePropValueBuilder.newBuilder(VehicleProperty.VEHICLE_MAP_SERVICE)
                .setAreaId(VehicleAreaType.VEHICLE_AREA_TYPE_NONE)
                .setTimestamp(SystemClock.elapsedRealtimeNanos())
                .build();
        /*
        Offering:
        Layer             | Dependency
        ===============================
        (2, 3, 444), [17] | {}

        Expected availability:
        {(2, 3, 444 [17])}
         */
        v.value.int32Values.addAll(
                Arrays.asList(
                        VmsMessageType.OFFERING, // MessageType
                        PUBLISHER_ID,
                        1, // Number of offered layers

                        SUBSCRIPTION_LAYER_ID,
                        MOCK_PUBLISHER_LAYER_SUBTYPE,
                        SUBSCRIPTION_LAYER_VERSION,
                        0 // number of dependencies for layer
                )
        );

        assertEquals(0, mSubscriberSemaphore.availablePermits());

        getMockedVehicleHal().injectEvent(v);
        assertTrue(mSubscriberSemaphore.tryAcquire(2L, TimeUnit.SECONDS));
        List<VmsAssociatedLayer> expectedAvailableLayers =
                new ArrayList<>(Arrays.asList(SUBSCRIPTION_ASSOCIATED_LAYER));
        assertTrue(expectedAvailableLayers.containsAll(clientCallback.getAvailableLayers()));
        assertEquals(expectedAvailableLayers.size(), clientCallback.getAvailableLayers().size());
    }

    // Test injecting a value in the HAL and verifying it does not propagates to a subscriber after
    // it has unregistered its callback.
    public void testSimpleAvailableLayersAfterUnregister() throws Exception {
        VmsSubscriberManager vmsSubscriberManager = (VmsSubscriberManager) getCar().getCarManager(
                Car.VMS_SUBSCRIBER_SERVICE);
        TestClientCallback clientCallback = new TestClientCallback();
        vmsSubscriberManager.registerClientCallback(clientCallback);
        vmsSubscriberManager.unregisterClientCallback();


        // Inject a value and wait for its callback in TestClientCallback.onLayersAvailabilityChanged.
        VehiclePropValue v = VehiclePropValueBuilder.newBuilder(VehicleProperty.VEHICLE_MAP_SERVICE)
                .setAreaId(VehicleAreaType.VEHICLE_AREA_TYPE_NONE)
                .setTimestamp(SystemClock.elapsedRealtimeNanos())
                .build();
        /*
        Offering:
        Layer             | Dependency
        ===============================
        (2, 3, 444), [17] | {}

        Expected availability:
        {(2, 3, 444 [17])}
         */
        v.value.int32Values.addAll(
                Arrays.asList(
                        VmsMessageType.OFFERING, // MessageType
                        PUBLISHER_ID,
                        1, // Number of offered layers

                        SUBSCRIPTION_LAYER_ID,
                        SUBSCRIPTION_LAYER_VERSION,
                        MOCK_PUBLISHER_LAYER_SUBTYPE,
                        0 // number of dependencies for layer
                )
        );

        assertEquals(0, mSubscriberSemaphore.availablePermits());
        getMockedVehicleHal().injectEvent(v);
        assertFalse(mSubscriberSemaphore.tryAcquire(2L, TimeUnit.SECONDS));
    }

    // Test injecting a value in the HAL and verifying it does not propagates to a subscriber after
    // it has unregistered its callback.
    public void testSomething() throws Exception {
        VmsSubscriberManager vmsSubscriberManager = (VmsSubscriberManager) getCar().getCarManager(
                Car.VMS_SUBSCRIBER_SERVICE);
        TestClientCallback clientCallback = new TestClientCallback();
        vmsSubscriberManager.registerClientCallback(clientCallback);
        vmsSubscriberManager.subscribe(SUBSCRIPTION_LAYER);
        try {
            vmsSubscriberManager.unregisterClientCallback();
        } catch (IllegalArgumentException e) {
            return;
        }
        fail();
    }


    // Test injecting a value in the HAL and verifying it propagates to a subscriber.
    public void testComplexAvailableLayers() throws Exception {
        VmsSubscriberManager vmsSubscriberManager = (VmsSubscriberManager) getCar().getCarManager(
                Car.VMS_SUBSCRIBER_SERVICE);
        TestClientCallback clientCallback = new TestClientCallback();
        vmsSubscriberManager.registerClientCallback(clientCallback);

        // Inject a value and wait for its callback in TestClientCallback.onLayersAvailabilityChanged.
        VehiclePropValue v = VehiclePropValueBuilder.newBuilder(VehicleProperty.VEHICLE_MAP_SERVICE)
                .setAreaId(VehicleAreaType.VEHICLE_AREA_TYPE_NONE)
                .setTimestamp(SystemClock.elapsedRealtimeNanos())
                .build();
        /*
        Offering:
        Layer  | Dependency
        ====================
        (2, 3, 444), [17] | {}
        (4, 5, 444), [17] | {(2, 3)}
        (6, 7, 444), [17] | {(2, 3), (4, 5)}
        (6, 7, 444), [17] | {(100, 200)}

        Expected availability:
        {(2, 3, 444 [17]), (4, 5, 444 [17]), (6, 7, 444 [17])}
         */

        v.value.int32Values.addAll(
                Arrays.asList(
                        VmsMessageType.OFFERING, // MessageType
                        PUBLISHER_ID,
                        4, // Number of offered layers

                        SUBSCRIPTION_LAYER_ID,
                        MOCK_PUBLISHER_LAYER_SUBTYPE,
                        SUBSCRIPTION_LAYER_VERSION,
                        0, // number of dependencies for layer

                        SUBSCRIPTION_DEPENDANT_LAYER_ID_1,
                        MOCK_PUBLISHER_LAYER_SUBTYPE,
                        SUBSCRIPTION_DEPENDANT_LAYER_VERSION_1,
                        1, // number of dependencies for layer
                        SUBSCRIPTION_LAYER_ID,
                        MOCK_PUBLISHER_LAYER_SUBTYPE,
                        SUBSCRIPTION_LAYER_VERSION,

                        SUBSCRIPTION_DEPENDANT_LAYER_ID_2,
                        MOCK_PUBLISHER_LAYER_SUBTYPE,
                        SUBSCRIPTION_DEPENDANT_LAYER_VERSION_2,
                        2, // number of dependencies for layer
                        SUBSCRIPTION_LAYER_ID,
                        MOCK_PUBLISHER_LAYER_SUBTYPE,
                        SUBSCRIPTION_LAYER_VERSION,
                        SUBSCRIPTION_DEPENDANT_LAYER_ID_1,
                        MOCK_PUBLISHER_LAYER_SUBTYPE,
                        SUBSCRIPTION_DEPENDANT_LAYER_VERSION_1,

                        SUBSCRIPTION_DEPENDANT_LAYER_ID_2,
                        MOCK_PUBLISHER_LAYER_SUBTYPE,
                        SUBSCRIPTION_DEPENDANT_LAYER_VERSION_2,
                        1, // number of dependencies for layer
                        SUBSCRIPTION_UNSUPPORTED_LAYER_ID,
                        MOCK_PUBLISHER_LAYER_SUBTYPE,
                        SUBSCRIPTION_UNSUPPORTED_LAYER_VERSION
                )
        );

        assertEquals(0, mSubscriberSemaphore.availablePermits());

        List<VmsAssociatedLayer> expectedAvailableLayers =
                new ArrayList<>(Arrays.asList(
                        SUBSCRIPTION_ASSOCIATED_LAYER,
                        SUBSCRIPTION_DEPENDANT_ASSOCIATED_LAYER_1,
                        SUBSCRIPTION_DEPENDANT_ASSOCIATED_LAYER_2
                ));
        getMockedVehicleHal().injectEvent(v);
        assertTrue(mSubscriberSemaphore.tryAcquire(2L, TimeUnit.SECONDS));
        assertTrue(expectedAvailableLayers.containsAll(clientCallback.getAvailableLayers()));
        assertEquals(expectedAvailableLayers.size(), clientCallback.getAvailableLayers().size());
    }

    private class HalHandler implements VehicleHalPropertyHandler {
        private VehiclePropValue mValue;

        @Override
        public synchronized void onPropertySet(VehiclePropValue value) {
            mValue = value;
            mHalHandlerSemaphore.release();
        }

        @Override
        public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
            return mValue != null ? mValue : value;
        }

        @Override
        public synchronized void onPropertySubscribe(int property, int zones, float sampleRate) {
            Log.d(TAG, "onPropertySubscribe property " + property + " sampleRate " + sampleRate);
        }

        @Override
        public synchronized void onPropertyUnsubscribe(int property) {
            Log.d(TAG, "onPropertyUnSubscribe property " + property);
        }

        public VehiclePropValue getValue() {
            return mValue;
        }
    }

    private class TestClientCallback implements VmsSubscriberClientCallback {
        private VmsLayer mLayer;
        private byte[] mPayload;
        private List<VmsLayer> mAvailableLayers = new ArrayList<>();

        @Override
        public void onVmsMessageReceived(VmsLayer layer, byte[] payload) {
            Log.d(TAG, "onVmsMessageReceived: layer: " + layer + " Payload: " + payload);
            mLayer = layer;
            mPayload = payload;
            mSubscriberSemaphore.release();
        }

        @Override
        public void onLayersAvailabilityChanged(List<VmsLayer> availableLayers) {
            Log.d(TAG, "onLayersAvailabilityChanged: Layers: " + availableLayers);
            mAvailableLayers.addAll(availableLayers);
            mSubscriberSemaphore.release();
        }

        public VmsLayer getLayer() {
            return mLayer;
        }

        public byte[] getPayload() {
            return mPayload;
        }

        public List<VmsLayer> getAvailableLayers() {
            return mAvailableLayers;
        }
    }
}
