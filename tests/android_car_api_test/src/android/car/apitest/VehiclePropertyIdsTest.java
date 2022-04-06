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
package android.car.apitest;

import static com.google.common.truth.Truth.assertThat;

import android.car.VehiclePropertyIds;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.SparseArray;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class VehiclePropertyIdsTest {
    private static final List<String> MISSING_VHAL_IDS = new ArrayList<>();

    private static final List<Integer> MISSING_VHAL_ID_VALUES = new ArrayList<>();

    private static final List<String> MISSING_VEHICLE_PROPERTY_IDS =
            new ArrayList<>(
                Arrays.asList(
                    "EXTERNAL_CAR_TIME",
                    "DISABLED_OPTIONAL_FEATURES",
                    "EVS_SERVICE_REQUEST",
                    "HW_CUSTOM_INPUT",
                    "HW_ROTARY_INPUT",
                    "SUPPORT_CUSTOMIZE_VENDOR_PERMISSION"));
    private static final List<Integer> MISSING_VEHICLE_PROPERTY_ID_VALUES =
            new ArrayList<>(
                Arrays.asList(
                    /*EXTERNAL_CAR_TIME=*/290457096,
                    /*DISABLED_OPTIONAL_FEATURES=*/286265094,
                    /*EVS_SERVICE_REQUEST=*/289476368,
                    /*HW_CUSTOM_INPUT=*/289475120,
                    /*HW_ROTARY_INPUT=*/289475104,
                    /*SUPPORT_CUSTOMIZE_VENDOR_PERMISSION=*/287313669));


    @Test
    public void testMatchingVehiclePropertyNamesInVehicleHal() {
        List<String> vehiclePropertyIdNames = getListOfConstantNames(VehiclePropertyIds.class);
        List<String> vehiclePropertyNames = getListOfConstantNames(VehicleProperty.class);
        assertThat(vehiclePropertyNames.size() + MISSING_VHAL_IDS.size()).isEqualTo(
                vehiclePropertyIdNames.size() + MISSING_VEHICLE_PROPERTY_IDS.size());
        for (String vehiclePropertyName: vehiclePropertyNames) {
            if (MISSING_VHAL_IDS.contains(vehiclePropertyName)
                    || MISSING_VEHICLE_PROPERTY_IDS.contains(vehiclePropertyName)) {
                continue;
            }
            if (vehiclePropertyName.equals("ANDROID_EPOCH_TIME")) {
                // This is renamed in AIDL VHAL.
                assertThat(vehiclePropertyIdNames).contains("EPOCH_TIME");
                continue;
            }
            assertThat(vehiclePropertyIdNames).contains(vehiclePropertyName);
        }
    }

    @Test
    public void testMatchingVehiclePropertyValuesInVehicleHal() {
        List<Integer> vehiclePropertyIds = getListOfConstantValues(VehiclePropertyIds.class);
        List<Integer> vehicleProperties = getListOfConstantValues(VehicleProperty.class);
        assertThat(vehicleProperties.size() + MISSING_VHAL_ID_VALUES.size()).isEqualTo(
                vehiclePropertyIds.size() + MISSING_VEHICLE_PROPERTY_ID_VALUES.size());
        for (int vehicleProperty: vehicleProperties) {
            if (MISSING_VHAL_ID_VALUES.contains(vehicleProperty)
                    || MISSING_VEHICLE_PROPERTY_ID_VALUES.contains(vehicleProperty)) {
                continue;
            }
            // TODO(b/151168399): VEHICLE_SPEED_DISPLAY_UNITS mismatch between java and hal.
            if (vehicleProperty == VehicleProperty.VEHICLE_SPEED_DISPLAY_UNITS) {
                continue;
            }
            assertThat(vehiclePropertyIds).contains(vehicleProperty);
        }
    }

    @Test
    public void testToString() {
        SparseArray<String> propsToString = new SparseArray<>();

        propsToString.put(3, "0x3");
        propsToString.put(VehiclePropertyIds.INVALID, "INVALID");
        propsToString.put(VehiclePropertyIds.INFO_VIN, "INFO_VIN");
        propsToString.put(VehiclePropertyIds.INFO_MAKE, "INFO_MAKE");
        propsToString.put(VehiclePropertyIds.INFO_MODEL, "INFO_MODEL");
        propsToString.put(VehiclePropertyIds.INFO_MODEL_YEAR, "INFO_MODEL_YEAR");
        propsToString.put(VehiclePropertyIds.INFO_FUEL_CAPACITY, "INFO_FUEL_CAPACITY");
        propsToString.put(VehiclePropertyIds.INFO_FUEL_TYPE, "INFO_FUEL_TYPE");
        propsToString.put(VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY, "INFO_EV_BATTERY_CAPACITY");
        propsToString.put(VehiclePropertyIds.INFO_MULTI_EV_PORT_LOCATIONS,
                "INFO_MULTI_EV_PORT_LOCATIONS");
        propsToString.put(VehiclePropertyIds.INFO_EV_CONNECTOR_TYPE, "INFO_EV_CONNECTOR_TYPE");
        propsToString.put(VehiclePropertyIds.INFO_FUEL_DOOR_LOCATION, "INFO_FUEL_DOOR_LOCATION");
        propsToString.put(VehiclePropertyIds.INFO_EV_PORT_LOCATION, "INFO_EV_PORT_LOCATION");
        propsToString.put(VehiclePropertyIds.INFO_DRIVER_SEAT, "INFO_DRIVER_SEAT");
        propsToString.put(VehiclePropertyIds.INFO_EXTERIOR_DIMENSIONS, "INFO_EXTERIOR_DIMENSIONS");
        propsToString.put(VehiclePropertyIds.PERF_ODOMETER, "PERF_ODOMETER");
        propsToString.put(VehiclePropertyIds.PERF_VEHICLE_SPEED, "PERF_VEHICLE_SPEED");
        propsToString.put(VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY,
                "PERF_VEHICLE_SPEED_DISPLAY");
        propsToString.put(VehiclePropertyIds.PERF_STEERING_ANGLE, "PERF_STEERING_ANGLE");
        propsToString.put(VehiclePropertyIds.PERF_REAR_STEERING_ANGLE, "PERF_REAR_STEERING_ANGLE");
        propsToString.put(VehiclePropertyIds.ENGINE_COOLANT_TEMP, "ENGINE_COOLANT_TEMP");
        propsToString.put(VehiclePropertyIds.ENGINE_OIL_LEVEL, "ENGINE_OIL_LEVEL");
        propsToString.put(VehiclePropertyIds.ENGINE_OIL_TEMP, "ENGINE_OIL_TEMP");
        propsToString.put(VehiclePropertyIds.ENGINE_RPM, "ENGINE_RPM");
        propsToString.put(VehiclePropertyIds.WHEEL_TICK, "WHEEL_TICK");
        propsToString.put(VehiclePropertyIds.FUEL_LEVEL, "FUEL_LEVEL");
        propsToString.put(VehiclePropertyIds.FUEL_DOOR_OPEN, "FUEL_DOOR_OPEN");
        propsToString.put(VehiclePropertyIds.EV_BATTERY_LEVEL, "EV_BATTERY_LEVEL");
        propsToString.put(VehiclePropertyIds.EV_CHARGE_PORT_OPEN, "EV_CHARGE_PORT_OPEN");
        propsToString.put(VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED, "EV_CHARGE_PORT_CONNECTED");
        propsToString.put(VehiclePropertyIds.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE,
                "EV_BATTERY_INSTANTANEOUS_CHARGE_RATE");
        propsToString.put(VehiclePropertyIds.RANGE_REMAINING, "RANGE_REMAINING");
        propsToString.put(VehiclePropertyIds.TIRE_PRESSURE, "TIRE_PRESSURE");
        propsToString.put(VehiclePropertyIds.CRITICALLY_LOW_TIRE_PRESSURE,
                "CRITICALLY_LOW_TIRE_PRESSURE");
        propsToString.put(VehiclePropertyIds.GEAR_SELECTION, "GEAR_SELECTION");
        propsToString.put(VehiclePropertyIds.CURRENT_GEAR, "CURRENT_GEAR");
        propsToString.put(VehiclePropertyIds.PARKING_BRAKE_ON, "PARKING_BRAKE_ON");
        propsToString.put(VehiclePropertyIds.PARKING_BRAKE_AUTO_APPLY, "PARKING_BRAKE_AUTO_APPLY");
        propsToString.put(VehiclePropertyIds.FUEL_LEVEL_LOW, "FUEL_LEVEL_LOW");
        propsToString.put(VehiclePropertyIds.NIGHT_MODE, "NIGHT_MODE");
        propsToString.put(VehiclePropertyIds.TURN_SIGNAL_STATE, "TURN_SIGNAL_STATE");
        propsToString.put(VehiclePropertyIds.IGNITION_STATE, "IGNITION_STATE");
        propsToString.put(VehiclePropertyIds.ABS_ACTIVE, "ABS_ACTIVE");
        propsToString.put(VehiclePropertyIds.TRACTION_CONTROL_ACTIVE, "TRACTION_CONTROL_ACTIVE");
        propsToString.put(VehiclePropertyIds.HVAC_FAN_SPEED, "HVAC_FAN_SPEED");
        propsToString.put(VehiclePropertyIds.HVAC_FAN_DIRECTION, "HVAC_FAN_DIRECTION");
        propsToString.put(VehiclePropertyIds.HVAC_TEMPERATURE_CURRENT, "HVAC_TEMPERATURE_CURRENT");
        propsToString.put(VehiclePropertyIds.HVAC_TEMPERATURE_SET, "HVAC_TEMPERATURE_SET");
        propsToString.put(VehiclePropertyIds.HVAC_TEMPERATURE_VALUE_SUGGESTION,
                "HVAC_TEMPERATURE_VALUE_SUGGESTION");
        propsToString.put(VehiclePropertyIds.HVAC_DEFROSTER, "HVAC_DEFROSTER");
        propsToString.put(VehiclePropertyIds.HVAC_AC_ON, "HVAC_AC_ON");
        propsToString.put(VehiclePropertyIds.HVAC_MAX_AC_ON, "HVAC_MAX_AC_ON");
        propsToString.put(VehiclePropertyIds.HVAC_MAX_DEFROST_ON, "HVAC_MAX_DEFROST_ON");
        propsToString.put(VehiclePropertyIds.HVAC_RECIRC_ON, "HVAC_RECIRC_ON");
        propsToString.put(VehiclePropertyIds.HVAC_DUAL_ON, "HVAC_DUAL_ON");
        propsToString.put(VehiclePropertyIds.HVAC_AUTO_ON, "HVAC_AUTO_ON");
        propsToString.put(VehiclePropertyIds.HVAC_SEAT_TEMPERATURE, "HVAC_SEAT_TEMPERATURE");
        propsToString.put(VehiclePropertyIds.HVAC_SIDE_MIRROR_HEAT, "HVAC_SIDE_MIRROR_HEAT");
        propsToString.put(VehiclePropertyIds.HVAC_STEERING_WHEEL_HEAT, "HVAC_STEERING_WHEEL_HEAT");
        propsToString.put(VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS,
                "HVAC_TEMPERATURE_DISPLAY_UNITS");
        propsToString.put(VehiclePropertyIds.HVAC_ACTUAL_FAN_SPEED_RPM,
                "HVAC_ACTUAL_FAN_SPEED_RPM");
        propsToString.put(VehiclePropertyIds.HVAC_POWER_ON, "HVAC_POWER_ON");
        propsToString.put(VehiclePropertyIds.HVAC_FAN_DIRECTION_AVAILABLE,
                "HVAC_FAN_DIRECTION_AVAILABLE");
        propsToString.put(VehiclePropertyIds.HVAC_AUTO_RECIRC_ON, "HVAC_AUTO_RECIRC_ON");
        propsToString.put(VehiclePropertyIds.HVAC_SEAT_VENTILATION, "HVAC_SEAT_VENTILATION");
        propsToString.put(VehiclePropertyIds.HVAC_ELECTRIC_DEFROSTER_ON,
                "HVAC_ELECTRIC_DEFROSTER_ON");
        propsToString.put(VehiclePropertyIds.DISTANCE_DISPLAY_UNITS, "DISTANCE_DISPLAY_UNITS");
        propsToString.put(VehiclePropertyIds.FUEL_VOLUME_DISPLAY_UNITS,
                "FUEL_VOLUME_DISPLAY_UNITS");
        propsToString.put(VehiclePropertyIds.TIRE_PRESSURE_DISPLAY_UNITS,
                "TIRE_PRESSURE_DISPLAY_UNITS");
        propsToString.put(VehiclePropertyIds.EV_BATTERY_DISPLAY_UNITS, "EV_BATTERY_DISPLAY_UNITS");
        propsToString.put(VehiclePropertyIds.FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME,
                "FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME");
        propsToString.put(VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE, "ENV_OUTSIDE_TEMPERATURE");
        propsToString.put(VehiclePropertyIds.AP_POWER_STATE_REQ, "AP_POWER_STATE_REQ");
        propsToString.put(VehiclePropertyIds.AP_POWER_STATE_REPORT, "AP_POWER_STATE_REPORT");
        propsToString.put(VehiclePropertyIds.AP_POWER_BOOTUP_REASON, "AP_POWER_BOOTUP_REASON");
        propsToString.put(VehiclePropertyIds.DISPLAY_BRIGHTNESS, "DISPLAY_BRIGHTNESS");
        propsToString.put(VehiclePropertyIds.HW_KEY_INPUT, "HW_KEY_INPUT");
        propsToString.put(VehiclePropertyIds.DOOR_POS, "DOOR_POS");
        propsToString.put(VehiclePropertyIds.DOOR_MOVE, "DOOR_MOVE");
        propsToString.put(VehiclePropertyIds.DOOR_LOCK, "DOOR_LOCK");
        propsToString.put(VehiclePropertyIds.MIRROR_Z_POS, "MIRROR_Z_POS");
        propsToString.put(VehiclePropertyIds.MIRROR_Z_MOVE, "MIRROR_Z_MOVE");
        propsToString.put(VehiclePropertyIds.MIRROR_Y_POS, "MIRROR_Y_POS");
        propsToString.put(VehiclePropertyIds.MIRROR_Y_MOVE, "MIRROR_Y_MOVE");
        propsToString.put(VehiclePropertyIds.MIRROR_LOCK, "MIRROR_LOCK");
        propsToString.put(VehiclePropertyIds.MIRROR_FOLD, "MIRROR_FOLD");
        propsToString.put(VehiclePropertyIds.SEAT_MEMORY_SELECT, "SEAT_MEMORY_SELECT");
        propsToString.put(VehiclePropertyIds.SEAT_MEMORY_SET, "SEAT_MEMORY_SET");
        propsToString.put(VehiclePropertyIds.SEAT_BELT_BUCKLED, "SEAT_BELT_BUCKLED");
        propsToString.put(VehiclePropertyIds.SEAT_BELT_HEIGHT_POS, "SEAT_BELT_HEIGHT_POS");
        propsToString.put(VehiclePropertyIds.SEAT_BELT_HEIGHT_MOVE, "SEAT_BELT_HEIGHT_MOVE");
        propsToString.put(VehiclePropertyIds.SEAT_FORE_AFT_POS, "SEAT_FORE_AFT_POS");
        propsToString.put(VehiclePropertyIds.SEAT_FORE_AFT_MOVE, "SEAT_FORE_AFT_MOVE");
        propsToString.put(VehiclePropertyIds.SEAT_BACKREST_ANGLE_1_POS,
                "SEAT_BACKREST_ANGLE_1_POS");
        propsToString.put(VehiclePropertyIds.SEAT_BACKREST_ANGLE_1_MOVE,
                "SEAT_BACKREST_ANGLE_1_MOVE");
        propsToString.put(VehiclePropertyIds.SEAT_BACKREST_ANGLE_2_POS,
                "SEAT_BACKREST_ANGLE_2_POS");
        propsToString.put(VehiclePropertyIds.SEAT_BACKREST_ANGLE_2_MOVE,
                "SEAT_BACKREST_ANGLE_2_MOVE");
        propsToString.put(VehiclePropertyIds.SEAT_HEIGHT_POS, "SEAT_HEIGHT_POS");
        propsToString.put(VehiclePropertyIds.SEAT_HEIGHT_MOVE, "SEAT_HEIGHT_MOVE");
        propsToString.put(VehiclePropertyIds.SEAT_DEPTH_POS, "SEAT_DEPTH_POS");
        propsToString.put(VehiclePropertyIds.SEAT_DEPTH_MOVE, "SEAT_DEPTH_MOVE");
        propsToString.put(VehiclePropertyIds.SEAT_TILT_POS, "SEAT_TILT_POS");
        propsToString.put(VehiclePropertyIds.SEAT_TILT_MOVE, "SEAT_TILT_MOVE");
        propsToString.put(VehiclePropertyIds.SEAT_LUMBAR_FORE_AFT_POS, "SEAT_LUMBAR_FORE_AFT_POS");
        propsToString.put(VehiclePropertyIds.SEAT_LUMBAR_FORE_AFT_MOVE,
                "SEAT_LUMBAR_FORE_AFT_MOVE");
        propsToString.put(VehiclePropertyIds.SEAT_LUMBAR_SIDE_SUPPORT_POS,
                "SEAT_LUMBAR_SIDE_SUPPORT_POS");
        propsToString.put(VehiclePropertyIds.SEAT_LUMBAR_SIDE_SUPPORT_MOVE,
                "SEAT_LUMBAR_SIDE_SUPPORT_MOVE");
        propsToString.put(VehiclePropertyIds.SEAT_HEADREST_HEIGHT_POS, "SEAT_HEADREST_HEIGHT_POS");
        propsToString.put(VehiclePropertyIds.SEAT_HEADREST_HEIGHT_MOVE,
                "SEAT_HEADREST_HEIGHT_MOVE");
        propsToString.put(VehiclePropertyIds.SEAT_HEADREST_ANGLE_POS, "SEAT_HEADREST_ANGLE_POS");
        propsToString.put(VehiclePropertyIds.SEAT_HEADREST_ANGLE_MOVE, "SEAT_HEADREST_ANGLE_MOVE");
        propsToString.put(VehiclePropertyIds.SEAT_HEADREST_FORE_AFT_POS,
                "SEAT_HEADREST_FORE_AFT_POS");
        propsToString.put(VehiclePropertyIds.SEAT_HEADREST_FORE_AFT_MOVE,
                "SEAT_HEADREST_FORE_AFT_MOVE");
        propsToString.put(VehiclePropertyIds.SEAT_OCCUPANCY, "SEAT_OCCUPANCY");
        propsToString.put(VehiclePropertyIds.WINDOW_POS, "WINDOW_POS");
        propsToString.put(VehiclePropertyIds.WINDOW_MOVE, "WINDOW_MOVE");
        propsToString.put(VehiclePropertyIds.WINDOW_LOCK, "WINDOW_LOCK");
        propsToString.put(VehiclePropertyIds.VEHICLE_MAP_SERVICE, "VEHICLE_MAP_SERVICE");
        propsToString.put(VehiclePropertyIds.OBD2_LIVE_FRAME, "OBD2_LIVE_FRAME");
        propsToString.put(VehiclePropertyIds.OBD2_FREEZE_FRAME, "OBD2_FREEZE_FRAME");
        propsToString.put(VehiclePropertyIds.OBD2_FREEZE_FRAME_INFO, "OBD2_FREEZE_FRAME_INFO");
        propsToString.put(VehiclePropertyIds.OBD2_FREEZE_FRAME_CLEAR, "OBD2_FREEZE_FRAME_CLEAR");
        propsToString.put(VehiclePropertyIds.HEADLIGHTS_STATE, "HEADLIGHTS_STATE");
        propsToString.put(VehiclePropertyIds.HIGH_BEAM_LIGHTS_STATE, "HIGH_BEAM_LIGHTS_STATE");
        propsToString.put(VehiclePropertyIds.FOG_LIGHTS_STATE, "FOG_LIGHTS_STATE");
        propsToString.put(VehiclePropertyIds.HAZARD_LIGHTS_STATE, "HAZARD_LIGHTS_STATE");
        propsToString.put(VehiclePropertyIds.HEADLIGHTS_SWITCH, "HEADLIGHTS_SWITCH");
        propsToString.put(VehiclePropertyIds.HIGH_BEAM_LIGHTS_SWITCH, "HIGH_BEAM_LIGHTS_SWITCH");
        propsToString.put(VehiclePropertyIds.FOG_LIGHTS_SWITCH, "FOG_LIGHTS_SWITCH");
        propsToString.put(VehiclePropertyIds.HAZARD_LIGHTS_SWITCH, "HAZARD_LIGHTS_SWITCH");
        propsToString.put(VehiclePropertyIds.CABIN_LIGHTS_STATE, "CABIN_LIGHTS_STATE");
        propsToString.put(VehiclePropertyIds.CABIN_LIGHTS_SWITCH, "CABIN_LIGHTS_SWITCH");
        propsToString.put(VehiclePropertyIds.READING_LIGHTS_STATE, "READING_LIGHTS_STATE");
        propsToString.put(VehiclePropertyIds.READING_LIGHTS_SWITCH, "READING_LIGHTS_SWITCH");
        propsToString.put(VehiclePropertyIds.VEHICLE_SPEED_DISPLAY_UNITS,
                "VEHICLE_SPEED_DISPLAY_UNITS");
        propsToString.put(VehiclePropertyIds.INITIAL_USER_INFO, "INITIAL_USER_INFO");
        propsToString.put(VehiclePropertyIds.SWITCH_USER, "SWITCH_USER");
        propsToString.put(VehiclePropertyIds.CREATE_USER, "CREATE_USER");
        propsToString.put(VehiclePropertyIds.REMOVE_USER, "REMOVE_USER");
        propsToString.put(VehiclePropertyIds.USER_IDENTIFICATION_ASSOCIATION,
                "USER_IDENTIFICATION_ASSOCIATION");
        propsToString.put(VehiclePropertyIds.POWER_POLICY_REQ, "POWER_POLICY_REQ");
        propsToString.put(VehiclePropertyIds.POWER_POLICY_GROUP_REQ, "POWER_POLICY_GROUP_REQ");
        propsToString.put(VehiclePropertyIds.CURRENT_POWER_POLICY, "CURRENT_POWER_POLICY");
        propsToString.put(VehiclePropertyIds.WATCHDOG_ALIVE, "WATCHDOG_ALIVE");
        propsToString.put(VehiclePropertyIds.WATCHDOG_TERMINATED_PROCESS,
                "WATCHDOG_TERMINATED_PROCESS");
        propsToString.put(VehiclePropertyIds.VHAL_HEARTBEAT, "VHAL_HEARTBEAT");
        propsToString.put(VehiclePropertyIds.CLUSTER_SWITCH_UI, "CLUSTER_SWITCH_UI");
        propsToString.put(VehiclePropertyIds.CLUSTER_DISPLAY_STATE, "CLUSTER_DISPLAY_STATE");
        propsToString.put(VehiclePropertyIds.CLUSTER_REPORT_STATE, "CLUSTER_REPORT_STATE");
        propsToString.put(VehiclePropertyIds.CLUSTER_REQUEST_DISPLAY, "CLUSTER_REQUEST_DISPLAY");
        propsToString.put(VehiclePropertyIds.CLUSTER_NAVIGATION_STATE, "CLUSTER_NAVIGATION_STATE");
        propsToString.put(VehiclePropertyIds.EPOCH_TIME, "EPOCH_TIME");
        propsToString.put(VehiclePropertyIds.STORAGE_ENCRYPTION_BINDING_SEED,
                "STORAGE_ENCRYPTION_BINDING_SEED");
        propsToString.put(VehiclePropertyIds.ELECTRONIC_TOLL_COLLECTION_CARD_STATUS,
                "ELECTRONIC_TOLL_COLLECTION_CARD_STATUS");
        propsToString.put(VehiclePropertyIds.ELECTRONIC_TOLL_COLLECTION_CARD_TYPE,
                "ELECTRONIC_TOLL_COLLECTION_CARD_TYPE");
        propsToString.put(VehiclePropertyIds.FRONT_FOG_LIGHTS_STATE, "FRONT_FOG_LIGHTS_STATE");
        propsToString.put(VehiclePropertyIds.FRONT_FOG_LIGHTS_SWITCH, "FRONT_FOG_LIGHTS_SWITCH");
        propsToString.put(VehiclePropertyIds.REAR_FOG_LIGHTS_STATE, "REAR_FOG_LIGHTS_STATE");
        propsToString.put(VehiclePropertyIds.REAR_FOG_LIGHTS_SWITCH, "REAR_FOG_LIGHTS_SWITCH");
        propsToString.put(VehiclePropertyIds.EV_CHARGE_CURRENT_DRAW_LIMIT,
                "EV_CHARGE_CURRENT_DRAW_LIMIT");
        propsToString.put(VehiclePropertyIds.EV_CHARGE_PERCENT_LIMIT, "EV_CHARGE_PERCENT_LIMIT");
        propsToString.put(VehiclePropertyIds.EV_CHARGE_STATE, "EV_CHARGE_STATE");
        propsToString.put(VehiclePropertyIds.EV_CHARGE_SWITCH, "EV_CHARGE_SWITCH");
        propsToString.put(VehiclePropertyIds.EV_CHARGE_TIME_REMAINING, "EV_CHARGE_TIME_REMAINING");
        propsToString.put(VehiclePropertyIds.EV_REGENERATIVE_BRAKING_STATE,
                "EV_REGENERATIVE_BRAKING_STATE");
        propsToString.put(VehiclePropertyIds.VEHICLE_CURB_WEIGHT, "VEHICLE_CURB_WEIGHT");
        propsToString.put(VehiclePropertyIds.TRAILER_PRESENT, "TRAILER_PRESENT");

        for (int i = 0; i < propsToString.size(); i++) {
            assertThat(VehiclePropertyIds.toString(propsToString.keyAt(i))).isEqualTo(
                    propsToString.valueAt(i));
        }
    }

    private static List<Integer> getListOfConstantValues(Class clazz) {
        List<Integer> list = new ArrayList<Integer>();
        for (Field field : clazz.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
                try {
                    list.add(field.getInt(null));
                } catch (IllegalAccessException e) {
                }
            }
        }
        return list;
    }

    private static List<String> getListOfConstantNames(Class clazz) {
        List<String> list = new ArrayList<String>();
        for (Field field : clazz.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
                list.add(field.getName());
            }
        }
        return list;
    }
}
