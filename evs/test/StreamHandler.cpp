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

#define LOG_TAG "EvsTest"

#include "StreamHandler.h"

#include <stdio.h>
#include <string.h>

#include <android/log.h>
#include <cutils/native_handle.h>
#include <ui/GraphicBufferMapper.h>

#include <algorithm>    // std::min


StreamHandler::StreamHandler(android::sp <IEvsCamera>  pCamera,  CameraDesc  cameraInfo,
                             android::sp <IEvsDisplay> pDisplay, DisplayDesc displayInfo) :
    mCamera(pCamera),
    mCameraInfo(cameraInfo),
    mDisplay(pDisplay),
    mDisplayInfo(displayInfo) {

    // Post a warning message if resolutions don't match since we handle it, but only
    // with simple/ugly results in copyBufferContents below.
    if ((mDisplayInfo.defaultHorResolution != cameraInfo.defaultHorResolution) ||
        (mDisplayInfo.defaultVerResolution != cameraInfo.defaultVerResolution)) {
        ALOGW("Camera and Display resolutions don't match -- images will be clipped");
    }
}


void StreamHandler::startStream() {
    // Mark ourselves as running
    mLock.lock();
    mRunning = true;
    mLock.unlock();

    // Tell the camera to start streaming
    mCamera->startVideoStream(this);
}


void StreamHandler::asyncStopStream() {
    // Tell the camera to stop streaming.
    // This will result in a null frame being delivered when the stream actually stops.
    mCamera->stopVideoStream();
}


void StreamHandler::blockingStopStream() {
    // Tell the stream to stop
    asyncStopStream();

    // Wait until the stream has actually stopped
    std::unique_lock<std::mutex> lock(mLock);
    mSignal.wait(lock, [this](){ return !mRunning; });
}


bool StreamHandler::isRunning() {
    std::unique_lock<std::mutex> lock(mLock);
    return mRunning;
}


Return<void> StreamHandler::deliverFrame(const BufferDesc& buffer) {
    ALOGD("Received a frame from the camera (%p)", buffer.memHandle.getNativeHandle());

    if (buffer.memHandle.getNativeHandle() == nullptr) {
        ALOGD("Got end of stream notification");

        // Signal that the last frame has been received and the stream is stopped
        mLock.lock();
        mRunning = false;
        mLock.unlock();
        mSignal.notify_all();

        ALOGI("End of stream signaled");
    } else {
        // Get the output buffer we'll use to display the imagery
        BufferDesc tgtBuffer = {};
        mDisplay->getTargetBuffer([&tgtBuffer]
                                  (const BufferDesc& buff) {
                                      tgtBuffer = buff;
                                      tgtBuffer.memHandle = native_handle_clone(buff.memHandle);
                                      ALOGD("Got output buffer (%p) with id %d cloned as (%p)",
                                            buff.memHandle.getNativeHandle(),
                                            tgtBuffer.bufferId,
                                            tgtBuffer.memHandle.getNativeHandle());
                                  }
        );

        if (tgtBuffer.memHandle == nullptr) {
            ALOGE("Didn't get requested output buffer -- skipping this frame.");
        } else {
            // Copy the contents of the of buffer.memHandle into tgtBuffer
            copyBufferContents(tgtBuffer, buffer);

            // TODO:  Add a bit of overlay graphics?
            // TODO:  Use OpenGL to render from texture?
            // NOTE:  If we mess with the frame contents, we'll need to update the frame inspection
            //        logic in the default (test) display driver.

            // Send the target buffer back for display
            ALOGD("Calling returnTargetBufferForDisplay (%p)",
                  tgtBuffer.memHandle.getNativeHandle());
            Return<EvsResult> result = mDisplay->returnTargetBufferForDisplay(tgtBuffer);
            if (!result.isOk()) {
                ALOGE("Error making the remote function call.  HIDL said %s",
                      result.description().c_str());
            }
            if (result != EvsResult::OK) {
                ALOGE("We encountered error %d when returning a buffer to the display!",
                      (EvsResult)result);
            }

            // Now release our copy of the handle
            // TODO:  If we don't end up needing to pass it back, then close our handle earlier
            // As it stands, the buffer might still be held by this process for some time after
            // it gets returned to the server via returnTargetBufferForDisplay()
            // Could/should this be fixed by "exporting" the tgtBuffer before returning it?
            native_handle_close(tgtBuffer.memHandle);
            native_handle_delete(const_cast<native_handle*>(tgtBuffer.memHandle.getNativeHandle()));
        }

        // Send the camera buffer back now that we're done with it
        ALOGD("Calling doneWithFrame");
        mCamera->doneWithFrame(buffer);

        ALOGD("Frame handling complete");
    }

    return Void();
}


bool StreamHandler::copyBufferContents(const BufferDesc& tgtBuffer,
                                       const BufferDesc& srcBuffer) {
    bool success = true;

    // Make sure we don't run off the end of either buffer
    const unsigned width     = std::min(tgtBuffer.width,
                                        srcBuffer.width);
    const unsigned height    = std::min(tgtBuffer.height,
                                        srcBuffer.height);

    android::GraphicBufferMapper &mapper = android::GraphicBufferMapper::get();


    // Lock our source buffer for reading
    unsigned char* srcPixels = nullptr;
    mapper.registerBuffer(srcBuffer.memHandle);
    mapper.lock(srcBuffer.memHandle,
                GRALLOC_USAGE_SW_READ_OFTEN,
                android::Rect(width, height),
                (void **) &srcPixels);

    // Lock our target buffer for writing
    unsigned char* tgtPixels = nullptr;
    mapper.registerBuffer(tgtBuffer.memHandle);
    mapper.lock(tgtBuffer.memHandle,
                GRALLOC_USAGE_SW_WRITE_OFTEN,
                android::Rect(width, height),
                (void **) &tgtPixels);

    if (srcPixels && tgtPixels) {
        for (unsigned row = 0; row < height; row++) {
            // Copy the entire row of pixel data
            memcpy(tgtPixels, srcPixels, width * sizeof(*srcPixels));

            // Advance to the next row
            tgtPixels += tgtBuffer.stride;
            srcPixels += srcBuffer.stride;
        }
    } else {
        ALOGE("Failed to copy buffer contents");
        success = false;
    }

    if (srcPixels) {
        mapper.unlock(srcBuffer.memHandle);
    }
    if (tgtPixels) {
        mapper.unlock(tgtBuffer.memHandle);
    }
    mapper.unregisterBuffer(srcBuffer.memHandle);
    mapper.unregisterBuffer(tgtBuffer.memHandle);

    return success;
}
