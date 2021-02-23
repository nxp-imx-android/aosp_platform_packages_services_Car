/*
 * Copyright (c) 2021, The Android Open Source Project
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

#include "CarPowerPolicyServer.h"
#include "SilentModeHandler.h"

#include <android-base/chrono_utils.h>
#include <android-base/file.h>
#include <android-base/strings.h>
#include <gmock/gmock.h>
#include <utils/StrongPointer.h>

#include <unistd.h>

#include <cstring>

namespace android {
namespace frameworks {
namespace automotive {
namespace powerpolicy {

using ::android::sp;
using ::android::base::ReadFileToString;
using ::android::base::Trim;
using ::android::base::WriteStringToFd;
using ::android::binder::Status;
using ::testing::_;

namespace {

constexpr const char* kBootReasonNormal = "reboot,shell";

constexpr int kMaxPollingAttempts = 5;
constexpr std::chrono::microseconds kPollingDelayUs = 50ms;

bool waitForSilentMode(SilentModeHandler* handler, bool expectedSilentMode) {
    int count = 0;
    while (true) {
        if (handler->isSilentMode() == expectedSilentMode) {
            return true;
        }
        if (count++; count == kMaxPollingAttempts) {
            break;
        }
        usleep(kPollingDelayUs.count());
    }
    return false;
}

}  // namespace

namespace internal {

class SilentModeHandlerPeer {
public:
    explicit SilentModeHandlerPeer(SilentModeHandler* handler) : mHandler(handler) {}

    ~SilentModeHandlerPeer() { mHandler->release(); }

    void init() {
        mHandler->mSilentModeHwStateFilename = mFileSilentModeHwState.path;
        mHandler->mKernelSilentModeFilename = mFileKernelSilentMode.path;
        mHandler->init();
    }

    void injectBootReason(const std::string& bootReason) { mHandler->mBootReason = bootReason; }

    void updateSilentModeHwState(bool isSilent) {
        WriteStringToFd(isSilent ? kValueSilentMode : kValueNonSilentMode,
                        mFileSilentModeHwState.fd);
    }

    std::string readKernelSilentMode() {
        std::string value;
        if (!ReadFileToString(mFileKernelSilentMode.path, &value)) {
            return "";
        }
        return Trim(value);
    }

private:
    SilentModeHandler* mHandler;
    TemporaryFile mFileSilentModeHwState;
    TemporaryFile mFileKernelSilentMode;
};

}  // namespace internal

class MockCarPowerPolicyServer : public ICarPowerPolicyServerInterface {
public:
    MOCK_METHOD(Status, getCurrentPowerPolicy, (CarPowerPolicy*), (override));
    MOCK_METHOD(Status, getPowerComponentState, (PowerComponent, bool*), (override));
    MOCK_METHOD(Status, registerPowerPolicyChangeCallback,
                (const sp<ICarPowerPolicyChangeCallback>&, const CarPowerPolicyFilter&),
                (override));
    MOCK_METHOD(Status, unregisterPowerPolicyChangeCallback,
                (const sp<ICarPowerPolicyChangeCallback>&), (override));
    MOCK_METHOD(void, notifySilentModeChange, (const bool), (override));
};

class SilentModeHandlerTest : public ::testing::Test {
public:
    SilentModeHandlerTest() { carPowerPolicyServer = new MockCarPowerPolicyServer(); }

    sp<MockCarPowerPolicyServer> carPowerPolicyServer;
};

TEST_F(SilentModeHandlerTest, TestSilentModeHwStateMonitoring) {
    SilentModeHandler handler(carPowerPolicyServer.get());
    internal::SilentModeHandlerPeer handlerPeer(&handler);
    handlerPeer.init();

    handlerPeer.updateSilentModeHwState(/*isSilent=*/true);

    ASSERT_TRUE(waitForSilentMode(&handler, /*expectedSilentMode=*/true))
            << "It should be silent mode when HW state is on";

    handlerPeer.updateSilentModeHwState(/*isSilent=*/false);

    ASSERT_TRUE(waitForSilentMode(&handler, /*expectedSilentMode=*/false))
            << "It should be non-silent mode when HW state is off";
}

TEST_F(SilentModeHandlerTest, TestRebootForForcedSilentMode) {
    SilentModeHandler handler(carPowerPolicyServer.get());
    internal::SilentModeHandlerPeer handlerPeer(&handler);
    handlerPeer.injectBootReason(kBootReasonForcedSilent);
    handlerPeer.init();

    ASSERT_TRUE(handler.isSilentMode())
            << "It should be silent mode when booting with forced silent mode";
    EXPECT_CALL(*carPowerPolicyServer, notifySilentModeChange(_)).Times(0);

    handlerPeer.updateSilentModeHwState(/*isSilent=*/false);

    ASSERT_TRUE(handler.isSilentMode())
            << "When booting with forced silent mode, silent mode should not change by HW state";
}

TEST_F(SilentModeHandlerTest, TestRebootForForcedNonSilentMode) {
    SilentModeHandler handler(carPowerPolicyServer.get());
    internal::SilentModeHandlerPeer handlerPeer(&handler);
    handlerPeer.injectBootReason(kBootReasonForcedNonSilent);
    handlerPeer.init();

    ASSERT_FALSE(handler.isSilentMode())
            << "It should be non-silent mode when booting with forced non-silent mode";

    handlerPeer.updateSilentModeHwState(/*isSilent=*/true);

    ASSERT_FALSE(handler.isSilentMode()) << "When booting with forced non-silent mode, silent mode "
                                            "should not change by HW state";
}

TEST_F(SilentModeHandlerTest, TestUpdateKernelSilentMode) {
    SilentModeHandler handler(carPowerPolicyServer.get());
    internal::SilentModeHandlerPeer handlerPeer(&handler);
    handlerPeer.init();

    handler.updateKernelSilentMode(true);

    ASSERT_EQ(handlerPeer.readKernelSilentMode(), kValueSilentMode)
            << "Kernel silent mode file should have 1";

    handler.updateKernelSilentMode(false);

    ASSERT_EQ(handlerPeer.readKernelSilentMode(), kValueNonSilentMode)
            << "Kernel silent mode file should have 0";
}

}  // namespace powerpolicy
}  // namespace automotive
}  // namespace frameworks
}  // namespace android
