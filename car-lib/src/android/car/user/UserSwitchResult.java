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

package android.car.user;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.os.Parcelable;

import com.android.internal.util.DataClass;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * User switch results.
 *
 * @hide
 */
@DataClass(
        genToString = true,
        genHiddenConstructor = true,
        genHiddenConstDefs = true)
public final class UserSwitchResult implements Parcelable {

    /**
     * When user switch is successful for both HAL and Android.
     */
    public static final int STATUS_SUCCESSFUL = CommonResults.STATUS_SUCCESSFUL;

    /**
     * When user switch is only successful for Hal but not for Android. Hal user switch rollover
     * message have been sent.
     */
    public static final int STATUS_ANDROID_FAILURE = CommonResults.STATUS_ANDROID_FAILURE;

    /**
     * When user switch fails for HAL. User switch for Android is not called.
     */
    public static final int STATUS_HAL_FAILURE = CommonResults.STATUS_HAL_FAILURE;

    /**
     * When user switch fails for HAL for some internal error. User switch for Android is not
     * called.
     */
    public static final int STATUS_HAL_INTERNAL_FAILURE = CommonResults.STATUS_HAL_INTERNAL_FAILURE;

    /**
     * When given parameters or environment states are invalid for switching user. HAL or Android
     * user switch is not requested.
     */
    public static final int STATUS_INVALID_REQUEST = CommonResults.STATUS_INVALID_REQUEST;

    /**
     * When target user is same as current user.
     */
    public static final int STATUS_ALREADY_REQUESTED_USER =
            CommonResults.LAST_COMMON_STATUS + 1;

    /**
     * When another user switch request for the same target user is in process.
     */
    public static final int STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO =
            CommonResults.LAST_COMMON_STATUS + 2;

    /**
     * When another user switch request for a new different target user is received. Previous
     * request is abandoned.
     */
    public static final int STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST =
            CommonResults.LAST_COMMON_STATUS + 3;

    /**
     * Gets the user switch result status.
     *
     * @return either {@link UserSwitchResult#STATUS_SUCCESSFUL},
     *         {@link UserSwitchResult#STATUS_ANDROID_FAILURE},
     *         {@link UserSwitchResult#STATUS_HAL_FAILURE},
     *         {@link UserSwitchResult#STATUS_HAL_INTERNAL_FAILURE},
     *         {@link UserSwitchResult#STATUS_ALREADY_REQUESTED_USER},
     *         {@link UserSwitchResult#STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO},
     *         {@link UserSwitchResult#STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST}, or
     *         {@link UserSwitchResult#STATUS_INVALID_REQUEST}.
     */
    private final int mStatus;

    /**
     * Gets the error message, if any.
     */
    @Nullable
    private final String mErrorMessage;

    /**
     * Check if {@link UserSwitchResult} is successful.
     */
    public boolean isSuccess() {
        return mStatus == STATUS_SUCCESSFUL || mStatus == STATUS_ALREADY_REQUESTED_USER;
    }



    // Code below generated by codegen v1.0.15.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/services/Car/car-lib/src/android/car/user/UserSwitchResult.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    /** @hide */
    @IntDef(prefix = "STATUS_", value = {
        STATUS_SUCCESSFUL,
        STATUS_ANDROID_FAILURE,
        STATUS_HAL_FAILURE,
        STATUS_HAL_INTERNAL_FAILURE,
        STATUS_ALREADY_REQUESTED_USER,
        STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO,
        STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST,
        STATUS_INVALID_REQUEST
    })
    @Retention(RetentionPolicy.SOURCE)
    @DataClass.Generated.Member
    public @interface Status {}

    /** @hide */
    @DataClass.Generated.Member
    public static String statusToString(@Status int value) {
        switch (value) {
            case STATUS_SUCCESSFUL:
                    return "STATUS_SUCCESSFUL";
            case STATUS_ANDROID_FAILURE:
                    return "STATUS_ANDROID_FAILURE";
            case STATUS_HAL_FAILURE:
                    return "STATUS_HAL_FAILURE";
            case STATUS_HAL_INTERNAL_FAILURE:
                    return "STATUS_HAL_INTERNAL_FAILURE";
            case STATUS_ALREADY_REQUESTED_USER:
                    return "STATUS_ALREADY_REQUESTED_USER";
            case STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO:
                    return "STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO";
            case STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST:
                    return "STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST";
            case STATUS_INVALID_REQUEST:
                    return "STATUS_INVALID_REQUEST";
            default: return Integer.toHexString(value);
        }
    }

    /**
     * Creates a new UserSwitchResult.
     *
     * @param status
     *   Gets the user switch result status.
     *
     *   @return either {@link UserSwitchResult#STATUS_SUCCESSFUL},
     *           {@link UserSwitchResult#STATUS_ANDROID_FAILURE},
     *           {@link UserSwitchResult#STATUS_HAL_FAILURE},
     *           {@link UserSwitchResult#STATUS_HAL_INTERNAL_FAILURE},
     *           {@link UserSwitchResult#STATUS_ALREADY_REQUESTED_USER},
     *           {@link UserSwitchResult#STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO},
     *           {@link UserSwitchResult#STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST}, or
     *           {@link UserSwitchResult#STATUS_INVALID_REQUEST}.
     * @param errorMessage
     *   Gets the error message, if any.
     * @hide
     */
    @DataClass.Generated.Member
    public UserSwitchResult(
            int status,
            @Nullable String errorMessage) {
        this.mStatus = status;
        this.mErrorMessage = errorMessage;

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * Gets the user switch result status.
     *
     * @return either {@link UserSwitchResult#STATUS_SUCCESSFUL},
     *         {@link UserSwitchResult#STATUS_ANDROID_FAILURE},
     *         {@link UserSwitchResult#STATUS_HAL_FAILURE},
     *         {@link UserSwitchResult#STATUS_HAL_INTERNAL_FAILURE},
     *         {@link UserSwitchResult#STATUS_ALREADY_REQUESTED_USER},
     *         {@link UserSwitchResult#STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO},
     *         {@link UserSwitchResult#STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST}, or
     *         {@link UserSwitchResult#STATUS_INVALID_REQUEST}.
     */
    @DataClass.Generated.Member
    public int getStatus() {
        return mStatus;
    }

    /**
     * Gets the error message, if any.
     */
    @DataClass.Generated.Member
    public @Nullable String getErrorMessage() {
        return mErrorMessage;
    }

    @Override
    @DataClass.Generated.Member
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "UserSwitchResult { " +
                "status = " + mStatus + ", " +
                "errorMessage = " + mErrorMessage +
        " }";
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@android.annotation.NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mErrorMessage != null) flg |= 0x2;
        dest.writeByte(flg);
        dest.writeInt(mStatus);
        if (mErrorMessage != null) dest.writeString(mErrorMessage);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ UserSwitchResult(@android.annotation.NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        int status = in.readInt();
        String errorMessage = (flg & 0x2) == 0 ? null : in.readString();

        this.mStatus = status;
        this.mErrorMessage = errorMessage;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @android.annotation.NonNull Parcelable.Creator<UserSwitchResult> CREATOR
            = new Parcelable.Creator<UserSwitchResult>() {
        @Override
        public UserSwitchResult[] newArray(int size) {
            return new UserSwitchResult[size];
        }

        @Override
        public UserSwitchResult createFromParcel(@android.annotation.NonNull android.os.Parcel in) {
            return new UserSwitchResult(in);
        }
    };

    @DataClass.Generated(
            time = 1590737883648L,
            codegenVersion = "1.0.15",
            sourceFile = "packages/services/Car/car-lib/src/android/car/user/UserSwitchResult.java",
            inputSignatures = "public static final  int STATUS_SUCCESSFUL\npublic static final  int STATUS_ANDROID_FAILURE\npublic static final  int STATUS_HAL_FAILURE\npublic static final  int STATUS_HAL_INTERNAL_FAILURE\npublic static final  int STATUS_ALREADY_REQUESTED_USER\npublic static final  int STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO\npublic static final  int STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST\npublic static final  int STATUS_INVALID_REQUEST\nprivate final  int mStatus\nprivate final @android.annotation.Nullable java.lang.String mErrorMessage\npublic  boolean isSuccess()\nclass UserSwitchResult extends java.lang.Object implements [android.os.Parcelable]\n@com.android.internal.util.DataClass(genToString=true, genHiddenConstructor=true, genHiddenConstDefs=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
