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

package android.support.car;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of {@link CarAppFocusManager} for embedded.
 * @hide
 */
public class CarAppFocusManagerEmbedded extends CarAppFocusManager {

    private final android.car.CarAppFocusManager mManager;

    private final Map<OnAppFocusChangedListener, OnAppFocusChangedListenerProxy>
            mChangeListeners = new HashMap<>();
    private final Map<OnAppFocusOwnershipLostListener, OnAppFocusOwnershipLostListenerProxy>
            mOwnershipListeners = new HashMap<>();

    /**
     * @hide
     */
    CarAppFocusManagerEmbedded(Object manager) {
        mManager = (android.car.CarAppFocusManager) manager;
    }

    @Override
    public void addFocusListener(OnAppFocusChangedListener listener, int appType)
            throws CarNotConnectedException {
        if (listener == null) {
            throw new IllegalArgumentException("null listener");
        }
        OnAppFocusChangedListenerProxy proxy;
        synchronized (this) {
            proxy = mChangeListeners.get(listener);
            if (proxy == null) {
                proxy = new OnAppFocusChangedListenerProxy(this, listener);
                mChangeListeners.put(listener, proxy);
            }
        }
        try {
            mManager.addFocusListener(proxy, appType);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public void removeFocusListener(OnAppFocusChangedListener listener, int appType)
            throws CarNotConnectedException {
        OnAppFocusChangedListenerProxy proxy;
        synchronized (this) {
            proxy = mChangeListeners.get(listener);
            if (proxy == null) {
                return;
            }
        }
        try {
            mManager.removeFocusListener(proxy, appType);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public void removeFocusListener(OnAppFocusChangedListener listener)
            throws CarNotConnectedException {
        OnAppFocusChangedListenerProxy proxy;
        synchronized (this) {
            proxy = mChangeListeners.remove(listener);
            if (proxy == null) {
                return;
            }
        }
        try {
            mManager.removeFocusListener(proxy);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public boolean isOwningFocus(int appType, OnAppFocusOwnershipLostListener listener)
            throws CarNotConnectedException {
        OnAppFocusOwnershipLostListenerProxy proxy;
        synchronized (this) {
            proxy = mOwnershipListeners.get(listener);
            if (proxy == null) {
                return false;
            }
        }
        try {
            return mManager.isOwningFocus(proxy, appType);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public int requestAppFocus(int appType, OnAppFocusOwnershipLostListener ownershipListener)
            throws IllegalStateException, SecurityException, CarNotConnectedException {
        if (ownershipListener == null) {
            throw new IllegalArgumentException("null listener");
        }
        OnAppFocusOwnershipLostListenerProxy proxy;
        synchronized (this) {
            proxy = mOwnershipListeners.get(ownershipListener);
            if (proxy == null) {
                proxy = new OnAppFocusOwnershipLostListenerProxy(this, ownershipListener);
                mOwnershipListeners.put(ownershipListener, proxy);
            }
        }
        try {
            return mManager.requestAppFocus(appType, proxy);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public void abandonAppFocus(OnAppFocusOwnershipLostListener ownershipListener, int appType)
            throws CarNotConnectedException {
        if (ownershipListener == null) {
            throw new IllegalArgumentException("null listener");
        }
        OnAppFocusOwnershipLostListenerProxy proxy;
        synchronized (this) {
            proxy = mOwnershipListeners.get(ownershipListener);
            if (proxy == null) {
                return;
            }
        }
        try {
            mManager.abandonAppFocus(proxy, appType);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public void abandonAppFocus(OnAppFocusOwnershipLostListener ownershipListener)
            throws CarNotConnectedException {
        if (ownershipListener == null) {
            throw new IllegalArgumentException("null listener");
        }
        OnAppFocusOwnershipLostListenerProxy proxy;
        synchronized (this) {
            proxy = mOwnershipListeners.get(ownershipListener);
            if (proxy == null) {
                return;
            }
        }
        try {
            mManager.abandonAppFocus(proxy);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public void onCarDisconnected() {
        // nothing to do
    }

    private static class OnAppFocusChangedListenerProxy
            implements android.car.CarAppFocusManager.OnAppFocusChangedListener {

        private final OnAppFocusChangedListener mListener;
        private final CarAppFocusManager mManager;

        OnAppFocusChangedListenerProxy(CarAppFocusManager manager,
                OnAppFocusChangedListener listener) {
            mManager = manager;
            mListener = listener;
        }

        @Override
        public void onAppFocusChanged(int appType, boolean active) {
            mListener.onAppFocusChanged(mManager, appType, active);
        }
    }

    private static class OnAppFocusOwnershipLostListenerProxy
            implements android.car.CarAppFocusManager.OnAppFocusOwnershipLostListener {

        private final OnAppFocusOwnershipLostListener mListener;
        private final CarAppFocusManager mManager;


        OnAppFocusOwnershipLostListenerProxy(CarAppFocusManager manager,
                OnAppFocusOwnershipLostListener listener) {
            mListener = listener;
            mManager = manager;
        }

        @Override
        public void onAppFocusOwnershipLost(int focus) {
            mListener.onAppFocusOwnershipLost(mManager, focus);
        }
    }
}
