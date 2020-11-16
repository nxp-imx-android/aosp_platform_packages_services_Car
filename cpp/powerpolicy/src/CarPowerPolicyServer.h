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

#ifndef CPP_POWERPOLICY_SRC_CARPOWERPOLICYSERVER_H_
#define CPP_POWERPOLICY_SRC_CARPOWERPOLICYSERVER_H_

#include "PolicyManager.h"
#include "PowerComponentHandler.h"

#include <android-base/result.h>
#include <android/frameworks/automotive/powerpolicy/BnCarPowerPolicyServer.h>
#include <android/frameworks/automotive/powerpolicy/internal/BnCarPowerPolicySystemNotification.h>
#include <android/hardware/automotive/vehicle/2.0/IVehicle.h>
#include <binder/IBinder.h>
#include <binder/Status.h>
#include <utils/Looper.h>
#include <utils/Mutex.h>
#include <utils/String16.h>
#include <utils/StrongPointer.h>
#include <utils/Vector.h>

#include <unordered_set>

namespace android {
namespace frameworks {
namespace automotive {
namespace powerpolicy {

struct CallbackInfo {
    CallbackInfo(const android::sp<ICarPowerPolicyChangeCallback>& callback,
                 const CarPowerPolicyFilter& filter, int32_t pid) :
          callback(callback),
          filter(filter),
          pid(pid) {}

    android::sp<ICarPowerPolicyChangeCallback> callback;
    CarPowerPolicyFilter filter;
    pid_t pid;
};

// Forward declaration for testing use only.
namespace internal {

class CarPowerPolicyServerPeer;

}  // namespace internal

// Forward declaration for defining binder death handler and property change listener.
class CarPowerPolicyServer;

class BinderDeathRecipient : public android::IBinder::DeathRecipient {
public:
    explicit BinderDeathRecipient(const android::sp<CarPowerPolicyServer>& service);

    void binderDied(const android::wp<android::IBinder>& who) override;

private:
    android::sp<CarPowerPolicyServer> mService;
};

class HidlDeathRecipient : public android::hardware::hidl_death_recipient {
public:
    explicit HidlDeathRecipient(const android::sp<CarPowerPolicyServer>& service);

    void serviceDied(uint64_t cookie,
                     const android::wp<android::hidl::base::V1_0::IBase>& who) override;

private:
    sp<CarPowerPolicyServer> mService;
};

class PropertyChangeListener :
      public android::hardware::automotive::vehicle::V2_0::IVehicleCallback {
public:
    explicit PropertyChangeListener(const android::sp<CarPowerPolicyServer>& service);

    android::hardware::Return<void> onPropertyEvent(
            const android::hardware::hidl_vec<
                    hardware::automotive::vehicle::V2_0::VehiclePropValue>& propValues) override;
    android::hardware::Return<void> onPropertySet(
            const android::hardware::automotive::vehicle::V2_0::VehiclePropValue& propValue);
    android::hardware::Return<void> onPropertySetError(
            android::hardware::automotive::vehicle::V2_0::StatusCode status, int32_t propId,
            int32_t areaId);

private:
    android::sp<CarPowerPolicyServer> mService;
};

class MessageHandlerImpl : public android::MessageHandler {
public:
    explicit MessageHandlerImpl(const android::sp<CarPowerPolicyServer>& service);

    void handleMessage(const android::Message& message) override;

private:
    android::sp<CarPowerPolicyServer> mService;
};

class CarServiceNotificationHandler :
      public android::frameworks::automotive::powerpolicy::internal::
              BnCarPowerPolicySystemNotification {
public:
    explicit CarServiceNotificationHandler(const android::sp<CarPowerPolicyServer>& server);

    android::status_t dump(int fd, const android::Vector<android::String16>& args) override;
    android::binder::Status notifyCarServiceReady(
            android::frameworks::automotive::powerpolicy::internal::PolicyState* policyState)
            override;
    android::binder::Status notifyPowerPolicyChange(const std::string& policyId) override;

private:
    android::sp<CarPowerPolicyServer> mService;
};

class CarPowerPolicyServer : public BnCarPowerPolicyServer {
public:
    static base::Result<sp<CarPowerPolicyServer>> startService(const sp<android::Looper>& looper);
    static void terminateService();

    status_t dump(int fd, const Vector<String16>& args) override;
    binder::Status getCurrentPowerPolicy(CarPowerPolicy* aidlReturn) override;
    binder::Status getPowerComponentState(PowerComponent componentId, bool* aidlReturn) override;
    binder::Status registerPowerPolicyChangeCallback(
            const android::sp<ICarPowerPolicyChangeCallback>& callback,
            const CarPowerPolicyFilter& filter) override;
    binder::Status unregisterPowerPolicyChangeCallback(
            const android::sp<ICarPowerPolicyChangeCallback>& callback) override;

    void connectToVhalHelper();
    void handleBinderDeath(const android::wp<android::IBinder>& who);
    void handleHidlDeath(const android::wp<android::hidl::base::V1_0::IBase>& who);
    android::binder::Status notifyCarServiceReady(
            android::frameworks::automotive::powerpolicy::internal::PolicyState* policyState);
    android::binder::Status notifyPowerPolicyChange(const std::string& policyId);
    android::base::Result<void> applyPowerPolicy(const std::string& policyId,
                                                 bool carServiceInOperation, bool notifyClients);
    android::base::Result<void> setPowerPolicyGroup(const std::string& groupId);

private:
    CarPowerPolicyServer();

    android::base::Result<void> init(const android::sp<android::Looper>& looper);
    void terminate();
    bool isRegisteredLocked(const android::sp<ICarPowerPolicyChangeCallback>& callback);
    void checkSilentModeFromKernel();
    void connectToVhal();
    void subscribeToVhal();
    void subscribeToProperty(
            int32_t prop,
            std::function<
                    void(const android::hardware::automotive::vehicle::V2_0::VehiclePropValue&)>
                    processor);
    android::base::Result<void> notifyVhalNewPowerPolicy(const std::string& policyId);
    bool isPropertySupported(int32_t prop);

private:
    static android::sp<CarPowerPolicyServer> sCarPowerPolicyServer;

    sp<android::Looper> mHandlerLooper;
    sp<MessageHandlerImpl> mMessageHandler;
    PowerComponentHandler mComponentHandler;
    PolicyManager mPolicyManager;
    android::Mutex mMutex;
    CarPowerPolicyPtr mCurrentPowerPolicy GUARDED_BY(mMutex);
    std::string mCurrentPolicyGroupId GUARDED_BY(mMutex);
    std::vector<CallbackInfo> mPolicyChangeCallbacks GUARDED_BY(mMutex);
    android::sp<android::hardware::automotive::vehicle::V2_0::IVehicle> mVhalService
            GUARDED_BY(mMutex);
    int64_t mLastApplyPowerPolicy GUARDED_BY(mMutex);
    int64_t mLastSetDefaultPowerPolicyGroup GUARDED_BY(mMutex);
    bool mCarServiceInOperation GUARDED_BY(mMutex);
    std::unordered_map<int32_t, bool> mSupportedProperties;
    android::sp<BinderDeathRecipient> mBinderDeathRecipient;
    android::sp<HidlDeathRecipient> mHidlDeathRecipient;
    android::sp<PropertyChangeListener> mPropertyChangeListener;
    android::sp<CarServiceNotificationHandler> mCarServiceNotificationHandler;
    int32_t mRemainingConnectionRetryCount;

    // For unit tests.
    friend class android::frameworks::automotive::powerpolicy::internal::CarPowerPolicyServerPeer;
};

}  // namespace powerpolicy
}  // namespace automotive
}  // namespace frameworks
}  // namespace android

#endif  // CPP_POWERPOLICY_SRC_CARPOWERPOLICYSERVER_H_
