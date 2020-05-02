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

package android.car.apitest;

import android.car.VehicleAreaType;
import android.car.hardware.CarPropertyValue;
import android.test.suitebuilder.annotation.MediumTest;

import org.junit.Test;

/**
 * Unit tests for {@link CarPropertyValue}
 */
@MediumTest
public class CarPropertyValueTest extends CarPropertyTestBase {

    @Test
    public void testSimpleFloatValue() {
        CarPropertyValue<Float> floatValue =
                new CarPropertyValue<>(FLOAT_PROPERTY_ID, WINDOW_DRIVER, 10f);

        writeToParcel(floatValue);

        CarPropertyValue<Float> valueRead = readFromParcel();
        assertEquals(10f, valueRead.getValue());
    }

    @Test
    public void testMixedValue() {
        Object[] values = {"android", 1, 2.0};
        CarPropertyValue<Object> mixedValue =
                new CarPropertyValue<>(MIXED_TYPE_PROPERTY_ID,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        values);
        writeToParcel(mixedValue);
        CarPropertyValue<Object[]> valueRead = readFromParcel();
        assertArrayEquals(values, valueRead.getValue());
        assertEquals(MIXED_TYPE_PROPERTY_ID, valueRead.getPropertyId());
        assertEquals(VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL, valueRead.getAreaId());
    }
}
