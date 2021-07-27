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

#include "SurroundViewAppCommon.h"
#include "SurroundViewService.h"
#include "SurroundViewCallback.h"

// libhidl:
using android::sp;
using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;
using android::hardware::Return;
using android::hardware::automotive::evs::V1_0::EvsResult;

using namespace android::hardware::automotive::sv::V1_0;
using namespace android::hardware::automotive::evs::V1_1;
using namespace android::hardware::automotive::sv::V1_0::implementation;
using namespace android::hardware::automotive::sv::app;

#define SURROUND_VIEW_LIBRARY

// Main entry point
int main(int argc, char** argv) {
    // Start up
    LOG(INFO) << "SV app starting";

    DemoMode mode = UNKNOWN;
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--use2d") == 0) {
            mode = DEMO_2D;
        } else if (strcmp(argv[i], "--use3d") == 0) {
            mode = DEMO_3D;
        } else {
            LOG(WARNING) << "Ignoring unrecognized command line arg: " << argv[i];
        }
    }

    if (mode == UNKNOWN) {
        LOG(ERROR) << "No demo mode is specified. Exiting";
        return EXIT_FAILURE;
    }

    // Set thread pool size to one to avoid concurrent events from the HAL.
    // This pool will handle the SurroundViewStream callbacks.
    configureRpcThreadpool(1, false /* callerWillJoin */);

    // Try to connect to EVS service
    LOG(INFO) << "Acquiring EVS Enumerator";
    sp<IEvsEnumerator> evs = IEvsEnumerator::getService();
    if (evs == nullptr) {
        LOG(ERROR) << "getService(default) returned NULL.  Exiting.";
        return EXIT_FAILURE;
    }

    // Create a new instance of the SurroundViewService.
    LOG(INFO) << "Acquiring SV Service";
    android::sp<ISurroundViewService> surroundViewService = SurroundViewService::getInstance();

    if (surroundViewService == nullptr) {
        LOG(ERROR) << "getService(default) returned NULL.";
        return EXIT_FAILURE;
    } else {
        LOG(INFO) << "Get ISurroundViewService default";
    }

    // Connect to evs display
    // getDisplayIdList returns a vector of uint64_t, so any valid display id is
    // guaranteed to be non-negative.
    int displayId = -1;
    evs->getDisplayIdList([&displayId](auto idList) {
        if (idList.size() > 0) {
            displayId = idList[0];
        }
    });
    if (displayId == -1) {
        LOG(ERROR) << "Cannot get a valid display";
        return EXIT_FAILURE;
    }

    LOG(INFO) << "Acquiring EVS Display with ID: " << displayId;
    sp<IEvsDisplay> display = evs->openDisplay_1_1(displayId);
    if (display == nullptr) {
        LOG(ERROR) << "EVS Display unavailable.  Exiting.";
        return EXIT_FAILURE;
    }

    if (mode == DEMO_2D) {
        if (!run2dSurroundView(surroundViewService, display)) {
            LOG(ERROR) << "Something went wrong in 2d surround view demo. "
                       << "Exiting.";
            return EXIT_FAILURE;
        }
    } else if (mode == DEMO_3D) {
        if (!run3dSurroundView(surroundViewService, display)) {
            LOG(ERROR) << "Something went wrong in 3d surround view demo. "
                       << "Exiting.";
            return EXIT_FAILURE;
        }
    }

    evs->closeDisplay(display);

    LOG(DEBUG) << "SV sample app finished running successfully";
    return EXIT_SUCCESS;
}
