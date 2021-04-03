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

#include "TelemetryServer.h"

#include <android-base/logging.h>

using ::android::automotive::telemetry::TelemetryServer;

// TODO(b/174608802): handle SIGQUIT/SIGTERM

int main(void) {
    LOG(INFO) << "Starting cartelemetryd";

    TelemetryServer server;

    // Register AIDL services. Aborts the server if fails.
    server.registerServices();

    LOG(VERBOSE) << "Service is created, joining the threadpool";
    server.startAndJoinThreadPool();
    return 1;  // never reaches
}
