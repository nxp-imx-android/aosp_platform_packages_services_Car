/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.annotation.Nullable;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.CarDrivingStateEvent.CarDrivingState;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictions.CarUxRestrictionsInfo;
import android.car.drivingstate.ICarDrivingStateChangeListener;
import android.car.drivingstate.ICarUxRestrictionsChangeListener;
import android.car.drivingstate.ICarUxRestrictionsManager;
import android.car.hardware.CarSensorEvent;
import android.car.hardware.CarSensorManager;
import android.car.hardware.ICarSensorEventListener;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * A service that listens to current driving state of the vehicle and maps it to the
 * appropriate UX restrictions for that driving state.
 */
public class CarUxRestrictionsManagerService extends ICarUxRestrictionsManager.Stub implements
        CarServiceBase {
    private static final String TAG = "CarUxR";
    private static final boolean DBG = false;
    // Default parameters to some of the UX restrictions if not configured in
    // car_ux_restrictions_map.xml
    static final int DEFAULT_MAX_LENGTH = 80;
    static final int DEFAULT_MAX_CUMULATIVE_ITEMS = 50;
    static final int DEFAULT_MAX_CONTENT_DEPTH = 3;

    private final Context mContext;
    private final CarDrivingStateService mDrivingStateService;
    private final CarSensorService mCarSensorService;
    private final CarUxRestrictionsServiceHelper mHelper;
    // List of clients listening to UX restriction events.
    private final List<UxRestrictionsClient> mUxRClients = new ArrayList<>();
    private CarUxRestrictions mCurrentUxRestrictions;
    private float mCurrentMovingSpeed;
    private boolean mFallbackToDefaults;

    public CarUxRestrictionsManagerService(Context context, CarDrivingStateService drvService,
            CarSensorService sensorService) {
        mContext = context;
        mDrivingStateService = drvService;
        mCarSensorService = sensorService;
        mHelper = new CarUxRestrictionsServiceHelper(mContext, R.xml.car_ux_restrictions_map);
        // Unrestricted until driving state information is received. During boot up, we don't want
        // everything to be blocked until data is available from CarSensorManager.  If we start
        // driving and we don't get speed or gear information, we have bigger problems.
        mCurrentUxRestrictions = createUxRestrictionsEvent(false,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE);
    }

    @Override
    public void init() {
        try {
            if (!mHelper.loadUxRestrictionsFromXml()) {
                Log.e(TAG, "Error reading Ux Restrictions Mapping. Falling back to defaults");
                mFallbackToDefaults = true;
            }
        } catch (IOException | XmlPullParserException e) {
            Log.e(TAG, "Exception reading UX restrictions XML mapping", e);
            mFallbackToDefaults = true;
        }
        // subscribe to driving State
        mDrivingStateService.registerDrivingStateChangeListener(
                mICarDrivingStateChangeEventListener);
        // subscribe to Sensor service for speed
        mCarSensorService.registerOrUpdateSensorListener(CarSensorManager.SENSOR_TYPE_CAR_SPEED,
                CarSensorManager.SENSOR_RATE_FASTEST, mICarSensorEventListener);
    }

    @Override
    public synchronized void release() {
        for (UxRestrictionsClient client : mUxRClients) {
            client.listenerBinder.unlinkToDeath(client, 0);
        }
        mUxRClients.clear();
        mDrivingStateService.unregisterDrivingStateChangeListener(
                mICarDrivingStateChangeEventListener);
    }

    // Binder methods

    /**
     * Register a {@link ICarUxRestrictionsChangeListener} to be notified for changes to the UX
     * restrictions
     *
     * @param listener listener to register
     */
    @Override
    public synchronized void registerUxRestrictionsChangeListener(
            ICarUxRestrictionsChangeListener listener) {
        if (listener == null) {
            if (DBG) {
                Log.e(TAG, "registerUxRestrictionsChangeListener(): listener null");
            }
            throw new IllegalArgumentException("Listener is null");
        }
        // If a new client is registering, create a new DrivingStateClient and add it to the list
        // of listening clients.
        UxRestrictionsClient client = findUxRestrictionsClient(listener);
        if (client == null) {
            client = new UxRestrictionsClient(listener);
            try {
                listener.asBinder().linkToDeath(client, 0);
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot link death recipient to binder " + e);
            }
            mUxRClients.add(client);
        }
        return;
    }

    /**
     * Iterates through the list of registered UX Restrictions clients -
     * {@link UxRestrictionsClient} and finds if the given client is already registered.
     *
     * @param listener Listener to look for.
     * @return the {@link UxRestrictionsClient} if found, null if not
     */
    @Nullable
    private UxRestrictionsClient findUxRestrictionsClient(
            ICarUxRestrictionsChangeListener listener) {
        IBinder binder = listener.asBinder();
        for (UxRestrictionsClient client : mUxRClients) {
            if (client.isHoldingBinder(binder)) {
                return client;
            }
        }
        return null;
    }


    /**
     * Unregister the given UX Restrictions listener
     *
     * @param listener client to unregister
     */
    @Override
    public synchronized void unregisterUxRestrictionsChangeListener(
            ICarUxRestrictionsChangeListener listener) {
        if (listener == null) {
            Log.e(TAG, "unregisterUxRestrictionsChangeListener(): listener null");
            throw new IllegalArgumentException("Listener is null");
        }

        UxRestrictionsClient client = findUxRestrictionsClient(listener);
        if (client == null) {
            Log.e(TAG, "unregisterUxRestrictionsChangeListener(): listener was not previously "
                    + "registered");
            return;
        }
        listener.asBinder().unlinkToDeath(client, 0);
        mUxRClients.remove(client);
    }

    /**
     * Gets the current UX restrictions
     *
     * @return {@link CarUxRestrictions} for the given event type
     */
    @Override
    @Nullable
    public synchronized CarUxRestrictions getCurrentUxRestrictions() {
        return mCurrentUxRestrictions;
    }

    /**
     * Get the maximum length of general purpose strings that can be displayed when
     * {@link CarUxRestrictions#UX_RESTRICTIONS_LIMIT_STRING_LENGTH} is imposed.
     *
     * @return the maximum length of string that can be displayed
     */
    @Override
    public int getMaxRestrictedStringLength() {
        return mHelper.getMaxStringLength();
    }

    /**
     * Get the maximum number of cumulative content items that can be displayed when
     * {@link CarUxRestrictions#UX_RESTRICTIONS_LIMIT_CONTENT} is imposed.
     * <p>
     * Please refer to this and {@link #getMaxContentDepth()} to know the upper bounds of
     * content serving when the restriction is in place.
     *
     * @return maximum number of cumulative items that can be displayed
     */
    public int getMaxCumulativeContentItems() {
        return mHelper.getMaxCumulativeContentItems();
    }

    /**
     * Get the maximum number of levels that the user can navigate to when
     * {@link CarUxRestrictions#UX_RESTRICTIONS_LIMIT_CONTENT} is imposed.
     * <p>
     * Please refer to this and {@link #getMaxCumulativeContentItems()} to know the upper bounds of
     * content serving when the restriction is in place.
     *
     * @return maximum number of cumulative items that can be displayed
     */
    public int getMaxContentDepth() {
        return mHelper.getMaxContentDepth();
    }

    /**
     * Class that holds onto client related information - listener interface, process that hosts the
     * binder object etc.
     * It also registers for death notifications of the host.
     */
    private class UxRestrictionsClient implements IBinder.DeathRecipient {
        private final IBinder listenerBinder;
        private final ICarUxRestrictionsChangeListener listener;

        public UxRestrictionsClient(ICarUxRestrictionsChangeListener l) {
            listener = l;
            listenerBinder = l.asBinder();
        }

        @Override
        public void binderDied() {
            if (DBG) {
                Log.d(TAG, "Binder died " + listenerBinder);
            }
            listenerBinder.unlinkToDeath(this, 0);
            synchronized (CarUxRestrictionsManagerService.this) {
                mUxRClients.remove(this);
            }
        }

        /**
         * Returns if the given binder object matches to what this client info holds.
         * Used to check if the listener asking to be registered is already registered.
         *
         * @return true if matches, false if not
         */
        public boolean isHoldingBinder(IBinder binder) {
            return listenerBinder == binder;
        }

        /**
         * Dispatch the event to the listener
         *
         * @param event {@link CarUxRestrictions}.
         */
        public void dispatchEventToClients(CarUxRestrictions event) {
            if (event == null) {
                return;
            }
            try {
                listener.onUxRestrictionsChanged(event);
            } catch (RemoteException e) {
                if (DBG) {
                    Log.d(TAG, "Dispatch to listener failed");
                }
            }
        }
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println(
                "Requires DO? " + mCurrentUxRestrictions.isRequiresDistractionOptimization());
        writer.println("Current UXR: " + mCurrentUxRestrictions.getActiveRestrictions());
        mHelper.dump(writer);
    }

    /**
     * {@link CarDrivingStateEvent} listener registered with the {@link CarDrivingStateService}
     * for getting driving state change notifications.
     */
    private final ICarDrivingStateChangeListener mICarDrivingStateChangeEventListener =
            new ICarDrivingStateChangeListener.Stub() {
                @Override
                public void onDrivingStateChanged(CarDrivingStateEvent event) {
                    if (DBG) {
                        Log.d(TAG, "Driving State Changed:" + event.eventValue);
                    }
                    handleDrivingStateEvent(event);
                }
            };

    /**
     * Handle the driving state change events coming from the {@link CarDrivingStateService}.
     * Map the driving state to the corresponding UX Restrictions and dispatch the
     * UX Restriction change to the registered clients.
     */
    private synchronized void handleDrivingStateEvent(CarDrivingStateEvent event) {
        if (event == null) {
            return;
        }
        int drivingState = event.eventValue;
        CarSensorEvent speed = mCarSensorService.getLatestSensorEvent(
                CarSensorManager.SENSOR_TYPE_CAR_SPEED);
        if (speed != null) {
            mCurrentMovingSpeed = speed.floatValues[0];
        } else if (drivingState == CarDrivingStateEvent.DRIVING_STATE_PARKED
                || drivingState == CarDrivingStateEvent.DRIVING_STATE_UNKNOWN) {
            // If speed is unavailable, but the driving state is parked or unknown, it can still be
            // handled.
            if (DBG) {
                Log.d(TAG, "Speed null when driving state is: " + drivingState);
            }
            mCurrentMovingSpeed = 0;
        } else {
            // If we get here with driving state != parked or unknown && speed == null,
            // something is wrong.  CarDrivingStateService could not have inferred idling or moving
            // when speed is not available
            Log.e(TAG, "Unexpected:  Speed null when driving state is: " + drivingState);
            return;
        }
        handleDispatchUxRestrictions(drivingState, mCurrentMovingSpeed);
    }

    /**
     * {@link CarSensorEvent} listener registered with the {@link CarSensorService} for getting
     * speed change notifications.
     */
    private final ICarSensorEventListener mICarSensorEventListener =
            new ICarSensorEventListener.Stub() {
                @Override
                public void onSensorChanged(List<CarSensorEvent> events) {
                    for (CarSensorEvent event : events) {
                        if (event != null
                                && event.sensorType == CarSensorManager.SENSOR_TYPE_CAR_SPEED) {
                            handleSpeedChange(event.floatValues[0]);
                        }
                    }
                }
            };

    private synchronized void handleSpeedChange(float newSpeed) {
        if (newSpeed == mCurrentMovingSpeed) {
            // Ignore if speed hasn't changed
            return;
        }
        int currentDrivingState = mDrivingStateService.getCurrentDrivingState().eventValue;
        if (currentDrivingState != CarDrivingStateEvent.DRIVING_STATE_MOVING) {
            // Ignore speed changes if the vehicle is not moving
            return;
        }
        mCurrentMovingSpeed = newSpeed;
        handleDispatchUxRestrictions(currentDrivingState, newSpeed);
    }

    /**
     * Handle dispatching UX restrictions change.
     *
     * @param currentDrivingState driving state of the vehicle
     * @param speed               speed of the vehicle
     */
    private synchronized void handleDispatchUxRestrictions(@CarDrivingState int currentDrivingState,
            float speed) {
        CarUxRestrictions uxRestrictions;
        // Get UX restrictions from the parsed configuration XML or fall back to defaults if not
        // available.
        if (mFallbackToDefaults) {
            uxRestrictions = getDefaultRestrictions(currentDrivingState);
        } else {
            uxRestrictions = mHelper.getUxRestrictions(currentDrivingState, speed);

        }

        if (DBG) {
            Log.d(TAG, String.format("DO old->new: %b -> %b",
                    mCurrentUxRestrictions.isRequiresDistractionOptimization(),
                    uxRestrictions.isRequiresDistractionOptimization()));
            Log.d(TAG, String.format("UxR old->new: 0x%x -> 0x%x",
                    mCurrentUxRestrictions.getActiveRestrictions(),
                    uxRestrictions.getActiveRestrictions()));
        }

        if (mCurrentUxRestrictions.isSameRestrictions(uxRestrictions)) {
            // Ignore dispatching if the restrictions has not changed.
            return;
        }
        mCurrentUxRestrictions = uxRestrictions;
        if (DBG) {
            Log.d(TAG, "dispatching to " + mUxRClients.size() + " clients");
        }
        for (UxRestrictionsClient client : mUxRClients) {
            client.dispatchEventToClients(uxRestrictions);
        }
    }

    private CarUxRestrictions getDefaultRestrictions(@CarDrivingState int drivingState) {
        int restrictions;
        boolean requiresOpt = false;
        switch (drivingState) {
            case CarDrivingStateEvent.DRIVING_STATE_PARKED:
                restrictions = CarUxRestrictions.UX_RESTRICTIONS_BASELINE;
                break;
            case CarDrivingStateEvent.DRIVING_STATE_IDLING:
                restrictions = CarUxRestrictions.UX_RESTRICTIONS_BASELINE;
                requiresOpt = true;
                break;
            case CarDrivingStateEvent.DRIVING_STATE_MOVING:
            default:
                restrictions = CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED;
                requiresOpt = true;
        }
        return createUxRestrictionsEvent(requiresOpt, restrictions);
    }

    static CarUxRestrictions createUxRestrictionsEvent(boolean requiresOpt,
            @CarUxRestrictionsInfo int uxr) {
        // In case the UXR is not baseline, set requiresDistractionOptimization to true since it
        // doesn't make sense to have an active non baseline restrictions without
        // requiresDistractionOptimization set to true.
        if (uxr != CarUxRestrictions.UX_RESTRICTIONS_BASELINE) {
            requiresOpt = true;
        }
        return new CarUxRestrictions(requiresOpt, uxr, SystemClock.elapsedRealtimeNanos());
    }
}
