/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.car.cluster.sample;

import static android.car.cluster.CarInstrumentClusterManager.CATEGORY_NAVIGATION;
import static android.car.cluster.sample.ClusterRenderingServiceImpl.LOCAL_BINDING_ACTION;
import static android.car.cluster.sample.ClusterRenderingServiceImpl.MSG_KEY_ACTIVITY_DISPLAY_ID;
import static android.car.cluster.sample.ClusterRenderingServiceImpl.MSG_KEY_ACTIVITY_STATE;
import static android.car.cluster.sample.ClusterRenderingServiceImpl.MSG_KEY_CATEGORY;
import static android.car.cluster.sample.ClusterRenderingServiceImpl.MSG_KEY_KEY_EVENT;
import static android.car.cluster.sample.ClusterRenderingServiceImpl.MSG_ON_KEY_EVENT;
import static android.car.cluster.sample.ClusterRenderingServiceImpl
        .MSG_ON_NAVIGATION_STATE_CHANGED;
import static android.car.cluster.sample.ClusterRenderingServiceImpl.MSG_REGISTER_CLIENT;
import static android.car.cluster.sample.ClusterRenderingServiceImpl
        .MSG_SET_ACTIVITY_LAUNCH_OPTIONS;
import static android.car.cluster.sample.ClusterRenderingServiceImpl.MSG_UNREGISTER_CLIENT;
import static android.content.Intent.ACTION_USER_SWITCHED;
import static android.content.Intent.ACTION_USER_UNLOCKED;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.car.cluster.CarInstrumentClusterManager;
import android.car.cluster.ClusterActivityState;
import android.car.cluster.sample.sensors.Sensors;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.car.cluster.navigation.NavigationState;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProviders;
import androidx.versionedparcelable.ParcelUtils;
import androidx.viewpager.widget.ViewPager;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main activity displayed on the instrument cluster. This activity contains fragments for each of
 * the cluster "facets" (e.g.: navigation, communication, media and car state). Users can navigate
 * to each facet by using the steering wheel buttons.
 * <p>
 * This activity runs on "system user" (see {@link UserHandle#USER_SYSTEM}) but it is visible on
 * all users (the same activity remains active even during user switch).
 * <p>
 * This activity also launches a default navigation app inside a virtual display (which is located
 * inside {@link NavigationFragment}). This navigation app is launched when:
 * <ul>
 * <li>Virtual display for navigation apps is ready.
 * <li>After every user switch.
 * </ul>
 * This is necessary because the navigation app runs under a normal user, and different users will
 * see different instances of the same application, with their own personalized data.
 */
public class MainClusterActivity extends FragmentActivity {
    private static final String TAG = "Cluster.MainActivity";

    private static final NavigationState NULL_NAV_STATE = new NavigationState.Builder().build();
    private static final int NO_DISPLAY = -1;

    private ViewPager mPager;
    private NavStateController mNavStateController;
    private ClusterViewModel mClusterViewModel;

    private Map<View, Facet<?>> mButtonToFacet = new HashMap<>();
    private SparseArray<Facet<?>> mOrderToFacet = new SparseArray<>();

    private Map<Sensors.Gear, View> mGearsToIcon = new HashMap<>();
    private InputMethodManager mInputMethodManager;
    private Messenger mService;
    private Messenger mServiceCallbacks = new Messenger(new MessageHandler(this));
    private VirtualDisplay mPendingVirtualDisplay = null;

    private static final int NAVIGATION_ACTIVITY_RETRY_INTERVAL_MS = 1000;

    private UserReceiver mUserReceiver;
    private ActivityMonitor mActivityMonitor = new ActivityMonitor();
    private final Handler mHandler = new Handler();
    private final Runnable mRetryLaunchNavigationActivity = this::tryLaunchNavigationActivity;
    private int mNavigationDisplayId = NO_DISPLAY;

    /**
     * Description of a virtual display
     */
    public static class VirtualDisplay {
        /** Identifier of the display */
        public final int mDisplayId;
        /** Rectangular area inside this display that can be viewed without obstructions */
        public final Rect mUnobscuredBounds;

        public VirtualDisplay(int displayId, Rect unobscuredBounds) {
            mDisplayId = displayId;
            mUnobscuredBounds = unobscuredBounds;
        }
    }

    private final View.OnFocusChangeListener mFacetButtonFocusListener =
            new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus) {
                mPager.setCurrentItem(mButtonToFacet.get(v).order);
            }
        }
    };

    private ServiceConnection mClusterRenderingServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "onServiceConnected, name: " + name + ", service: " + service);
            mService = new Messenger(service);
            sendServiceMessage(MSG_REGISTER_CLIENT, null, mServiceCallbacks);
            if (mPendingVirtualDisplay != null) {
                // If haven't reported the virtual display yet, do so on service connect.
                reportNavDisplay(mPendingVirtualDisplay);
                mPendingVirtualDisplay = null;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "onServiceDisconnected, name: " + name);
            mService = null;
            onNavigationStateChange(NULL_NAV_STATE);
        }
    };

    private static class MessageHandler extends Handler {
        private final WeakReference<MainClusterActivity> mActivity;

        MessageHandler(MainClusterActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            Bundle data = msg.getData();
            switch (msg.what) {
                case MSG_ON_KEY_EVENT:
                    KeyEvent event = data.getParcelable(MSG_KEY_KEY_EVENT);
                    if (event != null) {
                        mActivity.get().onKeyEvent(event);
                    }
                    break;
                case MSG_ON_NAVIGATION_STATE_CHANGED:
                    if (data == null) {
                        mActivity.get().onNavigationStateChange(null);
                    } else {
                        data.setClassLoader(ParcelUtils.class.getClassLoader());
                        NavigationState navState = NavigationState
                                .fromParcelable(data.getParcelable(
                                        ClusterRenderingServiceImpl.NAV_STATE_BUNDLE_KEY));
                        mActivity.get().onNavigationStateChange(navState);
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private ActivityMonitor.ActivityListener mNavigationActivityMonitor = (displayId, activity) -> {
        if (displayId != mNavigationDisplayId) {
            return;
        }
        mClusterViewModel.setCurrentNavigationActivity(activity);
    };

    private static class UserReceiver extends BroadcastReceiver {
        private WeakReference<MainClusterActivity> mActivity;

        UserReceiver(MainClusterActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        public void register(Context context) {
            IntentFilter intentFilter =  new IntentFilter(ACTION_USER_UNLOCKED);
            intentFilter.addAction(ACTION_USER_SWITCHED);
            context.registerReceiver(this, intentFilter);
        }

        public void unregister(Context context) {
            context.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            MainClusterActivity activity = mActivity.get();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Broadcast received: " + intent);
            }
            activity.tryLaunchNavigationActivity();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_main);

        mInputMethodManager = getSystemService(InputMethodManager.class);

        Intent intent = new Intent(this, ClusterRenderingServiceImpl.class);
        intent.setAction(LOCAL_BINDING_ACTION);
        bindService(intent, mClusterRenderingServiceConnection, 0);

        registerFacet(new Facet<>(findViewById(R.id.btn_nav), 0, NavigationFragment.class));
        registerFacet(new Facet<>(findViewById(R.id.btn_phone), 1, PhoneFragment.class));
        registerFacet(new Facet<>(findViewById(R.id.btn_music), 2, MusicFragment.class));
        registerFacet(new Facet<>(findViewById(R.id.btn_car_info), 3, CarInfoFragment.class));
        registerGear(findViewById(R.id.gear_parked), Sensors.Gear.PARK);
        registerGear(findViewById(R.id.gear_reverse), Sensors.Gear.REVERSE);
        registerGear(findViewById(R.id.gear_neutral), Sensors.Gear.NEUTRAL);
        registerGear(findViewById(R.id.gear_drive), Sensors.Gear.DRIVE);

        mPager = findViewById(R.id.pager);
        mPager.setAdapter(new ClusterPageAdapter(getSupportFragmentManager()));
        mOrderToFacet.get(0).button.requestFocus();
        mNavStateController = new NavStateController(findViewById(R.id.navigation_state));

        mClusterViewModel = ViewModelProviders.of(this).get(ClusterViewModel.class);
        mClusterViewModel.getNavigationFocus().observe(this, focus -> {
            mNavStateController.setActive(focus);
            // If focus is lost, we launch the default navigation activity again.
            if (!focus) {
                tryLaunchNavigationActivity();
            }
        });

        mClusterViewModel.getSensor(Sensors.SENSOR_GEAR).observe(this, this::updateSelectedGear);

        registerSensor(findViewById(R.id.info_fuel), mClusterViewModel.getFuelLevel());
        registerSensor(findViewById(R.id.info_speed),
                mClusterViewModel.getSensor(Sensors.SENSOR_SPEED));
        registerSensor(findViewById(R.id.info_range),
                mClusterViewModel.getSensor(Sensors.SENSOR_FUEL_RANGE));
        registerSensor(findViewById(R.id.info_rpm),
                mClusterViewModel.getSensor(Sensors.SENSOR_RPM));

        mActivityMonitor.start();

        mUserReceiver = new UserReceiver(this);
        mUserReceiver.register(this);
    }

    private <V> void registerSensor(TextView textView, LiveData<V> source) {
        String emptyValue = getString(R.string.info_value_empty);
        source.observe(this, value -> textView.setText(value != null
                ? value.toString() : emptyValue));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        mUserReceiver.unregister(this);
        mActivityMonitor.stop();
        if (mService != null) {
            sendServiceMessage(MSG_UNREGISTER_CLIENT, null, mServiceCallbacks);
            mService = null;
        }
        unbindService(mClusterRenderingServiceConnection);
    }

    private void onKeyEvent(KeyEvent event) {
        Log.i(TAG, "onKeyEvent, event: " + event);

        // This is a hack. We use SOURCE_CLASS_POINTER here because this type of input is associated
        // with the display. otherwise this event will be ignored in ViewRootImpl because injecting
        // KeyEvent w/o activity being focused is useless.
        event.setSource(event.getSource() | InputDevice.SOURCE_CLASS_POINTER);
        mInputMethodManager.dispatchKeyEventFromInputMethod(getCurrentFocus(), event);
    }

    private void onNavigationStateChange(NavigationState state) {
        Log.d(TAG, "onNavigationStateChange: " + state);
        if (mNavStateController != null) {
            mNavStateController.update(state);
        }
    }

    public void updateNavDisplay(VirtualDisplay virtualDisplay) {
        // Starting the default navigation activity. This activity will be shown when navigation
        // focus is not taken.
        startNavigationActivity(virtualDisplay.mDisplayId);
        // Notify the service (so it updates display properties on car service)
        if (mService == null) {
            // Service is not bound yet. Hold the information and notify when the service is bound.
            mPendingVirtualDisplay = virtualDisplay;
            return;
        } else {
            reportNavDisplay(virtualDisplay);
        }
    }

    private void reportNavDisplay(VirtualDisplay virtualDisplay) {
        Bundle data = new Bundle();
        data.putString(MSG_KEY_CATEGORY, CATEGORY_NAVIGATION);
        data.putInt(MSG_KEY_ACTIVITY_DISPLAY_ID, virtualDisplay.mDisplayId);
        data.putBundle(MSG_KEY_ACTIVITY_STATE, ClusterActivityState
                .create(virtualDisplay.mDisplayId != Display.INVALID_DISPLAY,
                        virtualDisplay.mUnobscuredBounds)
                .toBundle());
        sendServiceMessage(MSG_SET_ACTIVITY_LAUNCH_OPTIONS, data, null);
    }

    /**
     * Sends a message to the {@link ClusterRenderingServiceImpl}, which runs on a different
     * process.
     * @param what action to perform
     * @param data action data
     * @param replyTo {@link Messenger} where to reply back
     */
    private void sendServiceMessage(int what, Bundle data, Messenger replyTo) {
        try {
            Message message = Message.obtain(null, what);
            message.setData(data);
            message.replyTo = replyTo;
            mService.send(message);
        } catch (RemoteException ex) {
            Log.e(TAG, "Unable to deliver message " + what + ". Service died");
        }
    }

    public class ClusterPageAdapter extends FragmentPagerAdapter {
        public ClusterPageAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return mButtonToFacet.size();
        }

        @Override
        public Fragment getItem(int position) {
            return mOrderToFacet.get(position).getOrCreateFragment();
        }
    }

    private <T> void registerFacet(Facet<T> facet) {
        mOrderToFacet.append(facet.order, facet);
        mButtonToFacet.put(facet.button, facet);

        facet.button.setOnFocusChangeListener(mFacetButtonFocusListener);
    }

    private static class Facet<T> {
        Button button;
        Class<T> clazz;
        int order;

        Facet(Button button, int order, Class<T> clazz) {
            this.button = button;
            this.order = order;
            this.clazz = clazz;
        }

        private Fragment mFragment;

        Fragment getOrCreateFragment() {
            if (mFragment == null) {
                try {
                    mFragment = (Fragment) clazz.getConstructors()[0].newInstance();
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
            return mFragment;
        }
    }

    private void startNavigationActivity(int displayId) {
        mActivityMonitor.removeListener(mNavigationDisplayId, mNavigationActivityMonitor);
        mActivityMonitor.addListener(displayId, mNavigationActivityMonitor);
        mNavigationDisplayId = displayId;
        tryLaunchNavigationActivity();
    }

    /**
     * Tries to start a default navigation activity in the cluster. During system initialization
     * launching user activities might fail due the system not being ready or {@link PackageManager}
     * not being able to resolve the implicit intent. It is also possible that the system doesn't
     * have a default navigation activity selected yet.
     */
    private void tryLaunchNavigationActivity() {
        if (mNavigationDisplayId == NO_DISPLAY) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, String.format("Launch activity ignored (no display yet)"));
            }
            // Not ready to launch yet.
            return;
        }
        mHandler.removeCallbacks(mRetryLaunchNavigationActivity);

        ComponentName navigationActivity = getNavigationActivity();
        mClusterViewModel.setFreeNavigationActivity(navigationActivity);

        try {
            if (navigationActivity == null) {
                throw new ActivityNotFoundException();
            }
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(CATEGORY_NAVIGATION)
                    .setPackage(navigationActivity.getPackageName())
                    .setComponent(navigationActivity)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Log.d(TAG, "Launching: " + intent + " on display: " + mNavigationDisplayId);
            Bundle activityOptions = ActivityOptions.makeBasic()
                    .setLaunchDisplayId(mNavigationDisplayId)
                    .toBundle();

            startActivityAsUser(intent, activityOptions, UserHandle.CURRENT);
        } catch (ActivityNotFoundException ex) {
            // Some activities might not be available right on startup. We will retry.
            mHandler.postDelayed(mRetryLaunchNavigationActivity,
                    NAVIGATION_ACTIVITY_RETRY_INTERVAL_MS);
        } catch (Exception ex) {
            Log.e(TAG, "Unable to start navigation activity: " + navigationActivity, ex);
        }
    }

    /**
     * Returns a default navigation activity to show in the cluster.
     * In the current implementation we search for an activity with the
     * {@link CarInstrumentClusterManager#CATEGORY_NAVIGATION} category from the same navigation app
     * selected from CarLauncher (see CarLauncher#getMapsIntent()).
     * Alternatively, other implementations could:
     * <ul>
     * <li>Read this package from a resource (having a OEM default activity to show)
     * <li>Let the user select one from settings.
     * </ul>
     */
    private ComponentName getNavigationActivity() {
        PackageManager pm = getPackageManager();
        int userId = ActivityManager.getCurrentUser();

        // Get currently selected navigation app.
        Intent intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,
                Intent.CATEGORY_APP_MAPS);
        ResolveInfo navigationApp = pm.resolveActivityAsUser(intent,
                PackageManager.MATCH_DEFAULT_ONLY, userId);

        // Get all possible cluster activities
        intent = new Intent(Intent.ACTION_MAIN).addCategory(CATEGORY_NAVIGATION);
        List<ResolveInfo> candidates = pm.queryIntentActivitiesAsUser(intent, 0, userId);

        // If there is a select navigation app, try finding a matching auxiliary navigation activity
        if (navigationApp != null) {
            for (ResolveInfo candidate : candidates) {
                if (candidate.activityInfo.packageName.equals(navigationApp.activityInfo
                        .packageName)) {
                    Log.d(TAG, "Found activity: " + candidate);
                    return new ComponentName(candidate.activityInfo.packageName,
                            candidate.activityInfo.name);
                }
            }
        }

        // During initialization implicit intents might not provided a result. We will just
        // retry until we find one, or we exhaust the retries.
        Log.d(TAG, "No default activity found (it might not be available yet).");
        return null;
    }

    private void registerGear(View view, Sensors.Gear gear) {
        mGearsToIcon.put(gear, view);
    }

    private void updateSelectedGear(Sensors.Gear gear) {
        for (Map.Entry<Sensors.Gear, View> entry : mGearsToIcon.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == gear);
        }
    }
}
