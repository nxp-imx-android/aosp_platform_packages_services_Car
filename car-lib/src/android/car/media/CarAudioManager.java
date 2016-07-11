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

import android.annotation.IntDef;
import android.annotation.SystemApi;
import android.car.CarLibLog;
import android.car.CarNotConnectedException;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.IVolumeController;
import android.os.IBinder;
import android.os.RemoteException;
import android.car.CarManagerBase;
import android.util.Log;

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
    /**
     * Audio usage for external audio usage.
     * @hide
     */
    public static final int CAR_AUDIO_USAGE_EXTERNAL_AUDIO_SOURCE = 10;

    /** @hide */
    public static final int CAR_AUDIO_USAGE_MAX = CAR_AUDIO_USAGE_EXTERNAL_AUDIO_SOURCE;

    /** @hide */
    @IntDef({CAR_AUDIO_USAGE_DEFAULT, CAR_AUDIO_USAGE_MUSIC, CAR_AUDIO_USAGE_RADIO,
        CAR_AUDIO_USAGE_NAVIGATION_GUIDANCE, CAR_AUDIO_USAGE_VOICE_CALL,
        CAR_AUDIO_USAGE_VOICE_COMMAND, CAR_AUDIO_USAGE_ALARM, CAR_AUDIO_USAGE_NOTIFICATION,
        CAR_AUDIO_USAGE_SYSTEM_SOUND, CAR_AUDIO_USAGE_SYSTEM_SAFETY_ALERT,
        CAR_AUDIO_USAGE_EXTERNAL_AUDIO_SOURCE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface CarAudioUsage {}

    /** @hide */
    public static final String CAR_RADIO_TYPE_AM_FM = "RADIO_AM_FM";
    /** @hide */
    public static final String CAR_RADIO_TYPE_AM_FM_HD = "RADIO_AM_FM_HD";
    /** @hide */
    public static final String CAR_RADIO_TYPE_DAB = "RADIO_DAB";
    /** @hide */
    public static final String CAR_RADIO_TYPE_SATELLITE = "RADIO_SATELLITE";

    /** @hide */
    public static final String CAR_EXTERNAL_SOURCE_TYPE_CD_DVD = "CD_DVD";
    /** @hide */
    public static final String CAR_EXTERNAL_SOURCE_TYPE_AUX_IN0 = "AUX_IN0";
    /** @hide */
    public static final String CAR_EXTERNAL_SOURCE_TYPE_AUX_IN1 = "AUX_IN1";
    /** @hide */
    public static final String CAR_EXTERNAL_SOURCE_TYPE_EXT_NAV_GUIDANCE = "EXT_NAV_GUIDANCE";
    /** @hide */
    public static final String CAR_EXTERNAL_SOURCE_TYPE_EXT_VOICE_CALL = "EXT_VOICE_CALL";
    /** @hide */
    public static final String CAR_EXTERNAL_SOURCE_TYPE_EXT_VOICE_COMMAND = "EXT_VOICE_COMMAND";
    /** @hide */
    public static final String CAR_EXTERNAL_SOURCE_TYPE_EXT_SAFETY_ALERT = "EXT_SAFETY_ALERT";

    private final ICarAudio mService;
    private final AudioManager mAudioManager;

    /**
     * Get {@link AudioAttributes} relevant for the given usage in car.
     * @param carUsage
     * @return
     */
    public AudioAttributes getAudioAttributesForCarUsage(@CarAudioUsage int carUsage) {
        try {
            return mService.getAudioAttributesForCarUsage(carUsage);
        } catch (RemoteException e) {
            return createAudioAttributes(AudioAttributes.CONTENT_TYPE_UNKNOWN,
                    AudioAttributes.USAGE_UNKNOWN);
        }
    }

    /**
     * Get AudioAttributes for radio. This is necessary when there are multiple types of radio
     * in system.
     *
     * @param radioType String specifying the desired radio type. Should use only what is listed in
     *        {@link #getSupportedRadioTypes()}.
     * @return
     * @throws IllegalArgumentException If not supported type is passed.
     *
     * @hide
     */
    public AudioAttributes getAudioAttributesForRadio(String radioType)
            throws IllegalArgumentException {
        try {
            return mService.getAudioAttributesForRadio(radioType);
        } catch (RemoteException e) {
            return createAudioAttributes(AudioAttributes.CONTENT_TYPE_UNKNOWN,
                    AudioAttributes.USAGE_UNKNOWN);
        }
    }

    /**
     * Get AudioAttributes for external audio source.
     *
     * @param externalSourceType String specifying the desired source type. Should use only what is
     *        listed in {@link #getSupportedExternalSourceTypes()}.
     * @return
     * @throws IllegalArgumentException If not supported type is passed.
     *
     * @hide
     */
    public AudioAttributes getAudioAttributesForExternalSource(String externalSourceType)
            throws IllegalArgumentException {
        try {
            return mService.getAudioAttributesForExternalSource(externalSourceType);
        } catch (RemoteException e) {
            return createAudioAttributes(AudioAttributes.CONTENT_TYPE_UNKNOWN,
                    AudioAttributes.USAGE_UNKNOWN);
        }
    }

    /**
     * List all supported external audio sources.
     *
     * @return
     *
     * @hide
     */
    public String[] getSupportedExternalSourceTypes() {
        try {
            return mService.getSupportedExternalSourceTypes();
        } catch (RemoteException e) {
            return null;
        }
    }

    /**
     * List all supported radio sources.
     *
     * @return
     *
     * @hide
     */
    public String[] getSupportedRadioTypes() {
        try {
            return mService.getSupportedRadioTypes();
        } catch (RemoteException e) {
            return null;
        }
    }

    /**
     * Request audio focus.
     * Send a request to obtain the audio focus.
     * @param l
     * @param requestAttributes
     * @param durationHint
     * @param flags
     */
    public int requestAudioFocus(OnAudioFocusChangeListener l,
                                 AudioAttributes requestAttributes,
                                 int durationHint,
                                 int flags) throws IllegalArgumentException {
        return mAudioManager.requestAudioFocus(l, requestAttributes, durationHint, flags);
    }

    /**
     * Abandon audio focus. Causes the previous focus owner, if any, to receive focus.
     * @param l
     * @param aa
     * @return {@link #AUDIOFOCUS_REQUEST_FAILED} or {@link #AUDIOFOCUS_REQUEST_GRANTED}
     */
    public int abandonAudioFocus(OnAudioFocusChangeListener l, AudioAttributes aa) {
        return mAudioManager.abandonAudioFocus(l, aa);
    }

    /**
     * Sets the volume index for a particular stream.
     *
     * Requires {@link android.car.Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME} permission.
     *
     * @param streamType The stream whose volume index should be set.
     * @param index The volume index to set. See
     *            {@link #getStreamMaxVolume(int)} for the largest valid value.
     * @param flags One or more flags (e.g., {@link android.media.AudioManager#FLAG_SHOW_UI},
     *              {@link android.media.AudioManager#FLAG_PLAY_SOUND})
     */
    @SystemApi
    public void setStreamVolume(int streamType, int index, int flags)
            throws CarNotConnectedException {
        try {
            mService.setStreamVolume(streamType, index, flags);
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "setStreamVolume failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Registers a global volume controller interface.
     *
     * Requires {@link android.car.Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME} permission.
     *
     * @hide
     */
    @SystemApi
    public void setVolumeController(IVolumeController controller)
            throws CarNotConnectedException {
        try {
            mService.setVolumeController(controller);
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "setVolumeController failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Returns the maximum volume index for a particular stream.
     *
     * Requires {@link android.car.Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME} permission.
     *
     * @param stream The stream type whose maximum volume index is returned.
     * @return The maximum valid volume index for the stream.
     */
    @SystemApi
    public int getStreamMaxVolume(int stream) throws CarNotConnectedException {
        try {
            return mService.getStreamMaxVolume(stream);
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "getStreamMaxVolume failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Returns the minimum volume index for a particular stream.
     *
     * Requires {@link android.car.Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME} permission.
     *
     * @param stream The stream type whose maximum volume index is returned.
     * @return The maximum valid volume index for the stream.
     */
    @SystemApi
    public int getStreamMinVolume(int stream) throws CarNotConnectedException {
        try {
            return mService.getStreamMinVolume(stream);
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "getStreamMaxVolume failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Returns the current volume index for a particular stream.
     *
     * Requires {@link android.car.Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME} permission.
     *
     * @param stream The stream type whose volume index is returned.
     * @return The current volume index for the stream.
     *
     * @see #getStreamMaxVolume(int)
     * @see #setStreamVolume(int, int, int)
     */
    @SystemApi
    public int getStreamVolume(int stream) throws CarNotConnectedException {
        try {
            return mService.getStreamVolume(stream);
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "getStreamVolume failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Check if media audio is muted or not. This will include music and radio. Any application
     * taking audio focus for media stream will get it out of mute state.
     *
     * @return true if media is muted.
     * @throws CarNotConnectedException
     * @hide
     */
    @SystemApi
    public boolean isMediaMuted() throws CarNotConnectedException {
        try {
            return mService.isMediaMuted();
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "isMediaMuted failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Mute or unmute media stream including radio. This can involve audio focus change to stop
     * whatever app holding audio focus now. If requester is currently holding audio focus,
     * it will get LOSS_TRANSIENT focus loss.
     * This API requires {@link android.car.Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME} permission.
     *
     * @param mute
     * @return Mute state of system after the request. Note that mute request can fail if there
     *         is higher priority audio already being played like phone call.
     * @throws CarNotConnectedException
     * @hide
     */
    @SystemApi
    public boolean setMediaMute(boolean mute) throws CarNotConnectedException {
        try {
            return mService.setMediaMute(mute);
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "setMediaMute failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public void onCarDisconnected() {
    }

    /** @hide */
    public CarAudioManager(IBinder service, Context context) {
        mService = ICarAudio.Stub.asInterface(service);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    private AudioAttributes createAudioAttributes(int contentType, int usage) {
        AudioAttributes.Builder builder = new AudioAttributes.Builder();
        return builder.setContentType(contentType).setUsage(usage).build();
    }
}
