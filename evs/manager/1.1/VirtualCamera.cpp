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
 * limitations under the License.
 */

#include "VirtualCamera.h"
#include "HalCamera.h"
#include "Enumerator.h"

#include <ui/GraphicBufferAllocator.h>
#include <ui/GraphicBufferMapper.h>

using ::android::hardware::automotive::evs::V1_0::DisplayState;
using ::android::hardware::automotive::evs::V1_1::CameraDesc;


namespace android {
namespace automotive {
namespace evs {
namespace V1_1 {
namespace implementation {


VirtualCamera::VirtualCamera(sp<HalCamera> halCamera) :
    mHalCamera(halCamera),
    mStreamState(STOPPED){
}


VirtualCamera::~VirtualCamera() {
    shutdown();
}


void VirtualCamera::shutdown() {
    // In normal operation, the stream should already be stopped by the time we get here
    if (mHalCamera != nullptr && mStreamState != STOPPED) {
        // Note that if we hit this case, no terminating frame will be sent to the client,
        // but they're probably already dead anyway.
        ALOGW("Virtual camera being shutdown while stream is running");

        // Tell the frame delivery pipeline we don't want any more frames
        mStreamState = STOPPING;

        // Join a capture thread
        if (mCaptureThread.joinable()) {
            mCaptureThread.join();
        }

        if (mFramesHeld.size() > 0) {
            ALOGW("VirtualCamera destructing with frames in flight.");

            // Return to the underlying hardware camera any buffers the client was holding
            for (auto&& heldBuffer : mFramesHeld) {
                // Tell our parent that we're done with this buffer
                mHalCamera->doneWithFrame(heldBuffer);
            }
            mFramesHeld.clear();
        }

        // Retire from a master client
        mHalCamera->unsetMaster(this);

        // Give the underlying hardware camera the heads up that it might be time to stop
        mHalCamera->clientStreamEnding();
    }

    // Drop our reference to our associated hardware camera
    mHalCamera = nullptr;
}


bool VirtualCamera::deliverFrame(const BufferDesc_1_1& bufDesc) {
    if (mStreamState == STOPPED) {
        // A stopped stream gets no frames
        ALOGE("A stopped stream should not get any frames");
        return false;
    } else if (mFramesHeld.size() >= mFramesAllowed) {
        // Indicate that we declined to send the frame to the client because they're at quota
        ALOGI("Skipping new frame as we hold %zu of %u allowed.",
              mFramesHeld.size(), mFramesAllowed);

        if (mStream_1_1 != nullptr) {
            // Report a frame drop to v1.1 client.
            EvsEventDesc event;
            event.deviceId = bufDesc.deviceId;
            event.aType = EvsEventType::FRAME_DROPPED;
            auto result = mStream_1_1->notify(event);
            if (!result.isOk()) {
                ALOGE("Error delivering end of stream event");
            }
        }

        return false;
    } else {
        // Keep a record of this frame so we can clean up if we have to in case of client death
        mFramesHeld.emplace_back(bufDesc);

        // v1.0 client uses an old frame-delivery mechanism.
        if (mStream_1_1 == nullptr) {
            // Forward a frame to v1.0 client
            BufferDesc_1_0 frame_1_0 = {};
            const AHardwareBuffer_Desc* pDesc =
                reinterpret_cast<const AHardwareBuffer_Desc *>(&bufDesc.buffer.description);
            frame_1_0.width     = pDesc->width;
            frame_1_0.height    = pDesc->height;
            frame_1_0.format    = pDesc->format;
            frame_1_0.usage     = pDesc->usage;
            frame_1_0.stride    = pDesc->stride;
            frame_1_0.memHandle = bufDesc.buffer.nativeHandle;
            frame_1_0.pixelSize = bufDesc.pixelSize;
            frame_1_0.bufferId  = bufDesc.bufferId;

            mStream->deliverFrame(frame_1_0);
        }

        return true;
    }
}


bool VirtualCamera::notify(const EvsEventDesc& event) {
    switch(event.aType) {
        case EvsEventType::STREAM_STOPPED:
            if (mStreamState != STOPPING) {
                // Warn if we got an unexpected stream termination
                ALOGW("Stream unexpectedly stopped, current status 0x%X", mStreamState);
            }

            // Mark the stream as stopped.
            mStreamState = STOPPED;

            if (mStream_1_1 == nullptr) {
                // Send a null frame instead, for v1.0 client
                BufferDesc_1_0 nullBuff = {};
                auto result = mStream->deliverFrame(nullBuff);
                if (!result.isOk()) {
                    ALOGE("Error delivering end of stream marker");
                }
            }
            break;

        // v1.0 client will ignore all other events.
        case EvsEventType::PARAMETER_CHANGED:
            ALOGD("A camera parameter 0x%X is set to 0x%X", event.payload[0], event.payload[1]);
            break;

        case EvsEventType::MASTER_RELEASED:
            ALOGD("The master client has been released");
            break;

        default:
            ALOGE("Unknown event id 0x%X", event.aType);
            break;
    }

    if (mStream_1_1 != nullptr) {
        // Forward a received event to the v1.1 client
        auto result = mStream_1_1->notify(event);
        if (!result.isOk()) {
            ALOGE("Failed to forward an event");
            return false;
        }
    }

    return true;
}


// Methods from ::android::hardware::automotive::evs::V1_0::IEvsCamera follow.
Return<void> VirtualCamera::getCameraInfo(getCameraInfo_cb info_cb) {
    // Straight pass through to hardware layer
    return mHalCamera->getHwCamera()->getCameraInfo(info_cb);
}


Return<EvsResult> VirtualCamera::setMaxFramesInFlight(uint32_t bufferCount) {
    // How many buffers are we trying to add (or remove if negative)
    int bufferCountChange = bufferCount - mFramesAllowed;

    // Ask our parent for more buffers
    bool result = mHalCamera->changeFramesInFlight(bufferCountChange);
    if (!result) {
        ALOGE("Failed to change buffer count by %d to %d", bufferCountChange, bufferCount);
        return EvsResult::BUFFER_NOT_AVAILABLE;
    }

    // Update our notion of how many frames we're allowed
    mFramesAllowed = bufferCount;
    return EvsResult::OK;
}


Return<EvsResult> VirtualCamera::startVideoStream(const ::android::sp<IEvsCameraStream_1_0>& stream)  {
    // We only support a single stream at a time
    if (mStreamState != STOPPED) {
        ALOGE("ignoring startVideoStream call when a stream is already running.");
        return EvsResult::STREAM_ALREADY_RUNNING;
    }

    // Validate our held frame count is starting out at zero as we expect
    assert(mFramesHeld.size() == 0);

    // Record the user's callback for use when we have a frame ready
    mStream = stream;
    mStream_1_1 = IEvsCameraStream_1_1::castFrom(stream).withDefault(nullptr);
    if (mStream_1_1 == nullptr) {
        ALOGI("Start video stream for v1.0 client.");
    } else {
        ALOGI("Start video stream for v1.1 client.");
    }

    mStreamState = RUNNING;

    // Tell the underlying camera hardware that we want to stream
    Return<EvsResult> result = mHalCamera->clientStreamStarting();
    if ((!result.isOk()) || (result != EvsResult::OK)) {
        // If we failed to start the underlying stream, then we're not actually running
        mStream = mStream_1_1 = nullptr;
        mStreamState = STOPPED;
        return EvsResult::UNDERLYING_SERVICE_ERROR;
    }

    // Start a thread that waits on the fence and forwards collected frames
    // to the v1.1 client.
    if (mStream_1_1 != nullptr) {
        mCaptureThread = std::thread([this]() {
            constexpr int kFrameTimeoutMs = 500;
            while (mStreamState == RUNNING) {
                UniqueFence fence = mHalCamera->requestNewFrame(this);
                if (fence.Wait(kFrameTimeoutMs) < 0) {
                    ALOGE("%p: Camera hangs? %s", this, strerror(errno));
                } else {
                    // Fetch frames and forward to the client
                    if (mFramesHeld.size() > 0 && mStream_1_1 != nullptr) {
                        // Pass this buffer through to our client
                        hardware::hidl_vec<BufferDesc_1_1> frames;
                        frames.resize(1);
                        frames[0] = mFramesHeld.back();
                        mStream_1_1->deliverFrame_1_1(frames);
                    }
                }
            }
        });
    }

    // TODO(changyeon):
    // Detect and exit if we encounter a stalled stream or unresponsive driver?
    // Consider using a timer and watching for frame arrival?

    return EvsResult::OK;
}


Return<void> VirtualCamera::doneWithFrame(const BufferDesc_1_0& buffer) {
    if (buffer.memHandle == nullptr) {
        ALOGE("ignoring doneWithFrame called with invalid handle");
    } else {
        // Find this buffer in our "held" list
        auto it = mFramesHeld.begin();
        while (it != mFramesHeld.end()) {
            if (it->bufferId == buffer.bufferId) {
                // found it!
                break;
            }
            ++it;
        }
        if (it == mFramesHeld.end()) {
            // We should always find the frame in our "held" list
            ALOGE("Ignoring doneWithFrame called with unrecognized frameID %d", buffer.bufferId);
        } else {
            // Take this frame out of our "held" list
            mFramesHeld.erase(it);

            // Tell our parent that we're done with this buffer
            mHalCamera->doneWithFrame(buffer);
        }
    }

    return Void();
}


Return<void> VirtualCamera::stopVideoStream()  {
    if (mStreamState == RUNNING) {
        // Tell the frame delivery pipeline we don't want any more frames
        mStreamState = STOPPING;

        // Join a thread
        if (mCaptureThread.joinable()) {
            mCaptureThread.join();
        }

        // Deliver an empty frame to close out the frame stream
        if (mStream_1_1 != nullptr) {
            // v1.1 client waits for a stream stopped event
            EvsEventDesc event;
            event.aType = EvsEventType::STREAM_STOPPED;
            auto result = mStream_1_1->notify(event);
            if (!result.isOk()) {
                ALOGE("Error delivering end of stream event");
            }
        } else {
            // v1.0 client expects a null frame at the end of the stream
            BufferDesc_1_0 nullBuff = {};
            auto result = mStream->deliverFrame(nullBuff);
            if (!result.isOk()) {
                ALOGE("Error delivering end of stream marker");
            }
        }

        // Since we are single threaded, no frame can be delivered while this function is running,
        // so we can go directly to the STOPPED state here on the server.
        // Note, however, that there still might be frames already queued that client will see
        // after returning from the client side of this call.
        mStreamState = STOPPED;

        // Give the underlying hardware camera the heads up that it might be time to stop
        mHalCamera->clientStreamEnding();
    }

    return Void();
}


Return<int32_t> VirtualCamera::getExtendedInfo(uint32_t opaqueIdentifier)  {
    // Pass straight through to the hardware device
    return mHalCamera->getHwCamera()->getExtendedInfo(opaqueIdentifier);
}


Return<EvsResult> VirtualCamera::setExtendedInfo(uint32_t opaqueIdentifier, int32_t opaqueValue)  {
    // Pass straight through to the hardware device
    return mHalCamera->getHwCamera()->setExtendedInfo(opaqueIdentifier, opaqueValue);
}


// Methods from ::android::hardware::automotive::evs::V1_1::IEvsCamera follow.
Return<void> VirtualCamera::getCameraInfo_1_1(getCameraInfo_1_1_cb info_cb) {
    // Straight pass through to hardware layer
    auto hwCamera_1_1 =
        IEvsCamera_1_1::castFrom(mHalCamera->getHwCamera()).withDefault(nullptr);
    if (hwCamera_1_1 != nullptr) {
        return hwCamera_1_1->getCameraInfo_1_1(info_cb);
    } else {
        // Return an empty list
        CameraDesc nullCamera = {};
        info_cb(nullCamera);
        return Void();
    }
}


Return<EvsResult> VirtualCamera::doneWithFrame_1_1(
    const hardware::hidl_vec<BufferDesc_1_1>& buffers) {

    for (auto&& buffer : buffers) {
        if (buffer.buffer.nativeHandle == nullptr) {
            ALOGW("ignoring doneWithFrame called with invalid handle");
        } else {
            // Find this buffer in our "held" list
            auto it = mFramesHeld.begin();
            while (it != mFramesHeld.end()) {
                if (it->bufferId == buffer.bufferId) {
                    // found it!
                    break;
                }
                ++it;
            }
            if (it == mFramesHeld.end()) {
                // We should always find the frame in our "held" list
                ALOGE("Ignoring doneWithFrame called with unrecognized frameID %d",
                      buffer.bufferId);
            } else {
                // Take this frame out of our "held" list
                mFramesHeld.erase(it);

                // Tell our parent that we're done with this buffer
                mHalCamera->doneWithFrame(buffer);
            }
        }
    }

    return EvsResult::OK;
}


Return<EvsResult> VirtualCamera::setMaster() {
    return mHalCamera->setMaster(this);
}


Return<EvsResult> VirtualCamera::forceMaster(const sp<IEvsDisplay>& display) {
    if (display.get() == nullptr) {
        ALOGE("%s: Passed display is invalid", __FUNCTION__);
        return EvsResult::INVALID_ARG;
    }

    DisplayState state = display->getDisplayState();
    if (state == DisplayState::NOT_OPEN ||
        state == DisplayState::DEAD ||
        state >= DisplayState::NUM_STATES) {
        ALOGE("%s: Passed display is in invalid state", __FUNCTION__);
        return EvsResult::INVALID_ARG;
    }

    return mHalCamera->forceMaster(this);
}


Return<EvsResult> VirtualCamera::unsetMaster() {
    return mHalCamera->unsetMaster(this);
}


Return<void> VirtualCamera::getParameterList(getParameterList_cb _hidl_cb) {
    // Straight pass through to hardware layer
    auto hwCamera_1_1 =
        IEvsCamera_1_1::castFrom(mHalCamera->getHwCamera()).withDefault(nullptr);
    if (hwCamera_1_1 != nullptr) {
        return hwCamera_1_1->getParameterList(_hidl_cb);
    } else {
        // Return an empty list
        hardware::hidl_vec<CameraParam> emptyList;
        _hidl_cb(emptyList);
        return Void();
    }
}


Return<void> VirtualCamera::getIntParameterRange(CameraParam id,
                                                 getIntParameterRange_cb _hidl_cb) {
    // Straight pass through to hardware layer
    auto hwCamera_1_1 =
        IEvsCamera_1_1::castFrom(mHalCamera->getHwCamera()).withDefault(nullptr);
    if (hwCamera_1_1 != nullptr) {
        return hwCamera_1_1->getIntParameterRange(id, _hidl_cb);
    } else {
        // Return [0, 0, 0]
        _hidl_cb(0, 0, 0);
        return Void();
    }
    return Void();
}


Return<void> VirtualCamera::setIntParameter(CameraParam id,
                                            int32_t value,
                                            setIntParameter_cb _hidl_cb) {
    EvsResult status = mHalCamera->setParameter(this, id, value);

    hardware::hidl_vec<int32_t> values;
    values.resize(1);
    values[0] = value;
    _hidl_cb(status, values);

    return Void();
}


Return<void> VirtualCamera::getIntParameter(CameraParam id,
                                            getIntParameter_cb _hidl_cb) {
    int32_t value;
    EvsResult status = mHalCamera->getParameter(id, value);

    hardware::hidl_vec<int32_t> values;
    values.resize(1);
    values[0] = value;
    _hidl_cb(status, values);

    return Void();
}


} // namespace implementation
} // namespace V1_1
} // namespace evs
} // namespace automotive
} // namespace android
