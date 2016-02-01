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

package android.support.car.content.pm;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.car.annotation.VersionDef;
import android.support.car.os.ExtendableParcelable;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Contains application blocking policy
 * @hide
 */
public class CarAppBlockingPolicy extends ExtendableParcelable {
    private static final String TAG = CarAppBlockingPolicy.class.getSimpleName();

    @VersionDef(version = 1)
    public final AppBlockingPackageInfo[] whitelists;
    @VersionDef(version = 1)
    public final AppBlockingPackageInfo[] blacklists;

    private static final int VERSION = 1;

    private static final Method sReadBlobMethod;
    private static final Method sWriteBlobMethod;

    static {
        Class parcelClass = Parcel.class;
        Method readBlob = null;
        Method writeBlob = null;
        try {
            readBlob = parcelClass.getMethod("readBlob", new Class[] {});
            writeBlob = parcelClass.getMethod("writeBlob", new Class[] {byte[].class});
        } catch (NoSuchMethodException e) {
            // use it only when both methods are available.
            readBlob = null;
            writeBlob = null;
        }
        sReadBlobMethod = readBlob;
        sWriteBlobMethod = writeBlob;
    }

    public CarAppBlockingPolicy(AppBlockingPackageInfo[] whitelists,
            AppBlockingPackageInfo[] blacklists) {
        super(VERSION);
        this.whitelists = whitelists;
        this.blacklists = blacklists;
    }

    public CarAppBlockingPolicy(Parcel in) {
        super(in, VERSION);
        int lastPosition = readHeader(in);
        byte[] payload;
        if (sReadBlobMethod != null) {
            try {
                payload = (byte[]) sReadBlobMethod.invoke(in);
            } catch (IllegalAccessException | InvocationTargetException e) {
                Log.e(TAG, ":cannot call readBlob", e);
                payload = in.createByteArray();
            }
        } else {
            payload = in.createByteArray();
        }
        Parcel payloadParcel = Parcel.obtain();
        payloadParcel.unmarshall(payload, 0, payload.length);
        // reset to initial position to read
        payloadParcel.setDataPosition(0);
        whitelists = payloadParcel.createTypedArray(AppBlockingPackageInfo.CREATOR);
        blacklists = payloadParcel.createTypedArray(AppBlockingPackageInfo.CREATOR);
        payloadParcel.recycle();
        completeReading(in, lastPosition);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        int startingPosition = writeHeader(dest);
        Parcel payloadParcel = Parcel.obtain();
        payloadParcel.writeTypedArray(whitelists, 0);
        payloadParcel.writeTypedArray(blacklists, 0);
        byte[] payload = payloadParcel.marshall();
        if (sWriteBlobMethod != null) {
            try {
                sWriteBlobMethod.invoke(dest, payload);
            } catch (IllegalAccessException | InvocationTargetException e) {
                Log.e(TAG, ":cannot call writeBlob", e);
                dest.writeByteArray(payload);
            }
        } else {
            dest.writeByteArray(payload);
        }
        payloadParcel.recycle();
        completeWriting(dest, startingPosition);
    }

    public static final Parcelable.Creator<CarAppBlockingPolicy> CREATOR
            = new Parcelable.Creator<CarAppBlockingPolicy>() {
        public CarAppBlockingPolicy createFromParcel(Parcel in) {
            return new CarAppBlockingPolicy(in);
        }

        public CarAppBlockingPolicy[] newArray(int size) {
            return new CarAppBlockingPolicy[size];
        }
    };

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(blacklists);
        result = prime * result + Arrays.hashCode(whitelists);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        CarAppBlockingPolicy other = (CarAppBlockingPolicy) obj;
        if (!Arrays.equals(blacklists, other.blacklists)) {
            return false;
        }
        if (!Arrays.equals(whitelists, other.whitelists)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "CarAppBlockingPolicy [whitelists=" + Arrays.toString(whitelists) + ", blacklists="
                + Arrays.toString(blacklists) + "]";
    }
}
