/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.power;

import android.frameworks.automotive.powerpolicy.PowerComponent;

/**
 * Utility class used when dealing with PowerComponent.
 */
final class PowerComponentUtil {
    static final int INVALID_POWER_COMPONENT = -1;
    static final int FIRST_POWER_COMPONENT = PowerComponent.AUDIO;
    static final int LAST_POWER_COMPONENT = PowerComponent.MICROPHONE;

    private static final String POWER_COMPONENT_PREFIX = "POWER_COMPONENT_";

    private static final String POWER_COMPONENT_AUDIO = "AUDIO";
    private static final String POWER_COMPONENT_MEDIA = "MEDIA";
    private static final String POWER_COMPONENT_DISPLAY_MAIN = "DISPLAY_MAIN";
    private static final String POWER_COMPONENT_DISPLAY_CLUSTER = "DISPLAY_CLUSTER";
    private static final String POWER_COMPONENT_DISPLAY_FRONT_PASSENGER = "DISPLAY_FRONT_PASSENGER";
    private static final String POWER_COMPONENT_DISPLAY_REAR_PASSENGER = "DISPLAY_REAR_PASSENGER";
    private static final String POWER_COMPONENT_BLUETOOTH = "BLUETOOTH";
    private static final String POWER_COMPONENT_WIFI = "WIFI";
    private static final String POWER_COMPONENT_CELLULAR = "CELLULAR";
    private static final String POWER_COMPONENT_ETHERNET = "ETHERNET";
    private static final String POWER_COMPONENT_PROJECTION = "PROJECTION";
    private static final String POWER_COMPONENT_NFC = "NFC";
    private static final String POWER_COMPONENT_INPUT = "INPUT";
    private static final String POWER_COMPONENT_VOICE_INTERACTION = "VOICE_INTERACTION";
    private static final String POWER_COMPONENT_VISUAL_INTERACTION = "VISUAL_INTERACTION";
    private static final String POWER_COMPONENT_TRUSTED_DEVICE_DETECTION =
            "TRUSTED_DEVICE_DETECTION";
    private static final String POWER_COMPONENT_LOCATION = "LOCATION";
    private static final String POWER_COMPONENT_MICROPHONE = "MICROPHONE";

    // PowerComponentUtil is intended to provide static variables and methods.
    private PowerComponentUtil() {}

    static boolean isValidPowerComponent(int component) {
        return component >= PowerComponent.AUDIO
                && component <= PowerComponent.TRUSTED_DEVICE_DETECTION;
    }

    static int toPowerComponent(String component, boolean prefix) {
        if (component == null) {
            return INVALID_POWER_COMPONENT;
        }
        if (prefix) {
            if (!component.startsWith(POWER_COMPONENT_PREFIX)) {
                return INVALID_POWER_COMPONENT;
            }
            component = component.substring(POWER_COMPONENT_PREFIX.length());
        }
        switch (component) {
            case POWER_COMPONENT_AUDIO:
                return PowerComponent.AUDIO;
            case POWER_COMPONENT_MEDIA:
                return PowerComponent.MEDIA;
            case POWER_COMPONENT_DISPLAY_MAIN:
                return PowerComponent.DISPLAY_MAIN;
            case POWER_COMPONENT_DISPLAY_CLUSTER:
                return PowerComponent.DISPLAY_CLUSTER;
            case POWER_COMPONENT_DISPLAY_FRONT_PASSENGER:
                return PowerComponent.DISPLAY_FRONT_PASSENGER;
            case POWER_COMPONENT_DISPLAY_REAR_PASSENGER:
                return PowerComponent.DISPLAY_REAR_PASSENGER;
            case POWER_COMPONENT_BLUETOOTH:
                return PowerComponent.BLUETOOTH;
            case POWER_COMPONENT_WIFI:
                return PowerComponent.WIFI;
            case POWER_COMPONENT_CELLULAR:
                return PowerComponent.CELLULAR;
            case POWER_COMPONENT_ETHERNET:
                return PowerComponent.ETHERNET;
            case POWER_COMPONENT_PROJECTION:
                return PowerComponent.PROJECTION;
            case POWER_COMPONENT_NFC:
                return PowerComponent.NFC;
            case POWER_COMPONENT_INPUT:
                return PowerComponent.INPUT;
            case POWER_COMPONENT_VOICE_INTERACTION:
                return PowerComponent.VOICE_INTERACTION;
            case POWER_COMPONENT_VISUAL_INTERACTION:
                return PowerComponent.VISUAL_INTERACTION;
            case POWER_COMPONENT_TRUSTED_DEVICE_DETECTION:
                return PowerComponent.TRUSTED_DEVICE_DETECTION;
            default:
                return INVALID_POWER_COMPONENT;
        }
    }

    static String powerComponentToString(int component) {
        switch (component) {
            case PowerComponent.AUDIO:
                return POWER_COMPONENT_AUDIO;
            case PowerComponent.MEDIA:
                return POWER_COMPONENT_MEDIA;
            case PowerComponent.DISPLAY_MAIN:
                return POWER_COMPONENT_DISPLAY_MAIN;
            case PowerComponent.DISPLAY_CLUSTER:
                return POWER_COMPONENT_DISPLAY_CLUSTER;
            case PowerComponent.DISPLAY_FRONT_PASSENGER:
                return POWER_COMPONENT_DISPLAY_FRONT_PASSENGER;
            case PowerComponent.DISPLAY_REAR_PASSENGER:
                return POWER_COMPONENT_DISPLAY_REAR_PASSENGER;
            case PowerComponent.BLUETOOTH:
                return POWER_COMPONENT_BLUETOOTH;
            case PowerComponent.WIFI:
                return POWER_COMPONENT_WIFI;
            case PowerComponent.CELLULAR:
                return POWER_COMPONENT_CELLULAR;
            case PowerComponent.ETHERNET:
                return POWER_COMPONENT_ETHERNET;
            case PowerComponent.PROJECTION:
                return POWER_COMPONENT_PROJECTION;
            case PowerComponent.NFC:
                return POWER_COMPONENT_NFC;
            case PowerComponent.INPUT:
                return POWER_COMPONENT_INPUT;
            case PowerComponent.VOICE_INTERACTION:
                return POWER_COMPONENT_VOICE_INTERACTION;
            case PowerComponent.VISUAL_INTERACTION:
                return POWER_COMPONENT_VISUAL_INTERACTION;
            case PowerComponent.TRUSTED_DEVICE_DETECTION:
                return POWER_COMPONENT_TRUSTED_DEVICE_DETECTION;
            default:
                return "unknown component";
        }
    }
}
