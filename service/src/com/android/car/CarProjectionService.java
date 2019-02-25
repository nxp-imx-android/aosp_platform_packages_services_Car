/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.car;

import static android.car.CarProjectionManager.PROJECTION_LONG_PRESS_VOICE_SEARCH;
import static android.car.CarProjectionManager.PROJECTION_VOICE_SEARCH;
import static android.car.CarProjectionManager.ProjectionAccessPointCallback.ERROR_GENERIC;
import static android.car.projection.ProjectionStatus.PROJECTION_STATE_INACTIVE;
import static android.net.wifi.WifiManager.EXTRA_PREVIOUS_WIFI_AP_STATE;
import static android.net.wifi.WifiManager.EXTRA_WIFI_AP_FAILURE_REASON;
import static android.net.wifi.WifiManager.EXTRA_WIFI_AP_INTERFACE_NAME;
import static android.net.wifi.WifiManager.EXTRA_WIFI_AP_MODE;
import static android.net.wifi.WifiManager.EXTRA_WIFI_AP_STATE;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_DISABLED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_ENABLED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_ENABLING;

import android.annotation.Nullable;
import android.bluetooth.BluetoothDevice;
import android.car.CarProjectionManager;
import android.car.CarProjectionManager.ProjectionAccessPointCallback;
import android.car.ICarProjection;
import android.car.ICarProjectionCallback;
import android.car.ICarProjectionStatusListener;
import android.car.projection.ProjectionStatus;
import android.car.projection.ProjectionStatus.ProjectionState;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.GroupCipher;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiConfiguration.PairwiseCipher;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.LocalOnlyHotspotCallback;
import android.net.wifi.WifiManager.LocalOnlyHotspotReservation;
import android.net.wifi.WifiManager.SoftApCallback;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Car projection service allows to bound to projected app to boost it prioirity.
 * It also enables proejcted applications to handle voice action requests.
 */
class CarProjectionService extends ICarProjection.Stub implements CarServiceBase,
        BinderInterfaceContainer.BinderEventHandler<ICarProjectionCallback> {
    private static final String TAG = CarLog.TAG_PROJECTION;
    private static final boolean DBG = true;

    private final ProjectionCallbackHolder mProjectionCallbacks;
    private final CarInputService mCarInputService;
    private final CarBluetoothService mCarBluetoothService;
    private final Context mContext;
    private final WifiManager mWifiManager;
    private final Handler mHandler;
    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final HashMap<IBinder, WirelessClient> mWirelessClients = new HashMap<>();

    @GuardedBy("mLock")
    private @Nullable LocalOnlyHotspotReservation mLocalOnlyHotspotReservation;


    @GuardedBy("mLock")
    private @Nullable SoftApCallback mSoftApCallback;

    @GuardedBy("mLock")
    private final HashMap<IBinder, ProjectionReceiverClient> mProjectionReceiverClients =
            new HashMap<>();

    @Nullable
    private String mApBssid;


    @GuardedBy("mLock")
    private @ProjectionState int mCurrentProjectionState = PROJECTION_STATE_INACTIVE;


    @GuardedBy("mLock")
    private @Nullable String mCurrentProjectionPackage;

    private final List<ICarProjectionStatusListener> mProjectionStatusListeners =
            new CopyOnWriteArrayList<>();


    private static final int WIFI_MODE_TETHERED = 1;
    private static final int WIFI_MODE_LOCALONLY = 2;

    private static final int RAND_SSID_INT_MIN = 1000;
    private static final int RAND_SSID_INT_MAX = 9999;

    // Could be one of the WIFI_MODE_* constants.
    // TODO: read this from user settings, support runtime switch
    private int mWifiMode = WIFI_MODE_LOCALONLY;

    private final WifiConfiguration mProjectionWifiConfiguration;

    private final Runnable mVoiceAssistantKeyListener =
            () -> handleVoiceAssistantRequest(false);

    private final Runnable mLongVoiceAssistantKeyListener =
            () -> handleVoiceAssistantRequest(true);

    private final ServiceConnection mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service) {
                synchronized (mLock) {
                    mBound = true;
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName className) {
                // Service has crashed.
                Log.w(CarLog.TAG_PROJECTION, "Service disconnected: " + className);
                synchronized (mLock) {
                    mRegisteredService = null;
                }
                unbindServiceIfBound();
            }
        };

    private boolean mBound;
    private Intent mRegisteredService;

    CarProjectionService(Context context, @Nullable Handler handler,
            CarInputService carInputService, CarBluetoothService carBluetoothService) {
        mContext = context;
        mHandler = handler == null ? new Handler() : handler;
        mCarInputService = carInputService;
        mCarBluetoothService = carBluetoothService;
        mProjectionCallbacks = new ProjectionCallbackHolder(this);
        mWifiManager = context.getSystemService(WifiManager.class);
        mProjectionWifiConfiguration = createWifiConfiguration(context);
    }

    @Override
    public void registerProjectionRunner(Intent serviceIntent) {
        ICarImpl.assertProjectionPermission(mContext);
        // We assume one active projection app running in the system at one time.
        synchronized (mLock) {
            if (serviceIntent.filterEquals(mRegisteredService) && mBound) {
                return;
            }
            if (mRegisteredService != null) {
                Log.w(CarLog.TAG_PROJECTION, "Registering new service[" + serviceIntent
                        + "] while old service[" + mRegisteredService + "] is still running");
            }
            unbindServiceIfBound();
        }
        bindToService(serviceIntent);
    }

    @Override
    public void unregisterProjectionRunner(Intent serviceIntent) {
        ICarImpl.assertProjectionPermission(mContext);
        synchronized (mLock) {
            if (!serviceIntent.filterEquals(mRegisteredService)) {
                Log.w(CarLog.TAG_PROJECTION, "Request to unbind unregistered service["
                        + serviceIntent + "]. Registered service[" + mRegisteredService + "]");
                return;
            }
            mRegisteredService = null;
        }
        unbindServiceIfBound();
    }

    private void bindToService(Intent serviceIntent) {
        synchronized (mLock) {
            mRegisteredService = serviceIntent;
        }
        UserHandle userHandle = UserHandle.getUserHandleForUid(Binder.getCallingUid());
        mContext.bindServiceAsUser(serviceIntent, mConnection, Context.BIND_AUTO_CREATE,
                userHandle);
    }

    private void unbindServiceIfBound() {
        synchronized (mLock) {
            if (!mBound) {
                return;
            }
            mBound = false;
            mRegisteredService = null;
        }
        mContext.unbindService(mConnection);
    }

    private void handleVoiceAssistantRequest(boolean isTriggeredByLongPress) {
        Log.i(TAG, "Voice assistant request, long press = " + isTriggeredByLongPress);
        synchronized (mLock) {
            for (BinderInterfaceContainer.BinderInterface<ICarProjectionCallback> listener :
                    mProjectionCallbacks.getInterfaces()) {
                ProjectionCallback projectionCallback = (ProjectionCallback) listener;
                if ((projectionCallback.hasFilter(PROJECTION_LONG_PRESS_VOICE_SEARCH)
                        && isTriggeredByLongPress)
                        || (projectionCallback.hasFilter(PROJECTION_VOICE_SEARCH)
                        && !isTriggeredByLongPress)) {
                    dispatchVoiceAssistantRequest(
                            projectionCallback.binderInterface, isTriggeredByLongPress);
                }
            }
        }
    }

    @Override
    public void registerProjectionListener(ICarProjectionCallback callback, int filter) {
        ICarImpl.assertProjectionPermission(mContext);
        synchronized (mLock) {
            ProjectionCallback info = mProjectionCallbacks.get(callback);
            if (info == null) {
                info = new ProjectionCallback(mProjectionCallbacks, callback, filter);
                mProjectionCallbacks.addBinderInterface(info);
            } else {
                info.setFilter(filter);
            }
        }
        updateCarInputServiceListeners();
    }

    @Override
    public void unregisterProjectionListener(ICarProjectionCallback listener) {
        ICarImpl.assertProjectionPermission(mContext);
        synchronized (mLock) {
            mProjectionCallbacks.removeBinder(listener);
        }
        updateCarInputServiceListeners();
    }

    @Override
    public void startProjectionAccessPoint(final Messenger messenger, IBinder binder)
            throws RemoteException {
        ICarImpl.assertProjectionPermission(mContext);
        //TODO: check if access point already started with the desired configuration.
        registerWirelessClient(WirelessClient.of(messenger, binder));
        startAccessPoint();
    }

    @Override
    public void stopProjectionAccessPoint(IBinder token) {
        ICarImpl.assertProjectionPermission(mContext);
        Log.i(TAG, "Received stop access point request from " + token);

        boolean shouldReleaseAp;
        synchronized (mLock) {
            if (!unregisterWirelessClientLocked(token)) {
                Log.w(TAG, "Client " + token + " was not registered");
                return;
            }
            shouldReleaseAp = mWirelessClients.isEmpty();
        }

        if (shouldReleaseAp) {
            stopAccessPoint();
        }
    }

    /**
     * Request to disconnect the given profile on the given device, and prevent it from reconnecting
     * until either the request is released, or the process owning the given token dies.
     *
     * @param device  The device on which to inhibit a profile.
     * @param profile The {@link android.bluetooth.BluetoothProfile} to inhibit.
     * @param token   A {@link IBinder} to be used as an identity for the request. If the process
     *                owning the token dies, the request will automatically be released.
     * @return True if the profile was successfully inhibited, false if an error occurred.
     */
    @Override
    public boolean requestBluetoothProfileInhibit(
            BluetoothDevice device, int profile, IBinder token) {
        if (DBG) {
            Log.d(TAG, "requestBluetoothProfileInhibit device=" + device + " profile=" + profile
                    + " from uid " + Binder.getCallingUid());
        }
        ICarImpl.assertProjectionPermission(mContext);
        try {
            if (device == null) {
                // Will be caught by AIDL and thrown to caller.
                throw new NullPointerException("Device must not be null");
            }
            if (token == null) {
                throw new NullPointerException("Token must not be null");
            }
            return mCarBluetoothService.requestProfileInhibit(device, profile, token);
        } catch (RuntimeException e) {
            Log.e(TAG, "Error in requestBluetoothProfileInhibit", e);
            throw e;
        }
    }

    /**
     * Release an inhibit request made by {@link #requestBluetoothProfileInhibit}, and reconnect the
     * profile if no other inhibit requests are active.
     *
     * @param device  The device on which to release the inhibit request.
     * @param profile The profile on which to release the inhibit request.
     * @param token   The token provided in the original call to
     *                {@link #requestBluetoothProfileInhibit}.
     * @return True if the request was released, false if an error occurred.
     */
    @Override
    public boolean releaseBluetoothProfileInhibit(
            BluetoothDevice device, int profile, IBinder token) {
        if (DBG) {
            Log.d(TAG, "releaseBluetoothProfileInhibit device=" + device + " profile=" + profile
                    + " from uid " + Binder.getCallingUid());
        }
        ICarImpl.assertProjectionPermission(mContext);
        try {
            if (device == null) {
                // Will be caught by AIDL and thrown to caller.
                throw new NullPointerException("Device must not be null");
            }
            if (token == null) {
                throw new NullPointerException("Token must not be null");
            }
            return mCarBluetoothService.releaseProfileInhibit(device, profile, token);
        } catch (RuntimeException e) {
            Log.e(TAG, "Error in releaseBluetoothProfileInhibit", e);
            throw e;
        }
    }

    @Override
    public void updateProjectionStatus(ProjectionStatus status, IBinder token)
            throws RemoteException {
        if (DBG) {
            Log.d(TAG, "updateProjectionStatus, status: " + status + ", token: " + token);
        }
        ICarImpl.assertProjectionPermission(mContext);
        final String packageName = status.getPackageName();
        final int uid = Binder.getCallingUid();
        try {
            if (uid != mContext.getPackageManager().getPackageUid(packageName, 0)) {
                throw new SecurityException(
                        "UID " + uid + " cannot update status for package " + packageName);
            }
        } catch (PackageManager.NameNotFoundException e) {
            throw new SecurityException("Package " + packageName + " does not exist", e);
        }

        synchronized (mLock) {
            ProjectionReceiverClient client = getOrCreateProjectionReceiverClientLocked(token);
            client.mProjectionStatus = status;

            if (status.isActive() || TextUtils.equals(packageName, mCurrentProjectionPackage)) {
                mCurrentProjectionState = status.getState();
                mCurrentProjectionPackage = packageName;
            }
        }
        notifyProjectionStatusChanged(null /* notify all listeners */);
    }

    @Override
    public void registerProjectionStatusListener(ICarProjectionStatusListener listener)
            throws RemoteException {
        ICarImpl.assertProjectionStatusPermission(mContext);
        mProjectionStatusListeners.add(listener);

        // Immediately notify listener with the current status.
        notifyProjectionStatusChanged(listener);
    }

    @Override
    public void unregisterProjectionStatusListener(ICarProjectionStatusListener listener)
            throws RemoteException {
        ICarImpl.assertProjectionStatusPermission(mContext);
        mProjectionStatusListeners.remove(listener);
    }

    private ProjectionReceiverClient getOrCreateProjectionReceiverClientLocked(
            IBinder token) throws RemoteException {
        ProjectionReceiverClient client;
        client = mProjectionReceiverClients.get(token);
        if (client == null) {
            client = new ProjectionReceiverClient(() -> unregisterProjectionReceiverClient(token));
            token.linkToDeath(client.mDeathRecipient, 0 /* flags */);
            mProjectionReceiverClients.put(token, client);
        }
        return client;
    }

    private void unregisterProjectionReceiverClient(IBinder token) {
        synchronized (mLock) {
            ProjectionReceiverClient client = mProjectionReceiverClients.remove(token);
            if (client != null && TextUtils.equals(
                    client.mProjectionStatus.getPackageName(), mCurrentProjectionPackage)) {
                mCurrentProjectionPackage = null;
                mCurrentProjectionState = PROJECTION_STATE_INACTIVE;
            }
        }
    }

    private void notifyProjectionStatusChanged(
            @Nullable ICarProjectionStatusListener singleListenerToNotify)
            throws RemoteException {
        int currentState;
        String currentPackage;
        List<ProjectionStatus> statuses = new ArrayList<>();
        synchronized (mLock) {
            for (ProjectionReceiverClient client : mProjectionReceiverClients.values()) {
                statuses.add(client.mProjectionStatus);
            }
            currentState = mCurrentProjectionState;
            currentPackage = mCurrentProjectionPackage;
        }

        if (DBG) {
            Log.d(TAG, "Notify projection status change, state: " + currentState + ", pkg: "
                    + currentPackage + ", listeners: " + mProjectionStatusListeners.size()
                    + ", listenerToNotify: " + singleListenerToNotify);
        }

        if (singleListenerToNotify == null) {
            for (ICarProjectionStatusListener listener : mProjectionStatusListeners) {
                listener.onProjectionStatusChanged(currentState, currentPackage, statuses);
            }
        } else {
            singleListenerToNotify.onProjectionStatusChanged(
                    currentState, currentPackage, statuses);
        }
    }

    private void startAccessPoint() {
        synchronized (mLock) {
            switch (mWifiMode) {
                case WIFI_MODE_LOCALONLY: {
                    startLocalOnlyApLocked();
                    break;
                }
                case WIFI_MODE_TETHERED: {
                    startTetheredApLocked();
                    break;
                }
                default: {
                    Log.wtf(TAG, "Unexpected Access Point mode during starting: " + mWifiMode);
                    break;
                }
            }
        }
    }

    private void stopAccessPoint() {
        sendApStopped();

        synchronized (mLock) {
            switch (mWifiMode) {
                case WIFI_MODE_LOCALONLY: {
                    stopLocalOnlyApLocked();
                    break;
                }
                case WIFI_MODE_TETHERED: {
                    stopTetheredApLocked();
                    break;
                }
                default: {
                    Log.wtf(TAG, "Unexpected Access Point mode during stopping : " + mWifiMode);
                }
            }
        }
    }

    private void startTetheredApLocked() {
        Log.d(TAG, "startTetheredApLocked");

        final SoftApCallback callback = new ProjectionSoftApCallback();
        mWifiManager.registerSoftApCallback(callback, mHandler);

        if (!mWifiManager.startSoftAp(mProjectionWifiConfiguration)) {
            Log.e(TAG, "Failed to start soft AP");
            mWifiManager.unregisterSoftApCallback(callback);
            sendApFailed(ERROR_GENERIC);
        } else {
            mSoftApCallback = callback;
        }
    }

    private void stopTetheredApLocked() {
        Log.d(TAG, "stopTetheredAp");

        if (mSoftApCallback != null) {
            mWifiManager.unregisterSoftApCallback(mSoftApCallback);
            mSoftApCallback = null;
            if (!mWifiManager.stopSoftAp()) {
                Log.w(TAG, "Failed to request soft AP to stop.");
            }
        }
    }

    private void startLocalOnlyApLocked() {
        if (mLocalOnlyHotspotReservation != null) {
            Log.i(TAG, "Local-only hotspot is already registered.");
            sendApStarted(mLocalOnlyHotspotReservation.getWifiConfiguration());
            return;
        }

        Log.i(TAG, "Requesting to start local-only hotspot.");
        mWifiManager.startLocalOnlyHotspot(new LocalOnlyHotspotCallback() {
            @Override
            public void onStarted(LocalOnlyHotspotReservation reservation) {
                Log.d(TAG, "Local-only hotspot started");
                synchronized (mLock) {
                    mLocalOnlyHotspotReservation = reservation;
                }
                sendApStarted(reservation.getWifiConfiguration());
            }

            @Override
            public void onStopped() {
                Log.i(TAG, "Local-only hotspot stopped.");
                synchronized (mLock) {
                    mLocalOnlyHotspotReservation = null;
                }
                sendApStopped();
            }

            @Override
            public void onFailed(int localonlyHostspotFailureReason) {
                Log.w(TAG, "Local-only hotspot failed, reason: "
                        + localonlyHostspotFailureReason);
                synchronized (mLock) {
                    mLocalOnlyHotspotReservation = null;
                }
                int reason;
                switch (localonlyHostspotFailureReason) {
                    case LocalOnlyHotspotCallback.ERROR_NO_CHANNEL:
                        reason = ProjectionAccessPointCallback.ERROR_NO_CHANNEL;
                        break;
                    case LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED:
                        reason = ProjectionAccessPointCallback.ERROR_TETHERING_DISALLOWED;
                        break;
                    case LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE:
                        reason = ProjectionAccessPointCallback.ERROR_INCOMPATIBLE_MODE;
                        break;
                    default:
                        reason = ProjectionAccessPointCallback.ERROR_GENERIC;

                }
                sendApFailed(reason);
            }
        }, mHandler);
    }

    private void stopLocalOnlyApLocked() {
        Log.i(TAG, "stopLocalOnlyApLocked");

        if (mLocalOnlyHotspotReservation == null) {
            Log.w(TAG, "Requested to stop local-only hotspot which was already stopped.");
            return;
        }

        mLocalOnlyHotspotReservation.close();
        mLocalOnlyHotspotReservation = null;
    }

    private void sendApStarted(WifiConfiguration wifiConfiguration) {
        WifiConfiguration localWifiConfig = new WifiConfiguration(wifiConfiguration);
        localWifiConfig.BSSID = mApBssid;

        Message message = Message.obtain();
        message.what = CarProjectionManager.PROJECTION_AP_STARTED;
        message.obj = localWifiConfig;
        Log.i(TAG, "Sending PROJECTION_AP_STARTED, ssid: "
                + localWifiConfig.getPrintableSsid()
                + ", apBand: " + localWifiConfig.apBand
                + ", apChannel: " + localWifiConfig.apChannel
                + ", bssid: " + localWifiConfig.BSSID);
        sendApStatusMessage(message);
    }

    private void sendApStopped() {
        Message message = Message.obtain();
        message.what = CarProjectionManager.PROJECTION_AP_STOPPED;
        sendApStatusMessage(message);
        unregisterWirelessClients();
    }

    private void sendApFailed(int reason) {
        Message message = Message.obtain();
        message.what = CarProjectionManager.PROJECTION_AP_FAILED;
        message.arg1 = reason;
        sendApStatusMessage(message);
        unregisterWirelessClients();
    }

    private void sendApStatusMessage(Message message) {
        List<WirelessClient> clients;
        synchronized (mLock) {
            clients = new ArrayList<>(mWirelessClients.values());
        }
        for (WirelessClient client : clients) {
            client.send(message);
        }
    }

    private void updateCarInputServiceListeners() {
        boolean listenShortPress = false;
        boolean listenLongPress = false;
        synchronized (mLock) {
            for (BinderInterfaceContainer.BinderInterface<ICarProjectionCallback> listener :
                         mProjectionCallbacks.getInterfaces()) {
                ProjectionCallback projectionCallback = (ProjectionCallback) listener;
                listenShortPress |= projectionCallback.hasFilter(
                        PROJECTION_VOICE_SEARCH);
                listenLongPress |= projectionCallback.hasFilter(
                        PROJECTION_LONG_PRESS_VOICE_SEARCH);
            }
        }
        mCarInputService.setVoiceAssistantKeyListener(listenShortPress
                ? mVoiceAssistantKeyListener : null);
        mCarInputService.setLongVoiceAssistantKeyListener(listenLongPress
                ? mLongVoiceAssistantKeyListener : null);
    }

    @Override
    public void init() {
        mContext.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        final int currState = intent.getIntExtra(EXTRA_WIFI_AP_STATE,
                                WIFI_AP_STATE_DISABLED);
                        final int prevState = intent.getIntExtra(EXTRA_PREVIOUS_WIFI_AP_STATE,
                                WIFI_AP_STATE_DISABLED);
                        final int errorCode = intent.getIntExtra(EXTRA_WIFI_AP_FAILURE_REASON, 0);
                        final String ifaceName =
                                intent.getStringExtra(EXTRA_WIFI_AP_INTERFACE_NAME);
                        final int mode = intent.getIntExtra(EXTRA_WIFI_AP_MODE,
                                WifiManager.IFACE_IP_MODE_UNSPECIFIED);
                        handleWifiApStateChange(currState, prevState, errorCode, ifaceName, mode);
                    }
                },
                new IntentFilter(WifiManager.WIFI_AP_STATE_CHANGED_ACTION));
    }

    private void handleWifiApStateChange(int currState, int prevState, int errorCode,
            String ifaceName, int mode) {
        if (currState == WIFI_AP_STATE_ENABLING || currState == WIFI_AP_STATE_ENABLED) {
            Log.d(TAG,
                    "handleWifiApStateChange, curState: " + currState + ", prevState: " + prevState
                            + ", errorCode: " + errorCode + ", ifaceName: " + ifaceName + ", mode: "
                            + mode);

            try {
                NetworkInterface iface = NetworkInterface.getByName(ifaceName);
                byte[] bssid = iface.getHardwareAddress();
                mApBssid = String.format("%02x:%02x:%02x:%02x:%02x:%02x",
                        bssid[0], bssid[1], bssid[2], bssid[3], bssid[4], bssid[5]);
            } catch (SocketException e) {
                Log.e(TAG, e.toString(), e);
            }
        }
    }

    @Override
    public void release() {
        synchronized (mLock) {
            mProjectionCallbacks.clear();
        }
    }

    @Override
    public void onBinderDeath(
            BinderInterfaceContainer.BinderInterface<ICarProjectionCallback> bInterface) {
        unregisterProjectionListener(bInterface.binderInterface);
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("**CarProjectionService**");
        synchronized (mLock) {
            for (BinderInterfaceContainer.BinderInterface<ICarProjectionCallback> listener :
                         mProjectionCallbacks.getInterfaces()) {
                ProjectionCallback projectionCallback = (ProjectionCallback) listener;
                writer.println(projectionCallback.toString());
            }

            writer.println("Local-only hotspot reservation: " + mLocalOnlyHotspotReservation);
            writer.println("Wireless clients: " +  mWirelessClients.size());
            writer.println("Current wifi mode: " + mWifiMode);
            writer.println("SoftApCallback: " + mSoftApCallback);
            writer.println("Bound to projection app: " + mBound);
            writer.println("Registered Service: " + mRegisteredService);
            writer.println("Current projection state: " + mCurrentProjectionState);
            writer.println("Current projection package: " + mCurrentProjectionPackage);
            writer.println("Projection status: " + mProjectionReceiverClients);
        }
    }

    private void dispatchVoiceAssistantRequest(ICarProjectionCallback listener,
            boolean fromLongPress) {
        try {
            listener.onVoiceAssistantRequest(fromLongPress);
        } catch (RemoteException e) {
        }
    }

    private static class ProjectionCallbackHolder
            extends BinderInterfaceContainer<ICarProjectionCallback> {
        ProjectionCallbackHolder(CarProjectionService service) {
            super(service);
        }

        ProjectionCallback get(ICarProjectionCallback projectionCallback) {
            return (ProjectionCallback) getBinderInterface(projectionCallback);
        }
    }

    private static class ProjectionCallback extends
            BinderInterfaceContainer.BinderInterface<ICarProjectionCallback> {
        private int mFilter;

        private ProjectionCallback(ProjectionCallbackHolder holder, ICarProjectionCallback binder,
                int filter) {
            super(holder, binder);
            this.mFilter = filter;
        }

        private synchronized int getFilter() {
            return mFilter;
        }

        private boolean hasFilter(int filter) {
            return (getFilter() & filter) != 0;
        }

        private synchronized void setFilter(int filter) {
            mFilter = filter;
        }

        @Override
        public String toString() {
            return "ListenerInfo{filter=" + Integer.toHexString(getFilter()) + "}";
        }
    }

    private static WifiConfiguration createWifiConfiguration(Context context) {
        //TODO: consider to read current AP configuration and modify only parts that matter for
        //wireless projection (apBand, key management), do not modify password if it was set.
        WifiConfiguration config = new WifiConfiguration();
        config.apBand = WifiConfiguration.AP_BAND_5GHZ;
        config.SSID = context.getResources()
                .getString(R.string.config_TetheredProjectionAccessPointSsid)
                + "_" + getRandomIntForDefaultSsid();
        config.allowedKeyManagement.set(KeyMgmt.WPA2_PSK);
        config.allowedPairwiseCiphers.set(PairwiseCipher.CCMP);
        config.allowedGroupCiphers.set(GroupCipher.CCMP);
        config.preSharedKey = RandomPassword.generate();
        return config;
    }

    private void registerWirelessClient(WirelessClient client) throws RemoteException {
        synchronized (mLock) {
            if (unregisterWirelessClientLocked(client.token)) {
                Log.i(TAG, "Client was already registered, override it.");
            }
            mWirelessClients.put(client.token, client);
        }
        client.token.linkToDeath(new WirelessClientDeathRecipient(this, client), 0);
    }

    private void unregisterWirelessClients() {
        synchronized (mLock) {
            for (WirelessClient client: mWirelessClients.values()) {
                client.token.unlinkToDeath(client.deathRecipient, 0);
            }
            mWirelessClients.clear();
        }
    }

    private boolean unregisterWirelessClientLocked(IBinder token) {
        WirelessClient client = mWirelessClients.remove(token);
        if (client != null) {
            token.unlinkToDeath(client.deathRecipient, 0);
        }

        return client != null;
    }

    private class ProjectionSoftApCallback implements SoftApCallback {
        @Override
        public void onStateChanged(int state, int softApFailureReason) {
            Log.i(TAG, "ProjectionSoftApCallback, onStateChanged, state: " + state
                    + ", failed reason: softApFailureReason");

            switch (state) {
                case WifiManager.WIFI_AP_STATE_ENABLED: {
                    sendApStarted(mProjectionWifiConfiguration);
                    break;
                }
                case WIFI_AP_STATE_DISABLED: {
                    sendApStopped();
                    break;
                }
                case WifiManager.WIFI_AP_STATE_FAILED: {
                    Log.w(TAG, "WIFI_AP_STATE_FAILED, reason: " + softApFailureReason);
                    int reason;
                    switch (softApFailureReason) {
                        case WifiManager.SAP_START_FAILURE_NO_CHANNEL:
                            reason = ProjectionAccessPointCallback.ERROR_NO_CHANNEL;
                            break;
                        default:
                            reason = ERROR_GENERIC;
                    }
                    sendApFailed(reason);
                    break;
                }
            }
        }

        @Override
        public void onNumClientsChanged(int numClients) {
            Log.i(TAG, "ProjectionSoftApCallback, onNumClientsChanged: " + numClients);
        }
    }

    private static class WirelessClient {
        public final Messenger messenger;
        public final IBinder token;
        public @Nullable DeathRecipient deathRecipient;

        private WirelessClient(Messenger messenger, IBinder token) {
            this.messenger = messenger;
            this.token = token;
        }

        private static WirelessClient of(Messenger messenger, IBinder token) {
            return new WirelessClient(messenger, token);
        }

        void send(Message message) {
            try {
                Log.d(TAG, "Sending message " + message.what + " to " + this);
                messenger.send(message);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to send message", e);
            }
        }

        @Override
        public String toString() {
            return getClass().getSimpleName()
                    + "{token= " + token
                    + ", deathRecipient=" + deathRecipient + "}";
        }
    }

    private static class WirelessClientDeathRecipient implements DeathRecipient {
        final WeakReference<CarProjectionService> mServiceRef;
        final WirelessClient mClient;

        WirelessClientDeathRecipient(CarProjectionService service, WirelessClient client) {
            mServiceRef = new WeakReference<>(service);
            mClient = client;
            mClient.deathRecipient = this;
        }

        @Override
        public void binderDied() {
            Log.w(TAG, "Wireless client " + mClient + " died.");
            CarProjectionService service = mServiceRef.get();
            if (service == null) return;

            synchronized (service.mLock) {
                service.unregisterWirelessClientLocked(mClient.token);
            }
        }
    }

    private static class RandomPassword {
        private static final int PASSWORD_LENGTH = 12;
        private static final String PW_NUMBER = "0123456789";
        private static final String PW_LOWER_CASE = "abcdefghijklmnopqrstuvwxyz";
        private static final String PW_UPPER_CASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

        private static final char[] SYMBOLS =
                (PW_NUMBER + PW_LOWER_CASE + PW_UPPER_CASE).toCharArray();

        static String generate() {
            SecureRandom random = new SecureRandom();

            StringBuilder password = new StringBuilder();
            while (password.length() < PASSWORD_LENGTH) {
                int randomIndex = random.nextInt(SYMBOLS.length);
                password.append(SYMBOLS[randomIndex]);
            }
            return password.toString();
        }
    }

    private static int getRandomIntForDefaultSsid() {
        Random random = new Random();
        return random.nextInt((RAND_SSID_INT_MAX - RAND_SSID_INT_MIN) + 1) + RAND_SSID_INT_MIN;
    }

    private static class ProjectionReceiverClient {
        private final DeathRecipient mDeathRecipient;
        private ProjectionStatus mProjectionStatus;

        ProjectionReceiverClient(DeathRecipient deathRecipient) {
            mDeathRecipient = deathRecipient;
        }

        @Override
        public String toString() {
            return "ProjectionReceiverClient{"
                    + "mDeathRecipient=" + mDeathRecipient
                    + ", mProjectionStatus=" + mProjectionStatus
                    + '}';
        }
    }
}
