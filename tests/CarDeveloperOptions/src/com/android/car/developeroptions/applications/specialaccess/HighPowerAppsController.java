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

package com.android.car.developeroptions.applications.specialaccess;

import android.content.Context;

import com.android.car.developeroptions.R;
import com.android.car.developeroptions.core.BasePreferenceController;

public class HighPowerAppsController extends BasePreferenceController {

    public HighPowerAppsController(Context context, String key) {
        super(context, key);
    }

    @AvailabilityStatus
    public int getAvailabilityStatus() {
        return mContext.getResources().getBoolean(R.bool.config_show_high_power_apps)
                ? AVAILABLE
                : UNSUPPORTED_ON_DEVICE;
    }
}