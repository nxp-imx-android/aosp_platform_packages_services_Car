/*
 * Copyright 2019 The Android Open Source Project
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

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "OutputConfig.pb.h"
#include "StreamManager.h"
#include "gmock/gmock-matchers.h"
#include "types/Status.h"

using namespace android::automotive::computepipe::runner::stream_manager;
using namespace android::automotive::computepipe;

class SemanticManagerTest : public ::testing::Test {
  protected:
    static constexpr uint32_t kMaxSemanticDataSize = 1024;
    /**
     * Setup for the test fixture to initialize the semantic manager
     * After this, the semantic manager should be in RESET state.
     */
    void SetUp() override {
        proto::OutputConfig config;
        config.set_type(proto::PacketType::SEMANTIC_DATA);
        config.set_stream_name("semantic_stream");
        mStreamManager = mFactory.getStreamManager(config);
        std::function<Status(std::shared_ptr<MemHandle>)> cb =
            [this](std::shared_ptr<MemHandle> handle) -> Status {
            this->mPacketSize = handle->getSize();
            this->mCurrentPacket = (char*)malloc(this->mPacketSize);
            memcpy(mCurrentPacket, handle->getData(), this->mPacketSize);

            return SUCCESS;
        };
        ASSERT_EQ(mStreamManager->setIpcDispatchCallback(cb), Status::SUCCESS);
    }
    void deleteCurrentPacket() {
        if (mCurrentPacket) {
            free(mCurrentPacket);
            mCurrentPacket = nullptr;
        }
    }
    void TearDown() override {
        if (mCurrentPacket) {
            free(mCurrentPacket);
        }
        mStreamManager = nullptr;
    }
    std::unique_ptr<StreamManager> mStreamManager;
    StreamManagerFactory mFactory;
    char* mCurrentPacket = nullptr;
    uint32_t mPacketSize;
};

/**
 * Checks the ability to start semantic stream management
 * without config state
 */
TEST_F(SemanticManagerTest, NoConfigTest) {
    EXPECT_EQ(mStreamManager->start(), Status::ILLEGAL_STATE);
    mStreamManager->setMaxInFlightPackets(0);
    EXPECT_EQ(mStreamManager->start(), Status::SUCCESS);
}

/**
 * Checks Packet Queing without start.
 * Checks Packet Queuing with bad arguments.
 * Checks successful packet queuing.
 */
TEST_F(SemanticManagerTest, PacketQueueTest) {
    ASSERT_EQ(mStreamManager->setMaxInFlightPackets(0), Status::SUCCESS);
    ASSERT_EQ(mStreamManager->start(), Status::SUCCESS);
    std::string fakeData("FakeData");
    uint32_t size = fakeData.size();
    EXPECT_EQ(mStreamManager->queuePacket(nullptr, size, 0), Status::INVALID_ARGUMENT);
    EXPECT_EQ(mStreamManager->queuePacket(fakeData.c_str(), kMaxSemanticDataSize + 1, 0),
              Status::INVALID_ARGUMENT);
    EXPECT_EQ(mStreamManager->queuePacket(fakeData.c_str(), size, 0), Status::SUCCESS);
    EXPECT_STREQ(mCurrentPacket, fakeData.c_str());
}
