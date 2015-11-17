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

package android.support.car;

import android.content.Intent;

import android.support.car.ICarConnectionListener;

/** @hide */
interface ICar {
    int getVersion() = 0;
    IBinder getCarService(in String serviceName) = 1;
    boolean isConnectedToCar() = 2;
    int getCarConnectionType() = 3;
    void registerCarConnectionListener(int clientVersion, in ICarConnectionListener listener) = 4;
    void unregisterCarConnectionListener(in ICarConnectionListener listener) = 5;
}
