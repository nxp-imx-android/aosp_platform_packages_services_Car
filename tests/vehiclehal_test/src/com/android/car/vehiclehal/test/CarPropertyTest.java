/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.car.vehiclehal.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static java.lang.Integer.toHexString;

import android.car.Car;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.CarPropertyManager.CarPropertyEventCallback;
import android.util.ArraySet;
import android.util.Log;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * The test suite will execute end-to-end Car Property API test by generating VHAL property data
 * from default VHAL and verify those data on the fly. The test data is coming from assets/ folder
 * in the test APK and will be shared with VHAL to execute the test.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class CarPropertyTest extends E2eCarTestBase {

    private static final String TAG = Utils.concatTag(CarPropertyTest.class);

    // Test should be completed within 10 minutes as it only covers a finite set of properties
    private static final Duration TEST_TIME_OUT = Duration.ofMinutes(10);

    private static final String CAR_HVAC_TEST_JSON = "car_hvac_test.json";
    private static final String CAR_HVAC_TEST_SET_JSON = "car_hvac_test.json";
    private static final String CAR_INFO_TEST_JSON = "car_info_test.json";
    // kMixedTypePropertyForTest property ID
    private static final int MIXED_TYPE_PROPERTY = 0x21e01111;
    // kMixedTypePropertyForTest default value
    private static final Object[] DEFAULT_VALUE = {"MIXED property", true, 2, 3, 4.5f};
    private static final String CAR_PROPERTY_TEST_JSON = "car_property_test.json";
    private static final int GEAR_PROPERTY_ID = 289408000;

    private class CarPropertyEventReceiver implements CarPropertyEventCallback {

        private VhalEventVerifier mVerifier;
        private Integer mNumOfEventToSkip;

        CarPropertyEventReceiver(VhalEventVerifier verifier, int numOfEventToSkip) {
            mVerifier = verifier;
            mNumOfEventToSkip = numOfEventToSkip;
        }

        @Override
        public void onChangeEvent(CarPropertyValue carPropertyValue) {
            Log.d(TAG, "Received event: " + carPropertyValue);
            synchronized (mNumOfEventToSkip) {
                if (mNumOfEventToSkip > 0) {
                    mNumOfEventToSkip--;
                    return;
                }
            }
            mVerifier.verify(carPropertyValue);
        }

        @Override
        public void onErrorEvent(final int propertyId, final int zone) {
            Assert.fail("Error: propertyId=" + toHexString(propertyId) + " zone=" + zone);
        }
    }

    private int countNumPropEventsToSkip(CarPropertyManager propMgr, ArraySet<Integer> props) {
        int numToSkip = 0;
        for (CarPropertyConfig c : propMgr.getPropertyList(props)) {
            numToSkip += c.getAreaCount();
        }
        return numToSkip;
    }

    /**
     * This test will use {@link CarPropertyManager#setProperty(Class, int, int, Object)} to turn
     * on the HVAC_PROWER and then let Default VHAL to generate HVAC data and verify on-the-fly
     * in the test. It is simulating the HVAC actions coming from hard buttons in a car.
     * @throws Exception
     */
    @Test
    public void testHvacHardButtonOperations() throws Exception {

        Log.d(TAG, "Prepare HVAC test data");
        List<CarPropertyValue> expectedEvents = getExpectedEvents(CAR_HVAC_TEST_JSON);
        List<CarPropertyValue> expectedSetEvents = getExpectedEvents(CAR_HVAC_TEST_SET_JSON);

        CarPropertyManager propMgr = (CarPropertyManager) mCar.getCarManager(Car.PROPERTY_SERVICE);
        assertNotNull("CarPropertyManager is null", propMgr);

        final long waitForSetMillisecond = 2;
        for (CarPropertyValue expectedEvent : expectedSetEvents) {
            Class valueClass = expectedEvent.getValue().getClass();
            propMgr.setProperty(valueClass,
                    expectedEvent.getPropertyId(),
                    expectedEvent.getAreaId(),
                    expectedEvent.getValue());
            Thread.sleep(waitForSetMillisecond);
            CarPropertyValue receivedEvent = propMgr.getProperty(valueClass,
                    expectedEvent.getPropertyId(), expectedEvent.getAreaId());
            assertTrue("Mismatched events, expected: " + expectedEvent + ", received: "
                    + receivedEvent, Utils.areCarPropertyValuesEqual(expectedEvent, receivedEvent));
        }

        VhalEventVerifier verifier = new VhalEventVerifier(expectedEvents);

        ArraySet<Integer> props = new ArraySet<>();
        for (CarPropertyValue event : expectedEvents) {
            if (!props.contains(event.getPropertyId())) {
                props.add(event.getPropertyId());
            }
        }

        int numToSkip = countNumPropEventsToSkip(propMgr, props);
        Log.d(TAG, String.format("Start listening to the HAL."
                                 + " Skipping %d events for listener registration", numToSkip));
        CarPropertyEventCallback receiver =
                new CarPropertyEventReceiver(verifier, numToSkip);
        for (Integer prop : props) {
            propMgr.registerCallback(receiver, prop, 0);
        }

        File sharedJson = makeShareable(CAR_HVAC_TEST_JSON);
        Log.d(TAG, "Send command to VHAL to start generation");
        VhalEventGenerator hvacGenerator =
                new JsonVhalEventGenerator(mVehicle).setJsonFile(sharedJson);
        hvacGenerator.start();

        Log.d(TAG, "Receiving and verifying VHAL events");
        verifier.waitForEnd(TEST_TIME_OUT.toMillis());

        Log.d(TAG, "Send command to VHAL to stop generation");
        hvacGenerator.stop();
        propMgr.unregisterCallback(receiver);

        assertTrue("Detected mismatched events: " + verifier.getResultString(),
                    verifier.getMismatchedEvents().isEmpty());
    }

    /**
     * This test will load static vehicle information from test data file and verify them through
     * get calls.
     * @throws Exception
     */
    @Test
    public void testStaticInfoOperations() throws Exception {
        Log.d(TAG, "Prepare static car information");

        List<CarPropertyValue> expectedEvents = getExpectedEvents(CAR_INFO_TEST_JSON);
        CarPropertyManager propMgr = (CarPropertyManager) mCar.getCarManager(Car.PROPERTY_SERVICE);
        assertNotNull("CarPropertyManager is null", propMgr);

        File sharedJson = makeShareable(CAR_INFO_TEST_JSON);
        Log.d(TAG, "Send command to VHAL to start generation");
        VhalEventGenerator infoGenerator =
                new JsonVhalEventGenerator(mVehicle).setJsonFile(sharedJson);
        infoGenerator.start();

        // Wait for some time to ensure information is all loaded
        // It is assuming the test data is not very large
        Thread.sleep(2000);

        Log.d(TAG, "Send command to VHAL to stop generation");
        infoGenerator.stop();

        for (CarPropertyValue expectedEvent : expectedEvents) {
            CarPropertyValue actualEvent = propMgr.getProperty(
                    expectedEvent.getPropertyId(), expectedEvent.getAreaId());
            assertTrue(String.format(
                    "Mismatched car information data, actual: %s, expected: %s",
                    actualEvent, expectedEvent),
                    Utils.areCarPropertyValuesEqual(actualEvent, expectedEvent));
        }
    }

    /**
     * This test will test set/get on MIX type properties. It needs a vendor property in Google
     * Vehicle HAL. See kMixedTypePropertyForTest in google defaultConfig.h for details.
     * @throws Exception
     */
    @Test
    public void testMixedTypeProperty() throws Exception {
        CarPropertyManager propertyManager =
                (CarPropertyManager) mCar.getCarManager(Car.PROPERTY_SERVICE);
        ArraySet<Integer> propConfigSet = new ArraySet<>();
        propConfigSet.add(MIXED_TYPE_PROPERTY);

        List<CarPropertyConfig> configs = propertyManager.getPropertyList(propConfigSet);

        // use google HAL in the test
        assertNotEquals("Can not find MIXED type properties in HAL",
                0, configs.size());

        // test CarPropertyConfig
        CarPropertyConfig<?> cfg = configs.get(0);
        List<Integer> configArrayExpected = Arrays.asList(1, 1, 0, 2, 0, 0, 1, 0, 0);
        assertArrayEquals(configArrayExpected.toArray(), cfg.getConfigArray().toArray());

        // test SET/GET methods
        CarPropertyValue<Object[]> propertyValue = propertyManager.getProperty(Object[].class,
                MIXED_TYPE_PROPERTY, 0);
        assertArrayEquals(DEFAULT_VALUE, propertyValue.getValue());

        Object[] expectedValue = {"MIXED property", false, 5, 4, 3.2f};
        propertyManager.setProperty(Object[].class, MIXED_TYPE_PROPERTY, 0, expectedValue);
        // Wait for VHAL
        Thread.sleep(2000);
        CarPropertyValue<Object[]> result = propertyManager.getProperty(Object[].class,
                MIXED_TYPE_PROPERTY, 0);
        assertArrayEquals(expectedValue, result.getValue());
    }

    /**
     * This test will test the case: vehicle events comes to android out of order.
     * See the events in car_property_test.json.
     * @throws Exception
     */
    @Test
    public void testPropertyEventOutOfOrder() throws Exception {
        CarPropertyManager propMgr = (CarPropertyManager) mCar.getCarManager(Car.PROPERTY_SERVICE);
        assertNotNull("CarPropertyManager is null", propMgr);

        File sharedJson = makeShareable(CAR_PROPERTY_TEST_JSON);
        Log.d(TAG, "send command to VHAL to generate events");
        JsonVhalEventGenerator propertyGenerator =
                new JsonVhalEventGenerator(mVehicle).setJsonFile(sharedJson);

        GearEventTestCallback cb = new GearEventTestCallback();
        propMgr.registerCallback(cb, GEAR_PROPERTY_ID, CarPropertyManager.SENSOR_RATE_ONCHANGE);
        Thread.sleep(2000);
        propertyGenerator.start();
        Thread.sleep(2000);
        propertyGenerator.stop();
        // check VHAL ignored the last event in car_property_test, because it is out of order.
        int currentGear = propMgr.getIntProperty(GEAR_PROPERTY_ID, 0);
        assertEquals(16, currentGear);
    }

    private class GearEventTestCallback implements CarPropertyEventCallback {
        private long mTimestamp = 0L;

        @Override
        public void onChangeEvent(CarPropertyValue carPropertyValue) {
            if (carPropertyValue.getPropertyId() != GEAR_PROPERTY_ID) {
                return;
            }
            if (carPropertyValue.getStatus() == CarPropertyValue.STATUS_AVAILABLE) {
                Assert.assertTrue("Received events out of oder",
                        mTimestamp <= carPropertyValue.getTimestamp());
                mTimestamp = carPropertyValue.getTimestamp();
            }
        }

        @Override
        public void onErrorEvent(final int propertyId, final int zone) {
            Assert.fail("Error: propertyId: x0" + toHexString(propertyId) + " areaId: " + zone);
        }
    }
}
