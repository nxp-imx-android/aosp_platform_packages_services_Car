/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.car.developeroptions.datausage;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;

import com.android.car.developeroptions.R;

public class DataSaverPreference extends Preference implements DataSaverBackend.Listener {

    private final DataSaverBackend mDataSaverBackend;

    public DataSaverPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDataSaverBackend = new DataSaverBackend(context);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        mDataSaverBackend.addListener(this);
    }

    @Override
    public void onDetached() {
        super.onDetached();
        mDataSaverBackend.remListener(this);
    }

    @Override
    public void onDataSaverChanged(boolean isDataSaving) {
        setSummary(isDataSaving ? R.string.data_saver_on : R.string.data_saver_off);
    }

    @Override
    public void onWhitelistStatusChanged(int uid, boolean isWhitelisted) {
    }

    @Override
    public void onBlacklistStatusChanged(int uid, boolean isBlacklisted) {
    }
}
