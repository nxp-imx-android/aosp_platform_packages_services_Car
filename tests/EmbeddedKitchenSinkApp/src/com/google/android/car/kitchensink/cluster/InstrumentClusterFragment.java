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
package com.google.android.car.kitchensink.cluster;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.car.Car;
import android.support.car.CarAppFocusManager;
import android.support.car.CarAppFocusManager.AppFocusChangeListener;
import android.support.car.CarAppFocusManager.AppFocusOwnershipChangeListener;
import android.support.car.CarNotConnectedException;
import android.support.car.ServiceConnectionCallback;
import android.support.car.navigation.CarNavigationStatusManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.car.kitchensink.R;

/**
 * Contains functions to test instrument cluster API.
 */
public class InstrumentClusterFragment extends Fragment {
    private static final String TAG = InstrumentClusterFragment.class.getSimpleName();

    private CarNavigationStatusManager mCarNavigationStatusManager;
    private CarAppFocusManager mCarAppFocusManager;
    private Car mCarApi;

    private final ServiceConnectionCallback mServiceConnectionCallback =
            new ServiceConnectionCallback() {
                @Override
                public void onServiceConnected(ComponentName name) {
                    Log.d(TAG, "Connected to Car Service");
                    try {
                        mCarNavigationStatusManager = (CarNavigationStatusManager) mCarApi.getCarManager(
                                android.car.Car.CAR_NAVIGATION_SERVICE);
                        mCarAppFocusManager =
                                (CarAppFocusManager) mCarApi.getCarManager(Car.APP_FOCUS_SERVICE);
                    } catch (CarNotConnectedException e) {
                        Log.e(TAG, "Car is not connected!", e);
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    Log.d(TAG, "Disconnect from Car Service");
                }

                @Override
                public void onServiceSuspended(int cause) {
                    Log.d(TAG, "Car Service connection suspended");
                }

                @Override
                public void onServiceConnectionFailed(int cause) {
                    Log.d(TAG, "Car Service connection failed");
                }
            };

    private void initCarApi() {
        if (mCarApi != null && mCarApi.isConnected()) {
            mCarApi.disconnect();
            mCarApi = null;
        }

        mCarApi = Car.createCar(getContext(), mServiceConnectionCallback);
        mCarApi.connect();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.instrument_cluster, container, false);

        view.findViewById(R.id.cluster_start_button).setOnClickListener(v -> initCluster());
        view.findViewById(R.id.cluster_turn_left_button).setOnClickListener(v -> turnLeft());

        return view;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        initCarApi();

        super.onCreate(savedInstanceState);
    }

    private void turnLeft() {
        try {
            mCarNavigationStatusManager
                    .sendNavigationTurnEvent(CarNavigationStatusManager.TURN_TURN, "Huff Ave", 90,
                            -1, null, CarNavigationStatusManager.TURN_SIDE_LEFT);
            mCarNavigationStatusManager.sendNavigationTurnDistanceEvent(500, 10, 500,
                    CarNavigationStatusManager.DISTANCE_METERS);
        } catch (CarNotConnectedException e) {
            e.printStackTrace();
            initCarApi();  // This might happen due to inst cluster renderer crash.
        }
    }

    private void initCluster() {
        try {
            mCarAppFocusManager.addFocusListener(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION,
                    new AppFocusChangeListener() {
                @Override
                public void onAppFocusChange(int appType, boolean active) {
                    Log.d(TAG, "onAppFocusChange, appType: " + appType + " active: " + active);
                }
            });
        } catch (CarNotConnectedException e) {
            Log.e(TAG, "Failed to register focus listener", e);
        }

        AppFocusOwnershipChangeListener focusListener = new AppFocusOwnershipChangeListener() {
            @Override
            public void onAppFocusOwnershipLoss(int focus) {
                Log.w(TAG, "onAppFocusOwnershipLoss, focus: " + focus);
                new AlertDialog.Builder(getContext())
                        .setTitle(getContext().getApplicationInfo().name)
                        .setMessage(R.string.cluster_nav_app_context_loss)
                        .show();
            }
        };
        try {
            mCarAppFocusManager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION,
                    focusListener);
        } catch (CarNotConnectedException e) {
            Log.e(TAG, "Failed to set active focus", e);
        }

        try {
            boolean ownsFocus = mCarAppFocusManager.isOwningFocus(
                    CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION, focusListener);
            Log.d(TAG, "Owns APP_FOCUS_TYPE_NAVIGATION: " + ownsFocus);
            if (!ownsFocus) {
                throw new RuntimeException("Focus was not acquired.");
            }
        } catch (CarNotConnectedException e) {
            Log.e(TAG, "Failed to get owned focus", e);
        }

        try {
            mCarNavigationStatusManager
                    .sendNavigationStatus(CarNavigationStatusManager.STATUS_ACTIVE);
        } catch (CarNotConnectedException e) {
            Log.e(TAG, "Failed to set navigation status, reconnecting to the car", e);
            initCarApi();  // This might happen due to inst cluster renderer crash.
        }
    }
}
