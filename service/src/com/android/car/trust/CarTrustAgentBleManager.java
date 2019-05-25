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

package com.android.car.trust;

import android.annotation.IntDef;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.car.CarLocalServices;
import com.android.car.R;
import com.android.car.Utils;
import com.android.car.trust.BLEStream.BLEMessage;
import com.android.car.trust.BLEStream.BLEMessage.OperationType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.UUID;

/**
 * A BLE Service that is used for communicating with the trusted peer device. This extends from a
 * more generic {@link BLEManager} and has more context on the BLE requirements for the Trusted
 * device feature. It has knowledge on the GATT services and characteristics that are specific to
 * the Trusted Device feature.
 */
class CarTrustAgentBleManager extends BleManager {

    private static final String TAG = "CarTrustBLEManager";

    /** @hide */
    @IntDef(prefix = {"TRUSTED_DEVICE_OPERATION_"}, value = {
            TRUSTED_DEVICE_OPERATION_NONE,
            TRUSTED_DEVICE_OPERATION_ENROLLMENT,
            TRUSTED_DEVICE_OPERATION_UNLOCK
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface TrustedDeviceOperation {
    }

    private static final int TRUSTED_DEVICE_OPERATION_NONE = 0;
    private static final int TRUSTED_DEVICE_OPERATION_ENROLLMENT = 1;
    private static final int TRUSTED_DEVICE_OPERATION_UNLOCK = 2;

    @TrustedDeviceOperation
    private int mCurrentTrustedDeviceOperation = TRUSTED_DEVICE_OPERATION_NONE;
    private CarTrustedDeviceService mCarTrustedDeviceService;
    private CarTrustAgentEnrollmentService mCarTrustAgentEnrollmentService;
    private CarTrustAgentUnlockService mCarTrustAgentUnlockService;
    private String mOriginalBluetoothName;
    private byte[] mUniqueId;
    private String mRandomName;
    private int mMtuSize = 20;

    // Enrollment Service and Characteristic UUIDs
    private UUID mEnrollmentServiceUuid;
    private UUID mEnrollmentClientWriteUuid;
    private UUID mEnrollmentServerWriteUuid;
    private BluetoothGattService mEnrollmentGattService;

    // Unlock Service and Characteristic UUIDs
    private UUID mUnlockServiceUuid;
    private UUID mUnlockEscrowTokenUuid;
    private UUID mUnlockTokenHandleUuid;
    private BluetoothGattService mUnlockGattService;

    private BLEMessagePayloadStream mBleMessagePayloadStream = new BLEMessagePayloadStream();

    CarTrustAgentBleManager(Context context) {
        super(context);
    }

    // Overriding some of the {@link BLEManager} methods to be specific for Trusted Device feature.
    @Override
    public void onRemoteDeviceConnected(BluetoothDevice device) {
        if (getTrustedDeviceService() == null) {
            return;
        }
        // Retrieving device name only happens in enrollment, the retrieved device name will be
        // stored in sharedPreference for further use.
        if (mCurrentTrustedDeviceOperation == TRUSTED_DEVICE_OPERATION_ENROLLMENT
                && device.getName() == null) {
            retrieveDeviceName(device);
        }
        getTrustedDeviceService().onRemoteDeviceConnected(device);
    }

    @Override
    public void onRemoteDeviceDisconnected(BluetoothDevice device) {
        if (getTrustedDeviceService() != null) {
            getTrustedDeviceService().onRemoteDeviceDisconnected(device);
        }
    }

    @Override
    protected void onDeviceNameRetrieved(@Nullable String deviceName) {
        if (getTrustedDeviceService() != null) {
            getTrustedDeviceService().onDeviceNameRetrieved(deviceName);
        }
    }

    @Override
    protected void onMtuSizeChanged(int size) {
        mMtuSize = size;
    }

    @Override
    public void onCharacteristicWrite(BluetoothDevice device, int requestId,
            BluetoothGattCharacteristic characteristic, boolean preparedWrite,
            boolean responseNeeded, int offset, byte[] value) {
        UUID uuid = characteristic.getUuid();
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onCharacteristicWrite received uuid: " + uuid);
        }
        // This write operation is not thread safe individually, but is guarded by the callback
        // here.
        mBleMessagePayloadStream.write(value);
        if (!mBleMessagePayloadStream.isComplete()) {
            return;
        }
        if (uuid.equals(mEnrollmentClientWriteUuid)) {
            if (getEnrollmentService() != null) {
                getEnrollmentService().onEnrollmentDataReceived(
                        mBleMessagePayloadStream.toByteArray());
            }
        } else if (uuid.equals(mUnlockEscrowTokenUuid)) {
            if (getUnlockService() != null) {
                getUnlockService().onUnlockTokenReceived(mBleMessagePayloadStream.toByteArray());

            }
        } else if (uuid.equals(mUnlockTokenHandleUuid)) {
            if (getUnlockService() != null) {
                getUnlockService().onUnlockHandleReceived(mBleMessagePayloadStream.toByteArray());
            }
        }
        mBleMessagePayloadStream.reset();
    }

    @Override
    public void onCharacteristicRead(BluetoothDevice device, int requestId, int offset,
            final BluetoothGattCharacteristic characteristic) {
        // Ignored read requests.
    }

    @Nullable
    private CarTrustedDeviceService getTrustedDeviceService() {
        if (mCarTrustedDeviceService == null) {
            mCarTrustedDeviceService = CarLocalServices.getService(CarTrustedDeviceService.class);
        }
        return mCarTrustedDeviceService;
    }

    @Nullable
    private CarTrustAgentEnrollmentService getEnrollmentService() {
        if (mCarTrustAgentEnrollmentService != null) {
            return mCarTrustAgentEnrollmentService;
        }

        if (getTrustedDeviceService() != null) {
            mCarTrustAgentEnrollmentService =
                    getTrustedDeviceService().getCarTrustAgentEnrollmentService();
        }
        return mCarTrustAgentEnrollmentService;
    }

    @Nullable
    private CarTrustAgentUnlockService getUnlockService() {
        if (mCarTrustAgentUnlockService != null) {
            return mCarTrustAgentUnlockService;
        }

        if (getTrustedDeviceService() != null) {
            mCarTrustAgentUnlockService = getTrustedDeviceService().getCarTrustAgentUnlockService();
        }
        return mCarTrustAgentUnlockService;
    }

    @Nullable
    private byte[] getUniqueId() {
        if (mUniqueId != null) {
            return mUniqueId;
        }

        if (getTrustedDeviceService() != null && getTrustedDeviceService().getUniqueId() != null) {
            mUniqueId = Utils.uuidToBytes(getTrustedDeviceService().getUniqueId());
        }
        return mUniqueId;
    }

    @Nullable
    private String getRandomName() {
        if (mRandomName != null) {
            return mRandomName;
        }

        if (getTrustedDeviceService() != null) {
            mRandomName = getTrustedDeviceService().getRandomName();
        }
        return mRandomName;
    }

    /**
     * Setup the BLE GATT server for Enrollment. The GATT server for Enrollment comprises of one
     * GATT Service and 2 characteristics - one for the escrow token to be generated and sent from
     * the phone and the other for the handle generated and sent by the Head unit.
     */
    void setupEnrollmentBleServer() {
        mEnrollmentServiceUuid = UUID.fromString(
                getContext().getString(R.string.enrollment_service_uuid));
        mEnrollmentClientWriteUuid = UUID.fromString(
                getContext().getString(R.string.enrollment_client_write_uuid));
        mEnrollmentServerWriteUuid = UUID.fromString(
                getContext().getString(R.string.enrollment_server_write_uuid));

        mEnrollmentGattService = new BluetoothGattService(mEnrollmentServiceUuid,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // Characteristic to describe the escrow token being used for unlock
        BluetoothGattCharacteristic clientCharacteristic =
                new BluetoothGattCharacteristic(mEnrollmentClientWriteUuid,
                        BluetoothGattCharacteristic.PROPERTY_WRITE,
                        BluetoothGattCharacteristic.PERMISSION_WRITE);

        // Characteristic to describe the handle being used for this escrow token
        BluetoothGattCharacteristic serverCharacteristic =
                new BluetoothGattCharacteristic(mEnrollmentServerWriteUuid,
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                        BluetoothGattCharacteristic.PERMISSION_READ);

        mEnrollmentGattService.addCharacteristic(clientCharacteristic);
        mEnrollmentGattService.addCharacteristic(serverCharacteristic);
    }

    /**
     * Setup the BLE GATT server for Unlocking the Head unit. The GATT server for this phase also
     * comprises of 1 Service and 2 characteristics. However both the token and the handle are sent
     * ftrom the phone to the head unit.
     */
    void setupUnlockBleServer() {
        mUnlockServiceUuid = UUID.fromString(getContext().getString(R.string.unlock_service_uuid));
        mUnlockEscrowTokenUuid = UUID
                .fromString(getContext().getString(R.string.unlock_escrow_token_uuid));
        mUnlockTokenHandleUuid = UUID
                .fromString(getContext().getString(R.string.unlock_handle_uuid));

        mUnlockGattService = new BluetoothGattService(mUnlockServiceUuid,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // Characteristic to describe the escrow token being used for unlock
        BluetoothGattCharacteristic tokenCharacteristic = new BluetoothGattCharacteristic(
                mUnlockEscrowTokenUuid,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);

        // Characteristic to describe the handle being used for this escrow token
        BluetoothGattCharacteristic handleCharacteristic = new BluetoothGattCharacteristic(
                mUnlockTokenHandleUuid,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);

        mUnlockGattService.addCharacteristic(tokenCharacteristic);
        mUnlockGattService.addCharacteristic(handleCharacteristic);
    }

    void startEnrollmentAdvertising() {
        mCurrentTrustedDeviceOperation = TRUSTED_DEVICE_OPERATION_ENROLLMENT;
        // Replace name to ensure it is small enough to be advertised
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        String name = getRandomName();
        if (mOriginalBluetoothName == null) {
            mOriginalBluetoothName = adapter.getName();
        }
        if (name != null) {
            adapter.setName(name);
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Changing bluetooth adapter name from "
                    + mOriginalBluetoothName + " to " + name);
        }
        startAdvertising(mEnrollmentGattService,
                new AdvertiseData.Builder()
                        .setIncludeDeviceName(true)
                        .addServiceUuid(new ParcelUuid(mEnrollmentServiceUuid))
                        .build(),
                mEnrollmentAdvertisingCallback);
    }

    void stopEnrollmentAdvertising() {
        if (mOriginalBluetoothName != null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Changing bluetooth adapter name back to "
                        + mOriginalBluetoothName);
            }
            BluetoothAdapter.getDefaultAdapter().setName(mOriginalBluetoothName);
        }
        stopAdvertising(mEnrollmentAdvertisingCallback);
    }

    void startUnlockAdvertising() {
        mCurrentTrustedDeviceOperation = TRUSTED_DEVICE_OPERATION_UNLOCK;
        startAdvertising(mUnlockGattService,
                new AdvertiseData.Builder()
                        .setIncludeDeviceName(false)
                        .addServiceData(new ParcelUuid(mUnlockServiceUuid), getUniqueId())
                        .addServiceUuid(new ParcelUuid(mUnlockServiceUuid))
                        .build(),
                mUnlockAdvertisingCallback);
    }

    void stopUnlockAdvertising() {
        mCurrentTrustedDeviceOperation = TRUSTED_DEVICE_OPERATION_NONE;
        stopAdvertising(mUnlockAdvertisingCallback);
    }

    void disconnectRemoteDevice() {
        mBleMessagePayloadStream.reset();
        stopGattServer();
    }

    /**
     * Sends the given message to the specified device.
     *
     * @param device  The device to send the message to.
     * @param message A message to send.
     */
    void sendMessage(BluetoothDevice device, byte[] message, OperationType operation,
            boolean isPayloadEncrypted) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "sendMessage to: " + device.getAddress());
        }
        BluetoothGattCharacteristic serverCharacteristic = mEnrollmentGattService
                .getCharacteristic(mEnrollmentServerWriteUuid);
        List<BLEMessage> bleMessages = BLEMessageV1Factory.makeBLEMessages(message, operation,
                mMtuSize, isPayloadEncrypted);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "sending " + bleMessages.size() + " messages to device");
        }
        for (BLEMessage bleMessage : bleMessages) {
            // TODO(b/131719066) get acknowledgement from the phone then continue to send packets
            serverCharacteristic.setValue(bleMessage.toByteArray());
            notifyCharacteristicChanged(device, serverCharacteristic, false);
        }
    }

    private final AdvertiseCallback mEnrollmentAdvertisingCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            if (getEnrollmentService() != null) {
                getEnrollmentService().onEnrollmentAdvertiseStartSuccess();
            }
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Successfully started advertising service");
            }
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.e(TAG, "Failed to advertise, errorCode: " + errorCode);

            super.onStartFailure(errorCode);
            if (getEnrollmentService() != null) {
                getEnrollmentService().onEnrollmentAdvertiseStartFailure();
            }
        }
    };

    private final AdvertiseCallback mUnlockAdvertisingCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Unlock Advertising onStartSuccess");
            }
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.e(TAG, "Failed to advertise, errorCode: " + errorCode);
            super.onStartFailure(errorCode);
            if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED) {
                return;
            }
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Start unlock advertising fail, retry to advertising..");
            }
            setupUnlockBleServer();
            startUnlockAdvertising();
        }
    };
}
