/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.vms;

import android.car.userlib.CarUserManagerHelper;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;

import com.android.car.CarServiceBase;
import com.android.car.R;
import com.android.car.hal.VmsHalService;
import com.android.car.user.CarUserService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Map;

/**
 * Manages service connections lifecycle for VMS publisher clients.
 *
 * Binds to system-level clients at boot and creates/destroys bindings for userspace clients
 * according to the Android user lifecycle.
 */
public class VmsClientManager implements CarServiceBase {
    private static final boolean DBG = false;
    private static final String TAG = "VmsClientManager";
    private static final String HAL_CLIENT_NAME = "VmsHalClient";

    /**
     * Interface for receiving updates about client connections.
     */
    public interface ConnectionListener {
        /**
         * Called when a client connection is established or re-established.
         *
         * @param clientName    String that uniquely identifies the service and user.
         * @param clientService The IBinder of the client's communication channel.
         */
        void onClientConnected(String clientName, IBinder clientService);

        /**
         * Called when a client connection is terminated.
         *
         * @param clientName String that uniquely identifies the service and user.
         */
        void onClientDisconnected(String clientName);
    }

    private final Context mContext;
    private final Handler mHandler;
    private final CarUserService mUserService;
    private final CarUserManagerHelper mUserManagerHelper;
    private final IBinder mHalClient;
    private final int mMillisBeforeRebind;

    @GuardedBy("mListeners")
    private final ArrayList<ConnectionListener> mListeners = new ArrayList<>();
    @GuardedBy("mSystemClients")
    private final Map<String, ClientConnection> mSystemClients = new ArrayMap<>();
    @GuardedBy("mCurrentUserClients")
    private final Map<String, ClientConnection> mCurrentUserClients = new ArrayMap<>();
    @GuardedBy("mCurrentUserClients")
    private int mCurrentUser;

    @VisibleForTesting
    final Runnable mSystemUserUnlockedListener = this::bindToSystemClients;
    @VisibleForTesting
    final BroadcastReceiver mUserSwitchReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DBG) Log.d(TAG, "Received " + intent);
            switch (intent.getAction()) {
                case Intent.ACTION_USER_SWITCHED:
                case Intent.ACTION_USER_UNLOCKED:
                    bindToSystemClients();
                    bindToCurrentUserClients();
                    break;
                default:
                    Log.e(TAG, "Unexpected intent received: " + intent);
            }
        }
    };

    /**
     * Constructor for client managers.
     *
     * @param context           Context to use for registering receivers and binding services.
     * @param userService       User service for registering system unlock listener.
     * @param userManagerHelper User manager for querying current user state.
     * @param halService        Service providing the HAL client interface
     */
    public VmsClientManager(Context context, CarUserService userService,
            CarUserManagerHelper userManagerHelper, VmsHalService halService) {
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
        mUserService = userService;
        mUserManagerHelper = userManagerHelper;
        mHalClient = halService.getPublisherClient();
        mMillisBeforeRebind = mContext.getResources().getInteger(
                com.android.car.R.integer.millisecondsBeforeRebindToVmsPublisher);
    }

    @Override
    public void init() {
        mUserService.runOnUser0Unlock(mSystemUserUnlockedListener);

        IntentFilter userSwitchFilter = new IntentFilter();
        userSwitchFilter.addAction(Intent.ACTION_USER_SWITCHED);
        userSwitchFilter.addAction(Intent.ACTION_USER_UNLOCKED);
        mContext.registerReceiverAsUser(mUserSwitchReceiver, UserHandle.ALL, userSwitchFilter, null,
                null);
    }

    @Override
    public void release() {
        mContext.unregisterReceiver(mUserSwitchReceiver);
        notifyListenersOnClientDisconnected(HAL_CLIENT_NAME);
        synchronized (mSystemClients) {
            terminate(mSystemClients);
        }
        synchronized (mCurrentUserClients) {
            terminate(mCurrentUserClients);
        }
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("*" + getClass().getSimpleName() + "*");
        writer.println("mListeners:" + mListeners);
        writer.println("mSystemClients:" + mSystemClients.keySet());
        writer.println("mCurrentUser:" + mCurrentUser);
        writer.println("mCurrentUserClients:" + mCurrentUserClients.keySet());
    }

    /**
     * Registers a new client connection state listener.
     *
     * @param listener Listener to register.
     */
    public void registerConnectionListener(ConnectionListener listener) {
        synchronized (mListeners) {
            if (!mListeners.contains(listener)) {
                mListeners.add(listener);
            }
        }
        notifyListenerOfConnectedClients(listener);
    }

    /**
     * Unregisters a client connection state listener.
     *
     * @param listener Listener to remove.
     */
    public void unregisterConnectionListener(ConnectionListener listener) {
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }

    private void bindToSystemClients() {
        String[] clientNames = mContext.getResources().getStringArray(
                R.array.vmsPublisherSystemClients);
        Log.i(TAG, "Attempting to bind " + clientNames.length + " system client(s)");
        synchronized (mSystemClients) {
            for (String clientName : clientNames) {
                bind(mSystemClients, clientName, UserHandle.SYSTEM);
            }
        }
    }

    private void bindToCurrentUserClients() {
        int currentUserId = mUserManagerHelper.getCurrentForegroundUserId();
        synchronized (mCurrentUserClients) {
            if (mCurrentUser != currentUserId) {
                terminate(mCurrentUserClients);
            }
            mCurrentUser = currentUserId;

            // To avoid the risk of double-binding, clients running as the system user must only
            // ever be bound in bindToSystemClients().
            // In a headless multi-user system, the system user will never be in the foreground.
            if (mCurrentUser == UserHandle.USER_SYSTEM) {
                Log.e(TAG, "System user in foreground. Userspace clients will not be bound.");
                return;
            }

            String[] clientNames = mContext.getResources().getStringArray(
                    R.array.vmsPublisherUserClients);
            Log.i(TAG, "Attempting to bind " + clientNames.length + " user client(s)");
            UserHandle currentUserHandle = UserHandle.of(mCurrentUser);
            for (String clientName : clientNames) {
                bind(mCurrentUserClients, clientName, currentUserHandle);
            }
        }
    }

    private void bind(Map<String, ClientConnection> connectionMap, String clientName,
            UserHandle userHandle) {
        if (connectionMap.containsKey(clientName)) {
            Log.i(TAG, "Already bound: " + clientName);
            return;
        }

        ComponentName name = ComponentName.unflattenFromString(clientName);
        if (name == null) {
            Log.e(TAG, "Invalid client name: " + clientName);
            return;
        }

        if (!mContext.getPackageManager().isPackageAvailable(name.getPackageName())) {
            Log.w(TAG, "Client not installed: " + clientName);
            return;
        }

        ClientConnection connection = new ClientConnection(name, userHandle);
        if (connection.bind()) {
            Log.i(TAG, "Client bound: " + connection);
            connectionMap.put(clientName, connection);
        } else {
            Log.w(TAG, "Binding failed: " + connection);
        }
    }

    private void terminate(Map<String, ClientConnection> connectionMap) {
        connectionMap.values().forEach(ClientConnection::terminate);
        connectionMap.clear();
    }

    private void notifyListenerOfConnectedClients(ConnectionListener listener) {
        listener.onClientConnected(HAL_CLIENT_NAME, mHalClient);
        synchronized (mSystemClients) {
            mSystemClients.values().forEach(conn -> conn.notifyIfConnected(listener));
        }
        synchronized (mCurrentUserClients) {
            mCurrentUserClients.values().forEach(conn -> conn.notifyIfConnected(listener));
        }
    }

    private void notifyListenersOnClientConnected(String clientName, IBinder clientService) {
        synchronized (mListeners) {
            for (ConnectionListener listener : mListeners) {
                listener.onClientConnected(clientName, clientService);
            }
        }
    }

    private void notifyListenersOnClientDisconnected(String clientName) {
        synchronized (mListeners) {
            for (ConnectionListener listener : mListeners) {
                listener.onClientDisconnected(clientName);
            }
        }
    }

    class ClientConnection implements ServiceConnection {
        private final ComponentName mName;
        private final UserHandle mUser;
        private final String mFullName;
        private boolean mIsBound = false;
        private boolean mIsTerminated = false;
        private IBinder mClientService;

        ClientConnection(ComponentName name, UserHandle user) {
            mName = name;
            mUser = user;
            mFullName = mName.flattenToString() + " U=" + mUser.getIdentifier();
        }

        synchronized boolean bind() {
            if (mIsBound) {
                return true;
            }
            if (mIsTerminated) {
                return false;
            }

            if (DBG) Log.d(TAG, "binding: " + mFullName);
            Intent intent = new Intent();
            intent.setComponent(mName);
            try {
                mIsBound = mContext.bindServiceAsUser(intent, this, Context.BIND_AUTO_CREATE,
                        mHandler, mUser);
            } catch (SecurityException e) {
                Log.e(TAG, "While binding " + mFullName, e);
            }

            return mIsBound;
        }

        synchronized void unbind() {
            if (!mIsBound) {
                return;
            }

            if (DBG) Log.d(TAG, "unbinding: " + mFullName);
            try {
                mContext.unbindService(this);
            } catch (Throwable t) {
                Log.e(TAG, "While unbinding " + mFullName, t);
            }
            mIsBound = false;
            if (mClientService != null) {
                notifyListenersOnClientDisconnected(mFullName);
            }
            mClientService = null;
        }

        synchronized void rebind() {
            unbind();
            if (DBG) {
                Log.d(TAG,
                        String.format("rebinding %s after %dms", mFullName, mMillisBeforeRebind));
            }
            if (!mIsTerminated) {
                mHandler.postDelayed(this::bind, mMillisBeforeRebind);
            }
        }

        synchronized void terminate() {
            if (DBG) Log.d(TAG, "terminating: " + mFullName);
            mIsTerminated = true;
            unbind();
        }

        synchronized void notifyIfConnected(ConnectionListener listener) {
            if (mClientService != null) {
                listener.onClientConnected(mFullName, mClientService);
            }
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DBG) Log.d(TAG, "onServiceConnected: " + mFullName);
            mClientService = service;
            notifyListenersOnClientConnected(mFullName, mClientService);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (DBG) Log.d(TAG, "onServiceDisconnected: " + mFullName);
            rebind();
        }

        @Override
        public String toString() {
            return mFullName;
        }
    }
}
