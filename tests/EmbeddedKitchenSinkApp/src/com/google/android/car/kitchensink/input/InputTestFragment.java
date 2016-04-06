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
package com.google.android.car.kitchensink.input;

import android.annotation.Nullable;
import android.annotation.StringRes;
import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.test.CarTestManager;
import android.car.test.CarTestManagerBinderWrapper;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ToggleButton;

import com.google.android.car.kitchensink.CarEmulator;
import com.google.android.car.kitchensink.R;

import com.android.car.vehiclenetwork.VehicleNetworkConsts;
import com.android.car.vehiclenetwork.VehicleNetworkConsts.VehicleHwKeyInputAction;
import com.android.car.vehiclenetwork.VehiclePropValueUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.android.car.vehiclenetwork.VehicleNetworkConsts;
import com.android.car.vehiclenetwork.VehicleNetworkConsts.VehicleHwKeyInputAction;
import com.android.car.vehiclenetwork.VehiclePropValueUtil;

import com.google.android.car.kitchensink.R;

/**
 * Test input event handling to system.
 * vehicle hal should have VEHICLE_PROPERTY_HW_KEY_INPUT support for this to work.
 */
public class InputTestFragment extends Fragment {

    private static final String TAG = "CAR.INPUT.KS";

    private Car mCar;
    private CarTestManager mTestManager;
    private ToggleButton mEnableMocking;
    private CarEmulator mCarEmulator;

    List<Button> mButtons = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.input_test, container, false);

        Collections.addAll(mButtons,
                createButton(R.string.volume_up, KeyEvent.KEYCODE_VOLUME_UP),
                createButton(R.string.volume_down, KeyEvent.KEYCODE_VOLUME_DOWN),
                createButton(R.string.voice, KeyEvent.KEYCODE_VOICE_ASSIST),
                createButton(R.string.music, KeyEvent.KEYCODE_MUSIC));

        addButtonsToPanel((ViewGroup) view.findViewById(R.id.input_buttons), mButtons);

        mCar = Car.createCar(getContext(), new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.i(TAG, "onServiceConnected");
                try {
                    mTestManager = new CarTestManager(
                            (CarTestManagerBinderWrapper) mCar.getCarManager(Car.TEST_SERVICE));
                } catch (CarNotConnectedException e) {
                    throw new RuntimeException("Failed to create test service manager", e);
                }
                checkHwKeyInputSupported();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.i(TAG, "onServiceDisconnected");
            }
        });
        mCar.connect();

        mEnableMocking = (ToggleButton) view.findViewById(R.id.button_mock_input);
        mEnableMocking.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mCarEmulator == null) {
                mCarEmulator = new CarEmulator(mCar);
            }
            if (isChecked) {
                mCarEmulator.start();
            } else {
                mCarEmulator.stop();
                mCarEmulator = null;
            }

            checkHwKeyInputSupported();
        });

        return view;
    }

    private Button createButton(@StringRes int textResId, int keyCode) {
        Button button = new Button(getContext());
        button.setText(getContext().getString(textResId));
        button.setTextSize(32f);
        // Single touch + key event does not work as touch is happening in other window
        // at the same time. But long press will work.
        button.setOnTouchListener((v, event) -> {
            handleTouchEvent(event, keyCode);
            return true;
        });

        return button;
    }

    private void checkHwKeyInputSupported() {
        boolean hwKeyInputSupported = mTestManager.isPropertySupported(
                VehicleNetworkConsts.VEHICLE_PROPERTY_HW_KEY_INPUT);
        if (!hwKeyInputSupported) {
            Log.w(TAG, "VEHICLE_PROPERTY_HW_KEY_INPUT not supported");
        }

        for (View v : mButtons) {
            v.setEnabled(hwKeyInputSupported);
        }
    }

    private void handleTouchEvent(MotionEvent event, int keyCode) {
        int action = event.getActionMasked();
        Log.i(TAG, "handleTouchEvent, action:" + action + ",keyCode:" + keyCode);
        boolean shouldInject = false;
        boolean isDown = false;
        if (action == MotionEvent.ACTION_DOWN) {
            shouldInject = true;
            isDown = true;
        } else if (action == MotionEvent.ACTION_UP) {
            shouldInject = true;
        }
        if (shouldInject) {
            int[] values = { isDown ? VehicleHwKeyInputAction.VEHICLE_HW_KEY_INPUT_ACTION_DOWN :
                VehicleHwKeyInputAction.VEHICLE_HW_KEY_INPUT_ACTION_UP, keyCode, 0, 0 };
            long now = SystemClock.elapsedRealtimeNanos();
            mTestManager.injectEvent(VehiclePropValueUtil.createIntVectorValue(
                    VehicleNetworkConsts.VEHICLE_PROPERTY_HW_KEY_INPUT, values, now));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mCar.disconnect();
    }

    private static void addButtonsToPanel(ViewGroup panel, List<Button> buttons) {
        for (Button button : buttons) {
            panel.addView(button);
        }
    }
}
