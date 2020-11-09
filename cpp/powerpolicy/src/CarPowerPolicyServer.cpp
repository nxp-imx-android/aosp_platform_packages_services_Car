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

#define LOG_TAG "carpowerpolicyd"
#define DEBUG false  // STOPSHIP if true.

#include "CarPowerPolicyServer.h"

#include <android-base/file.h>
#include <android-base/stringprintf.h>
#include <android/frameworks/automotive/powerpolicy/BnCarPowerPolicyChangeCallback.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <hidl/HidlTransportSupport.h>
#include <private/android_filesystem_config.h>
#include <utils/String8.h>
#include <utils/Timers.h>

#include <inttypes.h>

namespace android {
namespace frameworks {
namespace automotive {
namespace powerpolicy {

using android::defaultServiceManager;
using android::IBinder;
using android::Looper;
using android::Mutex;
using android::sp;
using android::status_t;
using android::String16;
using android::Vector;
using android::wp;
using android::base::Error;
using android::base::Result;
using android::base::StringAppendF;
using android::base::StringPrintf;
using android::base::WriteStringToFd;
using android::binder::Status;
using android::frameworks::automotive::powerpolicy::internal::PolicyState;
using android::hardware::hidl_vec;
using android::hardware::interfacesEqual;
using android::hardware::Return;
using android::hardware::automotive::vehicle::V2_0::IVehicle;
using android::hardware::automotive::vehicle::V2_0::StatusCode;
using android::hardware::automotive::vehicle::V2_0::SubscribeFlags;
using android::hardware::automotive::vehicle::V2_0::SubscribeOptions;
using android::hardware::automotive::vehicle::V2_0::VehicleApPowerStateReport;
using android::hardware::automotive::vehicle::V2_0::VehiclePropConfig;
using android::hardware::automotive::vehicle::V2_0::VehicleProperty;
using android::hardware::automotive::vehicle::V2_0::VehiclePropValue;
using android::hidl::base::V1_0::IBase;

namespace {

const int32_t MSG_CONNECT_TO_VHAL = 1;  // Message to request of connecting to VHAL.

const nsecs_t kConnectionRetryIntervalNs = 200000000;  // 200 milliseconds.
const int32_t kMaxConnectionRetry = 25;                // Retry up to 5 seconds.

constexpr const char* kCarPowerPolicyServerInterface =
        "android.frameworks.automotive.powerpolicy.ICarPowerPolicyServer/default";
constexpr const char* kCarPowerPolicySystemNotificationInterface =
        "carpowerpolicy_system_notification";

std::string toString(const CallbackInfo& callback) {
    return StringPrintf("callback(pid %d, filter: %s)", callback.pid,
                        toString(callback.filter.components).c_str());
}

std::vector<CallbackInfo>::const_iterator lookupPowerPolicyChangeCallback(
        const std::vector<CallbackInfo>& callbacks, const sp<IBinder>& binder) {
    for (auto it = callbacks.begin(); it != callbacks.end(); it++) {
        if (BnCarPowerPolicyChangeCallback::asBinder(it->callback) == binder) {
            return it;
        }
    }
    return callbacks.end();
}

Status checkSystemPermission() {
    if (IPCThreadState::self()->getCallingUid() != AID_SYSTEM) {
        return Status::fromExceptionCode(Status::EX_SECURITY,
                                         "Calling process does not have proper privilege");
    }
    return Status::ok();
}

}  // namespace

sp<CarPowerPolicyServer> CarPowerPolicyServer::sCarPowerPolicyServer = nullptr;

BinderDeathRecipient::BinderDeathRecipient(const sp<CarPowerPolicyServer>& service) :
      mService(service) {}

void BinderDeathRecipient::binderDied(const wp<IBinder>& who) {
    mService->handleBinderDeath(who);
}

HidlDeathRecipient::HidlDeathRecipient(const sp<CarPowerPolicyServer>& service) :
      mService(service) {}

void HidlDeathRecipient::serviceDied(uint64_t /*cookie*/, const wp<IBase>& who) {
    mService->handleHidlDeath(who);
}

PropertyChangeListener::PropertyChangeListener(const sp<CarPowerPolicyServer>& service) :
      mService(service) {}

Return<void> PropertyChangeListener::onPropertyEvent(const hidl_vec<VehiclePropValue>& propValues) {
    for (const auto& value : propValues) {
        if (value.prop == static_cast<int32_t>(VehicleProperty::POWER_POLICY_GROUP_REQ)) {
            const auto& ret = mService->setPowerPolicyGroup(value.value.stringValue);
            if (!ret.ok()) {
                ALOGW("Failed to set power policy group(%s): %s", value.value.stringValue.c_str(),
                      ret.error().message().c_str());
            }
        } else if (value.prop == static_cast<int32_t>(VehicleProperty::POWER_POLICY_REQ)) {
            const auto& ret = mService->applyPowerPolicy(value.value.stringValue, false, false);
            if (!ret.ok()) {
                ALOGW("Failed to apply power policy(%s): %s", value.value.stringValue.c_str(),
                      ret.error().message().c_str());
            }
        }
    }
    return Return<void>();
}

Return<void> PropertyChangeListener::onPropertySet(const VehiclePropValue& /*propValue*/) {
    return Return<void>();
}

Return<void> PropertyChangeListener::onPropertySetError(StatusCode /*status*/, int32_t /*propId*/,
                                                        int32_t /*areaId*/) {
    return Return<void>();
}

MessageHandlerImpl::MessageHandlerImpl(const sp<CarPowerPolicyServer>& service) :
      mService(service) {}

void MessageHandlerImpl::handleMessage(const Message& message) {
    switch (message.what) {
        case MSG_CONNECT_TO_VHAL:
            mService->connectToVhalHelper();
            break;
        default:
            ALOGW("Unknown message: %d", message.what);
    }
}

CarServiceNotificationHandler::CarServiceNotificationHandler(
        const sp<CarPowerPolicyServer>& service) :
      mService(service) {}

status_t CarServiceNotificationHandler::dump(int fd, const Vector<String16>& args) {
    return mService->dump(fd, args);
}

Status CarServiceNotificationHandler::notifyCarServiceReady(PolicyState* policyState) {
    return mService->notifyCarServiceReady(policyState);
}

Status CarServiceNotificationHandler::notifyPowerPolicyChange(const std::string& policyId) {
    return mService->notifyPowerPolicyChange(policyId);
}

Status CarServiceNotificationHandler::notifyPowerPolicyDefinition(
        const std::string& policyId, const std::vector<std::string>& enabledComponents,
        const std::vector<std::string>& disabledComponents) {
    return mService->notifyPowerPolicyDefinition(policyId, enabledComponents, disabledComponents);
}

Result<sp<CarPowerPolicyServer>> CarPowerPolicyServer::startService(const sp<Looper>& looper) {
    if (sCarPowerPolicyServer != nullptr) {
        return Error(INVALID_OPERATION) << "Cannot start service more than once";
    }
    sp<CarPowerPolicyServer> server = new CarPowerPolicyServer();
    const auto& ret = server->init(looper);
    if (!ret.ok()) {
        return Error(ret.error().code())
                << "Failed to start car power policy server: " << ret.error();
    }
    sCarPowerPolicyServer = server;

    return sCarPowerPolicyServer;
}

void CarPowerPolicyServer::terminateService() {
    if (sCarPowerPolicyServer != nullptr) {
        sCarPowerPolicyServer->terminate();
        sCarPowerPolicyServer = nullptr;
    }
}

CarPowerPolicyServer::CarPowerPolicyServer() :
      mCurrentPowerPolicy(nullptr),
      mLastApplyPowerPolicy(-1),
      mLastSetDefaultPowerPolicyGroup(-1),
      mCarServiceInOperation(false) {
    mMessageHandler = new MessageHandlerImpl(this);
    mBinderDeathRecipient = new BinderDeathRecipient(this);
    mHidlDeathRecipient = new HidlDeathRecipient(this);
    mPropertyChangeListener = new PropertyChangeListener(this);
    mCarServiceNotificationHandler = new CarServiceNotificationHandler(this);
}

Status CarPowerPolicyServer::getCurrentPowerPolicy(CarPowerPolicy* aidlReturn) {
    Mutex::Autolock lock(mMutex);
    if (mCurrentPowerPolicy == nullptr) {
        return Status::fromExceptionCode(Status::EX_ILLEGAL_STATE,
                                         "The current power policy is not set");
    }
    *aidlReturn = *mCurrentPowerPolicy;
    return Status::ok();
}

Status CarPowerPolicyServer::getPowerComponentState(PowerComponent componentId, bool* aidlReturn) {
    const auto& ret = mComponentHandler.getPowerComponentState(componentId);
    if (!ret.ok()) {
        std::string errorMsg = ret.error().message();
        ALOGW("getPowerComponentState(%s) failed: %s", toString(componentId).c_str(),
              errorMsg.c_str());
        return Status::fromExceptionCode(Status::EX_ILLEGAL_ARGUMENT, errorMsg.c_str());
    }
    *aidlReturn = *ret;
    return Status::ok();
}

Status CarPowerPolicyServer::registerPowerPolicyChangeCallback(
        const sp<ICarPowerPolicyChangeCallback>& callback, const CarPowerPolicyFilter& filter) {
    Mutex::Autolock lock(mMutex);
    pid_t callingPid = IPCThreadState::self()->getCallingPid();
    uid_t callingUid = IPCThreadState::self()->getCallingUid();
    if (isRegisteredLocked(callback)) {
        std::string errorStr = StringPrintf("The callback(pid: %d, uid: %d) is already registered.",
                                            callingPid, callingUid);
        const char* errorCause = errorStr.c_str();
        ALOGW("Cannot register a callback: %s", errorCause);
        return Status::fromExceptionCode(Status::EX_ILLEGAL_ARGUMENT, errorCause);
    }
    sp<IBinder> binder = BnCarPowerPolicyChangeCallback::asBinder(callback);
    status_t status = binder->linkToDeath(mBinderDeathRecipient);
    if (status != OK) {
        std::string errorStr = StringPrintf("The given callback(pid: %d, uid: %d) is dead",
                                            callingPid, callingUid);
        const char* errorCause = errorStr.c_str();
        ALOGW("Cannot register a callback: %s", errorCause);
        return Status::fromExceptionCode(Status::EX_ILLEGAL_STATE, errorCause);
    }
    mPolicyChangeCallbacks.emplace_back(callback, filter, callingPid);

    if (DEBUG) {
        ALOGD("Power policy change callback(pid: %d, filter: %s) is registered", callingPid,
              toString(filter.components).c_str());
    }
    return Status::ok();
}

Status CarPowerPolicyServer::unregisterPowerPolicyChangeCallback(
        const sp<ICarPowerPolicyChangeCallback>& callback) {
    Mutex::Autolock lock(mMutex);
    pid_t callingPid = IPCThreadState::self()->getCallingPid();
    uid_t callingUid = IPCThreadState::self()->getCallingUid();
    sp<IBinder> binder = BnCarPowerPolicyChangeCallback::asBinder(callback);
    auto it = lookupPowerPolicyChangeCallback(mPolicyChangeCallbacks, binder);
    if (it == mPolicyChangeCallbacks.end()) {
        std::string errorStr =
                StringPrintf("The callback(pid: %d, uid: %d) has not been registered", callingPid,
                             callingUid);
        const char* errorCause = errorStr.c_str();
        ALOGW("Cannot unregister a callback: %s", errorCause);
        return Status::fromExceptionCode(Status::EX_ILLEGAL_ARGUMENT, errorCause);
    }
    binder->unlinkToDeath(mBinderDeathRecipient);
    mPolicyChangeCallbacks.erase(it);
    if (DEBUG) {
        ALOGD("Power policy change callback(pid: %d, uid: %d) is unregistered", callingPid,
              callingUid);
    }
    return Status::ok();
}

Status CarPowerPolicyServer::notifyCarServiceReady(PolicyState* policyState) {
    Status status = checkSystemPermission();
    if (!status.isOk()) {
        return status;
    }
    Mutex::Autolock lock(mMutex);
    policyState->policyId = mCurrentPowerPolicy.get() ? mCurrentPowerPolicy->policyId : "";
    policyState->policyGroupId = mCurrentPolicyGroupId;
    mCarServiceInOperation = true;
    ALOGI("CarService is now responsible for power policy management");
    return Status::ok();
}

Status CarPowerPolicyServer::notifyPowerPolicyChange(const std::string& policyId) {
    Status status = checkSystemPermission();
    if (!status.isOk()) {
        return status;
    }
    const auto& ret = applyPowerPolicy(policyId, true, true);
    if (!ret.ok()) {
        return Status::fromExceptionCode(Status::EX_ILLEGAL_STATE,
                                         StringPrintf("Failed to notify power policy change: %s",
                                                      ret.error().message().c_str())
                                                 .c_str());
    }
    return Status::ok();
}

Status CarPowerPolicyServer::notifyPowerPolicyDefinition(
        const std::string& policyId, const std::vector<std::string>& enabledComponents,
        const std::vector<std::string>& disabledComponents) {
    Status status = checkSystemPermission();
    if (!status.isOk()) {
        return status;
    }
    const auto& ret =
            mPolicyManager.definePowerPolicy(policyId, enabledComponents, disabledComponents);
    if (!ret.ok()) {
        return Status::
                fromExceptionCode(Status::EX_ILLEGAL_ARGUMENT,
                                  StringPrintf("Failed to notify power policy definition: %s",
                                               ret.error().message().c_str())
                                          .c_str());
    }
    return Status::ok();
}

status_t CarPowerPolicyServer::dump(int fd, const Vector<String16>& args) {
    {
        Mutex::Autolock lock(mMutex);
        const char* indent = "  ";
        const char* doubleIndent = "    ";
        WriteStringToFd("CAR POWER POLICY DAEMON\n", fd);
        WriteStringToFd(StringPrintf("%sCarService is in operation: %s\n", indent,
                                     mCarServiceInOperation ? "true" : "false"),
                        fd);
        WriteStringToFd(StringPrintf("%sConnection to VHAL: %s\n", indent,
                                     mVhalService.get() ? "connected" : "disconnected"),
                        fd);
        WriteStringToFd(StringPrintf("%sCurrent power policy: %s\n", indent,
                                     mCurrentPowerPolicy ? mCurrentPowerPolicy->policyId.c_str()
                                                         : "not set"),
                        fd);
        WriteStringToFd(StringPrintf("%sLast timestamp of applying power policy: %" PRId64 "\n",
                                     indent, mLastApplyPowerPolicy),
                        fd);
        WriteStringToFd(StringPrintf("%sCurrent power policy group ID: %s\n", indent,
                                     mCurrentPolicyGroupId.empty() ? "not set"
                                                                   : mCurrentPolicyGroupId.c_str()),
                        fd);
        WriteStringToFd(StringPrintf("%sLast timestamp of setting default power policy group: "
                                     "%" PRId64 "\n",
                                     indent, mLastSetDefaultPowerPolicyGroup),
                        fd);
        WriteStringToFd(StringPrintf("%sPolicy change callbacks:%s\n", indent,
                                     mPolicyChangeCallbacks.size() ? "" : " none"),
                        fd);
        for (auto& callback : mPolicyChangeCallbacks) {
            WriteStringToFd(StringPrintf("%s- %s\n", doubleIndent, toString(callback).c_str()), fd);
        }
    }
    auto ret = mPolicyManager.dump(fd, args);
    if (!ret.ok()) {
        ALOGW("Failed to dump power policy handler: %s", ret.error().message().c_str());
        return ret.error().code();
    }
    ret = mComponentHandler.dump(fd, args);
    if (!ret.ok()) {
        ALOGW("Failed to dump power component handler: %s", ret.error().message().c_str());
        return ret.error().code();
    }
    return OK;
}

Result<void> CarPowerPolicyServer::init(const sp<Looper>& looper) {
    mHandlerLooper = looper;
    mPolicyManager.init();
    mComponentHandler.init();
    checkSilentModeFromKernel();

    status_t status =
            defaultServiceManager()->addService(String16(kCarPowerPolicyServerInterface), this);
    if (status != OK) {
        return Error(status) << "Failed to add carpowerpolicyd to ServiceManager";
    }
    status =
            defaultServiceManager()->addService(String16(
                                                        kCarPowerPolicySystemNotificationInterface),
                                                mCarServiceNotificationHandler);
    if (status != OK) {
        return Error(status)
                << "Failed to add car power policy system notification to ServiceManager";
    }

    connectToVhal();
    return {};
}

void CarPowerPolicyServer::terminate() {
    {
        Mutex::Autolock lock(mMutex);
        for (auto it = mPolicyChangeCallbacks.begin(); it != mPolicyChangeCallbacks.end(); it++) {
            sp<IBinder> binder = BnCarPowerPolicyChangeCallback::asBinder(it->callback);
            binder->unlinkToDeath(mBinderDeathRecipient);
        }
        mPolicyChangeCallbacks.clear();
    }
    mComponentHandler.finalize();
}

void CarPowerPolicyServer::handleBinderDeath(const wp<IBinder>& who) {
    Mutex::Autolock lock(mMutex);
    IBinder* binder = who.unsafe_get();
    auto it = lookupPowerPolicyChangeCallback(mPolicyChangeCallbacks, binder);
    if (it != mPolicyChangeCallbacks.end()) {
        ALOGW("Power policy callback(pid: %d) died", it->pid);
        binder->unlinkToDeath(mBinderDeathRecipient);
        mPolicyChangeCallbacks.erase(it);
    }
}

void CarPowerPolicyServer::handleHidlDeath(const wp<IBase>& who) {
    {
        Mutex::Autolock lock(mMutex);
        if (!interfacesEqual(mVhalService, who.promote())) {
            return;
        }
        ALOGW("VHAL has died.");
        mVhalService->unlinkToDeath(mHidlDeathRecipient);
        mVhalService = nullptr;
    }
    connectToVhal();
}

Result<void> CarPowerPolicyServer::applyPowerPolicy(const std::string& policyId,
                                                    bool carServiceInOperation,
                                                    bool notifyClients) {
    CarPowerPolicyPtr policy = mPolicyManager.getPowerPolicy(policyId);
    if (policy == nullptr) {
        return Error()
                << StringPrintf("Failed to get power policy(%s): The policy is not registered.",
                                policyId.c_str());
    }

    std::vector<CallbackInfo> clients;
    {
        Mutex::Autolock lock(mMutex);
        if (mCarServiceInOperation != carServiceInOperation) {
            return Error() << (mCarServiceInOperation
                                       ? "After CarService starts serving, power policy cannot be "
                                         "managed in car power policy daemon"
                                       : "Before CarService starts serving, power policy cannot be "
                                         "applied from CarService");
        }
        mCurrentPowerPolicy = policy;
        clients = mPolicyChangeCallbacks;
    }

    const auto& retApply = mComponentHandler.applyPowerPolicy(policy);
    if (!retApply.ok()) {
        ALOGW("Failed to apply power policy(%s): %s", policyId.c_str(),
              retApply.error().message().c_str());
    }
    const auto& retNotify = notifyVhalNewPowerPolicy(policyId);
    if (!retNotify.ok()) {
        ALOGW("Failed to tell VHAL the new power policy(%s): %s", policyId.c_str(),
              retNotify.error().message().c_str());
    }
    if (notifyClients) {
        for (auto client : clients) {
            client.callback->onPolicyChanged(*policy);
        }
    }
    ALOGI("The current power policy is %s", policyId.c_str());
    return {};
}

Result<void> CarPowerPolicyServer::setPowerPolicyGroup(const std::string& groupId) {
    if (!mPolicyManager.isPowerPolicyGroupAvailable(groupId)) {
        return Error() << StringPrintf("Power policy group(%s) is not available", groupId.c_str());
    }
    Mutex::Autolock lock(mMutex);
    if (mCarServiceInOperation) {
        return Error() << "After CarService starts serving, power policy group cannot be set in "
                          "car power policy daemon";
    }
    mCurrentPolicyGroupId = groupId;
    ALOGI("The current power policy group is |%s|", groupId.c_str());
    return {};
}

bool CarPowerPolicyServer::isRegisteredLocked(const sp<ICarPowerPolicyChangeCallback>& callback) {
    sp<IBinder> binder = BnCarPowerPolicyChangeCallback::asBinder(callback);
    return lookupPowerPolicyChangeCallback(mPolicyChangeCallbacks, binder) !=
            mPolicyChangeCallbacks.end();
}

void CarPowerPolicyServer::checkSilentModeFromKernel() {
    // TODO(b/162599168): check if silent mode is set by kernel.
}

// This method ensures that the attempt to connect to VHAL occurs in the main thread.
void CarPowerPolicyServer::connectToVhal() {
    mRemainingConnectionRetryCount = kMaxConnectionRetry;
    mHandlerLooper->sendMessage(mMessageHandler, MSG_CONNECT_TO_VHAL);
}

// connectToVhalHelper is always executed in the main thread.
void CarPowerPolicyServer::connectToVhalHelper() {
    {
        Mutex::Autolock lock(mMutex);
        if (mVhalService.get() != nullptr) {
            return;
        }
    }
    sp<IVehicle> vhalService = IVehicle::tryGetService();
    if (vhalService.get() == nullptr) {
        ALOGW("Failed to connect to VHAL. Retrying in %" PRId64 " ms.",
              nanoseconds_to_milliseconds(kConnectionRetryIntervalNs));
        mRemainingConnectionRetryCount--;
        if (mRemainingConnectionRetryCount <= 0) {
            ALOGE("Failed to connect to VHAL after %d attempt%s. Gave up.", kMaxConnectionRetry,
                  kMaxConnectionRetry > 1 ? "s" : "");
            return;
        }
        mHandlerLooper->sendMessageDelayed(kConnectionRetryIntervalNs, mMessageHandler,
                                           MSG_CONNECT_TO_VHAL);
        return;
    }
    auto ret = vhalService->linkToDeath(mHidlDeathRecipient, /*cookie=*/0);
    if (!ret.isOk() || ret == false) {
        connectToVhal();
        ALOGW("Failed to connect to VHAL. VHAL is dead. Retrying...");
        return;
    }
    {
        Mutex::Autolock lock(mMutex);
        mVhalService = vhalService;
    }
    ALOGI("Connected to VHAL");
    subscribeToVhal();
    return;
}

void CarPowerPolicyServer::subscribeToVhal() {
    subscribeToProperty(static_cast<int32_t>(VehicleProperty::POWER_POLICY_REQ),
                        [this](const VehiclePropValue& value) {
                            if (value.value.stringValue.size() > 0) {
                                const auto& ret =
                                        applyPowerPolicy(value.value.stringValue, false, false);
                                if (ret.ok()) {
                                    Mutex::Autolock lock(mMutex);
                                    mLastApplyPowerPolicy = value.timestamp;
                                } else {
                                    ALOGW("Failed to apply power policy(%s): %s",
                                          value.value.stringValue.c_str(),
                                          ret.error().message().c_str());
                                }
                            }
                        });
    subscribeToProperty(static_cast<int32_t>(VehicleProperty::POWER_POLICY_GROUP_REQ),
                        [this](const VehiclePropValue& value) {
                            if (value.value.stringValue.size() > 0) {
                                const auto& ret = setPowerPolicyGroup(value.value.stringValue);
                                if (ret.ok()) {
                                    Mutex::Autolock lock(mMutex);
                                    mLastSetDefaultPowerPolicyGroup = value.timestamp;
                                } else {
                                    ALOGW("Failed to set power policy group(%s): %s",
                                          value.value.stringValue.c_str(),
                                          ret.error().message().c_str());
                                }
                            }
                        });
}

void CarPowerPolicyServer::subscribeToProperty(
        int32_t prop, std::function<void(const VehiclePropValue&)> processor) {
    if (!isPropertySupported(prop)) {
        ALOGW("Vehicle property(%d) is not supported by VHAL.", prop);
        return;
    }
    VehiclePropValue propValue{
            .prop = prop,
    };
    sp<IVehicle> vhalService;
    {
        Mutex::Autolock lock(mMutex);
        vhalService = mVhalService;
    }
    StatusCode status;
    vhalService->get(propValue, [&status, &propValue](StatusCode s, const VehiclePropValue& value) {
        status = s;
        propValue = value;
    });
    if (status != StatusCode::OK) {
        ALOGW("Failed to get vehicle property(%d) value.", prop);
        return;
    }
    processor(propValue);
    SubscribeOptions reqVhalProperties[] = {
            {.propId = prop, .flags = SubscribeFlags::EVENTS_FROM_CAR},
    };
    hidl_vec<SubscribeOptions> options;
    options.setToExternal(reqVhalProperties, arraysize(reqVhalProperties));
    status = vhalService->subscribe(mPropertyChangeListener, options);
    if (status != StatusCode::OK) {
        ALOGW("Failed to subscribe to vehicle property(%d).", prop);
    }
}

Result<void> CarPowerPolicyServer::notifyVhalNewPowerPolicy(const std::string& policyId) {
    int32_t prop = static_cast<int32_t>(VehicleProperty::CURRENT_POWER_POLICY);
    if (!isPropertySupported(prop)) {
        return Error() << StringPrintf("Vehicle property(%d) is not supported by VHAL.", prop);
    }
    VehiclePropValue propValue{
            .prop = prop,
            .value = {
                    .stringValue = policyId,
            },
    };
    sp<IVehicle> vhalService;
    {
        Mutex::Autolock lock(mMutex);
        vhalService = mVhalService;
    }
    auto ret = vhalService->set(propValue);
    if (!ret.isOk() || ret != StatusCode::OK) {
        return Error() << "Failed to set CURRENT_POWER_POLICY property";
    }
    return {};
}

bool CarPowerPolicyServer::isPropertySupported(int32_t prop) {
    if (mSupportedProperties.count(prop) > 0) {
        return mSupportedProperties[prop];
    }
    StatusCode status;
    hidl_vec<int32_t> props = {prop};
    sp<IVehicle> vhalService;
    {
        Mutex::Autolock lock(mMutex);
        vhalService = mVhalService;
    }
    vhalService->getPropConfigs(props,
                                [&status](StatusCode s,
                                          hidl_vec<VehiclePropConfig> /*propConfigs*/) {
                                    status = s;
                                });
    mSupportedProperties[prop] = status == StatusCode::OK;
    return mSupportedProperties[prop];
}

}  // namespace powerpolicy
}  // namespace automotive
}  // namespace frameworks
}  // namespace android
