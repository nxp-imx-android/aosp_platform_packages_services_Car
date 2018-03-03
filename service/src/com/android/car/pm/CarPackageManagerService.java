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

package com.android.car.pm;

import android.annotation.Nullable;
import android.app.ActivityManager.StackInfo;
import android.car.Car;
import android.car.content.pm.AppBlockingPackageInfo;
import android.car.content.pm.CarAppBlockingPolicy;
import android.car.content.pm.CarAppBlockingPolicyService;
import android.car.content.pm.CarPackageManager;
import android.car.content.pm.ICarPackageManager;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.ICarUxRestrictionsChangeListener;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.content.res.Resources;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import com.android.car.CarLog;
import com.android.car.CarServiceBase;
import com.android.car.CarServiceUtils;
import com.android.car.CarUxRestrictionsManagerService;
import com.android.car.R;
import com.android.car.SystemActivityMonitoringService;
import com.android.car.SystemActivityMonitoringService.TopTaskInfoContainer;
import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

//TODO monitor app installing and refresh policy, bug: 31970400

public class CarPackageManagerService extends ICarPackageManager.Stub implements CarServiceBase {
    private static final boolean DBG_POLICY_SET = false;
    private static final boolean DBG_POLICY_CHECK = false;
    private static final boolean DBG_POLICY_ENFORCEMENT = false;

    private final Context mContext;
    private final SystemActivityMonitoringService mSystemActivityMonitoringService;
    private final PackageManager mPackageManager;

    private final HandlerThread mHandlerThread;
    private final PackageHandler mHandler;

    private String mDefaultActivityWhitelist;
    /**
     * Hold policy set from policy service or client.
     * Key: packageName of policy service
     */
    @GuardedBy("this")
    private final HashMap<String, ClientPolicy> mClientPolicies = new HashMap<>();
    @GuardedBy("this")
    private HashMap<String, AppBlockingPackageInfoWrapper> mActivityWhitelists = new HashMap<>();
    @GuardedBy("this")
    private LinkedList<AppBlockingPolicyProxy> mProxies;

    @GuardedBy("this")
    private final LinkedList<CarAppBlockingPolicy> mWaitingPolicies = new LinkedList<>();

    private final CarUxRestrictionsManagerService mCarUxRestrictionsService;
    private boolean mEnableActivityBlocking;
    private final ComponentName mActivityBlockingActivity;

    private final ActivityLaunchListener mActivityLaunchListener = new ActivityLaunchListener();
    private final UxRestrictionsListener mUxRestrictionsListener;
    private final BootPhaseEventReceiver mBootPhaseEventReceiver = new BootPhaseEventReceiver();

    public CarPackageManagerService(Context context,
            CarUxRestrictionsManagerService uxRestrictionsService,
            SystemActivityMonitoringService systemActivityMonitoringService) {
        mContext = context;
        mCarUxRestrictionsService = uxRestrictionsService;
        mSystemActivityMonitoringService = systemActivityMonitoringService;
        mPackageManager = mContext.getPackageManager();
        mUxRestrictionsListener = new UxRestrictionsListener(uxRestrictionsService);
        mHandlerThread = new HandlerThread(CarLog.TAG_PACKAGE);
        mHandlerThread.start();
        mHandler = new PackageHandler(mHandlerThread.getLooper());
        Resources res = context.getResources();
        mEnableActivityBlocking = res.getBoolean(R.bool.enableActivityBlockingForSafety);
        String blockingActivity = res.getString(R.string.activityBlockingActivity);
        mActivityBlockingActivity = ComponentName.unflattenFromString(blockingActivity);
    }

    @Override
    public void setAppBlockingPolicy(String packageName, CarAppBlockingPolicy policy, int flags) {
        if (DBG_POLICY_SET) {
            Log.i(CarLog.TAG_PACKAGE, "policy setting from binder call, client:" + packageName);
        }
        doSetAppBlockingPolicy(packageName, policy, flags, true /*setNow*/);
    }

    private void doSetAppBlockingPolicy(String packageName, CarAppBlockingPolicy policy, int flags,
            boolean setNow) {
        if (mContext.checkCallingOrSelfPermission(Car.PERMISSION_CONTROL_APP_BLOCKING)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "requires permission " + Car.PERMISSION_CONTROL_APP_BLOCKING);
        }
        CarServiceUtils.assertPackageName(mContext, packageName);
        if (policy == null) {
            throw new IllegalArgumentException("policy cannot be null");
        }
        if ((flags & CarPackageManager.FLAG_SET_POLICY_ADD) != 0 &&
                (flags & CarPackageManager.FLAG_SET_POLICY_REMOVE) != 0) {
            throw new IllegalArgumentException(
                    "Cannot set both FLAG_SET_POLICY_ADD and FLAG_SET_POLICY_REMOVE flag");
        }
        mHandler.requestUpdatingPolicy(packageName, policy, flags);
        if (setNow) {
            mHandler.requestPolicySetting();
            if ((flags & CarPackageManager.FLAG_SET_POLICY_WAIT_FOR_CHANGE) != 0) {
                synchronized (policy) {
                    try {
                        policy.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
    }

    @Override
    public boolean isActivityAllowedWhileDriving(String packageName, String className) {
        assertPackageAndClassName(packageName, className);
        synchronized (this) {
            if (DBG_POLICY_CHECK) {
                Log.i(CarLog.TAG_PACKAGE, "isActivityAllowedWhileDriving" +
                        dumpPoliciesLocked(false));
            }
            AppBlockingPackageInfo info = searchFromBlacklistsLocked(packageName);
            if (info != null) {
                return false;
            }
            return isActivityInWhitelistsLocked(packageName, className);
        }
    }

    @Override
    public boolean isServiceAllowedWhileDriving(String packageName, String className) {
        if (packageName == null) {
            throw new IllegalArgumentException("Package name null");
        }
        synchronized (this) {
            if (DBG_POLICY_CHECK) {
                Log.i(CarLog.TAG_PACKAGE, "isServiceAllowedWhileDriving" +
                        dumpPoliciesLocked(false));
            }
            AppBlockingPackageInfo info = searchFromBlacklistsLocked(packageName);
            if (info != null) {
                return false;
            }
            info = searchFromWhitelistsLocked(packageName);
            if (info != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isActivityBackedBySafeActivity(ComponentName activityName) {
        if (!mUxRestrictionsListener.isRestricted()) {
            return true;
        }
        StackInfo info = mSystemActivityMonitoringService.getFocusedStackForTopActivity(
                activityName);
        if (info == null) { // not top in focused stack
            return true;
        }
        if (info.taskNames.length <= 1) { // nothing below this.
            return false;
        }
        ComponentName activityBehind = ComponentName.unflattenFromString(
                info.taskNames[info.taskNames.length - 2]);
        return isActivityAllowedWhileDriving(activityBehind.getPackageName(),
                activityBehind.getClassName());
    }

    public Looper getLooper() {
        return mHandlerThread.getLooper();
    }

    private void assertPackageAndClassName(String packageName, String className) {
        if (packageName == null) {
            throw new IllegalArgumentException("Package name null");
        }
        if (className == null) {
            throw new IllegalArgumentException("Class name null");
        }
    }

    @GuardedBy("this")
    private AppBlockingPackageInfo searchFromBlacklistsLocked(String packageName) {
        for (ClientPolicy policy : mClientPolicies.values()) {
            AppBlockingPackageInfoWrapper wrapper = policy.blacklistsMap.get(packageName);
            if (wrapper != null && wrapper.isMatching) {
                return wrapper.info;
            }
        }
        return null;
    }

    @GuardedBy("this")
    private AppBlockingPackageInfo searchFromWhitelistsLocked(String packageName) {
        for (ClientPolicy policy : mClientPolicies.values()) {
            AppBlockingPackageInfoWrapper wrapper = policy.whitelistsMap.get(packageName);
            if (wrapper != null && wrapper.isMatching) {
                return wrapper.info;
            }
        }
        AppBlockingPackageInfoWrapper wrapper = mActivityWhitelists.get(packageName);
        return (wrapper != null) ? wrapper.info : null;
    }

    @GuardedBy("this")
    private boolean isActivityInWhitelistsLocked(String packageName, String className) {
        for (ClientPolicy policy : mClientPolicies.values()) {
            if (isActivityInMapAndMatching(policy.whitelistsMap, packageName, className)) {
                return true;
            }
        }
        return isActivityInMapAndMatching(mActivityWhitelists, packageName, className);
    }

    private boolean isActivityInMapAndMatching(HashMap<String, AppBlockingPackageInfoWrapper> map,
            String packageName, String className) {
        AppBlockingPackageInfoWrapper wrapper = map.get(packageName);
        if (wrapper == null || !wrapper.isMatching) {
            if (DBG_POLICY_CHECK) {
                Log.d(CarLog.TAG_PACKAGE, "Pkg not in whitelist:" + packageName);
            }
            return false;
        }
        return wrapper.info.isActivityCovered(className);
    }

    @Override
    public void init() {
        synchronized (this) {
            mHandler.requestInit();
        }
    }

    @Override
    public void release() {
        synchronized (this) {
            mHandler.requestRelease();
            // wait for release do be done. This guarantees that init is done.
            try {
                wait();
            } catch (InterruptedException e) {
            }
            mActivityWhitelists.clear();
            mClientPolicies.clear();
            if (mProxies != null) {
                for (AppBlockingPolicyProxy proxy : mProxies) {
                    proxy.disconnect();
                }
                mProxies.clear();
            }
            wakeupClientsWaitingForPolicySetitngLocked();
        }
        mContext.unregisterReceiver(mBootPhaseEventReceiver);
        mCarUxRestrictionsService.unregisterUxRestrictionsChangeListener(mUxRestrictionsListener);
        mSystemActivityMonitoringService.registerActivityLaunchListener(null);
    }

    // run from HandlerThread
    private void doHandleInit() {
        IntentFilter bootIntent = new IntentFilter();
        bootIntent.addAction(Intent.ACTION_LOCKED_BOOT_COMPLETED);
        mContext.registerReceiver(mBootPhaseEventReceiver, bootIntent);
        try {
            mCarUxRestrictionsService.registerUxRestrictionsChangeListener(mUxRestrictionsListener);
        } catch (IllegalArgumentException e) {
            // can happen while mocking is going on while init is still done.
            Log.w(CarLog.TAG_PACKAGE, "sensor subscription failed", e);
            return;
        }
        mSystemActivityMonitoringService.registerActivityLaunchListener(
                mActivityLaunchListener);
    }

    private void doParseInstalledPackages() {
        startAppBlockingPolicies();
        generateActivityWhitelist();
        mUxRestrictionsListener.checkIfTopActivityNeedsBlocking();
    }

    private synchronized void doHandleRelease() {
        notifyAll();
    }

    @GuardedBy("this")
    private void wakeupClientsWaitingForPolicySetitngLocked() {
        for (CarAppBlockingPolicy waitingPolicy : mWaitingPolicies) {
            synchronized (waitingPolicy) {
                waitingPolicy.notifyAll();
            }
        }
        mWaitingPolicies.clear();
    }

    private void doSetPolicy() {
        synchronized (this) {
            wakeupClientsWaitingForPolicySetitngLocked();
        }
        blockTopActivitiesIfNecessary();
    }

    private void doUpdatePolicy(String packageName, CarAppBlockingPolicy policy, int flags) {
        if (DBG_POLICY_SET) {
            Log.i(CarLog.TAG_PACKAGE, "setting policy from:" + packageName + ",policy:" + policy +
                    ",flags:0x" + Integer.toHexString(flags));
        }
        AppBlockingPackageInfoWrapper[] blacklistWrapper = verifyList(policy.blacklists);
        AppBlockingPackageInfoWrapper[] whitelistWrapper = verifyList(policy.whitelists);
        synchronized (this) {
            ClientPolicy clientPolicy = mClientPolicies.get(packageName);
            if (clientPolicy == null) {
                clientPolicy = new ClientPolicy();
                mClientPolicies.put(packageName, clientPolicy);
            }
            if ((flags & CarPackageManager.FLAG_SET_POLICY_ADD) != 0) {
                clientPolicy.addToBlacklists(blacklistWrapper);
                clientPolicy.addToWhitelists(whitelistWrapper);
            } else if ((flags & CarPackageManager.FLAG_SET_POLICY_REMOVE) != 0) {
                clientPolicy.removeBlacklists(blacklistWrapper);
                clientPolicy.removeWhitelists(whitelistWrapper);
            } else { //replace.
                clientPolicy.replaceBlacklists(blacklistWrapper);
                clientPolicy.replaceWhitelists(whitelistWrapper);
            }
            if ((flags & CarPackageManager.FLAG_SET_POLICY_WAIT_FOR_CHANGE) != 0) {
                mWaitingPolicies.add(policy);
            }
            if (DBG_POLICY_SET) {
                Log.i(CarLog.TAG_PACKAGE, "policy set:" + dumpPoliciesLocked(false));
            }
        }
        blockTopActivitiesIfNecessary();
    }

    private AppBlockingPackageInfoWrapper[] verifyList(AppBlockingPackageInfo[] list) {
        if (list == null) {
            return null;
        }
        LinkedList<AppBlockingPackageInfoWrapper> wrappers = new LinkedList<>();
        for (int i = 0; i < list.length; i++) {
            AppBlockingPackageInfo info = list[i];
            if (info == null) {
                continue;
            }
            boolean isMatching = isInstalledPackageMatching(info);
            wrappers.add(new AppBlockingPackageInfoWrapper(info, isMatching));
        }
        return wrappers.toArray(new AppBlockingPackageInfoWrapper[wrappers.size()]);
    }

    boolean isInstalledPackageMatching(AppBlockingPackageInfo info) {
        PackageInfo packageInfo = null;
        try {
            packageInfo = mPackageManager.getPackageInfo(info.packageName,
                    PackageManager.GET_SIGNATURES);
        } catch (NameNotFoundException e) {
            return false;
        }
        if (packageInfo == null) {
            return false;
        }
        // if it is system app and client specified the flag, do not check signature
        if ((info.flags & AppBlockingPackageInfo.FLAG_SYSTEM_APP) == 0 ||
                (!packageInfo.applicationInfo.isSystemApp() &&
                        !packageInfo.applicationInfo.isUpdatedSystemApp())) {
            Signature[] signatires = packageInfo.signatures;
            if (!isAnySignatureMatching(signatires, info.signatures)) {
                return false;
            }
        }
        int version = packageInfo.versionCode;
        if (info.minRevisionCode == 0) {
            if (info.maxRevisionCode == 0) { // all versions
                return true;
            } else { // only max version matters
                return info.maxRevisionCode > version;
            }
        } else { // min version matters
            if (info.maxRevisionCode == 0) {
                return info.minRevisionCode < version;
            } else {
                return (info.minRevisionCode < version) && (info.maxRevisionCode > version);
            }
        }
    }

    /**
     * Any signature from policy matching with package's signatures is treated as matching.
     */
    boolean isAnySignatureMatching(Signature[] fromPackage, Signature[] fromPolicy) {
        if (fromPackage == null) {
            return false;
        }
        if (fromPolicy == null) {
            return false;
        }
        ArraySet<Signature> setFromPackage = new ArraySet<Signature>();
        for (Signature sig : fromPackage) {
            setFromPackage.add(sig);
        }
        for (Signature sig : fromPolicy) {
            if (setFromPackage.contains(sig)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return list of whitelist including default activity. Key is package name while
     * value is list of activities. If list is empty, whole activities in the package
     * are whitelisted.
     * @return
     */
    private HashMap<String, Set<String>> parseConfigWhitelist() {
        HashMap<String, Set<String>> packageToActivityMap = new HashMap<>();
        Set<String> defaultActivity = new ArraySet<>();
        defaultActivity.add(mActivityBlockingActivity.getClassName());
        packageToActivityMap.put(mActivityBlockingActivity.getPackageName(), defaultActivity);
        Resources res = mContext.getResources();
        String whitelist = res.getString(R.string.defauiltActivityWhitelist);
        mDefaultActivityWhitelist = whitelist;
        String[] entries = whitelist.split(",");
        for (String entry : entries) {
            String[] packageActivityPair = entry.split("/");
            Set<String> activities = packageToActivityMap.get(packageActivityPair[0]);
            boolean newPackage = false;
            if (activities == null) {
                activities = new ArraySet<>();
                newPackage = true;
                packageToActivityMap.put(packageActivityPair[0], activities);
            }
            if (packageActivityPair.length == 1) { // whole package
                activities.clear();
            } else if (packageActivityPair.length == 2) {
                // add class name only when the whole package is not whitelisted.
                if (newPackage || (activities.size() > 0)) {
                    activities.add(packageActivityPair[1]);
                }
            }
        }
        return packageToActivityMap;
    }

    private void generateActivityWhitelist() {
        HashMap<String, AppBlockingPackageInfoWrapper> activityWhitelist = new HashMap<>();
        HashMap<String, Set<String>> configWhitelist = parseConfigWhitelist();

        List<PackageInfo> packages = mPackageManager.getInstalledPackages(
                PackageManager.GET_SIGNATURES | PackageManager.GET_ACTIVITIES
                        | PackageManager.MATCH_DIRECT_BOOT_AWARE
                        | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
        for (PackageInfo info : packages) {
            if (info.applicationInfo == null) {
                continue;
            }

            int flags = 0;
            String[] activities = null;

            if (info.applicationInfo.isSystemApp()
                    || info.applicationInfo.isUpdatedSystemApp()) {
                flags = AppBlockingPackageInfo.FLAG_SYSTEM_APP;
            }

            /* 1. Check if all or some of this app is in the <defauiltActivityWhitelist>
                  in config.xml */
            Set<String> configActivitiesForPackage = configWhitelist.get(info.packageName);
            if (configActivitiesForPackage != null) {
                if (DBG_POLICY_CHECK) {
                    Log.d(CarLog.TAG_PACKAGE, info.packageName + " whitelisted");
                }
                if (configActivitiesForPackage.size() == 0) {
                    // Whole Pkg has been whitelisted
                    flags |= AppBlockingPackageInfo.FLAG_WHOLE_ACTIVITY;
                    // Add all activities to the whitelist
                    activities = getActivitiesInPackage(info);
                    if (activities == null && DBG_POLICY_CHECK) {
                        Log.d(CarLog.TAG_PACKAGE, info.packageName + ": Activities null");
                    }
                } else {
                    if (DBG_POLICY_CHECK) {
                        Log.d(CarLog.TAG_PACKAGE, "Partially Whitelisted. WL Activities:");
                        for (String a : configActivitiesForPackage) {
                            Log.d(CarLog.TAG_PACKAGE, a);
                        }
                    }
                    activities = configActivitiesForPackage.toArray(
                            new String[configActivitiesForPackage.size()]);
                }
            } else {
                /* 2. If app is not listed in the config.xml check their Manifest meta-data to
                  see if they have any Distraction Optimized(DO) activities */
                try {
                    activities = CarAppMetadataReader.findDistractionOptimizedActivities(
                            mContext,
                            info.packageName);
                } catch (NameNotFoundException e) {
                    Log.w(CarLog.TAG_PACKAGE, "Error reading metadata: " + info.packageName);
                    continue;
                }
                if (activities != null) {
                    // Some of the activities in this app are Distraction Optimized.
                    if (DBG_POLICY_CHECK) {
                        for (String activity : activities) {
                            Log.d(CarLog.TAG_PACKAGE,
                                    "adding " + activity + " from " + info.packageName
                                            + " to whitelist");
                        }
                    }
                }
            }
            // Nothing to add to whitelist
            if (activities == null) {
                continue;
            }

            Signature[] signatures;
            signatures = info.signatures;
            AppBlockingPackageInfo appBlockingInfo = new AppBlockingPackageInfo(
                    info.packageName, 0, 0, flags, signatures, activities);
            AppBlockingPackageInfoWrapper wrapper = new AppBlockingPackageInfoWrapper(
                    appBlockingInfo, true);
            activityWhitelist.put(info.packageName, wrapper);
        }
        synchronized (this) {
            mActivityWhitelists.putAll(activityWhitelist);
        }
    }

    @Nullable
    private String[] getActivitiesInPackage(PackageInfo info) {
        if (info == null || info.activities == null) {
            return null;
        }
        List<String> activityList = new ArrayList<>();
        for (ActivityInfo aInfo : info.activities) {
            activityList.add(aInfo.name);
        }
        return activityList.toArray(new String[activityList.size()]);
    }

    private void startAppBlockingPolicies() {
        Intent policyIntent = new Intent();
        policyIntent.setAction(CarAppBlockingPolicyService.SERVICE_INTERFACE);
        List<ResolveInfo> policyInfos = mPackageManager.queryIntentServices(policyIntent, 0);
        if (policyInfos == null) { //no need to wait for service binding and retrieval.
            mHandler.requestPolicySetting();
            return;
        }
        LinkedList<AppBlockingPolicyProxy> proxies = new LinkedList<>();
        for (ResolveInfo resolveInfo : policyInfos) {
            ServiceInfo serviceInfo = resolveInfo.serviceInfo;
            if (serviceInfo == null) {
                continue;
            }
            if (serviceInfo.isEnabled()) {
                if (mPackageManager.checkPermission(Car.PERMISSION_CONTROL_APP_BLOCKING,
                        serviceInfo.packageName) != PackageManager.PERMISSION_GRANTED) {
                    continue;
                }
                Log.i(CarLog.TAG_PACKAGE, "found policy holding service:" + serviceInfo);
                AppBlockingPolicyProxy proxy = new AppBlockingPolicyProxy(this, mContext,
                        serviceInfo);
                proxy.connect();
                proxies.add(proxy);
            }
        }
        synchronized (this) {
            mProxies = proxies;
        }
    }

    public void onPolicyConnectionAndSet(AppBlockingPolicyProxy proxy,
            CarAppBlockingPolicy policy) {
        doHandlePolicyConnection(proxy, policy);
    }

    public void onPolicyConnectionFailure(AppBlockingPolicyProxy proxy) {
        doHandlePolicyConnection(proxy, null);
    }

    private void doHandlePolicyConnection(AppBlockingPolicyProxy proxy,
            CarAppBlockingPolicy policy) {
        boolean shouldSetPolicy = false;
        synchronized (this) {
            if (mProxies == null) {
                proxy.disconnect();
                return;
            }
            mProxies.remove(proxy);
            if (mProxies.size() == 0) {
                shouldSetPolicy = true;
                mProxies = null;
            }
        }
        try {
            if (policy != null) {
                if (DBG_POLICY_SET) {
                    Log.i(CarLog.TAG_PACKAGE, "policy setting from policy service:" +
                            proxy.getPackageName());
                }
                doSetAppBlockingPolicy(proxy.getPackageName(), policy, 0, false /*setNow*/);
            }
        } finally {
            proxy.disconnect();
            if (shouldSetPolicy) {
                mHandler.requestPolicySetting();
            }
        }
    }

    @Override
    public void dump(PrintWriter writer) {
        synchronized (this) {
            writer.println("*PackageManagementService*");
            writer.println("mEnableActivityBlocking:" + mEnableActivityBlocking);
            writer.println("ActivityRestricted:" + mUxRestrictionsListener.isRestricted());
            writer.print(dumpPoliciesLocked(true));
        }
    }

    @GuardedBy("this")
    private String dumpPoliciesLocked(boolean dumpAll) {
        StringBuilder sb = new StringBuilder();
        if (dumpAll) {
            sb.append("**System white list**\n");
            for (AppBlockingPackageInfoWrapper wrapper : mActivityWhitelists.values()) {
                sb.append(wrapper.toString() + "\n");
            }
        }
        sb.append("**Client Policies**\n");
        for (Entry<String, ClientPolicy> entry : mClientPolicies.entrySet()) {
            sb.append("Client:" + entry.getKey() + "\n");
            sb.append("  whitelists:\n");
            for (AppBlockingPackageInfoWrapper wrapper : entry.getValue().whitelistsMap.values()) {
                sb.append(wrapper.toString() + "\n");
            }
            sb.append("  blacklists:\n");
            for (AppBlockingPackageInfoWrapper wrapper : entry.getValue().blacklistsMap.values()) {
                sb.append(wrapper.toString() + "\n");
            }
        }
        sb.append("**Unprocessed policy services**\n");
        if (mProxies != null) {
            for (AppBlockingPolicyProxy proxy : mProxies) {
                sb.append(proxy.toString() + "\n");
            }
        }
        sb.append("**Default Whitelist string**\n");
        sb.append(mDefaultActivityWhitelist + "\n");

        return sb.toString();
    }

    private void blockTopActivityIfNecessary(TopTaskInfoContainer topTask) {
        boolean restricted = mUxRestrictionsListener.isRestricted();
        if (!restricted) {
            return;
        }
        doBlockTopActivityIfNotAllowed(topTask);
    }

    private void doBlockTopActivityIfNotAllowed(TopTaskInfoContainer topTask) {
        if (topTask.topActivity == null) {
            return;
        }
        boolean allowed = isActivityAllowedWhileDriving(
                topTask.topActivity.getPackageName(),
                topTask.topActivity.getClassName());
        if (DBG_POLICY_ENFORCEMENT) {
            Log.i(CarLog.TAG_PACKAGE, "new activity:" + topTask.toString() + " allowed:" + allowed);
        }
        if (allowed) {
            return;
        }
        synchronized(this) {
            if (!mEnableActivityBlocking) {
                Log.d(CarLog.TAG_PACKAGE, "Current activity " + topTask.topActivity +
                    " not allowed, blocking disabled. Number of tasks in stack:"
                    + topTask.stackInfo.taskIds.length);
                return;
            }
        }
        if (DBG_POLICY_CHECK) {
            Log.i(CarLog.TAG_PACKAGE, "Current activity " + topTask.topActivity +
                    " not allowed, will block, number of tasks in stack:" +
                    topTask.stackInfo.taskIds.length);
        }
        Intent newActivityIntent = new Intent();
        newActivityIntent.setComponent(mActivityBlockingActivity);
        newActivityIntent.putExtra(
                ActivityBlockingActivity.INTENT_KEY_BLOCKED_ACTIVITY,
                topTask.topActivity.flattenToString());
        mSystemActivityMonitoringService.blockActivity(topTask, newActivityIntent);
    }

    private void blockTopActivitiesIfNecessary() {
        boolean restricted = mUxRestrictionsListener.isRestricted();
        if (!restricted) {
            return;
        }
        List<TopTaskInfoContainer> topTasks = mSystemActivityMonitoringService.getTopTasks();
        for (TopTaskInfoContainer topTask : topTasks) {
            doBlockTopActivityIfNotAllowed(topTask);
        }
    }

    public synchronized void setEnableActivityBlocking(boolean enable) {
        mEnableActivityBlocking = enable;
    }

    /**
     * Get the distraction optimized activities for the given package.
     *
     * @param pkgName Name of the package
     * @return Array of the distraction optimized activities in the package
     */
    @Nullable
    public String[] getDistractionOptimizedActivities(String pkgName) {
        try {
            return CarAppMetadataReader.findDistractionOptimizedActivities(mContext, pkgName);
        } catch (NameNotFoundException e) {
            return null;
        }
    }
    /**
     * Reading policy and setting policy can take time. Run it in a separate handler thread.
     */
    private class PackageHandler extends Handler {
        private final int MSG_INIT = 0;
        private final int MSG_PARSE_PKG = 1;
        private final int MSG_SET_POLICY = 2;
        private final int MSG_UPDATE_POLICY = 3;
        private final int MSG_RELEASE = 4;

        private PackageHandler(Looper looper) {
            super(looper);
        }

        private void requestInit() {
            Message msg = obtainMessage(MSG_INIT);
            sendMessage(msg);
        }

        private void requestRelease() {
            removeMessages(MSG_INIT);
            removeMessages(MSG_SET_POLICY);
            removeMessages(MSG_UPDATE_POLICY);
            Message msg = obtainMessage(MSG_RELEASE);
            sendMessage(msg);
        }

        private void requestPolicySetting() {
            Message msg = obtainMessage(MSG_SET_POLICY);
            sendMessage(msg);
        }

        private void requestUpdatingPolicy(String packageName, CarAppBlockingPolicy policy,
                int flags) {
            Pair<String, CarAppBlockingPolicy> pair = new Pair<>(packageName, policy);
            Message msg = obtainMessage(MSG_UPDATE_POLICY, flags, 0, pair);
            sendMessage(msg);
        }

        private void requestParsingInstalledPkgs() {
            Message msg = obtainMessage(MSG_PARSE_PKG);
            sendMessage(msg);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_INIT:
                    doHandleInit();
                    break;
                case MSG_PARSE_PKG:
                    doParseInstalledPackages();
                    break;
                case MSG_SET_POLICY:
                    doSetPolicy();
                    break;
                case MSG_UPDATE_POLICY:
                    Pair<String, CarAppBlockingPolicy> pair =
                            (Pair<String, CarAppBlockingPolicy>) msg.obj;
                    doUpdatePolicy(pair.first, pair.second, msg.arg1);
                    break;
                case MSG_RELEASE:
                    doHandleRelease();
                    break;
            }
        }
    }

    private static class AppBlockingPackageInfoWrapper {
        private final AppBlockingPackageInfo info;
        /**
         * Whether the current info is matching with the target package in system. Mismatch can
         * happen for version out of range or signature mismatch.
         */
        private boolean isMatching;

        private AppBlockingPackageInfoWrapper(AppBlockingPackageInfo info, boolean isMatching) {
            this.info =info;
            this.isMatching = isMatching;
        }

        @Override
        public String toString() {
            return "AppBlockingPackageInfoWrapper [info=" + info + ", isMatching=" + isMatching +
                    "]";
        }
    }

    /**
     * Client policy holder per each client. Should be accessed with CarpackageManagerService.this
     * held.
     */
    private static class ClientPolicy {
        private final HashMap<String, AppBlockingPackageInfoWrapper> whitelistsMap =
                new HashMap<>();
        private final HashMap<String, AppBlockingPackageInfoWrapper> blacklistsMap =
                new HashMap<>();

        private void replaceWhitelists(AppBlockingPackageInfoWrapper[] whitelists) {
            whitelistsMap.clear();
            addToWhitelists(whitelists);
        }

        private void addToWhitelists(AppBlockingPackageInfoWrapper[] whitelists) {
            if (whitelists == null) {
                return;
            }
            for (AppBlockingPackageInfoWrapper wrapper : whitelists) {
                if (wrapper != null) {
                    whitelistsMap.put(wrapper.info.packageName, wrapper);
                }
            }
        }

        private void removeWhitelists(AppBlockingPackageInfoWrapper[] whitelists) {
            if (whitelists == null) {
                return;
            }
            for (AppBlockingPackageInfoWrapper wrapper : whitelists) {
                if (wrapper != null) {
                    whitelistsMap.remove(wrapper.info.packageName);
                }
            }
        }

        private void replaceBlacklists(AppBlockingPackageInfoWrapper[] blacklists) {
            blacklistsMap.clear();
            addToBlacklists(blacklists);
        }

        private void addToBlacklists(AppBlockingPackageInfoWrapper[] blacklists) {
            if (blacklists == null) {
                return;
            }
            for (AppBlockingPackageInfoWrapper wrapper : blacklists) {
                if (wrapper != null) {
                    blacklistsMap.put(wrapper.info.packageName, wrapper);
                }
            }
        }

        private void removeBlacklists(AppBlockingPackageInfoWrapper[] blacklists) {
            if (blacklists == null) {
                return;
            }
            for (AppBlockingPackageInfoWrapper wrapper : blacklists) {
                if (wrapper != null) {
                    blacklistsMap.remove(wrapper.info.packageName);
                }
            }
        }
    }

    private class ActivityLaunchListener
        implements SystemActivityMonitoringService.ActivityLaunchListener {
        @Override
        public void onActivityLaunch(TopTaskInfoContainer topTask) {
            blockTopActivityIfNecessary(topTask);
        }
    }

    /**
     * Listens to the UX restrictions from {@link CarUxRestrictionsManagerService} and initiates
     * checking if the foreground Activity should be blocked.
     */
    private class UxRestrictionsListener extends ICarUxRestrictionsChangeListener.Stub {
        @GuardedBy("this")
        @Nullable
        private CarUxRestrictions mCurrentUxRestrictions;
        private final CarUxRestrictionsManagerService uxRestrictionsService;

        public UxRestrictionsListener(CarUxRestrictionsManagerService service) {
            uxRestrictionsService = service;
            mCurrentUxRestrictions = uxRestrictionsService.getCurrentUxRestrictions();
        }

        @Override
        public void onUxRestrictionsChanged(CarUxRestrictions restrictions) {
            if (DBG_POLICY_CHECK) {
                Log.d(CarLog.TAG_PACKAGE, "Received uxr restrictions: "
                        + restrictions.isRequiresDistractionOptimization()
                        + " : " + restrictions.getActiveRestrictions());
            }
            synchronized (this) {
                mCurrentUxRestrictions = new CarUxRestrictions(restrictions);
            }
            checkIfTopActivityNeedsBlocking();
        }

        private void checkIfTopActivityNeedsBlocking() {
            boolean shouldCheck = false;
            synchronized (this) {
                if (mCurrentUxRestrictions != null
                        && mCurrentUxRestrictions.isRequiresDistractionOptimization()) {
                    shouldCheck = true;
                }
            }
            if (DBG_POLICY_CHECK) {
                Log.d(CarLog.TAG_PACKAGE, "block?: " + shouldCheck);
            }
            if (shouldCheck) {
                blockTopActivitiesIfNecessary();
            }
        }

        private synchronized boolean isRestricted() {
            // if current restrictions is null, try querying the service, once.
            if (mCurrentUxRestrictions == null) {
                mCurrentUxRestrictions = uxRestrictionsService.getCurrentUxRestrictions();
            }
            if (mCurrentUxRestrictions != null) {
                return mCurrentUxRestrictions.isRequiresDistractionOptimization();
            }
            // If restriction information is still not available (could happen during bootup),
            // return not restricted.  This maintains parity with previous implementation but needs
            // a revisit as we test more.
            return false;
        }
    }

    /**
     * Listens to the boot events to know when to initiate parsing installed packages.
     */
    private class BootPhaseEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DBG_POLICY_CHECK) {
                Log.d(CarLog.TAG_PACKAGE, "Received " + intent.getAction());
            }
            if (intent != null && intent.getAction().equals(Intent.ACTION_LOCKED_BOOT_COMPLETED)) {
                mHandler.requestParsingInstalledPkgs();
            }
        }
    }
}
