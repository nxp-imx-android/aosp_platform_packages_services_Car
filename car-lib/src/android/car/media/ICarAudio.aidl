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

package android.car.media;

import android.media.AudioAttributes;
import android.media.IVolumeController;

/**
 * Binder interface for {@link android.car.media.CarAudioManager}.
 * Check {@link android.car.media.CarAudioManager} APIs for expected behavior of each calls.
 *
 * @hide
 */
interface ICarAudio {
    AudioAttributes getAudioAttributesForCarUsage(int carUsage) = 0;
    void setStreamVolume(int streamType, int index, int flags) = 1;
    void setVolumeController(IVolumeController controller) = 2;
    int getStreamMaxVolume(int streamType) = 3;
    int getStreamMinVolume(int streamType) = 4;
    int getStreamVolume(int streamType) = 5;
    boolean isMediaMuted() = 6;
    boolean setMediaMute(boolean mute) = 7;
}
