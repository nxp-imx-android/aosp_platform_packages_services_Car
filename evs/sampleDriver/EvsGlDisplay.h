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

#ifndef ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_EVSGLDISPLAY_H
#define ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_EVSGLDISPLAY_H

#include <android/hardware/automotive/evs/1.0/IEvsDisplay.h>
#include <ui/GraphicBuffer.h>

#include "GlWrapper.h"

using ::android::hardware::automotive::evs::V1_0::IEvsDisplay;
using ::android::hardware::automotive::evs::V1_0::EvsResult;
using ::android::hardware::automotive::evs::V1_0::DisplayDesc;
using ::android::hardware::automotive::evs::V1_0::DisplayState;
using EvsResult   = ::android::hardware::automotive::evs::V1_0::EvsResult;
using BufferDesc_1_0  = ::android::hardware::automotive::evs::V1_0::BufferDesc;
using EvsDisplayState = ::android::hardware::automotive::evs::V1_0::DisplayState;

namespace android {
namespace hardware {
namespace automotive {
namespace evs {
namespace V1_1 {
namespace implementation {


class EvsGlDisplay : public IEvsDisplay {
public:
    // Methods from ::android::hardware::automotive::evs::V1_0::IEvsDisplay follow.
    Return<void>            getDisplayInfo(getDisplayInfo_cb _hidl_cb)  override;
    Return<EvsResult>       setDisplayState(EvsDisplayState state)  override;
    Return<EvsDisplayState> getDisplayState()  override;
    Return<void>            getTargetBuffer(getTargetBuffer_cb _hidl_cb)  override;
    Return<EvsResult>       returnTargetBufferForDisplay(const BufferDesc_1_0& buffer)  override;

    // Implementation details
    EvsGlDisplay();
    virtual ~EvsGlDisplay() override;

    void forceShutdown();   // This gets called if another caller "steals" ownership of the display

private:
    DisplayDesc     mInfo           = {};
    BufferDesc_1_0  mBuffer         = {};       // A graphics buffer into which we'll store images

    bool            mFrameBusy      = false;    // A flag telling us our buffer is in use
    EvsDisplayState mRequestedState = EvsDisplayState::NOT_VISIBLE;

    GlWrapper       mGlWrapper;

    std::mutex      mAccessLock;
};

} // namespace implementation
} // namespace V1_0
} // namespace evs
} // namespace automotive
} // namespace hardware
} // namespace android

#endif  // ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_0_EVSGLDISPLAY_H
