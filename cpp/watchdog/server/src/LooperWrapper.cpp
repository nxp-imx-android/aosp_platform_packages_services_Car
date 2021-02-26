/**
 * Copyright (c) 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "LooperWrapper.h"

#include <utils/Looper.h>

namespace android {
namespace automotive {
namespace watchdog {

using ::android::sp;

void LooperWrapper::wake() {
    if (mLooper == nullptr) {
        ALOGW("No looper in LooperWrapper");
        return;
    }
    return mLooper->wake();
}

int LooperWrapper::pollAll(int timeoutMillis) {
    if (mLooper == nullptr) {
        ALOGW("No looper in LooperWrapper");
        return 0;
    }
    return mLooper->pollAll(timeoutMillis);
}

void LooperWrapper::sendMessage(const sp<MessageHandler>& handler, const Message& message) {
    if (mLooper == nullptr) {
        ALOGW("No looper in LooperWrapper");
        return;
    }
    return mLooper->sendMessage(handler, message);
}

void LooperWrapper::sendMessageAtTime(nsecs_t uptime, const sp<MessageHandler>& handler,
                                      const Message& message) {
    if (mLooper == nullptr) {
        ALOGW("No looper in LooperWrapper");
        return;
    }
    return mLooper->sendMessageAtTime(uptime, handler, message);
}

void LooperWrapper::removeMessages(const sp<MessageHandler>& handler) {
    if (mLooper == nullptr) {
        ALOGW("No looper in LooperWrapper");
        return;
    }
    return mLooper->removeMessages(handler);
}

void LooperWrapper::removeMessages(const android::sp<MessageHandler>& handler, int what) {
    if (mLooper == nullptr) {
        ALOGW("No looper in LooperWrapper");
        return;
    }
    return mLooper->removeMessages(handler, what);
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
