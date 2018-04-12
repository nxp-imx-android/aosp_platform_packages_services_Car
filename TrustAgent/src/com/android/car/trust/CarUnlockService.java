/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.car.trust;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.UUID;

/**
 * A service that receives unlock requests from remote devices.
 */
public class CarUnlockService extends SimpleBleServer {

    private final IBinder mBinder = new UnlockServiceBinder();

    private BluetoothGattService mUnlockService;
    private BluetoothGattCharacteristic mUnlockEscrowToken;
    private BluetoothGattCharacteristic mUnlockTokenHandle;

    private OnUnlockDeviceListener mOnUnlockDeviceListener;

    private byte[] mCurrentToken;
    private Long mCurrentHandle;

    public class UnlockServiceBinder extends Binder {
        public CarUnlockService getService() {
            return CarUnlockService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setupUnlockService();
    }

    /**
     * Start advertising the BLE unlock service
     */
    public void start() {
        ParcelUuid uuid = new ParcelUuid(UUID.fromString(getString(R.string.unlock_service_uuid)));
        Log.d(Utils.LOG_TAG, "CarUnlockService start with uuid: " + uuid);
        start(uuid, mUnlockService, new Handler());
    }

    public void setOnUnlockDeviceListener(OnUnlockDeviceListener callback) {
        mOnUnlockDeviceListener = callback;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCharacteristicWrite(BluetoothDevice device,
            int requestId, BluetoothGattCharacteristic characteristic,
            boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
        UUID uuid = characteristic.getUuid();

        if (uuid.equals(mUnlockTokenHandle.getUuid())) {
            Log.d(Utils.LOG_TAG, "Unlock handle received, value: " + Utils.getLong(value));
            mCurrentHandle = Utils.getLong(value);
            unlockDataReceived();
        } else if (uuid.equals(mUnlockEscrowToken.getUuid())) {
            Log.d(Utils.LOG_TAG, "Unlock escrow token received, value: " + Utils.getLong(value));
            mCurrentToken = value;
            unlockDataReceived();
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothDevice device,
            int requestId, int offset, BluetoothGattCharacteristic characteristic) {
        // The BLE unlock service should not receive any read requests.
    }

    private synchronized void unlockDataReceived() {
        // If any piece of the unlocking data is not received, then do not unlock.
        if (mCurrentHandle == null || mCurrentToken == null) {
            return;
        }

        Log.d(Utils.LOG_TAG, "Handle and token both received, requesting unlock. Time: "
                + System.currentTimeMillis());
        // Both the handle and token has been received, try to unlock the device.
        mOnUnlockDeviceListener.onUnlockDevice(mCurrentToken, mCurrentHandle);

        // Once we've notified the client of the unlocking data, clear it out.
        mCurrentToken = null;
        mCurrentHandle = null;
    }


    // Create services and characteristics to receive tokens and handles for unlocking the device.
    private void setupUnlockService() {
        mUnlockService = new BluetoothGattService(
                UUID.fromString(getString(R.string.unlock_service_uuid)),
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // Characteristic to describe the escrow token being used for unlock
        mUnlockEscrowToken = new BluetoothGattCharacteristic(
                UUID.fromString(getString(R.string.unlock_escrow_token_uiid)),
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);

        // Characteristic to describe the handle being used for this escrow token
        mUnlockTokenHandle = new BluetoothGattCharacteristic(
                UUID.fromString(getString(R.string.unlock_handle_uiid)),
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);

        mUnlockService.addCharacteristic(mUnlockEscrowToken);
        mUnlockService.addCharacteristic(mUnlockTokenHandle);
    }

    /**
     * Interface to send the token to unlock a device
     */
    public interface OnUnlockDeviceListener {
        /**
         * Sends token to unlock the trust device
         * @param token
         * @param handle
         */
        void onUnlockDevice(byte[] token, long handle);
    }
}
