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

#ifndef CPP_WATCHDOG_SERVER_TESTS_MOCKWATCHDOGSERVICEHELPER_H_
#define CPP_WATCHDOG_SERVER_TESTS_MOCKWATCHDOGSERVICEHELPER_H_

#include "WatchdogProcessService.h"
#include "WatchdogServiceHelper.h"

#include <android-base/result.h>
#include <android/automotive/watchdog/TimeoutLength.h>
#include <android/automotive/watchdog/internal/ICarWatchdogServiceForSystem.h>
#include <binder/Status.h>
#include <utils/StrongPointer.h>

namespace android {
namespace automotive {
namespace watchdog {

class MockWatchdogServiceHelper : public WatchdogServiceHelper {
public:
    MockWatchdogServiceHelper() {}
    ~MockWatchdogServiceHelper() {}

    MOCK_METHOD(android::base::Result<void>, init,
                (const android::sp<WatchdogProcessService>& watchdogProcessService), (override));
    MOCK_METHOD(android::binder::Status, registerService,
                (const sp<android::automotive::watchdog::internal::ICarWatchdogServiceForSystem>&
                         service),
                (override));
    MOCK_METHOD(android::binder::Status, unregisterService,
                (const sp<android::automotive::watchdog::internal::ICarWatchdogServiceForSystem>&
                         service),
                (override));

    MOCK_METHOD(android::binder::Status, checkIfAlive,
                (const android::wp<android::IBinder>& who, int32_t sessionId,
                 TimeoutLength timeout),
                (const, override));
    MOCK_METHOD(android::binder::Status, prepareProcessTermination,
                (const android::wp<android::IBinder>& who), (override));

    MOCK_METHOD(void, terminate, (), (override));
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  //  CPP_WATCHDOG_SERVER_TESTS_MOCKWATCHDOGSERVICEHELPER_H_
