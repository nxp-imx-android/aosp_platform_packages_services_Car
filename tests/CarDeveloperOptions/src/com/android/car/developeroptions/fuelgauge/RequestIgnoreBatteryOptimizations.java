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

package com.android.car.developeroptions.fuelgauge;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerWhitelistManager;
import android.util.Log;

import com.android.car.developeroptions.R;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;

public class RequestIgnoreBatteryOptimizations extends AlertActivity implements
        DialogInterface.OnClickListener {
    static final String TAG = "RequestIgnoreBatteryOptimizations";

    private PowerWhitelistManager mPowerWhitelistManager;
    String mPackageName;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPowerWhitelistManager = getSystemService(PowerWhitelistManager.class);

        Uri data = getIntent().getData();
        if (data == null) {
            Log.w(TAG, "No data supplied for IGNORE_BATTERY_OPTIMIZATION_SETTINGS in: "
                    + getIntent());
            finish();
            return;
        }
        mPackageName = data.getSchemeSpecificPart();
        if (mPackageName == null) {
            Log.w(TAG, "No data supplied for IGNORE_BATTERY_OPTIMIZATION_SETTINGS in: "
                    + getIntent());
            finish();
            return;
        }

        PowerManager power = getSystemService(PowerManager.class);
        if (power.isIgnoringBatteryOptimizations(mPackageName)) {
            Log.i(TAG, "Not should prompt, already ignoring optimizations: " + mPackageName);
            finish();
            return;
        }

        ApplicationInfo ai;
        try {
            ai = getPackageManager().getApplicationInfo(mPackageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Requested package doesn't exist: " + mPackageName);
            finish();
            return;
        }

        if (getPackageManager().checkPermission(
                Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, mPackageName)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Requested package " + mPackageName + " does not hold permission "
                    + Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            finish();
            return;
        }

        final AlertController.AlertParams p = mAlertParams;
        p.mTitle = getText(R.string.high_power_prompt_title);
        p.mMessage = getString(R.string.high_power_prompt_body, ai.loadLabel(getPackageManager()));
        p.mPositiveButtonText = getText(R.string.allow);
        p.mNegativeButtonText = getText(R.string.deny);
        p.mPositiveButtonListener = this;
        p.mNegativeButtonListener = this;
        setupAlert();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case BUTTON_POSITIVE:
                mPowerWhitelistManager.addToWhitelist(mPackageName);
                setResult(RESULT_OK);
                break;
            case BUTTON_NEGATIVE:
                break;
        }
    }
}
