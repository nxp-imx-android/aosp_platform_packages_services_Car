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

package android.car;

/** @hide */
interface ICar {
    /**
     * IBinder is ICarServiceHelper but passed as IBinder due to aidl hidden.
     * Only this method is oneway as it is called from system server.
     * This should be the 1st method. Do not change the order.
     */
    oneway void setCarServiceHelper(in IBinder helper) = 0;
    IBinder getCarService(in String serviceName) = 1;
    int getCarConnectionType() = 2;
}
