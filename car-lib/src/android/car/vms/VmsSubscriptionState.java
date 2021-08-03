/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.car.vms;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.ArraySet;

import com.android.car.internal.util.DataClass;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Subscription state of Vehicle Map Service layers.
 *
 * The subscription state is used by publishers to determine which layers to publish data for, as
 * any data published to a layer without subscribers will be dropped by the Vehicle Map Service.
 *
 * Sequence numbers are used to indicate the succession of subscription states, and increase
 * monotonically with each change in subscriptions. They must be used by clients to ignore states
 * that are received out-of-order.
 *
 * @hide
 */
@SystemApi
@DataClass(genAidl = true, genEqualsHashCode = true, genToString = true)
public final class VmsSubscriptionState implements Parcelable {
    /**
     * Sequence number of the subscription state
     */
    private final int mSequenceNumber;

    /**
     * Layers with subscriptions to all publishers
     */
    private @NonNull Set<VmsLayer> mLayers;

    /**
     * Layers with subscriptions to a subset of publishers
     */
    private @NonNull Set<VmsAssociatedLayer> mAssociatedLayers;

    private void onConstructed() {
        mLayers = Collections.unmodifiableSet(mLayers);
        mAssociatedLayers = Collections.unmodifiableSet(mAssociatedLayers);
    }

    private void parcelLayers(Parcel dest, int flags) {
        dest.writeArraySet(new ArraySet<>(mLayers));
    }

    @SuppressWarnings("unchecked")
    private Set<VmsLayer> unparcelLayers(Parcel in) {
        return (Set<VmsLayer>) in.readArraySet(VmsLayer.class.getClassLoader());
    }

    private void parcelAssociatedLayers(Parcel dest, int flags) {
        dest.writeArraySet(new ArraySet<>(mAssociatedLayers));
    }

    @SuppressWarnings("unchecked")
    private Set<VmsAssociatedLayer> unparcelAssociatedLayers(Parcel in) {
        return (Set<VmsAssociatedLayer>) in.readArraySet(VmsAssociatedLayer.class.getClassLoader());
    }



    // Code below generated by codegen v1.0.14.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/services/Car/car-lib/src/android/car/vms/VmsSubscriptionState.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    /**
     * Creates a new VmsSubscriptionState.
     *
     * @param sequenceNumber
     *   Sequence number of the subscription state
     * @param layers
     *   Layers with subscriptions to all publishers
     * @param associatedLayers
     *   Layers with subscriptions to a subset of publishers
     */
    @DataClass.Generated.Member
    public VmsSubscriptionState(
            int sequenceNumber,
            @NonNull Set<VmsLayer> layers,
            @NonNull Set<VmsAssociatedLayer> associatedLayers) {
        this.mSequenceNumber = sequenceNumber;
        this.mLayers = layers;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, mLayers);
        this.mAssociatedLayers = associatedLayers;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, mAssociatedLayers);

        onConstructed();
    }

    /**
     * Sequence number of the subscription state
     */
    @DataClass.Generated.Member
    public int getSequenceNumber() {
        return mSequenceNumber;
    }

    /**
     * Layers with subscriptions to all publishers
     */
    @DataClass.Generated.Member
    public @NonNull Set<VmsLayer> getLayers() {
        return mLayers;
    }

    /**
     * Layers with subscriptions to a subset of publishers
     */
    @DataClass.Generated.Member
    public @NonNull Set<VmsAssociatedLayer> getAssociatedLayers() {
        return mAssociatedLayers;
    }

    @Override
    @DataClass.Generated.Member
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "VmsSubscriptionState { " +
                "sequenceNumber = " + mSequenceNumber + ", " +
                "layers = " + mLayers + ", " +
                "associatedLayers = " + mAssociatedLayers +
        " }";
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@android.annotation.Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(VmsSubscriptionState other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        VmsSubscriptionState that = (VmsSubscriptionState) o;
        //noinspection PointlessBooleanExpression
        return true
                && mSequenceNumber == that.mSequenceNumber
                && Objects.equals(mLayers, that.mLayers)
                && Objects.equals(mAssociatedLayers, that.mAssociatedLayers);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + mSequenceNumber;
        _hash = 31 * _hash + Objects.hashCode(mLayers);
        _hash = 31 * _hash + Objects.hashCode(mAssociatedLayers);
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeInt(mSequenceNumber);
        parcelLayers(dest, flags);
        parcelAssociatedLayers(dest, flags);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ VmsSubscriptionState(@NonNull Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        int sequenceNumber = in.readInt();
        Set<VmsLayer> layers = unparcelLayers(in);
        Set<VmsAssociatedLayer> associatedLayers = unparcelAssociatedLayers(in);

        this.mSequenceNumber = sequenceNumber;
        this.mLayers = layers;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, mLayers);
        this.mAssociatedLayers = associatedLayers;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, mAssociatedLayers);

        onConstructed();
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<VmsSubscriptionState> CREATOR
            = new Parcelable.Creator<VmsSubscriptionState>() {
        @Override
        public VmsSubscriptionState[] newArray(int size) {
            return new VmsSubscriptionState[size];
        }

        @Override
        public VmsSubscriptionState createFromParcel(@NonNull Parcel in) {
            return new VmsSubscriptionState(in);
        }
    };

    @DataClass.Generated(
            time = 1582061018243L,
            codegenVersion = "1.0.14",
            sourceFile = "packages/services/Car/car-lib/src/android/car/vms/VmsSubscriptionState.java",
            inputSignatures = "private final  int mSequenceNumber\nprivate @android.annotation.NonNull java.util.Set<android.car.vms.VmsLayer> mLayers\nprivate @android.annotation.NonNull java.util.Set<android.car.vms.VmsAssociatedLayer> mAssociatedLayers\nprivate  void onConstructed()\nprivate  void parcelLayers(android.os.Parcel,int)\nprivate @java.lang.SuppressWarnings(\"unchecked\") java.util.Set<android.car.vms.VmsLayer> unparcelLayers(android.os.Parcel)\nprivate  void parcelAssociatedLayers(android.os.Parcel,int)\nprivate @java.lang.SuppressWarnings(\"unchecked\") java.util.Set<android.car.vms.VmsAssociatedLayer> unparcelAssociatedLayers(android.os.Parcel)\nclass VmsSubscriptionState extends java.lang.Object implements [android.os.Parcelable]\n@com.android.car.internal.util.DataClass(genAidl=true, genEqualsHashCode=true, genToString=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
