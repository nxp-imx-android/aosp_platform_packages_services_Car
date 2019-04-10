/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.car.developeroptions.biometrics.fingerprint;

/**
 * An abstraction for a view that contains an animation that shows the user
 * where the fingerprint sensor is on the device.
 */
public interface FingerprintFindSensorAnimation {

    /**
     * Start the animation
     */
    void startAnimation();

    /**
     * Stop the animation
     */
    void stopAnimation();

    /**
     * Pause the animation
     */
    void pauseAnimation();

}
