/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.car.watchdog;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.internal.util.DataClass;

/**
 * Number of bytes attributed to each application or system state.
 */
@DataClass(genToString = true)
public final class PerStateBytes implements Parcelable {
    /**
     * Number of bytes attributed to the application foreground mode.
     */
    private long mForegroundModeBytes;

    /**
     * Number of bytes attributed to the application background mode.
     */
    private long mBackgroundModeBytes;

    /**
     * Number of bytes attributed to the system garage mode.
     */
    private long mGarageModeBytes;



    // Code below generated by codegen v1.0.22.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/services/Car/car-lib/src/android/car/watchdog/PerStateBytes.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    /**
     * Creates a new PerStateBytes.
     *
     * @param foregroundModeBytes
     *   Number of bytes attributed to the application foreground mode.
     * @param backgroundModeBytes
     *   Number of bytes attributed to the application background mode.
     * @param garageModeBytes
     *   Number of bytes attributed to the system garage mode.
     */
    @DataClass.Generated.Member
    public PerStateBytes(
            long foregroundModeBytes,
            long backgroundModeBytes,
            long garageModeBytes) {
        this.mForegroundModeBytes = foregroundModeBytes;
        this.mBackgroundModeBytes = backgroundModeBytes;
        this.mGarageModeBytes = garageModeBytes;

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * Number of bytes attributed to the application foreground mode.
     */
    @DataClass.Generated.Member
    public long getForegroundModeBytes() {
        return mForegroundModeBytes;
    }

    /**
     * Number of bytes attributed to the application background mode.
     */
    @DataClass.Generated.Member
    public long getBackgroundModeBytes() {
        return mBackgroundModeBytes;
    }

    /**
     * Number of bytes attributed to the system garage mode.
     */
    @DataClass.Generated.Member
    public long getGarageModeBytes() {
        return mGarageModeBytes;
    }

    @Override
    @DataClass.Generated.Member
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "PerStateBytes { " +
                "foregroundModeBytes = " + mForegroundModeBytes + ", " +
                "backgroundModeBytes = " + mBackgroundModeBytes + ", " +
                "garageModeBytes = " + mGarageModeBytes +
        " }";
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@android.annotation.NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeLong(mForegroundModeBytes);
        dest.writeLong(mBackgroundModeBytes);
        dest.writeLong(mGarageModeBytes);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ PerStateBytes(@android.annotation.NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        long foregroundModeBytes = in.readLong();
        long backgroundModeBytes = in.readLong();
        long garageModeBytes = in.readLong();

        this.mForegroundModeBytes = foregroundModeBytes;
        this.mBackgroundModeBytes = backgroundModeBytes;
        this.mGarageModeBytes = garageModeBytes;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @android.annotation.NonNull Parcelable.Creator<PerStateBytes> CREATOR
            = new Parcelable.Creator<PerStateBytes>() {
        @Override
        public PerStateBytes[] newArray(int size) {
            return new PerStateBytes[size];
        }

        @Override
        public PerStateBytes createFromParcel(@android.annotation.NonNull android.os.Parcel in) {
            return new PerStateBytes(in);
        }
    };

    @DataClass.Generated(
            time = 1614388529869L,
            codegenVersion = "1.0.22",
            sourceFile = "packages/services/Car/car-lib/src/android/car/watchdog/PerStateBytes.java",
            inputSignatures = "private  long mForegroundModeBytes\nprivate  long mBackgroundModeBytes\nprivate  long mGarageModeBytes\nclass PerStateBytes extends java.lang.Object implements [android.os.Parcelable]\n@com.android.internal.util.DataClass(genToString=true)")
    @Deprecated
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
