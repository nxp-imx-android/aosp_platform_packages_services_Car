/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.car;

/** @hide */
interface ICar {
    // All oneway methods are called from system server and should be placed in top positions.
    // Do not change the number of oneway methods as system server make binder call based on this
    // order - if you change them, you need to change the constants on CarServiceHelperService.

    /**
     * IBinder is ICarServiceHelper but passed as IBinder due to aidl hidden.
     */
    oneway void setCarServiceHelper(in IBinder helper) = 0;

    /**
     * Notify of user lifecycle events.
     *
     * @param eventType - type as defined by CarUserManager.UserLifecycleEventType
     * @param timestampMs - when the event happened
     * @param fromUserId - user id of previous user when type is SWITCHING (or UserHandle.USER_NULL)
     * @param toUserId - user id of new user.
     */
    oneway void onUserLifecycleEvent(int eventType, long timestampMs, int fromUserId,
            int toUserId) = 1;

    /**
     * Notify when first user was unlocked, for metrics (and lifecycle) purposes.
     *
     * @param userId - id of first non-system user locked
     * @param timestampMs - when the user was unlocked
     * @param duration - how long it took to unlock (from SystemServer start)
     */
    oneway void onFirstUserUnlocked(int userId, long timestampMs, long duration) = 2;


    // TODO(b/145689885): 2 method below are deprecated (onUserLifecycleEvent covers then) so
    // they're have higher codes to make it easier to add other

    /**
     * Notify lock / unlock of user id to car service.
     * unlocked: 1 if unlocked 0 otherwise.
     */
    oneway void setUserLockStatus(in int userId, in int unlocked) = 9;

    /**
     * Notify of user switching.  This is called only for foreground users when the user is starting
     * to boot.
     *
     * @param userId - user id of new user.
     */
    oneway void onSwitchUser(in int userId) = 10;


    // Methods below start on 11 to make it easier to add more oneway methods above
    IBinder getCarService(in String serviceName) = 11;
    int getCarConnectionType() = 12;
    boolean isFeatureEnabled(in String featureName) = 13;
    int enableFeature(in String featureName) = 14;
    int disableFeature(in String featureName) = 15;
    List<String> getAllEnabledFeatures() = 16;
    List<String> getAllPendingDisabledFeatures() = 17;
    List<String> getAllPendingEnabledFeatures() = 18;
    /**
     * Get class name for experimental feature. Class should have constructor taking (Car, IBinder)
     * and should inherit CarManagerBase.
     */
    String getCarManagerClassForFeature(in String featureName) = 19;
}
