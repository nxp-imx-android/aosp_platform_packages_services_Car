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

#ifndef CPP_WATCHDOG_SERVER_SRC_WATCHDOGPROCESSSERVICE_H_
#define CPP_WATCHDOG_SERVER_SRC_WATCHDOGPROCESSSERVICE_H_

#include <android-base/result.h>
#include <android/automotive/watchdog/BnCarWatchdogClient.h>
#include <android/automotive/watchdog/internal/BnCarWatchdogClient.h>
#include <android/automotive/watchdog/internal/BnCarWatchdogMonitor.h>
#include <android/automotive/watchdog/internal/PowerCycle.h>
#include <android/automotive/watchdog/internal/UserState.h>
#include <android/hardware/automotive/vehicle/2.0/IVehicle.h>
#include <binder/IBinder.h>
#include <binder/Status.h>
#include <cutils/multiuser.h>
#include <utils/Looper.h>
#include <utils/Mutex.h>
#include <utils/String16.h>
#include <utils/StrongPointer.h>
#include <utils/Vector.h>

#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace android {
namespace automotive {
namespace watchdog {

class WatchdogProcessService : public android::RefBase {
public:
    explicit WatchdogProcessService(const android::sp<Looper>& handlerLooper);

    virtual android::base::Result<void> dump(int fd, const android::Vector<String16>& args);

    virtual android::binder::Status registerClient(const android::sp<ICarWatchdogClient>& client,
                                                   TimeoutLength timeout);
    virtual android::binder::Status unregisterClient(const sp<ICarWatchdogClient>& client);
    virtual android::binder::Status registerMediator(
            const sp<android::automotive::watchdog::internal::ICarWatchdogClient>& mediator);
    virtual android::binder::Status unregisterMediator(
            const sp<android::automotive::watchdog::internal::ICarWatchdogClient>& mediator);
    virtual android::binder::Status registerMonitor(
            const sp<android::automotive::watchdog::internal::ICarWatchdogMonitor>& monitor);
    virtual android::binder::Status unregisterMonitor(
            const sp<android::automotive::watchdog::internal::ICarWatchdogMonitor>& monitor);
    virtual android::binder::Status tellClientAlive(const sp<ICarWatchdogClient>& client,
                                                    int32_t sessionId);
    virtual android::binder::Status tellMediatorAlive(
            const sp<android::automotive::watchdog::internal::ICarWatchdogClient>& mediator,
            const std::vector<int32_t>& clientsNotResponding, int32_t sessionId);
    virtual android::binder::Status tellDumpFinished(
            const android::sp<android::automotive::watchdog::internal::ICarWatchdogMonitor>&
                    monitor,
            int32_t pid);
    virtual android::binder::Status notifyPowerCycleChange(
            android::automotive::watchdog::internal::PowerCycle cycle);
    virtual android::binder::Status notifyUserStateChange(
            userid_t userId, android::automotive::watchdog::internal::UserState state);

    android::base::Result<void> start();
    void terminate();
    void doHealthCheck(int what);

private:
    enum ClientType {
        Regular,
        Mediator,
    };

    struct ClientInfo {
        ClientInfo(const android::sp<ICarWatchdogClient>& client, pid_t pid, userid_t userId,
                   ClientType type) :
              client(client),
              pid(pid),
              userId(userId),
              type(type) {}
        std::string toString();

        android::sp<ICarWatchdogClient> client;
        pid_t pid;
        userid_t userId;
        int sessionId;
        ClientType type;
    };

    struct HeartBeat {
        int64_t eventTime;
        int64_t value;
    };

    typedef std::unordered_map<int, ClientInfo> PingedClientMap;

    class BinderDeathRecipient : public android::IBinder::DeathRecipient {
    public:
        explicit BinderDeathRecipient(const android::sp<WatchdogProcessService>& service);

        void binderDied(const android::wp<android::IBinder>& who) override;

    private:
        android::sp<WatchdogProcessService> mService;
    };

    class HidlDeathRecipient : public android::hardware::hidl_death_recipient {
    public:
        explicit HidlDeathRecipient(const android::sp<WatchdogProcessService>& service);

        void serviceDied(uint64_t cookie,
                         const android::wp<android::hidl::base::V1_0::IBase>& who) override;

    private:
        android::sp<WatchdogProcessService> mService;
    };

    class PropertyChangeListener :
          public android::hardware::automotive::vehicle::V2_0::IVehicleCallback {
    public:
        explicit PropertyChangeListener(const android::sp<WatchdogProcessService>& service);

        android::hardware::Return<void> onPropertyEvent(
                const android::hardware::hidl_vec<
                        android::hardware::automotive::vehicle::V2_0::VehiclePropValue>& propValues)
                override;
        android::hardware::Return<void> onPropertySet(
                const android::hardware::automotive::vehicle::V2_0::VehiclePropValue& propValue)
                override;
        android::hardware::Return<void> onPropertySetError(
                android::hardware::automotive::vehicle::V2_0::StatusCode errorCode, int32_t propId,
                int32_t areaId) override;

    private:
        android::sp<WatchdogProcessService> mService;
    };

    class MessageHandlerImpl : public MessageHandler {
    public:
        explicit MessageHandlerImpl(const android::sp<WatchdogProcessService>& service);

        void handleMessage(const Message& message) override;

    private:
        android::sp<WatchdogProcessService> mService;
    };

private:
    android::binder::Status registerClientLocked(const android::sp<ICarWatchdogClient>& client,
                                                 TimeoutLength timeout, ClientType clientType);
    android::binder::Status unregisterClientLocked(const std::vector<TimeoutLength>& timeouts,
                                                   android::sp<IBinder> binder,
                                                   ClientType clientType);
    bool isRegisteredLocked(const android::sp<ICarWatchdogClient>& client);
    android::binder::Status tellClientAliveLocked(const android::sp<ICarWatchdogClient>& client,
                                                  int32_t sessionId);
    android::base::Result<void> startHealthCheckingLocked(TimeoutLength timeout);
    android::base::Result<void> dumpAndKillClientsIfNotResponding(TimeoutLength timeout);
    android::base::Result<void> dumpAndKillAllProcesses(
            const std::vector<int32_t>& processesNotResponding, bool reportToVhal);
    int32_t getNewSessionId();
    bool isWatchdogEnabled();
    android::base::Result<void> updateVhal(
            const android::hardware::automotive::vehicle::V2_0::VehiclePropValue& value);
    android::base::Result<void> connectToVhalLocked();
    void subscribeToVhalHeartBeatLocked();
    void reportWatchdogAliveToVhal();
    void reportTerminatedProcessToVhal(const std::vector<int32_t>& processesNotResponding);
    android::base::Result<std::string> readProcCmdLine(int32_t pid);
    void handleBinderDeath(const android::wp<android::IBinder>& who);
    void handleHidlDeath(const android::wp<android::hidl::base::V1_0::IBase>& who);
    void queryVhalPropertiesLocked();
    bool isVhalPropertySupportedLocked(
            android::hardware::automotive::vehicle::V2_0::VehicleProperty propId);
    void updateVhalHeartBeat(int64_t value);
    void checkVhalHealth();
    void terminateVhal();

    using Processor =
            std::function<void(std::vector<ClientInfo>&, std::vector<ClientInfo>::const_iterator)>;
    bool findClientAndProcessLocked(const std::vector<TimeoutLength> timeouts,
                                    const android::sp<android::IBinder> binder,
                                    const Processor& processor);

private:
    android::sp<Looper> mHandlerLooper;
    android::sp<MessageHandlerImpl> mMessageHandler;
    android::Mutex mMutex;
    std::unordered_map<TimeoutLength, std::vector<ClientInfo>> mClients GUARDED_BY(mMutex);
    std::unordered_map<TimeoutLength, PingedClientMap> mPingedClients GUARDED_BY(mMutex);
    std::unordered_set<userid_t> mStoppedUserIds GUARDED_BY(mMutex);
    android::sp<android::automotive::watchdog::internal::ICarWatchdogMonitor> mMonitor
            GUARDED_BY(mMutex);
    bool mWatchdogEnabled GUARDED_BY(mMutex);
    // mLastSessionId is accessed only within main thread. No need for mutual-exclusion.
    int32_t mLastSessionId;
    bool mServiceStarted;
    android::sp<android::hardware::automotive::vehicle::V2_0::IVehicle> mVhalService
            GUARDED_BY(mMutex);
    android::sp<BinderDeathRecipient> mBinderDeathRecipient;
    android::sp<HidlDeathRecipient> mHidlDeathRecipient;
    std::unordered_set<android::hardware::automotive::vehicle::V2_0::VehicleProperty>
            mNotSupportedVhalProperties;
    android::sp<PropertyChangeListener> mPropertyChangeListener;
    HeartBeat mVhalHeartBeat GUARDED_BY(mMutex);
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  // CPP_WATCHDOG_SERVER_SRC_WATCHDOGPROCESSSERVICE_H_
