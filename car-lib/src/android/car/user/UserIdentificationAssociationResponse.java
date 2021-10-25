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

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.user.CarUserManager.UserIdentificationAssociationValue;
import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.ArrayUtils;
import com.android.car.internal.util.DataClass;
import com.android.internal.util.Preconditions;

import java.util.Objects;

/**
 * Results of a {@link CarUserManager#getUserIdentificationAssociation(int[]) request.
 *
 * @hide
 */
@DataClass(
        genToString = true,
        genHiddenConstructor = false,
        genHiddenConstDefs = true)
public final class UserIdentificationAssociationResponse implements Parcelable {

    /**
     * Whether the request was successful.
     *
     * <p>A successful option has non-null {@link #getValues()}
     */
    private final boolean mSuccess;

    /**
     * Gets the error message returned by the HAL.
     */
    @Nullable
    private final String mErrorMessage;

    /**
     * Gets the list of values associated with the request.
     *
     * <p><b>NOTE: </b>It's only set when the response is {@link #isSuccess() successful}.
     *
     * <p>For {@link CarUserManager#getUserIdentificationAssociation(int...)}, the values are
     * defined on
     * {@link android.hardware.automotive.vehicle.V2_0.UserIdentificationAssociationValue}.
     *
     * <p>For {@link CarUserManager#setUserIdentificationAssociation(int...)}, the values are
     * defined on
     * {@link android.hardware.automotive.vehicle.V2_0.UserIdentificationAssociationSetValue}.
     */
    @Nullable
    private final int[] mValues;

    private UserIdentificationAssociationResponse(
            boolean success,
            @Nullable String errorMessage,
            @UserIdentificationAssociationValue int[] values) {
        this.mSuccess = success;
        this.mErrorMessage = errorMessage;
        this.mValues = values;
    }

    /**
     * Factory method for failed UserIdentificationAssociationResponse requests.
     */
    @NonNull
    public static UserIdentificationAssociationResponse forFailure() {
        return forFailure(/* errorMessage= */ null);
    }

    /**
     * Factory method for failed UserIdentificationAssociationResponse requests.
     */
    @NonNull
    public static UserIdentificationAssociationResponse forFailure(@Nullable String errorMessage) {
        return new UserIdentificationAssociationResponse(/* success= */ false,
                errorMessage, /* values= */ null);
    }

    /**
     * Factory method for successful UserIdentificationAssociationResponse requests.
     */
    @NonNull
    public static UserIdentificationAssociationResponse forSuccess(
            @UserIdentificationAssociationValue int[] values) {
        Preconditions.checkArgument(!ArrayUtils.isEmpty(values), "must have at least one value");
        return new UserIdentificationAssociationResponse(/* success= */ true,
                /* errorMessage= */ null, Objects.requireNonNull(values));
    }

    /**
     * Factory method for successful UserIdentificationAssociationResponse requests.
     */
    @NonNull
    public static UserIdentificationAssociationResponse forSuccess(
            @UserIdentificationAssociationValue int[] values, @Nullable String errorMessage) {
        Preconditions.checkArgument(!ArrayUtils.isEmpty(values), "must have at least one value");
        return new UserIdentificationAssociationResponse(/* success= */ true, errorMessage,
                Objects.requireNonNull(values));
    }





    // Code below generated by codegen v1.0.20.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/services/Car/car-lib/src/android/car/user/UserIdentificationAssociationResponse.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    /**
     * Whether the request was successful.
     *
     * <p>A successful option has non-null {@link #getValues()}
     */
    @DataClass.Generated.Member
    public boolean isSuccess() {
        return mSuccess;
    }

    /**
     * Gets the error message returned by the HAL.
     */
    @DataClass.Generated.Member
    public @Nullable String getErrorMessage() {
        return mErrorMessage;
    }

    /**
     * Gets the list of values associated with the request.
     *
     * <p><b>NOTE: </b>It's only set when the response is {@link #isSuccess() successful}.
     *
     * <p>For {@link CarUserManager#getUserIdentificationAssociation(int...)}, the values are
     * defined on
     * {@link android.hardware.automotive.vehicle.V2_0.UserIdentificationAssociationValue}.
     *
     * <p>For {@link CarUserManager#setUserIdentificationAssociation(int...)}, the values are
     * defined on
     * {@link android.hardware.automotive.vehicle.V2_0.UserIdentificationAssociationSetValue}.
     */
    @DataClass.Generated.Member
    public @Nullable int[] getValues() {
        return mValues;
    }

    @Override
    @DataClass.Generated.Member
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "UserIdentificationAssociationResponse { " +
                "success = " + mSuccess + ", " +
                "errorMessage = " + mErrorMessage + ", " +
                "values = " + java.util.Arrays.toString(mValues) +
        " }";
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mSuccess) flg |= 0x1;
        if (mErrorMessage != null) flg |= 0x2;
        if (mValues != null) flg |= 0x4;
        dest.writeByte(flg);
        if (mErrorMessage != null) dest.writeString(mErrorMessage);
        if (mValues != null) dest.writeIntArray(mValues);
    }

    @Override
    @DataClass.Generated.Member
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ UserIdentificationAssociationResponse(@NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        boolean success = (flg & 0x1) != 0;
        String errorMessage = (flg & 0x2) == 0 ? null : in.readString();
        int[] values = (flg & 0x4) == 0 ? null : in.createIntArray();

        this.mSuccess = success;
        this.mErrorMessage = errorMessage;
        this.mValues = values;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<UserIdentificationAssociationResponse> CREATOR
            = new Parcelable.Creator<UserIdentificationAssociationResponse>() {
        @Override
        public UserIdentificationAssociationResponse[] newArray(int size) {
            return new UserIdentificationAssociationResponse[size];
        }

        @Override
        public UserIdentificationAssociationResponse createFromParcel(@NonNull android.os.Parcel in) {
            return new UserIdentificationAssociationResponse(in);
        }
    };

    @DataClass.Generated(
            time = 1604638584791L,
            codegenVersion = "1.0.20",
            sourceFile = "packages/services/Car/car-lib/src/android/car/user/UserIdentificationAssociationResponse.java",
            inputSignatures = "private final  boolean mSuccess\nprivate final @android.annotation.Nullable java.lang.String mErrorMessage\nprivate final @android.annotation.Nullable int[] mValues\npublic static @android.annotation.NonNull android.car.user.UserIdentificationAssociationResponse forFailure()\npublic static @android.annotation.NonNull android.car.user.UserIdentificationAssociationResponse forFailure(java.lang.String)\npublic static @android.annotation.NonNull android.car.user.UserIdentificationAssociationResponse forSuccess(int[])\npublic static @android.annotation.NonNull android.car.user.UserIdentificationAssociationResponse forSuccess(int[],java.lang.String)\nclass UserIdentificationAssociationResponse extends java.lang.Object implements [android.os.Parcelable]\n@com.android.car.internal.util.DataClass(genToString=true, genHiddenConstructor=false, genHiddenConstDefs=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
