
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

//Autogenerated from vehicle.h. Do not modify manually.

package com.android.car.vehiclenetwork;

public class VehicleNetworkConsts {

public static final int VEHICLE_PROPERTY_INFO_VIN = 0x00000100;
public static final int VEHICLE_PROPERTY_INFO_MAKE = 0x00000101;
public static final int VEHICLE_PROPERTY_INFO_MODEL = 0x00000102;
public static final int VEHICLE_PROPERTY_INFO_MODEL_YEAR = 0x00000103;
public static final int VEHICLE_PROPERTY_INFO_FUEL_CAPACITY = 0x00000104;
public static final int VEHICLE_PROPERTY_PERF_ODOMETER = 0x00000204;
public static final int VEHICLE_PROPERTY_PERF_VEHICLE_SPEED = 0x00000207;
public static final int VEHICLE_PROPERTY_ENGINE_COOLANT_TEMP = 0x00000301;
public static final int VEHICLE_PROPERTY_ENGINE_OIL_TEMP = 0x00000304;
public static final int VEHICLE_PROPERTY_ENGINE_RPM = 0x00000305;
public static final int VEHICLE_PROPERTY_GEAR_SELECTION = 0x00000400;
public static final int VEHICLE_PROPERTY_CURRENT_GEAR = 0x00000401;
public static final int VEHICLE_PROPERTY_PARKING_BRAKE_ON = 0x00000402;
public static final int VEHICLE_PROPERTY_DRIVING_STATUS = 0x00000404;
public static final int VEHICLE_PROPERTY_FUEL_LEVEL_LOW = 0x00000405;
public static final int VEHICLE_PROPERTY_NIGHT_MODE = 0x00000407;
public static final int VEHICLE_PROPERTY_HVAC_FAN_SPEED = 0x00000500;
public static final int VEHICLE_PROPERTY_HVAC_FAN_DIRECTION = 0x00000501;
public static final int VEHICLE_PROPERTY_HVAC_TEMPERATURE_CURRENT = 0x00000502;
public static final int VEHICLE_PROPERTY_HVAC_TEMPERATURE_SET = 0x00000503;
public static final int VEHICLE_PROPERTY_HVAC_DEFROSTER = 0x00000504;
public static final int VEHICLE_PROPERTY_HVAC_AC_ON = 0x00000505;
public static final int VEHICLE_PROPERTY_ENV_OUTSIDE_TEMP = 0x00000703;
public static final int VEHICLE_PROPERTY_CUSTOM_START = 0xf0000000;
public static final int VEHICLE_PROPERTY_CUSTOM_END = 0xf7ffffff;
public static int getVehicleValueType(int property) {
switch (property) {
case VEHICLE_PROPERTY_INFO_VIN: return VehicleValueType.VEHICLE_VALUE_TYPE_STRING;
case VEHICLE_PROPERTY_INFO_MAKE: return VehicleValueType.VEHICLE_VALUE_TYPE_STRING;
case VEHICLE_PROPERTY_INFO_MODEL: return VehicleValueType.VEHICLE_VALUE_TYPE_STRING;
case VEHICLE_PROPERTY_INFO_MODEL_YEAR: return VehicleValueType.VEHICLE_VALUE_TYPE_INT32;
case VEHICLE_PROPERTY_INFO_FUEL_CAPACITY: return VehicleValueType.VEHICLE_VALUE_TYPE_FLOAT;
case VEHICLE_PROPERTY_PERF_ODOMETER: return VehicleValueType.VEHICLE_VALUE_TYPE_FLOAT;
case VEHICLE_PROPERTY_PERF_VEHICLE_SPEED: return VehicleValueType.VEHICLE_VALUE_TYPE_FLOAT;
case VEHICLE_PROPERTY_ENGINE_COOLANT_TEMP: return VehicleValueType.VEHICLE_VALUE_TYPE_FLOAT;
case VEHICLE_PROPERTY_ENGINE_OIL_TEMP: return VehicleValueType.VEHICLE_VALUE_TYPE_FLOAT;
case VEHICLE_PROPERTY_ENGINE_RPM: return VehicleValueType.VEHICLE_VALUE_TYPE_FLOAT;
case VEHICLE_PROPERTY_GEAR_SELECTION: return VehicleValueType.VEHICLE_VALUE_TYPE_INT32;
case VEHICLE_PROPERTY_CURRENT_GEAR: return VehicleValueType.VEHICLE_VALUE_TYPE_INT32;
case VEHICLE_PROPERTY_PARKING_BRAKE_ON: return VehicleValueType.VEHICLE_VALUE_TYPE_BOOLEAN;
case VEHICLE_PROPERTY_DRIVING_STATUS: return VehicleValueType.VEHICLE_VALUE_TYPE_INT32;
case VEHICLE_PROPERTY_FUEL_LEVEL_LOW: return VehicleValueType.VEHICLE_VALUE_TYPE_BOOLEAN;
case VEHICLE_PROPERTY_NIGHT_MODE: return VehicleValueType.VEHICLE_VALUE_TYPE_BOOLEAN;
case VEHICLE_PROPERTY_HVAC_FAN_SPEED: return VehicleValueType.VEHICLE_VALUE_TYPE_ZONED_INT32;
case VEHICLE_PROPERTY_HVAC_FAN_DIRECTION: return VehicleValueType.VEHICLE_VALUE_TYPE_ZONED_INT32;
case VEHICLE_PROPERTY_HVAC_TEMPERATURE_CURRENT: return VehicleValueType.VEHICLE_VALUE_TYPE_ZONED_FLOAT;
case VEHICLE_PROPERTY_HVAC_TEMPERATURE_SET: return VehicleValueType.VEHICLE_VALUE_TYPE_ZONED_FLOAT;
case VEHICLE_PROPERTY_HVAC_DEFROSTER: return VehicleValueType.VEHICLE_VALUE_TYPE_ZONED_BOOLEAN;
case VEHICLE_PROPERTY_HVAC_AC_ON: return VehicleValueType.VEHICLE_VALUE_TYPE_ZONED_BOOLEAN;
case VEHICLE_PROPERTY_ENV_OUTSIDE_TEMP: return VehicleValueType.VEHICLE_VALUE_TYPE_FLOAT;
default: return VehicleValueType.VEHICLE_VALUE_TYPE_SHOUD_NOT_USE;
}
}

public static String getVehiclePropertyName(int property) {
switch (property) {
case VEHICLE_PROPERTY_INFO_VIN: return "VEHICLE_PROPERTY_INFO_VIN";
case VEHICLE_PROPERTY_INFO_MAKE: return "VEHICLE_PROPERTY_INFO_MAKE";
case VEHICLE_PROPERTY_INFO_MODEL: return "VEHICLE_PROPERTY_INFO_MODEL";
case VEHICLE_PROPERTY_INFO_MODEL_YEAR: return "VEHICLE_PROPERTY_INFO_MODEL_YEAR";
case VEHICLE_PROPERTY_INFO_FUEL_CAPACITY: return "VEHICLE_PROPERTY_INFO_FUEL_CAPACITY";
case VEHICLE_PROPERTY_PERF_ODOMETER: return "VEHICLE_PROPERTY_PERF_ODOMETER";
case VEHICLE_PROPERTY_PERF_VEHICLE_SPEED: return "VEHICLE_PROPERTY_PERF_VEHICLE_SPEED";
case VEHICLE_PROPERTY_ENGINE_COOLANT_TEMP: return "VEHICLE_PROPERTY_ENGINE_COOLANT_TEMP";
case VEHICLE_PROPERTY_ENGINE_OIL_TEMP: return "VEHICLE_PROPERTY_ENGINE_OIL_TEMP";
case VEHICLE_PROPERTY_ENGINE_RPM: return "VEHICLE_PROPERTY_ENGINE_RPM";
case VEHICLE_PROPERTY_GEAR_SELECTION: return "VEHICLE_PROPERTY_GEAR_SELECTION";
case VEHICLE_PROPERTY_CURRENT_GEAR: return "VEHICLE_PROPERTY_CURRENT_GEAR";
case VEHICLE_PROPERTY_PARKING_BRAKE_ON: return "VEHICLE_PROPERTY_PARKING_BRAKE_ON";
case VEHICLE_PROPERTY_DRIVING_STATUS: return "VEHICLE_PROPERTY_DRIVING_STATUS";
case VEHICLE_PROPERTY_FUEL_LEVEL_LOW: return "VEHICLE_PROPERTY_FUEL_LEVEL_LOW";
case VEHICLE_PROPERTY_NIGHT_MODE: return "VEHICLE_PROPERTY_NIGHT_MODE";
case VEHICLE_PROPERTY_HVAC_FAN_SPEED: return "VEHICLE_PROPERTY_HVAC_FAN_SPEED";
case VEHICLE_PROPERTY_HVAC_FAN_DIRECTION: return "VEHICLE_PROPERTY_HVAC_FAN_DIRECTION";
case VEHICLE_PROPERTY_HVAC_TEMPERATURE_CURRENT: return "VEHICLE_PROPERTY_HVAC_TEMPERATURE_CURRENT";
case VEHICLE_PROPERTY_HVAC_TEMPERATURE_SET: return "VEHICLE_PROPERTY_HVAC_TEMPERATURE_SET";
case VEHICLE_PROPERTY_HVAC_DEFROSTER: return "VEHICLE_PROPERTY_HVAC_DEFROSTER";
case VEHICLE_PROPERTY_HVAC_AC_ON: return "VEHICLE_PROPERTY_HVAC_AC_ON";
case VEHICLE_PROPERTY_ENV_OUTSIDE_TEMP: return "VEHICLE_PROPERTY_ENV_OUTSIDE_TEMP";
case VEHICLE_PROPERTY_CUSTOM_START: return "VEHICLE_PROPERTY_CUSTOM_START";
case VEHICLE_PROPERTY_CUSTOM_END: return "VEHICLE_PROPERTY_CUSTOM_END";
default: return "UNKNOWN_PROPERTY";
}
}

public static class VehicleValueType {
public static final int VEHICLE_VALUE_TYPE_SHOUD_NOT_USE = 0x00;
public static final int VEHICLE_VALUE_TYPE_STRING = 0x01;
public static final int VEHICLE_VALUE_TYPE_FLOAT = 0x02;
public static final int VEHICLE_VALUE_TYPE_INT64 = 0x03;
public static final int VEHICLE_VALUE_TYPE_INT32 = 0x04;
public static final int VEHICLE_VALUE_TYPE_BOOLEAN = 0x05;
public static final int VEHICLE_VALUE_TYPE_BYTES = 0x06;
public static final int VEHICLE_VALUE_TYPE_ZONED_INT32 = 0x07;
public static final int VEHICLE_VALUE_TYPE_ZONED_FLOAT = 0x08;
public static final int VEHICLE_VALUE_TYPE_ZONED_BOOLEAN = 0x09;
}
public static class VehicleUnitType {
public static final int VEHICLE_UNIT_TYPE_SHOULD_NOT_USE = 0x00000000;
public static final int VEHICLE_UNIT_TYPE_METER_PER_SEC = 0x00000001;
public static final int VEHICLE_UNIT_TYPE_RPM = 0x00000002;
public static final int VEHICLE_UNIT_TYPE_HZ = 0x00000003;
public static final int VEHICLE_UNIT_TYPE_PERCENTILE = 0x00000010;
public static final int VEHICLE_UNIT_TYPE_MILLIMETER = 0x00000020;
public static final int VEHICLE_UNIT_TYPE_METER = 0x00000021;
public static final int VEHICLE_UNIT_TYPE_KILOMETER = 0x00000023;
public static final int VEHICLE_UNIT_TYPE_CELCIUS = 0x00000030;
public static final int VEHICLE_UNIT_TYPE_MILLILITER = 0x00000040;
public static final int VEHICLE_UNIT_TYPE_NANO_SECS = 0x00000050;
public static final int VEHICLE_UNOT_TYPE_SECS = 0x00000053;
public static final int VEHICLE_UNIT_TYPE_YEAR = 0x00000059;
}
public static class VehicleErrorCode {
public static final int VEHICLE_NO_ERROR = 0x0;
public static final int VEHICLE_ERROR_UNKNOWN = 0x01;
}
public static class VehiclePropChangeMode {
public static final int VEHICLE_PROP_CHANGE_MODE_STATIC = 0x00;
public static final int VEHICLE_PROP_CHANGE_MODE_ON_CHANGE = 0x01;
public static final int VEHICLE_PROP_CHANGE_MODE_CONTINUOUS = 0x02;
}
public static class VehiclePropAccess {
public static final int VEHICLE_PROP_ACCESS_READ = 0x01;
public static final int VEHICLE_PROP_ACCESS_WRITE = 0x02;
public static final int VEHICLE_PROP_ACCESS_READ_WRITE = 0x03;
}
public static class VehiclePermissionModel {
public static final int VEHICLE_PERMISSION_NO_RESTRICTION = 0;
public static final int VEHICLE_PERMISSION_OEM_ONLY = 0x1;
public static final int VEHICLE_PERMISSION_SYSTEM_APP_ONLY = 0x2;
public static final int VEHICLE_PERMISSION_OEM_OR_SYSTEM_APP = 0x3;
}
public static class VehicleDrivingStatus {
public static final int VEHICLE_DRIVING_STATUS_UNRESTRICTED = 0x00;
public static final int VEHICLE_DRIVING_STATUS_NO_VIDEO = 0x01;
public static final int VEHICLE_DRIVING_STATUS_NO_KEYBOARD_INPUT = 0x02;
public static final int VEHICLE_DRIVING_STATUS_NO_VOICE_INPUT = 0x04;
public static final int VEHICLE_DRIVING_STATUS_NO_CONFIG = 0x08;
public static final int VEHICLE_DRIVING_STATUS_LIMIT_MESSAGE_LEN = 0x10;
}
public static class VehicleGear {
public static final int VEHICLE_GEAR_NEUTRAL = 0x0001;
public static final int VEHICLE_GEAR_REVERSE = 0x0002;
public static final int VEHICLE_GEAR_PARKING = 0x0004;
public static final int VEHICLE_GEAR_DRIVE = 0x0008;
public static final int VEHICLE_GEAR_L = 0x0010;
public static final int VEHICLE_GEAR_1 = 0x0010;
public static final int VEHICLE_GEAR_2 = 0x0020;
public static final int VEHICLE_GEAR_3 = 0x0040;
public static final int VEHICLE_GEAR_4 = 0x0080;
public static final int VEHICLE_GEAR_5 = 0x0100;
public static final int VEHICLE_GEAR_6 = 0x0200;
public static final int VEHICLE_GEAR_7 = 0x0400;
public static final int VEHICLE_GEAR_8 = 0x0800;
public static final int VEHICLE_GEAR_9 = 0x1000;
}
public static class VehicleZone {
public static final int VEHICLE_ZONE_ROW_1_LEFT = 0x00000001;
public static final int VEHICLE_ZONE_ROW_1_CENTER = 0x00000002;
public static final int VEHICLE_ZONE_ROW_1_RIGHT = 0x00000004;
public static final int VEHICLE_ZONE_ROW_1_ALL = 0x00000008;
public static final int VEHICLE_ZONE_ROW_2_LEFT = 0x00000010;
public static final int VEHICLE_ZONE_ROW_2_CENTER = 0x00000020;
public static final int VEHICLE_ZONE_ROW_2_RIGHT = 0x00000040;
public static final int VEHICLE_ZONE_ROW_2_ALL = 0x00000080;
public static final int VEHICLE_ZONE_ROW_3_LEFT = 0x00000100;
public static final int VEHICLE_ZONE_ROW_3_CENTER = 0x00000200;
public static final int VEHICLE_ZONE_ROW_3_RIGHT = 0x00000400;
public static final int VEHICLE_ZONE_ROW_3_ALL = 0x00000800;
public static final int VEHICLE_ZONE_ROW_4_LEFT = 0x00001000;
public static final int VEHICLE_ZONE_ROW_4_CENTER = 0x00002000;
public static final int VEHICLE_ZONE_ROW_4_RIGHT = 0x00004000;
public static final int VEHICLE_ZONE_ROW_4_ALL = 0x00008000;
public static final int VEHICLE_ZONE_ALL = 0x80000000;
}
public static class VehicleSeat {
public static final int VEHICLE_SEAT_DRIVER_LHD = 0x0001;
public static final int VEHICLE_SEAT_DRIVER_RHD = 0x0002;
public static final int VEHICLE_SEAT_ROW_1_PASSENGER_1 = 0x0010;
public static final int VEHICLE_SEAT_ROW_1_PASSENGER_2 = 0x0020;
public static final int VEHICLE_SEAT_ROW_1_PASSENGER_3 = 0x0040;
public static final int VEHICLE_SEAT_ROW_2_PASSENGER_1 = 0x0100;
public static final int VEHICLE_SEAT_ROW_2_PASSENGER_2 = 0x0200;
public static final int VEHICLE_SEAT_ROW_2_PASSENGER_3 = 0x0400;
public static final int VEHICLE_SEAT_ROW_3_PASSENGER_1 = 0x1000;
public static final int VEHICLE_SEAT_ROW_3_PASSENGER_2 = 0x2000;
public static final int VEHICLE_SEAT_ROW_3_PASSENGER_3 = 0x4000;
}
public static class VehicleWindow {
public static final int VEHICLE_WINDOW_FRONT_WINDSHIELD = 0x0001;
public static final int VEHICLE_WINDOW_REAR_WINDSHIELD = 0x0002;
public static final int VEHICLE_WINDOW_ROOF_TOP = 0x0004;
public static final int VEHICLE_WINDOW_ROW_1_LEFT = 0x0010;
public static final int VEHICLE_WINDOW_ROW_1_RIGHT = 0x0020;
public static final int VEHICLE_WINDOW_ROW_2_LEFT = 0x0100;
public static final int VEHICLE_WINDOW_ROW_2_RIGHT = 0x0200;
public static final int VEHICLE_WINDOW_ROW_3_LEFT = 0x1000;
public static final int VEHICLE_WINDOW_ROW_3_RIGHT = 0x2000;
}
public static class VehicleTurnSignal {
public static final int VEHICLE_SIGNAL_NONE = 0x00;
public static final int VEHICLE_SIGNAL_RIGHT = 0x01;
public static final int VEHICLE_SIGNAL_LEFT = 0x02;
public static final int VEHICLE_SIGNAL_EMERGENCY = 0x04;
}
public static class VehicleBoolean {
public static final int VEHICLE_FALSE = 0x00;
public static final int VEHICLE_TRUE = 0x01;
}

}

