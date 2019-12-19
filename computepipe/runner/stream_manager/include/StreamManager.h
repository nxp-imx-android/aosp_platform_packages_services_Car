// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef COMPUTEPIPE_RUNNER_STREAM_MANAGER_H
#define COMPUTEPIPE_RUNNER_STREAM_MANAGER_H

#include <functional>
#include <memory>
#include <string>

#include "MemHandle.h"
#include "OutputConfig.pb.h"
#include "types/Status.h"

namespace android {
namespace automotive {
namespace computepipe {
namespace runner {
namespace stream_manager {

/**
 * Manager the operations of an output stream from the graph.
 * Should be constructed using the StreamManagerFactory.
 * The manager instance for a given stream depends on the streams
 * description specified in OuputConfig.
 */

class StreamManager {
  public:
    enum State {
        /* State on construction. */
        RESET = 0,
        /* State once inflight packets set */
        CONFIG_DONE = 1,
        /* State once packets are */
        RUNNING = 2,
        /**
         * State once stop issued
         * Returns to config done, once all in flight packets handled.
         */
        STOPPED = 3,
    };
    /* Constructor for StreamManager Base class */
    explicit StreamManager(std::string streamName, const proto::PacketType& type)
        : mName(streamName), mType(type) {
    }
    /**
     * IPC dispatch routine invoked by Stream manager, to send output packet to client.
     * Callback must be set before any client specific configs are applied.
     */
    virtual Status setIpcDispatchCallback(std::function<Status(std::shared_ptr<MemHandle>)>& cb) = 0;
    /* Retrieves the current state */
    State getState() {
        return mState;
    }
    /* Sets max in flight packets based on client specification */
    virtual Status setMaxInFlightPackets(uint32_t maxPackets) = 0;
    /* Starts stream manager */
    virtual Status start() = 0;
    /* Stops stream manager */
    virtual Status stop(bool flush) = 0;
    /* Initiates cleanup. Forgets maxinflightPackets */
    virtual Status cleanup() = 0;
    /* Frees previously dispatched packet. Once client has confirmed usage */
    virtual Status freePacket(const std::shared_ptr<MemHandle>& memhandle) = 0;
    /* Queue's packet produced by graph stream */
    virtual Status queuePacket(const char* data, const uint32_t size, uint64_t timestamp) = 0;
    /* Destructor */
    virtual ~StreamManager() = default;

  protected:
    std::string mName;
    proto::PacketType mType;
    State mState = RESET;
};

/**
 * Factory for generating stream manager instances
 */
class StreamManagerFactory {
  public:
    std::unique_ptr<StreamManager> getStreamManager(const proto::OutputConfig& config);
    StreamManagerFactory(const StreamManagerFactory&) = delete;
    StreamManagerFactory& operator=(const StreamManagerFactory&) = delete;
    StreamManagerFactory() = default;
};

}  // namespace stream_manager
}  // namespace runner
}  // namespace computepipe
}  // namespace automotive
}  // namespace android

#endif
