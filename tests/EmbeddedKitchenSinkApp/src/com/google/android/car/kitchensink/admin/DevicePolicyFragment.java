/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.car.kitchensink.admin;

import android.annotation.Nullable;
import android.app.AlertDialog;
import android.car.Car;
import android.car.admin.CarDevicePolicyManager;
import android.car.admin.CreateUserResult;
import android.car.admin.RemoveUserResult;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.KitchenSinkActivity;
import com.google.android.car.kitchensink.R;
import com.google.android.car.kitchensink.users.ExistingUsersView;
import com.google.android.car.kitchensink.users.UserInfoView;

/**
 * Test UI for {@link CarDevicePolicyManager}.
 */
public final class DevicePolicyFragment extends Fragment {

    private static final String TAG = DevicePolicyFragment.class.getSimpleName();

    private UserManager mUserManager;
    private CarDevicePolicyManager mCarDevicePolicyManager;

    // Current user
    private UserInfoView mCurrentUser;

    // Existing users
    private ExistingUsersView mCurrentUsers;

    // New user
    private EditText mNewUserNameText;
    private CheckBox mNewUserIsAdminCheckBox;
    private CheckBox mNewUserIsGuestCheckBox;
    private EditText mNewUserExtraFlagsText;

    // Actions
    private Button mCreateUserButton;
    private Button mRemoveUserButton;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.device_policy, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mUserManager = UserManager.get(getContext());
        Car car = ((KitchenSinkActivity) getHost()).getCar();
        mCarDevicePolicyManager = (CarDevicePolicyManager) car
                .getCarManager(Car.CAR_DEVICE_POLICY_SERVICE);

        mCurrentUser = view.findViewById(R.id.current_user);
        mCurrentUsers = view.findViewById(R.id.current_users);
        mRemoveUserButton = view.findViewById(R.id.remove_user);

        mNewUserNameText = view.findViewById(R.id.new_user_name);
        mNewUserIsAdminCheckBox = view.findViewById(R.id.new_user_is_admin);
        mNewUserIsGuestCheckBox = view.findViewById(R.id.new_user_is_guest);
        mCreateUserButton = view.findViewById(R.id.create_user);

        mRemoveUserButton.setOnClickListener((v) -> removeUser());
        mCreateUserButton.setOnClickListener((v) -> createUser());

        updateState();
    }

    private void updateState() {
        // Current user
        int userId = UserHandle.myUserId();
        UserInfo user = mUserManager.getUserInfo(userId);
        Log.v(TAG, "updateState(): currentUser= " + user);
        mCurrentUser.update(user);

        // Existing users
        mCurrentUsers.updateState();
    }

    private void removeUser() {
        int userId = mCurrentUsers.getSelectedUserId();
        Log.i(TAG, "Remove user: " + userId);
        RemoveUserResult result = mCarDevicePolicyManager.removeUser(UserHandle.of(userId));
        if (result.isSuccess()) {
            updateState();
            showMessage("User %d removed", userId);
        } else {
            showMessage("Failed to remove user %d: %s", userId, result);
        }
    }

    private void createUser() {
        String name = mNewUserNameText.getText().toString();
        if (TextUtils.isEmpty(name)) {
            name = null;
        }
        // Type is treated as a flag here so we can emulate an invalid value by selecting both.
        int type = CarDevicePolicyManager.USER_TYPE_REGULAR;
        boolean isAdmin = mNewUserIsAdminCheckBox.isChecked();
        if (isAdmin) {
            type |= CarDevicePolicyManager.USER_TYPE_ADMIN;
        }
        boolean isGuest = mNewUserIsGuestCheckBox.isChecked();
        if (isGuest) {
            type |= CarDevicePolicyManager.USER_TYPE_GUEST;
        }
        CreateUserResult result = mCarDevicePolicyManager.createUser(name, type);
        if (result.isSuccess()) {
            showMessage("User crated: %s", result.getUserHandle().getIdentifier());
            updateState();
        } else {
            showMessage("Failed to create user with type %d: %s", type, result);
        }
    }

    private void showMessage(String pattern, Object... args) {
        String message = String.format(pattern, args);
        Log.v(TAG, "showMessage(): " + message);
        new AlertDialog.Builder(getContext()).setMessage(message).show();
    }
}
