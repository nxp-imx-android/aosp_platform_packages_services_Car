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
import android.car.Car;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.CarDrivingStateEvent.CarDrivingState;
import android.car.drivingstate.ICarDrivingState;
import android.car.drivingstate.ICarDrivingStateChangeListener;
import android.car.hardware.CarSensorEvent;
import android.car.hardware.CarSensorManager;
import android.car.hardware.ICarSensorEventListener;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * A service that infers the current driving state of the vehicle.  It doesn't directly listen to
 * vehicle properties from VHAL to do so.  Instead, it computes the driving state from listening to
 * the relevant sensors from {@link CarSensorService}
 */
public class CarDrivingStateService extends ICarDrivingState.Stub implements CarServiceBase {
    private static final String TAG = "CarDrivingState";
    private static final boolean DBG = false;
    private final Context mContext;
    private CarSensorService mSensorService;
    // List of clients listening to driving state events.
    private final List<DrivingStateClient> mDivingStateClients = new ArrayList<>();
    // Array of sensors that the service needs to listen to from CarSensorService for deriving
    // the driving state. ToDo (ramperry@) - fine tune this list - b/69859926
    private static final int[] mRequiredSensors = {
            CarSensorManager.SENSOR_TYPE_CAR_SPEED,
            CarSensorManager.SENSOR_TYPE_GEAR};
    private CarDrivingStateEvent mCurrentDrivingState;


    public CarDrivingStateService(Context context, CarSensorService sensorService) {
        mContext = context;
        mSensorService = sensorService;
        mCurrentDrivingState = createDrivingStateEvent(CarDrivingStateEvent.DRIVING_STATE_UNKNOWN);
    }

    @Override
    public void init() {
        if (!checkSensorSupport()) {
            Log.e(TAG, "init failure.  Driving state will always be fully restrictive");
            return;
        }
        subscribeToSensors();
    }

    @Override
    public synchronized void release() {
        for (int sensor : mRequiredSensors) {
            mSensorService.unregisterSensorListener(sensor, mICarSensorEventListener);
        }
        for (DrivingStateClient client : mDivingStateClients) {
            client.listenerBinder.unlinkToDeath(client, 0);
        }
        mDivingStateClients.clear();
        mCurrentDrivingState = createDrivingStateEvent(CarDrivingStateEvent.DRIVING_STATE_UNKNOWN);
    }

    /**
     * Checks if the {@link CarSensorService} supports the required sensors.
     *
     * @return {@code true} if supported, {@code false} if not
     */
    private synchronized boolean checkSensorSupport() {
        int sensorList[] = mSensorService.getSupportedSensors();
        for (int sensor : mRequiredSensors) {
            if (!CarSensorManager.isSensorSupported(sensorList, sensor)) {
                Log.e(TAG, "Required sensor not supported: " + sensor);
                return false;
            }
        }
        return true;
    }

    /**
     * Subscribe to the {@link CarSensorService} for required sensors.
     */
    private synchronized void subscribeToSensors() {
        for (int sensor : mRequiredSensors) {
            mSensorService.registerOrUpdateSensorListener(sensor,
                    CarSensorManager.SENSOR_RATE_FASTEST,
                    mICarSensorEventListener);
        }

    }

    // Binder methods

    /**
     * Register a {@link ICarDrivingStateChangeListener} to be notified for changes to the driving
     * state.
     *
     * @param listener {@link ICarDrivingStateChangeListener}
     */
    @Override
    public synchronized void registerDrivingStateChangeListener(
            ICarDrivingStateChangeListener listener) {
        if (listener == null) {
            if (DBG) {
                Log.e(TAG, "registerDrivingStateChangeListener(): listener null");
            }
            throw new IllegalArgumentException("Listener is null");
        }
        // If a new client is registering, create a new DrivingStateClient and add it to the list
        // of listening clients.
        DrivingStateClient client = findDrivingStateClientLocked(listener);
        if (client == null) {
            client = new DrivingStateClient(listener);
            try {
                listener.asBinder().linkToDeath(client, 0);
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot link death recipient to binder " + e);
                return;
            }
            mDivingStateClients.add(client);
        }
    }

    /**
     * Iterates through the list of registered Driving State Change clients -
     * {@link DrivingStateClient} and finds if the given client is already registered.
     *
     * @param listener Listener to look for.
     * @return the {@link DrivingStateClient} if found, null if not
     */
    @Nullable
    private DrivingStateClient findDrivingStateClientLocked(
            ICarDrivingStateChangeListener listener) {
        IBinder binder = listener.asBinder();
        // Find the listener by comparing the binder object they host.
        for (DrivingStateClient client : mDivingStateClients) {
            if (client.isHoldingBinder(binder)) {
                return client;
            }
        }
        return null;
    }

    /**
     * Unregister the given Driving State Change listener
     *
     * @param listener client to unregister
     */
    @Override
    public synchronized void unregisterDrivingStateChangeListener(
            ICarDrivingStateChangeListener listener) {
        if (listener == null) {
            Log.e(TAG, "unregisterDrivingStateChangeListener(): listener null");
            throw new IllegalArgumentException("Listener is null");
        }

        DrivingStateClient client = findDrivingStateClientLocked(listener);
        if (client == null) {
            Log.e(TAG, "unregisterDrivingStateChangeListener(): listener was not previously "
                    + "registered");
            return;
        }
        listener.asBinder().unlinkToDeath(client, 0);
        mDivingStateClients.remove(client);
    }

    /**
     * Gets the current driving state
     *
     * @return {@link CarDrivingStateEvent} for the given event type
     */
    @Override
    @Nullable
    public synchronized CarDrivingStateEvent getCurrentDrivingState() {
        return mCurrentDrivingState;
    }

    /**
     * Class that holds onto client related information - listener interface, process that hosts the
     * binder object etc.
     * <p>
     * It also registers for death notifications of the host.
     */
    private class DrivingStateClient implements IBinder.DeathRecipient {
        private final IBinder listenerBinder;
        private final ICarDrivingStateChangeListener listener;

        public DrivingStateClient(ICarDrivingStateChangeListener l) {
            listener = l;
            listenerBinder = l.asBinder();
        }

        @Override
        public void binderDied() {
            if (DBG) {
                Log.d(TAG, "Binder died " + listenerBinder);
            }
            listenerBinder.unlinkToDeath(this, 0);
            synchronized (CarDrivingStateService.this) {
                mDivingStateClients.remove(this);
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
         * Dispatch the events to the listener
         *
         * @param event {@link CarDrivingStateEvent}.
         */
        public void dispatchEventToClients(CarDrivingStateEvent event) {
            if (event == null) {
                return;
            }
            try {
                listener.onDrivingStateChanged(event);
            } catch (RemoteException e) {
                if (DBG) {
                    Log.d(TAG, "Dispatch to listener failed");
                }
            }
        }
    }

    @Override
    public void dump(PrintWriter writer) {

    }

    /**
     * {@link CarSensorEvent} listener registered with the {@link CarSensorService} for getting
     * sensor change notifications.
     */
    private final ICarSensorEventListener mICarSensorEventListener =
            new ICarSensorEventListener.Stub() {
                @Override
                public void onSensorChanged(List<CarSensorEvent> events) {
                    for (CarSensorEvent event : events) {
                        Log.d(TAG, "Sensor Changed:" + event.sensorType);
                        handleSensorEvent(event);
                    }
                }
            };

    /**
     * Handle the sensor events coming from the {@link CarSensorService}.
     * Compute the driving state, map it to the corresponding UX Restrictions and dispatch the
     * events to the registered clients.
     */
    private synchronized void handleSensorEvent(CarSensorEvent event) {
        switch (event.sensorType) {
            case CarSensorManager.SENSOR_TYPE_GEAR:
            case CarSensorManager.SENSOR_TYPE_CAR_SPEED:
                int drivingState = inferDrivingStateLocked();
                // Check if the driving state has changed.  If it has, update our records and
                // dispatch the new events to the listeners.
                if (DBG) {
                    Log.d(TAG, "Driving state new->old " + drivingState + "->"
                            + mCurrentDrivingState.eventValue);
                }
                if (drivingState == mCurrentDrivingState.eventValue) {
                    break;
                }
                // Update if there is a change in state.
                mCurrentDrivingState = createDrivingStateEvent(drivingState);

                if (DBG) {
                    Log.d(TAG, "dispatching to " + mDivingStateClients.size() + " clients");
                }
                for (DrivingStateClient client : mDivingStateClients) {
                    client.dispatchEventToClients(mCurrentDrivingState);
                }
                break;
            default:
                break;
        }
    }

    /**
     * Infers the current driving state of the car from the other Car Sensor properties like
     * Current Gear, Speed etc.
     * ToDo (ramperry@) - Fine tune this - b/69859926
     *
     * @return Current driving state
     */
    @CarDrivingState
    private int inferDrivingStateLocked() {
        int drivingState = CarDrivingStateEvent.DRIVING_STATE_UNKNOWN;
        CarSensorEvent lastGear = mSensorService.getLatestSensorEvent(
                CarSensorManager.SENSOR_TYPE_GEAR);
        CarSensorEvent lastSpeed = mSensorService.getLatestSensorEvent(
                CarSensorManager.SENSOR_TYPE_CAR_SPEED);

        /*
            Simple logic to start off deriving driving state:
            1. If gear == parked, then Driving State is parked.
            2. If gear != parked,
                2a. if speed == 0, then driving state is idling
                2b. if speed != 0, then driving state is moving
                2c. if speed unavailable, then driving state is unknown
            This logic needs to be tested and iterated on.  Tracked in b/69859926
         */
        if (lastGear != null) {
            if (DBG) {
                Log.d(TAG, "Last known Gear:" + lastGear.intValues[0]);
            }
            if (isGearInParking(lastGear)) {
                drivingState = CarDrivingStateEvent.DRIVING_STATE_PARKED;
            } else if (lastSpeed == null) {
                drivingState = CarDrivingStateEvent.DRIVING_STATE_UNKNOWN;
            } else {
                if (DBG) {
                    Log.d(TAG, "Speed: " + lastSpeed);
                }
                if (lastSpeed.floatValues[0] == 0f) {
                    drivingState = CarDrivingStateEvent.DRIVING_STATE_IDLING;
                } else {
                    drivingState = CarDrivingStateEvent.DRIVING_STATE_MOVING;
                }
            }
        }
        return drivingState;
    }

    private boolean isSpeedZero(CarSensorEvent event) {
        return event.floatValues[0] == 0f;
    }

    private boolean isGearInParking(CarSensorEvent event) {
        int gear = event.intValues[0];
        return gear == CarSensorEvent.GEAR_PARK;
    }

    private static CarDrivingStateEvent createDrivingStateEvent(int eventValue) {
        return new CarDrivingStateEvent(eventValue, SystemClock.elapsedRealtimeNanos());
    }
}
