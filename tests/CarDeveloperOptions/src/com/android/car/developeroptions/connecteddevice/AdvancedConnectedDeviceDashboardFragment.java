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
package com.android.car.developeroptions.connecteddevice;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.SearchIndexableResource;

import com.android.car.developeroptions.R;
import com.android.car.developeroptions.bluetooth.BluetoothFilesPreferenceController;
import com.android.car.developeroptions.dashboard.DashboardFragment;
import com.android.car.developeroptions.nfc.AndroidBeamPreferenceController;
import com.android.car.developeroptions.print.PrintSettingPreferenceController;
import com.android.car.developeroptions.search.BaseSearchIndexProvider;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This fragment contains all the advanced connection preferences(i.e, Bluetooth, NFC, USB..)
 */
@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class AdvancedConnectedDeviceDashboardFragment extends DashboardFragment {

    private static final String TAG = "AdvancedConnectedDeviceFrag";

    static final String KEY_BLUETOOTH = "bluetooth_settings";

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.CONNECTION_DEVICE_ADVANCED;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getHelpResource() {
        return R.string.help_url_connected_devices;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.connected_devices_advanced;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        return buildControllers(context, getSettingsLifecycle());
    }

    private static List<AbstractPreferenceController> buildControllers(Context context,
            Lifecycle lifecycle) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();

        controllers.add(new BluetoothFilesPreferenceController(context));

        final PrintSettingPreferenceController printerController =
                new PrintSettingPreferenceController(context);

        if (lifecycle != null) {
            lifecycle.addObserver(printerController);
        }
        controllers.add(printerController);

        return controllers;
    }

    /**
     * For Search.
     */
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(
                        Context context, boolean enabled) {
                    final SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.connected_devices_advanced;
                    return Arrays.asList(sir);
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    final List<String> keys = super.getNonIndexableKeys(context);
                    PackageManager pm = context.getPackageManager();
                    if (!pm.hasSystemFeature(PackageManager.FEATURE_NFC)) {
                        keys.add(AndroidBeamPreferenceController.KEY_ANDROID_BEAM_SETTINGS);
                    }

                    return keys;
                }

                @Override
                public List<AbstractPreferenceController> createPreferenceControllers(
                        Context context) {
                    return buildControllers(context, null /* lifecycle */);
                }
            };
}
