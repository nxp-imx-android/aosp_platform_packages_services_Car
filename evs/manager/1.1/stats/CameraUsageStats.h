/*
 * Copyright 2020 The Android Open Source Project
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

#ifndef ANDROID_AUTOMOTIVE_EVS_V1_1_CAMERAUSAGESTATS_H
#define ANDROID_AUTOMOTIVE_EVS_V1_1_CAMERAUSAGESTATS_H

#include <inttypes.h>

#include <android-base/stringprintf.h>
#include <utils/Mutex.h>
#include <utils/RefBase.h>
#include <utils/SystemClock.h>

namespace android {
namespace automotive {
namespace evs {
namespace V1_1 {
namespace implementation {

struct CameraUsageStatsRecord {
public:
    // Time a snapshot is generated
    nsecs_t timestamp;

    // Total number of frames received
    int64_t framesReceived;

    // Total number of frames returned to EVS HAL
    int64_t framesReturned;

    // Number of frames ignored because no clients are listening
    int64_t framesIgnored;

    // Number of frames skipped to synchronize camera frames
    int64_t framesSkippedToSync;

    // Roundtrip latency of the very first frame after the stream started.
    int64_t framesFirstRoundtripLatency;

    // Peak mFrame roundtrip latency
    int64_t framesPeakRoundtripLatency;

    // Average mFrame roundtrip latency
    double  framesAvgRoundtripLatency;

    // Number of the erroneous streaming events
    int32_t erroneousEventsCount;

    // Peak number of active clients
    int32_t peakClientsCount;

    // Calculates a delta between two records
    CameraUsageStatsRecord& operator-=(const CameraUsageStatsRecord& rhs) {
        // Only calculates differences in the frame statistics
        framesReceived = framesReceived - rhs.framesReceived;
        framesReturned = framesReturned - rhs.framesReturned;
        framesIgnored = framesIgnored - rhs.framesIgnored;
        framesSkippedToSync = framesSkippedToSync - rhs.framesSkippedToSync;
        erroneousEventsCount = erroneousEventsCount - rhs.erroneousEventsCount;

        return *this;
    }

    friend CameraUsageStatsRecord operator-(CameraUsageStatsRecord lhs,
                                      const CameraUsageStatsRecord& rhs) noexcept {
        lhs -= rhs; // reuse compound assignment
        return lhs;
    }

    // Constructs a string that shows collected statistics
    std::string toString(const char* indent = "") const {
        std::string buffer;
        android::base::StringAppendF(&buffer,
                "%sTime Collected: @%" PRId64 "ms\n"
                "%sFrames Received: %" PRId64 "\n"
                "%sFrames Returned: %" PRId64 "\n"
                "%sFrames Ignored : %" PRId64 "\n"
                "%sFrames Skipped To Sync: %" PRId64 "\n\n",
                indent, ns2ms(timestamp),
                indent, framesReceived,
                indent, framesReturned,
                indent, framesIgnored,
                indent, framesSkippedToSync);

        return buffer;
    }
};


class CameraUsageStats : public RefBase {
public:
    CameraUsageStats()
        : mMutex(Mutex()),
          mTimeCreatedMs(android::uptimeMillis()),
          mStats({}) {}

private:
    // Mutex to protect a collection record
    mutable Mutex mMutex;

    // Time this object was created
    int64_t mTimeCreatedMs;

    // Usage statistics to collect
    CameraUsageStatsRecord mStats GUARDED_BY(mMutex);

public:
    void framesReceived(int n = 1) EXCLUDES(mMutex);
    void framesReturned(int n = 1) EXCLUDES(mMutex);
    void framesIgnored(int n = 1) EXCLUDES(mMutex);
    void framesSkippedToSync(int n = 1) EXCLUDES(mMutex);
    void eventsReceived() EXCLUDES(mMutex);
    int64_t getTimeCreated() const EXCLUDES(mMutex);
    int64_t getFramesReceived() const EXCLUDES(mMutex);
    int64_t getFramesReturned() const EXCLUDES(mMutex);
    CameraUsageStatsRecord snapshot() const EXCLUDES(mMutex);

    // Generates a string with current statistics
    static std::string toString(const CameraUsageStatsRecord& record, const char* indent = "");
};


} // namespace implementation
} // namespace V1_1
} // namespace evs
} // namespace automotive
} // namespace android

#endif // ANDROID_AUTOMOTIVE_EVS_V1_1_CAMERAUSAGESTATS_H
