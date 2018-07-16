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
package android.car;

/**
 * Used by lights switch properties to enumerate user selected switch setting.
 * Use getProperty and setProperty in {@link android.car.hardware.property.CarPropertyManager} to
 * set and get this VHAL property.
 * @hide
 */
public final class VehicleLightSwitch {
    public static final int OFF = 0;
    public static final int ON = 1;
    public static final int DAYTIME_RUNNING = 2;
    public static final int AUTOMATIC = 0x100;

    private VehicleLightSwitch() {}
}
