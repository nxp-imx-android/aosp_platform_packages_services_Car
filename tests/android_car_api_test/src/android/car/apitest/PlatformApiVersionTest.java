/*
 * Copyright (C) 2022 The Android Open Source Project
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


import static android.car.PlatformApiVersion.TIRAMISU_0;
import static android.car.PlatformApiVersion.TIRAMISU_1;
import static android.os.Build.VERSION_CODES.TIRAMISU;

import static com.google.common.truth.Truth.assertWithMessage;

import android.car.PlatformApiVersion;
import android.car.test.AbstractExpectableTestCase;
import android.os.Parcel;

import org.junit.Test;

public final class PlatformApiVersionTest extends AbstractExpectableTestCase {

    @Test
    public void testTiramisu() {
        expectWithMessage("TIRAMISU_0").that(TIRAMISU_0).isNotNull();
        expectWithMessage("TIRAMISU_0.major").that(TIRAMISU_0.getMajorVersion())
                .isEqualTo(TIRAMISU);
        expectWithMessage("TIRAMISU_0.minor").that(TIRAMISU_0.getMinorVersion())
                .isEqualTo(0);
    }

    @Test
    public void testTiramisu_1() {
        expectWithMessage("TIRAMISU_1").that(TIRAMISU_1).isNotNull();
        expectWithMessage("TIRAMISU_1.major").that(TIRAMISU_1.getMajorVersion())
                .isEqualTo(TIRAMISU);
        expectWithMessage("TIRAMISU_1.minor").that(TIRAMISU_1.getMinorVersion())
                .isEqualTo(1);
    }

    @Test
    public void testMarshalling() {
        PlatformApiVersion original = PlatformApiVersion.forMajorAndMinorVersions(66, 6);
        Parcel parcel =  Parcel.obtain();
        try {
            original.writeToParcel(parcel, /* flags= */ 0);
            parcel.setDataPosition(0);

            PlatformApiVersion clone = PlatformApiVersion.CREATOR.createFromParcel(parcel);

            assertWithMessage("CREATOR.createFromParcel()").that(clone).isNotNull();
            expectWithMessage("clone.major").that(clone.getMajorVersion()).isEqualTo(66);
            expectWithMessage("clone.minor").that(clone.getMinorVersion()).isEqualTo(6);

        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void testNewArray() {
        PlatformApiVersion[] array = PlatformApiVersion.CREATOR.newArray(42);

        assertWithMessage("CREATOR.newArray()").that(array).isNotNull();
        expectWithMessage("CREATOR.newArray()").that(array).hasLength(42);
    }
}
