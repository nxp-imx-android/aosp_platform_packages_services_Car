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

package android.car.oem;

import android.car.CarVersion;
import android.car.oem.IOemCarAudioFocusService;
import android.car.oem.IOemCarServiceCallback;

/*
 * Binder for communicating with OEM Car Service.
 */

/** @hide */
interface IOemCarService {
    // Life cycle methods
    /*
     * Called when CarService is ready to take request. OemCarService is initialized before
     * Car Service is ready. This signals OEM Service that CarService is ready.
     * OemCarServiceCallback is passed from Car Service to OEM Service. One important call in the
     * callback is sendOemCarServiceReady() which should be called to inform that OEM service is
     * ready.
     */
    void onCarServiceReady(in IOemCarServiceCallback callback);

    /*
     * This is to check if OEM service is ready. This callback should have returned within limited
     * time else CarService and OemService would be crashed.
     */
    boolean isOemServiceReady();

    /*
     * Gets the supported CarVersion for the OEM service. It is possible that CarModule is updated
     * but OEM service is not updated. CarService needs to be aware of that.
     */
    CarVersion getSupportedCarVersion();

    /*
     * Gets the supported CarVersion for the OEM service. It is possible that CarModule is updated
     * but OEM service is not updated. CarService needs to be aware of that.
     */
    String getAllStackTraces();

    // Component services
    IOemCarAudioFocusService getOemAudioFocusService();
}
