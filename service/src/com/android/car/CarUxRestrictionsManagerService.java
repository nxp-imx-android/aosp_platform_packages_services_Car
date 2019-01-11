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

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import android.annotation.Nullable;
import android.car.Car;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.CarDrivingStateEvent.CarDrivingState;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsConfiguration;
import android.car.drivingstate.ICarDrivingStateChangeListener;
import android.car.drivingstate.ICarUxRestrictionsChangeListener;
import android.car.drivingstate.ICarUxRestrictionsManager;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarPropertyEventListener;
import android.car.userlib.CarUserManagerHelper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.AtomicFile;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * A service that listens to current driving state of the vehicle and maps it to the
 * appropriate UX restrictions for that driving state.
 * <p>
 * <h1>UX Restrictions Configuration</h1>
 * When this service starts, it will first try reading the configuration set through
 * {@link #saveUxRestrictionsConfigurationForNextBoot(CarUxRestrictionsConfiguration)}.
 * If one is not available, it will try reading the configuration saved in
 * {@code R.xml.car_ux_restrictions_map}. If XML is somehow unavailable, it will
 * fall back to a hard-coded configuration.
 */
public class CarUxRestrictionsManagerService extends ICarUxRestrictionsManager.Stub implements
        CarServiceBase {
    private static final String TAG = "CarUxR";
    private static final boolean DBG = false;
    private static final int MAX_TRANSITION_LOG_SIZE = 20;
    private static final int PROPERTY_UPDATE_RATE = 5; // Update rate in Hz
    private static final float SPEED_NOT_AVAILABLE = -1.0F;

    @VisibleForTesting
    /* package */ static final String CONFIG_FILENAME_PRODUCTION = "prod_config.json";
    @VisibleForTesting
    /* package */ static final String CONFIG_FILENAME_STAGED = "staged_config.json";

    private final Context mContext;
    private final CarDrivingStateService mDrivingStateService;
    private final CarPropertyService mCarPropertyService;
    private final CarUserManagerHelper mCarUserManagerHelper;
    // List of clients listening to UX restriction events.
    private final List<UxRestrictionsClient> mUxRClients = new ArrayList<>();
    private CarUxRestrictionsConfiguration mCarUxRestrictionsConfiguration;
    private CarUxRestrictions mCurrentUxRestrictions;
    private float mCurrentMovingSpeed;
    // Flag to disable broadcasting UXR changes - for development purposes
    @GuardedBy("this")
    private boolean mUxRChangeBroadcastEnabled = true;
    // For dumpsys logging
    private final LinkedList<Utils.TransitionLog> mTransitionLogs = new LinkedList<>();

    // When UXR service boots up the context does not have access to storage yet, so it
    // will likely read configuration from XML resource. Register to receive broadcast to
    // attempt to read saved configuration when it becomes available.
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
                // If the system user is not headless, then we can read config as soon as the
                // system has completed booting.
                if (!mCarUserManagerHelper.isHeadlessSystemUser()) {
                    logd("not headless on boot complete");
                    PendingResult pendingResult = goAsync();
                    LoadRestrictionsTask task = new LoadRestrictionsTask(pendingResult);
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
            } else if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                int userHandle = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
                logd("USER_SWITCHED: " + userHandle);
                if (mCarUserManagerHelper.isHeadlessSystemUser()
                        && userHandle > UserHandle.USER_SYSTEM) {
                    PendingResult pendingResult = goAsync();
                    LoadRestrictionsTask task = new LoadRestrictionsTask(pendingResult);
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
            }
        }
    };

    private class LoadRestrictionsTask extends AsyncTask<Void, Void, Void> {
        private final BroadcastReceiver.PendingResult mPendingResult;

        private LoadRestrictionsTask(BroadcastReceiver.PendingResult pendingResult) {
            mPendingResult = pendingResult;
        }

        @Override
        protected Void doInBackground(Void... params) {
            mCarUxRestrictionsConfiguration = loadConfig();
            handleDispatchUxRestrictions(mDrivingStateService.getCurrentDrivingState().eventValue,
                    getCurrentSpeed());
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            mPendingResult.finish();
        }
    }

    public CarUxRestrictionsManagerService(Context context, CarDrivingStateService drvService,
            CarPropertyService propertyService, CarUserManagerHelper carUserManagerHelper) {
        mContext = context;
        mDrivingStateService = drvService;
        mCarPropertyService = propertyService;
        mCarUserManagerHelper = carUserManagerHelper;
        // NOTE: during boot phase context cannot access file system so we most likely will
        // use XML config. If prod config is set, it will be loaded in broadcast receiver.
        mCarUxRestrictionsConfiguration = loadConfig();
        // Unrestricted until driving state information is received. During boot up, we don't want
        // everything to be blocked until data is available from CarPropertyManager.  If we start
        // driving and we don't get speed or gear information, we have bigger problems.
        mCurrentUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ false,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, SystemClock.elapsedRealtimeNanos())
                .build();
    }

    @Override
    public synchronized void init() {
        // subscribe to driving State
        mDrivingStateService.registerDrivingStateChangeListener(
                mICarDrivingStateChangeEventListener);
        // subscribe to property service for speed
        mCarPropertyService.registerListener(VehicleProperty.PERF_VEHICLE_SPEED,
                PROPERTY_UPDATE_RATE, mICarPropertyEventListener);
        registerReceiverToLoadConfig();
        initializeUxRestrictions();
    }

    private void registerReceiverToLoadConfig() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        filter.addAction(Intent.ACTION_LOCKED_BOOT_COMPLETED);
        mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    @Override
    public CarUxRestrictionsConfiguration getConfig() {
        return mCarUxRestrictionsConfiguration;
    }

    /**
     * Loads a UX restrictions configuration and returns it.
     * <p>Reads config from the following sources in order:
     * <ol>
     * <li>saved config set by
     * {@link #saveUxRestrictionsConfigurationForNextBoot(CarUxRestrictionsConfiguration)};
     * <li>XML resource config from {@code R.xml.car_ux_restrictions_map};
     * <li>hardcoded default config.
     * </ol>
     */
    @VisibleForTesting
    /* package */ synchronized CarUxRestrictionsConfiguration loadConfig() {
        promoteStagedConfig();

        CarUxRestrictionsConfiguration config = null;
        // Production config, if available, is the first choice.
        File prodConfig = mContext.getFileStreamPath(CONFIG_FILENAME_PRODUCTION);
        if (prodConfig.exists()) {
            logd("Attempting to read production config");
            config = readPersistedConfig(prodConfig);
            if (config != null) {
                return config;
            }
        }

        // XML config is the second choice.
        logd("Attempting to read config from XML resource");
        config = readXmlConfig();
        if (config != null) {
            return config;
        }

        // This should rarely happen.
        Log.w(TAG, "Creating default config");
        return createDefaultConfig();
    }

    @Nullable
    private CarUxRestrictionsConfiguration readXmlConfig() {
        try {
            return CarUxRestrictionsConfigurationXmlParser.parse(mContext,
                    R.xml.car_ux_restrictions_map);
        } catch (IOException | XmlPullParserException e) {
            Log.e(TAG, "Could not read config from XML resource", e);
        }
        return null;
    }

    private void promoteStagedConfig() {
        Path stagedConfig = mContext.getFileStreamPath(CONFIG_FILENAME_STAGED).toPath();

        CarDrivingStateEvent currentDrivingStateEvent =
                mDrivingStateService.getCurrentDrivingState();
        // Only promote staged config when car is parked.
        if (currentDrivingStateEvent != null
                && currentDrivingStateEvent.eventValue == CarDrivingStateEvent.DRIVING_STATE_PARKED
                && Files.exists(stagedConfig)) {
            Path prod = mContext.getFileStreamPath(CONFIG_FILENAME_PRODUCTION).toPath();
            try {
                logd("Attempting to promote stage config");
                Files.move(stagedConfig, prod, REPLACE_EXISTING);
            } catch (IOException e) {
                Log.e(TAG, "Could not promote state config", e);
            }
        }
    }

    // Update current restrictions by getting the current driving state and speed.
    private void initializeUxRestrictions() {
        CarDrivingStateEvent currentDrivingStateEvent =
                mDrivingStateService.getCurrentDrivingState();
        // if we don't have enough information from the CarPropertyService to compute the UX
        // restrictions, then leave the UX restrictions unchanged from what it was initialized to
        // in the constructor.
        if (currentDrivingStateEvent == null || currentDrivingStateEvent.eventValue
                == CarDrivingStateEvent.DRIVING_STATE_UNKNOWN) {
            return;
        }
        int currentDrivingState = currentDrivingStateEvent.eventValue;
        Float currentSpeed = getCurrentSpeed();
        if (currentSpeed == SPEED_NOT_AVAILABLE) {
            return;
        }
        // At this point the underlying CarPropertyService has provided us enough information to
        // compute the UX restrictions that could be potentially different from the initial UX
        // restrictions.
        handleDispatchUxRestrictions(currentDrivingState, currentSpeed);
    }

    private Float getCurrentSpeed() {
        CarPropertyValue value = mCarPropertyService.getProperty(VehicleProperty.PERF_VEHICLE_SPEED,
                0);
        if (value != null) {
            return (Float) value.getValue();
        }
        return SPEED_NOT_AVAILABLE;
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
            Log.e(TAG, "registerUxRestrictionsChangeListener(): listener null");
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

    @Override
    public synchronized boolean saveUxRestrictionsConfigurationForNextBoot(
            CarUxRestrictionsConfiguration config) {
        ICarImpl.assertPermission(mContext, Car.PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION);
        return persistConfig(config, CONFIG_FILENAME_STAGED);
    }

    @Override
    @Nullable
    public CarUxRestrictionsConfiguration getStagedConfig() {
        File stagedConfig = mContext.getFileStreamPath(CONFIG_FILENAME_STAGED);
        if (stagedConfig.exists()) {
            logd("Attempting to read staged config");
            return readPersistedConfig(stagedConfig);
        } else {
            return null;
        }
    }

    /**
     * Writes configuration into the specified file.
     *
     * IO access on file is not thread safe. Caller should ensure threading protection.
     */
    private boolean persistConfig(CarUxRestrictionsConfiguration config, String filename) {
        AtomicFile stagedFile = new AtomicFile(mContext.getFileStreamPath(filename));
        FileOutputStream fos;
        try {
            fos = stagedFile.startWrite();
        } catch (IOException e) {
            Log.e(TAG, "Could not open file to persist config", e);
            return false;
        }
        try (JsonWriter jsonWriter = new JsonWriter(
                new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {
            config.writeJson(jsonWriter);
        } catch (IOException e) {
            Log.e(TAG, "Could not persist config", e);
            stagedFile.failWrite(fos);
            return false;
        }
        stagedFile.finishWrite(fos);
        return true;
    }

    @Nullable
    private CarUxRestrictionsConfiguration readPersistedConfig(File file) {
        if (!file.exists()) {
            Log.e(TAG, "Could not find config file: " + file.getName());
            return null;
        }

        AtomicFile config = new AtomicFile(file);
        try (JsonReader reader = new JsonReader(
                new InputStreamReader(config.openRead(), StandardCharsets.UTF_8))) {
            return CarUxRestrictionsConfiguration.readJson(reader);
        } catch (IOException e) {
            Log.e(TAG, "Could not read persisted config file " + file.getName(), e);
        }
        return null;
    }

    /**
     * Enable/disable UX restrictions change broadcast blocking.
     * Setting this to true will stop broadcasts of UX restriction change to listeners.
     * This method works only on debug builds and the caller of this method needs to have the same
     * signature of the car service.
     *
     */
    public synchronized void setUxRChangeBroadcastEnabled(boolean enable) {
        if (!isDebugBuild()) {
            Log.e(TAG, "Cannot set UX restriction change broadcast.");
            return;
        }
        // Check if the caller has the same signature as that of the car service.
        if (mContext.getPackageManager().checkSignatures(Process.myUid(), Binder.getCallingUid())
                != PackageManager.SIGNATURE_MATCH) {
            throw new SecurityException(
                    "Caller " + mContext.getPackageManager().getNameForUid(Binder.getCallingUid())
                            + " does not have the right signature");
        }
        if (enable) {
            // if enabling it back, send the current restrictions
            mUxRChangeBroadcastEnabled = enable;
            handleDispatchUxRestrictions(mDrivingStateService.getCurrentDrivingState().eventValue,
                    getCurrentSpeed());
        } else {
            // fake parked state, so if the system is currently restricted, the restrictions are
            // relaxed.
            handleDispatchUxRestrictions(CarDrivingStateEvent.DRIVING_STATE_PARKED, 0);
            mUxRChangeBroadcastEnabled = enable;
        }
    }

    private boolean isDebugBuild() {
        return Build.IS_USERDEBUG || Build.IS_ENG;
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
            logd("Binder died " + listenerBinder);
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
                Log.e(TAG, "Dispatch to listener failed", e);
            }
        }
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println(
                "Requires DO? " + mCurrentUxRestrictions.isRequiresDistractionOptimization());
        writer.println("Current UXR: " + mCurrentUxRestrictions.getActiveRestrictions());
        if (isDebugBuild()) {
            writer.println("mUxRChangeBroadcastEnabled? " + mUxRChangeBroadcastEnabled);
        }
        mCarUxRestrictionsConfiguration.dump(writer);
        writer.println("UX Restriction change log:");
        for (Utils.TransitionLog tlog : mTransitionLogs) {
            writer.println(tlog);
        }
    }

    /**
     * {@link CarDrivingStateEvent} listener registered with the {@link CarDrivingStateService}
     * for getting driving state change notifications.
     */
    private final ICarDrivingStateChangeListener mICarDrivingStateChangeEventListener =
            new ICarDrivingStateChangeListener.Stub() {
                @Override
                public void onDrivingStateChanged(CarDrivingStateEvent event) {
                    logd("Driving State Changed:" + event.eventValue);
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
        Float speed = getCurrentSpeed();

        if (speed != SPEED_NOT_AVAILABLE) {
            mCurrentMovingSpeed = speed;
        } else if (drivingState == CarDrivingStateEvent.DRIVING_STATE_PARKED
                || drivingState == CarDrivingStateEvent.DRIVING_STATE_UNKNOWN) {
            // If speed is unavailable, but the driving state is parked or unknown, it can still be
            // handled.
            logd("Speed null when driving state is: " + drivingState);
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
     * {@link CarPropertyEvent} listener registered with the {@link CarPropertyService} for getting
     * speed change notifications.
     */
    private final ICarPropertyEventListener mICarPropertyEventListener =
            new ICarPropertyEventListener.Stub() {
                @Override
                public void onEvent(List<CarPropertyEvent> events) throws RemoteException {
                    for (CarPropertyEvent event : events) {
                        if ((event.getEventType()
                                == CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE)
                                && (event.getCarPropertyValue().getPropertyId()
                                == VehicleProperty.PERF_VEHICLE_SPEED)) {
                            handleSpeedChange((Float) event.getCarPropertyValue().getValue());
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
        if (isDebugBuild() && !mUxRChangeBroadcastEnabled) {
            Log.d(TAG, "Not dispatching UX Restriction due to setting");
            return;
        }

        CarUxRestrictions uxRestrictions =
                mCarUxRestrictionsConfiguration.getUxRestrictions(currentDrivingState, speed);

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
        // for dumpsys logging
        StringBuilder extraInfo = new StringBuilder();
        extraInfo.append(
                mCurrentUxRestrictions.isRequiresDistractionOptimization() ? "DO -> "
                        : "No DO -> ");
        extraInfo.append(
                uxRestrictions.isRequiresDistractionOptimization() ? "DO" : "No DO");
        addTransitionLog(TAG, mCurrentUxRestrictions.getActiveRestrictions(),
                uxRestrictions.getActiveRestrictions(), System.currentTimeMillis(),
                extraInfo.toString());

        mCurrentUxRestrictions = uxRestrictions;
        logd("dispatching to " + mUxRClients.size() + " clients");
        for (UxRestrictionsClient client : mUxRClients) {
            client.dispatchEventToClients(uxRestrictions);
        }
    }

    CarUxRestrictionsConfiguration createDefaultConfig() {
        return new CarUxRestrictionsConfiguration.Builder()
                .setUxRestrictions(CarDrivingStateEvent.DRIVING_STATE_PARKED,
                        false, CarUxRestrictions.UX_RESTRICTIONS_BASELINE)
                .setUxRestrictions(CarDrivingStateEvent.DRIVING_STATE_IDLING,
                        false, CarUxRestrictions.UX_RESTRICTIONS_BASELINE)
                .setUxRestrictions(CarDrivingStateEvent.DRIVING_STATE_MOVING,
                        true, CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED)
                .setUxRestrictions(CarDrivingStateEvent.DRIVING_STATE_UNKNOWN,
                        true, CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED)
                .build();
    }

    private void addTransitionLog(String name, int from, int to, long timestamp, String extra) {
        if (mTransitionLogs.size() >= MAX_TRANSITION_LOG_SIZE) {
            mTransitionLogs.remove();
        }

        Utils.TransitionLog tLog = new Utils.TransitionLog(name, from, to, timestamp, extra);
        mTransitionLogs.add(tLog);
    }

    private static void logd(String msg) {
        if (DBG) {
            Log.d(TAG, msg);
        }
    }
}
