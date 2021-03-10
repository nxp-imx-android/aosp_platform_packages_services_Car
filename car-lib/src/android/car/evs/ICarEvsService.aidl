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

package android.car.evs;

import android.car.evs.ICarEvsStatusListener;
import android.car.evs.ICarEvsStreamCallback;
import android.hardware.HardwareBuffer;
import android.os.IBinder;

/**
 * @hide
 */
interface ICarEvsService {

    /**
     * Registers a listener to receive changes in CarEvsManager's status.
     */
    void registerStatusListener(in ICarEvsStatusListener listener);

    /**
     * Unregisters a service listener.
     */
    void unregisterStatusListener(in ICarEvsStatusListener listener);

    /**
     * Requests to start a given service type.
     */
    int startVideoStream(int serviceType, in IBinder token, in ICarEvsStreamCallback callback);

    /**
     * Requests to stop a currently active service.
     */
    void stopVideoStream(in ICarEvsStreamCallback callback);

    /**
     * Returns the buffer when its usages are done.
     */
    void returnFrameBuffer(in HardwareBuffer buffer);

    /**
     * Returns a current status of CarEvsService.
     */
    int getCurrentStatus();

    /**
     * Returns a generated session token.
     */
    IBinder generateSessionToken();
}
