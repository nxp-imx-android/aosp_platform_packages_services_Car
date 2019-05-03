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

package com.android.car.developeroptions.dashboard;

import android.util.ArrayMap;

import com.android.car.developeroptions.DisplaySettings;
import com.android.car.developeroptions.LegalSettings;
import com.android.car.developeroptions.accounts.AccountDashboardFragment;
import com.android.car.developeroptions.accounts.AccountDetailDashboardFragment;
import com.android.car.developeroptions.applications.AppAndNotificationDashboardFragment;
import com.android.car.developeroptions.connecteddevice.AdvancedConnectedDeviceDashboardFragment;
import com.android.car.developeroptions.connecteddevice.ConnectedDeviceDashboardFragment;
import com.android.car.developeroptions.development.DevelopmentSettingsDashboardFragment;
import com.android.car.developeroptions.deviceinfo.StorageDashboardFragment;
import com.android.car.developeroptions.deviceinfo.aboutphone.MyDeviceInfoFragment;
import com.android.car.developeroptions.display.NightDisplaySettings;
import com.android.car.developeroptions.enterprise.EnterprisePrivacySettings;
import com.android.car.developeroptions.fuelgauge.PowerUsageSummary;
import com.android.car.developeroptions.fuelgauge.batterysaver.BatterySaverSettings;
import com.android.car.developeroptions.gestures.GestureSettings;
import com.android.car.developeroptions.homepage.TopLevelSettings;
import com.android.car.developeroptions.language.LanguageAndInputSettings;
import com.android.car.developeroptions.network.NetworkDashboardFragment;
import com.android.car.developeroptions.notification.SoundSettings;
import com.android.car.developeroptions.notification.ZenModeSettings;
import com.android.car.developeroptions.privacy.PrivacyDashboardFragment;
import com.android.car.developeroptions.security.LockscreenDashboardFragment;
import com.android.car.developeroptions.security.SecuritySettings;
import com.android.car.developeroptions.system.SystemDashboardFragment;
import com.android.settingslib.drawer.CategoryKey;

import java.util.Map;

/**
 * A registry to keep track of which page hosts which category.
 */
public class DashboardFragmentRegistry {

    /**
     * Map from parent fragment to category key. The parent fragment hosts child with
     * category_key.
     */
    public static final Map<String, String> PARENT_TO_CATEGORY_KEY_MAP;

    /**
     * Map from category_key to parent. This is a helper to look up which fragment hosts the
     * category_key.
     */
    public static final Map<String, String> CATEGORY_KEY_TO_PARENT_MAP;

    static {
        PARENT_TO_CATEGORY_KEY_MAP = new ArrayMap<>();
        PARENT_TO_CATEGORY_KEY_MAP.put(TopLevelSettings.class.getName(),
                CategoryKey.CATEGORY_HOMEPAGE);
        PARENT_TO_CATEGORY_KEY_MAP.put(
                NetworkDashboardFragment.class.getName(), CategoryKey.CATEGORY_NETWORK);
        PARENT_TO_CATEGORY_KEY_MAP.put(ConnectedDeviceDashboardFragment.class.getName(),
                CategoryKey.CATEGORY_CONNECT);
        PARENT_TO_CATEGORY_KEY_MAP.put(AdvancedConnectedDeviceDashboardFragment.class.getName(),
                CategoryKey.CATEGORY_DEVICE);
        PARENT_TO_CATEGORY_KEY_MAP.put(AppAndNotificationDashboardFragment.class.getName(),
                CategoryKey.CATEGORY_APPS);
        PARENT_TO_CATEGORY_KEY_MAP.put(PowerUsageSummary.class.getName(),
                CategoryKey.CATEGORY_BATTERY);
        PARENT_TO_CATEGORY_KEY_MAP.put(DisplaySettings.class.getName(),
                CategoryKey.CATEGORY_DISPLAY);
        PARENT_TO_CATEGORY_KEY_MAP.put(SoundSettings.class.getName(),
                CategoryKey.CATEGORY_SOUND);
        PARENT_TO_CATEGORY_KEY_MAP.put(StorageDashboardFragment.class.getName(),
                CategoryKey.CATEGORY_STORAGE);
        PARENT_TO_CATEGORY_KEY_MAP.put(SecuritySettings.class.getName(),
                CategoryKey.CATEGORY_SECURITY);
        PARENT_TO_CATEGORY_KEY_MAP.put(AccountDetailDashboardFragment.class.getName(),
                CategoryKey.CATEGORY_ACCOUNT_DETAIL);
        PARENT_TO_CATEGORY_KEY_MAP.put(AccountDashboardFragment.class.getName(),
                CategoryKey.CATEGORY_ACCOUNT);
        PARENT_TO_CATEGORY_KEY_MAP.put(
                SystemDashboardFragment.class.getName(), CategoryKey.CATEGORY_SYSTEM);
        PARENT_TO_CATEGORY_KEY_MAP.put(LanguageAndInputSettings.class.getName(),
                CategoryKey.CATEGORY_SYSTEM_LANGUAGE);
        PARENT_TO_CATEGORY_KEY_MAP.put(DevelopmentSettingsDashboardFragment.class.getName(),
                CategoryKey.CATEGORY_SYSTEM_DEVELOPMENT);
        PARENT_TO_CATEGORY_KEY_MAP.put(LockscreenDashboardFragment.class.getName(),
                CategoryKey.CATEGORY_SECURITY_LOCKSCREEN);
        PARENT_TO_CATEGORY_KEY_MAP.put(ZenModeSettings.class.getName(),
                CategoryKey.CATEGORY_DO_NOT_DISTURB);
        PARENT_TO_CATEGORY_KEY_MAP.put(GestureSettings.class.getName(),
                CategoryKey.CATEGORY_GESTURES);
        PARENT_TO_CATEGORY_KEY_MAP.put(NightDisplaySettings.class.getName(),
                CategoryKey.CATEGORY_NIGHT_DISPLAY);
        PARENT_TO_CATEGORY_KEY_MAP.put(PrivacyDashboardFragment.class.getName(),
                CategoryKey.CATEGORY_PRIVACY);
        PARENT_TO_CATEGORY_KEY_MAP.put(EnterprisePrivacySettings.class.getName(),
                CategoryKey.CATEGORY_ENTERPRISE_PRIVACY);
        PARENT_TO_CATEGORY_KEY_MAP.put(LegalSettings.class.getName(),
                CategoryKey.CATEGORY_ABOUT_LEGAL);
        PARENT_TO_CATEGORY_KEY_MAP.put(MyDeviceInfoFragment.class.getName(),
                CategoryKey.CATEGORY_MY_DEVICE_INFO);
        PARENT_TO_CATEGORY_KEY_MAP.put(BatterySaverSettings.class.getName(),
                CategoryKey.CATEGORY_BATTERY_SAVER_SETTINGS);

        CATEGORY_KEY_TO_PARENT_MAP = new ArrayMap<>(PARENT_TO_CATEGORY_KEY_MAP.size());

        for (Map.Entry<String, String> parentToKey : PARENT_TO_CATEGORY_KEY_MAP.entrySet()) {
            CATEGORY_KEY_TO_PARENT_MAP.put(parentToKey.getValue(), parentToKey.getKey());
        }
    }
}
