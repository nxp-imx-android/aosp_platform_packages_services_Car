/*
 * Copyright (C) 2016 The Android Open Source Project
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

#define LOG_NDEBUG 0
#define LOG_TAG "CAR.INPUT"

#include <string.h>
#include <sys/time.h>
#include <linux/input.h>
#include <jni.h>
#include <JNIHelp.h>
#include <android/keycodes.h>
#include <cutils/log.h>
#include <utils/Errors.h>
#include <unordered_map>

namespace android {

static int androidKeyCodeToLinuxKeyCode(int androidKeyCode) {
    // Map Android Key Code to Linux Kernel codes
    // according to frameworks/base/data/keyboards/Generic.kl

    static const std::unordered_map<int, int> key_map {
      { AKEYCODE_VOLUME_UP,          KEY_VOLUMEUP },
      { AKEYCODE_VOLUME_DOWN,        KEY_VOLUMEDOWN },
      { AKEYCODE_VOLUME_MUTE,        KEY_MUTE },
      { AKEYCODE_CALL,               KEY_PHONE },
      { AKEYCODE_ENDCALL,            KEY_END },  // Currently not supported in Generic.kl
      { AKEYCODE_MUSIC,              KEY_SOUND },
      { AKEYCODE_MEDIA_PLAY_PAUSE,   KEY_PLAYPAUSE },
      { AKEYCODE_MEDIA_PLAY,         KEY_PLAY },
      { AKEYCODE_BREAK,              KEY_PAUSE },
      { AKEYCODE_MEDIA_STOP,         KEY_STOP },
      { AKEYCODE_MEDIA_FAST_FORWARD, KEY_FASTFORWARD },
      { AKEYCODE_MEDIA_REWIND,       KEY_REWIND },
      { AKEYCODE_MEDIA_NEXT,         KEY_NEXTSONG },
      { AKEYCODE_MEDIA_PREVIOUS,     KEY_PREVIOUSSONG },
      { AKEYCODE_CHANNEL_UP,         KEY_CHANNELUP },
      { AKEYCODE_CHANNEL_DOWN,       KEY_CHANNELDOWN },
      { AKEYCODE_VOICE_ASSIST,       KEY_MICMUTE },
      { AKEYCODE_HOME,               KEY_HOME }
    };

    std::unordered_map<int, int>::const_iterator got = key_map.find(androidKeyCode);

    if (got == key_map.end()) {
        ALOGW("Unmapped android key code %d dropped", androidKeyCode);
        return 0;
    } else {
        return got->second;
    }
}

/*
 * Class:     com_android_car_CarInputService
 * Method:    nativeInjectKeyEvent
 * Signature: (IIZ)I
 */
static jint com_android_car_CarInputService_nativeInjectKeyEvent
  (JNIEnv *env, jobject /*object*/, jint fd, jint keyCode, jboolean down) {
    int linuxKeyCode = androidKeyCodeToLinuxKeyCode(keyCode);
    if (linuxKeyCode == 0) {
        return BAD_VALUE;
    }
    struct input_event ev[2];
    memset(reinterpret_cast<void*>(&ev), 0, sizeof(ev));
    struct timeval now;
    gettimeofday(&now, NULL);
    // kernel driver is not using time now, but set it to be safe.
    ev[0].time = now;
    ev[0].type = EV_KEY;
    ev[0].code = linuxKeyCode;
    ev[0].value = (down ? 1 : 0);
    // force delivery and flushing
    ev[1].time = now;
    ev[1].type = EV_SYN;
    ev[1].code = SYN_REPORT;
    ev[1].value = 0;
    ALOGI("injectKeyEvent down %d keyCode %d, value %d", down, ev[0].code, ev[0].value);
    int r = write(fd, reinterpret_cast<void*>(&ev), sizeof(ev));
    if (r != sizeof(ev)) {
        return -EIO;
    }
    return 0;
}

static JNINativeMethod gMethods[] = {
    { "nativeInjectKeyEvent", "(IIZ)I",
            (void*)com_android_car_CarInputService_nativeInjectKeyEvent },
};

int register_com_android_car_CarInputService(JNIEnv *env) {
    return jniRegisterNativeMethods(env, "com/android/car/CarInputService",
            gMethods, NELEM(gMethods));
}

} // namespace android
