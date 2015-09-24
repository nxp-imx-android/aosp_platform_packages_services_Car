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
package com.android.car;

/**
 * Container class to hold static definitions for system test api for Car.
 * Client should still use Car api for all operations, and this class is only for defining
 * additional parameters available when car api becomes system api.
 * @hide
 */
public class CarSystemTest {
    /**
     * Service for testing. This is system app only feature.
     * Service name for {@link CarTestManager}, to be used in {@link #getCarManager(String)}.
     * @hide
     */
    public static final String TEST_SERVICE = "car-service-test";

    /** permission necessary to mock vehicle hal for testing */
    public static final String PERMISSION_MOCK_VEHICLE_HAL =
            "android.support.car.permission.CAR_MOCK_VEHICLE_HAL";
}
