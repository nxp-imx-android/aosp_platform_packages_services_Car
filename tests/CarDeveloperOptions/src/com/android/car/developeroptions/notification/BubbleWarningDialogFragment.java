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
package com.android.car.developeroptions.notification;

import android.app.Dialog;
import android.app.settings.SettingsEnums;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;

import com.android.car.developeroptions.R;
import com.android.car.developeroptions.core.instrumentation.InstrumentedDialogFragment;

public class BubbleWarningDialogFragment extends InstrumentedDialogFragment {
    static final String KEY_PKG = "p";
    static final String KEY_UID = "u";


    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DIALOG_APP_BUBBLE_SETTINGS;
    }

    public BubbleWarningDialogFragment setPkgInfo(String pkg, int uid) {
        Bundle args = new Bundle();
        args.putString(KEY_PKG, pkg);
        args.putInt(KEY_UID, uid);
        setArguments(args);
        return this;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bundle args = getArguments();
        final String pkg = args.getString(KEY_PKG);
        final int uid = args.getInt(KEY_UID);

        final String title =
                getResources().getString(R.string.bubbles_feature_disabled_dialog_title);
        final String summary = getResources()
                .getString(R.string.bubbles_feature_disabled_dialog_text);
        return new AlertDialog.Builder(getContext())
                .setMessage(summary)
                .setTitle(title)
                .setCancelable(true)
                .setPositiveButton(R.string.bubbles_feature_disabled_button_approve,
                        (dialog, id) ->
                                BubblePreferenceController.applyBubblesApproval(
                                        getContext(), pkg, uid))
                .setNegativeButton(R.string.bubbles_feature_disabled_button_cancel,
                        (dialog, id) ->
                                BubblePreferenceController.revertBubblesApproval(
                                        getContext(), pkg, uid))
                .create();
    }
}
