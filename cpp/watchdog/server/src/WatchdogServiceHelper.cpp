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

#define LOG_TAG "carwatchdogd"

#include "WatchdogServiceHelper.h"

#include "ServiceManager.h"
#include "WatchdogProcessService.h"

#include <android/automotive/watchdog/internal/BnCarWatchdogServiceForSystem.h>

namespace android {
namespace automotive {
namespace watchdog {

namespace aawi = android::automotive::watchdog::internal;

using android::IBinder;
using android::sp;
using android::wp;
using android::automotive::watchdog::internal::BnCarWatchdogServiceForSystem;
using android::automotive::watchdog::internal::ICarWatchdogServiceForSystem;
using android::base::Error;
using android::base::Result;
using android::binder::Status;

namespace {

Status fromExceptionCode(int32_t exceptionCode, std::string message) {
    ALOGW("%s.", message.c_str());
    return Status::fromExceptionCode(exceptionCode, message.c_str());
}

}  // namespace

Result<void> WatchdogServiceHelper::init(const sp<WatchdogProcessService>& watchdogProcessService) {
    if (watchdogProcessService == nullptr) {
        return Error() << "Must provide a non-null watchdog process service instance";
    }
    mWatchdogProcessService = watchdogProcessService;
    return mWatchdogProcessService->registerWatchdogServiceHelper(this);
}

WatchdogServiceHelper::~WatchdogServiceHelper() {
    terminate();
}

Status WatchdogServiceHelper::registerService(
        const android::sp<ICarWatchdogServiceForSystem>& service) {
    std::unique_lock writeLock(mRWMutex);
    if (mWatchdogProcessService == nullptr) {
        return Status::fromExceptionCode(Status::EX_ILLEGAL_STATE,
                                         "Must initialize watchdog service helper before "
                                         "registering car watchdog service");
    }
    sp<IBinder> curBinder = BnCarWatchdogServiceForSystem::asBinder(mService);
    sp<IBinder> newBinder = BnCarWatchdogServiceForSystem::asBinder(service);
    if (mService != nullptr && curBinder == newBinder) {
        return Status::ok();
    }
    status_t ret = newBinder->linkToDeath(this);
    if (ret != OK) {
        return Status::fromExceptionCode(Status::EX_ILLEGAL_STATE,
                                         "Failed to register car watchdog service as it is dead");
    }
    unregisterServiceLocked();
    Status status = mWatchdogProcessService->registerCarWatchdogService(newBinder);
    if (!status.isOk()) {
        newBinder->unlinkToDeath(this);
        return status;
    }
    mService = service;
    return Status::ok();
}

Status WatchdogServiceHelper::unregisterService(const sp<ICarWatchdogServiceForSystem>& service) {
    std::unique_lock writeLock(mRWMutex);
    sp<IBinder> binder = BnCarWatchdogServiceForSystem::asBinder(service);
    if (binder != BnCarWatchdogServiceForSystem::asBinder(mService)) {
        return fromExceptionCode(Status::EX_ILLEGAL_ARGUMENT,
                                 "Failed to unregister car watchdog service as it is not "
                                 "registered");
    }
    unregisterServiceLocked();
    return Status::ok();
}

void WatchdogServiceHelper::binderDied(const wp<android::IBinder>& who) {
    std::unique_lock writeLock(mRWMutex);
    IBinder* diedBinder = who.unsafe_get();
    sp<IBinder> curBinder = BnCarWatchdogServiceForSystem::asBinder(mService);
    if (curBinder == nullptr || diedBinder != curBinder) {
        return;
    }
    ALOGW("Car watchdog service had died.");
    mService.clear();
    mWatchdogProcessService->unregisterCarWatchdogService(curBinder);
}

void WatchdogServiceHelper::terminate() {
    std::unique_lock writeLock(mRWMutex);
    unregisterServiceLocked();
    mWatchdogProcessService.clear();
}

Status WatchdogServiceHelper::checkIfAlive(const wp<IBinder>& who, int32_t sessionId,
                                           TimeoutLength timeout) const {
    sp<ICarWatchdogServiceForSystem> service;
    {
        std::shared_lock readLock(mRWMutex);
        if (mService == nullptr ||
            who.unsafe_get() != BnCarWatchdogServiceForSystem::asBinder(mService)) {
            return fromExceptionCode(Status::EX_ILLEGAL_ARGUMENT,
                                     "Dropping checkIfAlive request as the given car watchdog "
                                     "service "
                                     "binder isn't registered");
        }
        service = mService;
    }
    return service->checkIfAlive(sessionId, static_cast<aawi::TimeoutLength>(timeout));
}

Status WatchdogServiceHelper::prepareProcessTermination(const wp<IBinder>& who) {
    sp<ICarWatchdogServiceForSystem> service;
    {
        std::unique_lock writeLock(mRWMutex);
        if (mService == nullptr ||
            who.unsafe_get() != BnCarWatchdogServiceForSystem::asBinder(mService)) {
            return fromExceptionCode(Status::EX_ILLEGAL_ARGUMENT,
                                     "Dropping prepareProcessTermination request as the given "
                                     "car watchdog service binder isn't registered");
        }
        service = mService;
    }
    Status status = service->prepareProcessTermination();
    if (status.isOk()) {
        unregisterServiceLocked();
    }
    return status;
}

void WatchdogServiceHelper::unregisterServiceLocked() {
    if (mService == nullptr) return;
    sp<IBinder> binder = BnCarWatchdogServiceForSystem::asBinder(mService);
    binder->unlinkToDeath(this);
    mService.clear();
    mWatchdogProcessService->unregisterCarWatchdogService(binder);
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
