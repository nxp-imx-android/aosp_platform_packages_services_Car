/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.car.kitchensink.alertdialog;

import android.annotation.Nullable;
import android.annotation.StringRes;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.hardware.automotive.vehicle.V2_0.VehicleHwKeyInputAction;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import com.google.android.car.kitchensink.CarEmulator;
import com.google.android.car.kitchensink.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shows alert dialogs
 */
public class AlertDialogTestFragment extends Fragment {

    private static final String TAG = "CAR.ALERT";

    private Button alertMessageBtn;
    private Button alertCustomViewBtn;


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.alert_dialog_test, container, false);

        alertMessageBtn = view.findViewById(R.id.alert_message_btn);
        alertCustomViewBtn = view.findViewById(R.id.alert_custom_view_btn);

        alertMessageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder;
                builder = new AlertDialog.Builder(getContext());
                builder.setTitle("Alert Title")
                        .setMessage("Message for Alert dialog window, this part is text only")
                        .setPositiveButton(
                                android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // do nothing
                            }
                        })
                        .setNegativeButton(
                                android.R.string.no, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // do nothing
                            }
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
            }
        });

        alertCustomViewBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder;
                builder = new AlertDialog.Builder(getContext());
                builder.setTitle("Alert Title")
                        .setView(R.layout.alert_dialog_bluetooth_pin_entry)
                        .setPositiveButton(
                                android.R.string.yes, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        // do nothing
                                    }
                                })
                        .setNegativeButton(
                                android.R.string.no, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        // do nothing
                                    }
                                })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
            }
        });

        return view;
    }
}
