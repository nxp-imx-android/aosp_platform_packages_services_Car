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

package android.car;
import android.bluetooth.BluetoothDevice;

/** @hide */
interface ICarBluetooth {
    void setBluetoothDeviceConnectionPriority(in BluetoothDevice deviceToSet, in int profileToSet,
                in int priorityToSet);
    void clearBluetoothDeviceConnectionPriority(in int profileToClear,in int priorityToClear);
    boolean isPriorityDevicePresent(in int profile, in int priorityToCheck);
    String getDeviceNameWithPriority(in int profile, in int priorityToCheck);
    boolean requestTemporaryDisconnect(in BluetoothDevice device, in int profile, in IBinder token);
    boolean releaseTemporaryDisconnect(in BluetoothDevice device, in int profile, in IBinder token);
}
