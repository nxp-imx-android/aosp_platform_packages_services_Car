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
package android.car.userlib;

import static com.android.internal.util.Preconditions.checkArgument;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.car.userlib.HalCallback.HalCallbackStatus;
import android.content.pm.UserInfo;
import android.content.pm.UserInfo.UserInfoFlag;
import android.hardware.automotive.vehicle.V2_0.InitialUserInfoRequestType;
import android.hardware.automotive.vehicle.V2_0.InitialUserInfoResponse;
import android.hardware.automotive.vehicle.V2_0.InitialUserInfoResponseAction;
import android.hardware.automotive.vehicle.V2_0.UserFlags;
import android.hardware.automotive.vehicle.V2_0.UserIdentificationAssociation;
import android.hardware.automotive.vehicle.V2_0.UserIdentificationAssociationSetValue;
import android.hardware.automotive.vehicle.V2_0.UserIdentificationAssociationType;
import android.hardware.automotive.vehicle.V2_0.UserIdentificationAssociationValue;
import android.hardware.automotive.vehicle.V2_0.UserIdentificationGetRequest;
import android.hardware.automotive.vehicle.V2_0.UserIdentificationResponse;
import android.hardware.automotive.vehicle.V2_0.UserIdentificationSetAssociation;
import android.hardware.automotive.vehicle.V2_0.UserIdentificationSetRequest;
import android.hardware.automotive.vehicle.V2_0.UsersInfo;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.DebugUtils;
import android.util.Log;

import com.android.internal.util.Preconditions;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Provides utility methods for User HAL related functionalities.
 */
public final class UserHalHelper {

    private static final String TAG = UserHalHelper.class.getSimpleName();
    private static final boolean DEBUG = false;

    public static final int INITIAL_USER_INFO_PROPERTY = 299896583;
    public static final int USER_IDENTIFICATION_ASSOCIATION_PROPERTY = 299896587;

    private static final String STRING_SEPARATOR = "\\|\\|";

    /**
     * Gets user-friendly representation of the status.
     */
    public static String halCallbackStatusToString(@HalCallbackStatus int status) {
        switch (status) {
            case HalCallback.STATUS_OK:
                return "OK";
            case HalCallback.STATUS_HAL_SET_TIMEOUT:
                return "HAL_SET_TIMEOUT";
            case HalCallback.STATUS_HAL_RESPONSE_TIMEOUT:
                return "HAL_RESPONSE_TIMEOUT";
            case HalCallback.STATUS_WRONG_HAL_RESPONSE:
                return "WRONG_HAL_RESPONSE";
            case HalCallback.STATUS_CONCURRENT_OPERATION:
                return "CONCURRENT_OPERATION";
            default:
                return "UNKNOWN-" + status;
        }
    }

    /**
     * Converts a string to a {@link InitialUserInfoRequestType}.
     *
     * @return valid type or numeric value if passed "as is"
     *
     * @throws IllegalArgumentException if type is not valid neither a number
     */
    public static int parseInitialUserInfoRequestType(@NonNull String type) {
        switch(type) {
            case "FIRST_BOOT":
                return InitialUserInfoRequestType.FIRST_BOOT;
            case "FIRST_BOOT_AFTER_OTA":
                return InitialUserInfoRequestType.FIRST_BOOT_AFTER_OTA;
            case "COLD_BOOT":
                return InitialUserInfoRequestType.COLD_BOOT;
            case "RESUME":
                return InitialUserInfoRequestType.RESUME;
            default:
                try {
                    return Integer.parseInt(type);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("invalid type: " + type);
                }
        }
    }

    /**
     * Converts Android user flags to HALs.
     */
    public static int convertFlags(@NonNull UserInfo user) {
        checkArgument(user != null, "user cannot be null");
        checkArgument(user != null, "user cannot be null");

        int flags = UserFlags.NONE;
        if (user.id == UserHandle.USER_SYSTEM) {
            flags |= UserFlags.SYSTEM;
        }
        if (user.isAdmin()) {
            flags |= UserFlags.ADMIN;
        }
        if (user.isGuest()) {
            flags |= UserFlags.GUEST;
        }
        if (user.isEphemeral()) {
            flags |= UserFlags.EPHEMERAL;
        }

        return flags;
    }

    /**
     * Converts Android user flags to HALs.
     */
    public static int getFlags(@NonNull UserManager um, @UserIdInt int userId) {
        Preconditions.checkArgument(um != null, "UserManager cannot be null");
        UserInfo user = um.getUserInfo(userId);
        Preconditions.checkArgument(user != null, "No user with id %d", userId);
        return convertFlags(user);
    }

    /**
     * Checks if a HAL flag contains {@link UserFlags#SYSTEM}.
     */
    public static boolean isSystem(int flags) {
        return (flags & UserFlags.SYSTEM) != 0;
    }

    /**
     * Checks if a HAL flag contains {@link UserFlags#GUEST}.
     */
    public static boolean isGuest(int flags) {
        return (flags & UserFlags.GUEST) != 0;
    }

    /**
     * Checks if a HAL flag contains {@link UserFlags#EPHEMERAL}.
     */
    public static boolean isEphemeral(int flags) {
        return (flags & UserFlags.EPHEMERAL) != 0;
    }

    /**
     * Checks if a HAL flag contains {@link UserFlags#ADMIN}.
     */
    public static boolean isAdmin(int flags) {
        return (flags & UserFlags.ADMIN) != 0;
    }

    /**
     * Converts HAL flags to Android's.
     */
    @UserInfoFlag
    public static int toUserInfoFlags(int halFlags) {
        int flags = 0;
        if (isEphemeral(halFlags)) {
            flags |= UserInfo.FLAG_EPHEMERAL;
        }
        if (isAdmin(halFlags)) {
            flags |= UserInfo.FLAG_ADMIN;
        }
        return flags;
    }

    /**
     * Gets a user-friendly representation of the user flags.
     */
    @NonNull
    public static String userFlagsToString(int flags) {
        return DebugUtils.flagsToString(UserFlags.class, "", flags);
    }

    /**
     * Creates a {@link VehiclePropValue} with the given {@code prop}, {@code requestId},
     * and {@code requestType}.
     */
    @NonNull
    public static VehiclePropValue createPropRequest(int prop, int requestId, int requestType) {
        VehiclePropValue propRequest = createPropRequest(prop, requestId);
        propRequest.value.int32Values.add(requestType);

        return propRequest;
    }

    /**
     * Creates a {@link VehiclePropValue} with the given {@code prop} and {@code requestId}.
     */
    @NonNull
    public static VehiclePropValue createPropRequest(int prop, int requestId) {
        VehiclePropValue propRequest = new VehiclePropValue();
        propRequest.prop = prop;
        propRequest.timestamp = SystemClock.elapsedRealtime();
        propRequest.value.int32Values.add(requestId);

        return propRequest;
    }

    /**
     * Adds users information to prop value.
     */
    public static void addUsersInfo(@NonNull VehiclePropValue propRequest,
                @NonNull UsersInfo usersInfo) {
        Objects.requireNonNull(propRequest, "VehiclePropValue cannot be null");
        Objects.requireNonNull(usersInfo.currentUser, "Current user cannot be null");
        checkArgument(usersInfo.numberUsers == usersInfo.existingUsers.size(),
                "Number of existing users info does not match numberUsers");

        addUserInfo(propRequest, usersInfo.currentUser);
        propRequest.value.int32Values.add(usersInfo.numberUsers);
        for (int i = 0; i < usersInfo.numberUsers; i++) {
            android.hardware.automotive.vehicle.V2_0.UserInfo userInfo =
                    usersInfo.existingUsers.get(i);
            addUserInfo(propRequest, userInfo);
        }
    }

    /**
     * Adds user information to prop value.
     */
    public static void addUserInfo(@NonNull VehiclePropValue propRequest,
            @NonNull android.hardware.automotive.vehicle.V2_0.UserInfo userInfo) {
        Objects.requireNonNull(propRequest, "VehiclePropValue cannot be null");
        Objects.requireNonNull(userInfo, "UserInfo cannot be null");

        propRequest.value.int32Values.add(userInfo.userId);
        propRequest.value.int32Values.add(userInfo.flags);
    }

    /**
     * Checks if the given {@code value} is a valid {@link UserIdentificationAssociationType}.
     */
    public static boolean isValidUserIdentificationAssociationType(int type) {
        switch(type) {
            case UserIdentificationAssociationType.KEY_FOB:
            case UserIdentificationAssociationType.CUSTOM_1:
            case UserIdentificationAssociationType.CUSTOM_2:
            case UserIdentificationAssociationType.CUSTOM_3:
            case UserIdentificationAssociationType.CUSTOM_4:
                return true;
        }
        return false;
    }

    /**
     * Checks if the given {@code value} is a valid {@link UserIdentificationAssociationValue}.
     */
    public static boolean isValidUserIdentificationAssociationValue(int value) {
        switch(value) {
            case UserIdentificationAssociationValue.ASSOCIATED_ANOTHER_USER:
            case UserIdentificationAssociationValue.ASSOCIATED_CURRENT_USER:
            case UserIdentificationAssociationValue.NOT_ASSOCIATED_ANY_USER:
            case UserIdentificationAssociationValue.UNKNOWN:
                return true;
        }
        return false;
    }

    /**
     * Checks if the given {@code value} is a valid {@link UserIdentificationAssociationSetValue}.
     */
    public static boolean isValidUserIdentificationAssociationSetValue(int value) {
        switch(value) {
            case UserIdentificationAssociationSetValue.ASSOCIATE_CURRENT_USER:
            case UserIdentificationAssociationSetValue.DISASSOCIATE_CURRENT_USER:
            case UserIdentificationAssociationSetValue.DISASSOCIATE_ALL_USERS:
                return true;
        }
        return false;
    }

    /**
     * Creates a {@link UserIdentificationResponse} from a generic {@link VehiclePropValue} sent by
     * HAL.
     *
     * @throws IllegalArgumentException if the HAL property doesn't have the proper format.
     */
    @NonNull
    public static UserIdentificationResponse toUserIdentificationResponse(
            @NonNull VehiclePropValue prop) {
        Objects.requireNonNull(prop, "prop cannot be null");
        checkArgument(prop.prop == USER_IDENTIFICATION_ASSOCIATION_PROPERTY,
                "invalid prop on %s", prop);
        // need at least 4: request_id, number associations, type1, value1
        assertMinimumSize(prop, 4);

        int requestId = prop.value.int32Values.get(0);
        checkArgument(requestId > 0, "invalid request id (%d) on %s", requestId, prop);

        int numberAssociations = prop.value.int32Values.get(1);
        checkArgument(numberAssociations >= 1, "invalid number of items on %s", prop);
        int numberOfNonItems = 2; // requestId and size
        int numberItems = prop.value.int32Values.size() - numberOfNonItems;
        checkArgument(numberItems == numberAssociations * 2, "number of items mismatch on %s",
                prop);

        UserIdentificationResponse response = new UserIdentificationResponse();
        response.requestId = requestId;
        response.errorMessage = prop.value.stringValue;

        response.numberAssociation = numberAssociations;
        int i = numberOfNonItems;
        for (int a = 0; a < numberAssociations; a++) {
            int index;
            UserIdentificationAssociation association = new UserIdentificationAssociation();
            index = i++;
            association.type = prop.value.int32Values.get(index);
            checkArgument(isValidUserIdentificationAssociationType(association.type),
                    "invalid type at index %d on %s", index, prop);
            index = i++;
            association.value = prop.value.int32Values.get(index);
            checkArgument(isValidUserIdentificationAssociationValue(association.value),
                    "invalid value at index %d on %s", index, prop);
            response.associations.add(association);
        }

        return response;
    }

    /**
     * Creates a {@link InitialUserInfoResponse} from a generic {@link VehiclePropValue} sent by
     * HAL.
     *
     * @throws IllegalArgumentException if the HAL property doesn't have the proper format.
     */
    @NonNull
    public static InitialUserInfoResponse toInitialUserInfoResponse(
            @NonNull VehiclePropValue prop) {
        if (DEBUG) Log.d(TAG, "toInitialUserInfoResponse(): " + prop);
        Objects.requireNonNull(prop, "prop cannot be null");
        checkArgument(prop.prop == INITIAL_USER_INFO_PROPERTY, "invalid prop on %s", prop);

        // need at least 2: request_id, action_type
        assertMinimumSize(prop, 2);

        int requestId = prop.value.int32Values.get(0);
        checkArgument(requestId > 0, "invalid request id (%d) on %s", requestId, prop);

        InitialUserInfoResponse response = new InitialUserInfoResponse();
        response.requestId = requestId;
        response.action = prop.value.int32Values.get(1);

        String[] stringValues = null;
        if (!TextUtils.isEmpty(prop.value.stringValue)) {
            stringValues = TextUtils.split(prop.value.stringValue, STRING_SEPARATOR);
            if (DEBUG) {
                Log.d(TAG, "toInitialUserInfoResponse(): values=" + Arrays.toString(stringValues)
                        + " length: " + stringValues.length);
            }
        }
        if (stringValues != null && stringValues.length > 0) {
            response.userLocales = stringValues[0];
        }

        switch (response.action) {
            case InitialUserInfoResponseAction.DEFAULT:
                response.userToSwitchOrCreate.userId = UserHandle.USER_NULL;
                response.userToSwitchOrCreate.flags = UserFlags.NONE;
                break;
            case InitialUserInfoResponseAction.SWITCH:
                assertMinimumSize(prop, 3);
                response.userToSwitchOrCreate.userId = prop.value.int32Values.get(2);
                response.userToSwitchOrCreate.flags = UserFlags.NONE;
                break;
            case InitialUserInfoResponseAction.CREATE:
                assertMinimumSize(prop, 3);
                response.userToSwitchOrCreate.userId = UserHandle.USER_NULL;
                response.userToSwitchOrCreate.flags = prop.value.int32Values.get(2);
                if (stringValues.length > 1) {
                    response.userNameToCreate = stringValues[1];
                }
                break;
            default:
                throw new IllegalArgumentException(
                        "Invalid response action (" + response.action + " on " + prop);
        }

        if (DEBUG) Log.d(TAG, "returning : " + response);

        return response;
    }

    /**
     * Creates a generic {@link VehiclePropValue} (that can be sent to HAL) from a
     * {@link UserIdentificationGetRequest}.
     *
     * @throws IllegalArgumentException if the request doesn't have the proper format.
     */
    @NonNull
    public static VehiclePropValue toVehiclePropValue(
            @NonNull UserIdentificationGetRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        checkArgument(request.numberAssociationTypes > 0,
                "invalid number of association types mismatch on %s", request);
        checkArgument(request.numberAssociationTypes == request.associationTypes.size(),
                "number of association types mismatch on %s", request);
        checkArgument(request.requestId > 0, "invalid requestId on %s", request);

        VehiclePropValue propValue = createPropRequest(USER_IDENTIFICATION_ASSOCIATION_PROPERTY,
                request.requestId);
        addUserInfo(propValue, request.userInfo);
        propValue.value.int32Values.add(request.numberAssociationTypes);

        for (int i = 0; i < request.numberAssociationTypes; i++) {
            int type = request.associationTypes.get(i);
            checkArgument(isValidUserIdentificationAssociationType(type),
                    "invalid type at index %d on %s", i, request);
            propValue.value.int32Values.add(type);
        }

        return propValue;
    }

    /**
     * Creates a generic {@link VehiclePropValue} (that can be sent to HAL) from a
     * {@link UserIdentificationSetRequest}.
     *
     * @throws IllegalArgumentException if the request doesn't have the proper format.
     */
    @NonNull
    public static VehiclePropValue toVehiclePropValue(
            @NonNull UserIdentificationSetRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        checkArgument(request.numberAssociations > 0,
                "invalid number of associations  mismatch on %s", request);
        checkArgument(request.numberAssociations == request.associations.size(),
                "number of associations mismatch on %s", request);
        checkArgument(request.requestId > 0, "invalid requestId on %s", request);

        VehiclePropValue propValue = createPropRequest(USER_IDENTIFICATION_ASSOCIATION_PROPERTY,
                request.requestId);
        addUserInfo(propValue, request.userInfo);
        propValue.value.int32Values.add(request.numberAssociations);

        for (int i = 0; i < request.numberAssociations; i++) {
            UserIdentificationSetAssociation association = request.associations.get(i);
            checkArgument(isValidUserIdentificationAssociationType(association.type),
                    "invalid type at index %d on %s", i, request);
            propValue.value.int32Values.add(association.type);
            checkArgument(isValidUserIdentificationAssociationSetValue(association.value),
                    "invalid value at index %d on %s", i, request);
            propValue.value.int32Values.add(association.value);
        }

        return propValue;
    }

    /**
     * Creates a {@link UsersInfo} instance populated with the current users.
     */
    @NonNull
    public static UsersInfo newUsersInfo(@NonNull UserManager um) {
        Preconditions.checkArgument(um != null, "UserManager cannot be null");

        List<UserInfo> users = um.getUsers(/*excludeDying= */ true);

        if (users == null || users.isEmpty()) {
            Log.w(TAG, "newUsersInfo(): no users");
            return emptyUsersInfo();
        }

        UsersInfo usersInfo = new UsersInfo();
        usersInfo.currentUser.userId = ActivityManager.getCurrentUser();
        UserInfo currentUser = null;
        usersInfo.numberUsers = users.size();

        for (int i = 0; i < usersInfo.numberUsers; i++) {
            UserInfo user = users.get(i);
            if (user.id == usersInfo.currentUser.userId) {
                currentUser = user;
            }
            android.hardware.automotive.vehicle.V2_0.UserInfo halUser =
                    new android.hardware.automotive.vehicle.V2_0.UserInfo();
            halUser.userId = user.id;
            halUser.flags = convertFlags(user);
            usersInfo.existingUsers.add(halUser);
        }

        if (currentUser != null) {
            usersInfo.currentUser.flags = convertFlags(currentUser);
        } else {
            Log.w(TAG, "newUsersInfo(): could not get flags for current user ("
                    + usersInfo.currentUser.userId + ")");
        }

        return usersInfo;
    }

    @NonNull
    private static UsersInfo emptyUsersInfo() {
        UsersInfo usersInfo = new UsersInfo();
        usersInfo.currentUser.userId = UserHandle.USER_NULL;
        usersInfo.currentUser.flags = UserFlags.NONE;
        return usersInfo;
    }

    private static void assertMinimumSize(@NonNull VehiclePropValue prop, int minSize) {
        checkArgument(prop.value.int32Values.size() >= minSize,
                "not enough int32Values (minimum is %d) on %s", minSize, prop);
    }

    private UserHalHelper() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}
