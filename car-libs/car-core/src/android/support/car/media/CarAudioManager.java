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
package android.support.car.media;

import android.media.AudioAttributes;
import android.os.RemoteException;
import android.support.annotation.IntDef;
import android.support.car.CarManagerBase;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * APIs for handling car specific audio stuffs.
 */
public class CarAudioManager implements CarManagerBase {

    /**
     * Audio usage for unspecified type.
     */
    public static final int CAR_AUDIO_USAGE_DEFAULT = 0;
    /**
     * Audio usage for playing music.
     */
    public static final int CAR_AUDIO_USAGE_MUSIC = 1;
    /**
     * Audio usage for H/W radio.
     */
    public static final int CAR_AUDIO_USAGE_RADIO = 2;
    /**
     * Audio usage for playing navigation guidance.
     */
    public static final int CAR_AUDIO_USAGE_NAVIGATION_GUIDANCE = 3;
    /**
     * Audio usage for voice call
     */
    public static final int CAR_AUDIO_USAGE_VOICE_CALL = 4;
    /**
     * Audio usage for voice search or voice command.
     */
    public static final int CAR_AUDIO_USAGE_VOICE_COMMAND = 5;
    /**
     * Audio usage for playing alarm.
     */
    public static final int CAR_AUDIO_USAGE_ALARM = 6;
    /**
     * Audio usage for notification sound.
     */
    public static final int CAR_AUDIO_USAGE_NOTIFICATION = 7;
    /**
     * Audio usage for system sound like UI feedback.
     */
    public static final int CAR_AUDIO_USAGE_SYSTEM_SOUND = 8;
    /**
     * Audio usage for playing safety alert.
     */
    public static final int CAR_AUDIO_USAGE_SYSTEM_SAFETY_ALERT = 9;

    /** @hide */
    public static final int CAR_AUDIO_USAGE_MAX = CAR_AUDIO_USAGE_SYSTEM_SAFETY_ALERT;

    /** @hide */
    @IntDef({CAR_AUDIO_USAGE_DEFAULT, CAR_AUDIO_USAGE_MUSIC, CAR_AUDIO_USAGE_RADIO,
        CAR_AUDIO_USAGE_NAVIGATION_GUIDANCE, CAR_AUDIO_USAGE_VOICE_CALL,
        CAR_AUDIO_USAGE_VOICE_COMMAND, CAR_AUDIO_USAGE_ALARM, CAR_AUDIO_USAGE_NOTIFICATION,
        CAR_AUDIO_USAGE_SYSTEM_SOUND, CAR_AUDIO_USAGE_SYSTEM_SAFETY_ALERT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface CarAudioUsage {}

    private final ICarAudio mService;

    /**
     * Get {@link AudioAttrbutes} relevant for the given usage in car.
     * @param carUsage
     * @return
     */
    public AudioAttributes getAudioAttributesForCarUsage(@CarAudioUsage int carUsage) {
        try {
            return mService.getAudioAttributesForCarUsage(carUsage);
        } catch (RemoteException e) {
            AudioAttributes.Builder builder = new AudioAttributes.Builder();
            return builder.setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN).
                    setUsage(AudioAttributes.USAGE_UNKNOWN).build();
        }
    }

    @Override
    public void onCarDisconnected() {
        // TODO Auto-generated method stub
    }

    /** @hide */
    public CarAudioManager(ICarAudio service) {
        mService = service;
    }
}
