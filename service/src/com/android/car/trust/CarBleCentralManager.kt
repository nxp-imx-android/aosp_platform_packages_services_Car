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

package com.android.car.trust

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.car.encryptionrunner.Key
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.android.car.Utils
import com.android.car.guard
import java.math.BigInteger
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet

private const val TAG = "CarBleCentralManager"

// system/bt/internal_include/bt_target.h#GATT_MAX_PHY_CHANNEL
private const val MAX_CONNECTIONS = 7

private val CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/**
 * Communication manager for a car that maintains continuous connections with all devices in the car
 * for the duration of a drive.
 *
 * @param context Application's [Context].
 * @param bleCentralManager [BleCentralManager] for establishing connections.
 * @param carCompanionDeviceStorage Shared [CarCompanionDeviceStorage] for companion features.
 * @param serviceUuid [UUID] of peripheral's service.
 * @param bgServiceMask iOS overflow bit mask for service UUID.
 * @param writeCharacteristicUuid [UUID] of characteristic the car will write to.
 * @param readCharacteristicUuid [UUID] of characteristic the device will write to.
 */
internal class CarBleCentralManager(
    private val context: Context,
    private val bleCentralManager: BleCentralManager,
    carCompanionDeviceStorage: CarCompanionDeviceStorage,
    private val serviceUuid: UUID,
    bgServiceMask: String,
    writeCharacteristicUuid: UUID,
    readCharacteristicUuid: UUID
) : CarBleManager(carCompanionDeviceStorage) {

    private val ignoredDevices = CopyOnWriteArraySet<BleDevice>()

    private val parsedBgServiceBitMask by lazy {
        BigInteger(bgServiceMask, 16)
    }

    private val scanSettings by lazy {
        ScanSettings.Builder()
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .build()
    }

    private val scanCallback by lazy {
        object : ScanCallback() {
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                // This method should never be called because our scan mode is
                // SCAN_MODE_LOW_LATENCY, meaning we will be notified of individual matches.
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scanning failed with error code: $errorCode")
            }

            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (shouldAttemptConnection(result)) {
                    startDeviceConnection(result.device)
                }
            }
        }
    }

    /**
     * Start process of finding and connecting to devices.
     */
    override fun start() {
        super.start()
        startScanning()
    }

    private fun startScanning() {
        bleCentralManager.startScanning(null, scanSettings, scanCallback)
    }

    /**
     * Stop process and disconnect from any connected devices.
     */
    override fun stop() {
        super.stop()
        bleCentralManager.stopScanning()
    }

    private fun ignoreDevice(deviceToIgnore: BleDevice) {
        ignoredDevices.add(deviceToIgnore)
    }

    private fun isDeviceIgnored(device: BluetoothDevice): Boolean {
        return ignoredDevices.any { it.device == device }
    }

    private fun shouldAttemptConnection(result: ScanResult): Boolean {
        // Ignore any results that are not connectable.
        if (!result.isConnectable) {
            return false
        }

        // Do not attempt to connect if we have already hit our max. This should rarely happen
        // and is protecting against a race condition of scanning stopped and new results coming in.
        if (connectedDevicesCount() >= MAX_CONNECTIONS) {
            return false
        }

        val device = result.device

        // Check if already attempting to connect to this device.
        if (getConnectedDevice(device) != null) {
            return false
        }

        // Connect to any device that is advertising our service UUID.
        if (result.scanRecord?.serviceUuids?.contains(ParcelUuid(serviceUuid)) == true ||
            result.containsUuidsInOverflow(parsedBgServiceBitMask)) {
            return true
        }

        // Do not connect if device has already been ignored.
        if (isDeviceIgnored(device)) {
            return false
        }

        // Can safely ignore devices advertising unrecognized service uuids.
        if (result.scanRecord?.serviceUuids?.isNotEmpty() == true) {
            return false
        }

        // TODO (b/139066293) Current implementation quickly exhausts connections resulting in
        // greatly reduced performance for connecting to devices we know we want to connect to.
        // Return true once fixed.
        return false
    }

    private val connectionCallback by lazy {
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (gatt == null) {
                    Log.w(TAG, "Null gatt passed to onConnectionStateChange. Ignoring.")
                    return
                }
                val bleDevice = getConnectedDevice(gatt) ?: return
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        deviceConnected(bleDevice)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        deviceDisconnected(bleDevice, status)
                    }
                    else -> {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "Connection state changed: " +
                                BluetoothProfile.getConnectionStateName(newState) +
                                " state: $status")
                        }
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (gatt == null) {
                    Log.w(TAG, "Null gatt passed to onServicesDiscovered. Ignoring.")
                    return
                }
                val connectedDevice = getConnectedDevice(gatt) ?: return
                val service = gatt.getService(serviceUuid) ?: run {
                    ignoreDevice(connectedDevice)
                    gatt.disconnect()
                    return
                }
                connectedDevice.state = BleDeviceState.CONNECTED
                val readCharacteristic = service.getCharacteristic(readCharacteristicUuid)
                val writeCharacteristic = service.getCharacteristic(writeCharacteristicUuid)
                if (readCharacteristic == null || writeCharacteristic == null) {
                    Log.w(TAG, "Unable to find expected characteristics on peripheral")
                    gatt.disconnect()
                    return
                }

                // Turn on notifications for read characteristic
                val descriptor = readCharacteristic.getDescriptor(CHARACTERISTIC_CONFIG).apply {
                    value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                }
                if (!gatt.writeDescriptor(descriptor)) {
                    Log.e(TAG, "Write descriptor failed!")
                    gatt.disconnect()
                    return
                }

                if (!gatt.setCharacteristicNotification(readCharacteristic, true)) {
                    Log.e(TAG, "Set notifications failed!")
                    gatt.disconnect()
                    return
                }

                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Service and characteristics successfully discovered")
                }

                val secureChannelCallback = object : SecureBleChannel.Callback {
                    override fun onSecureChannelEstablished(encryptionKey: Key) {
                        connectedDevice.deviceId?.guard {
                            deviceId -> notifyCallbacks { it.onSecureChannelEstablished(deviceId) }
                        }
                    }

                    override fun onMessageReceived(message: ByteArray) {
                        connectedDevice.deviceId?.guard {
                            deviceId -> notifyCallbacks { it.onMessageReceived(deviceId, message) }
                        }
                    }

                    override fun onMessageReceivedError(exception: java.lang.Exception) {
                    }

                    override fun onEstablishSecureChannelFailure() {
                    }

                    override fun onDeviceIdReceived(deviceId: String) {
                        connectedDevice.deviceId = deviceId
                        notifyCallbacks { it.onDeviceConnected(deviceId) }
                    }
                }
                // TODO(b/141312136) create SecureBleChannel and assign to connectedDevice.
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt?,
                descriptor: BluetoothGattDescriptor?,
                status: Int
            ) {
                if (gatt == null) {
                    Log.w(TAG, "Null gatt passed to onDescriptorWrite. Ignoring.")
                    return
                }

                // TODO (b/139067881) Replace with sending unique device id
                getConnectedDevice(gatt)?.guard {
                    sendMessage(it, Utils.uuidToBytes(UUID.randomUUID()), false)
                } ?: Log.w(TAG, "Descriptor callback called for disconnected device")
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                if (characteristic?.uuid != readCharacteristicUuid) {
                    return
                }
                if (gatt == null) {
                    Log.w(TAG, "Null gatt passed to onCharacteristicChanged. Ignoring.")
                    return
                }

                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Change detected on characteristic " +
                        "${characteristic.uuid}")
                }

                val connectedDevice = getConnectedDevice(gatt) ?: return
                val message = characteristic.value

                connectedDevice.deviceId?.also { deviceId ->
                    notifyCallbacks { it.onMessageReceived(deviceId, message) }
                } ?: run {
                    // Device id is the first message expected back from peripheral
                    val deviceId = String(message)
                    connectedDevice.deviceId = deviceId
                    notifyCallbacks { it.onDeviceConnected(deviceId) }
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
            }
        }
    }

    private fun startDeviceConnection(device: BluetoothDevice) {
        val gatt = device.connectGatt(context, false, connectionCallback,
            BluetoothDevice.TRANSPORT_LE) ?: return
        addConnectedDevice(BleDevice(device, gatt).apply { this.state = BleDeviceState.CONNECTING })

        // Stop scanning if we have reached the maximum connections
        if (connectedDevicesCount() >= MAX_CONNECTIONS) {
            bleCentralManager.stopScanning()
        }
    }

    private fun deviceConnected(device: BleDevice) {
        device.state = BleDeviceState.PENDING_VERIFICATION

        device.gatt?.discoverServices()

        val connectedCount = connectedDevicesCount()
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "New device connected: ${device.gatt?.device?.address}. " +
                "Active connections: $connectedCount")
        }
    }

    private fun deviceDisconnected(device: BleDevice, status: Int) {
        removeConnectedDevice(device)
        device.gatt?.close()
        device.deviceId?.guard { deviceId ->
            notifyCallbacks { it.onDeviceDisconnected(deviceId) }
        }
        val connectedCount = connectedDevicesCount()
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Device disconnected: ${device.gatt?.device?.address} with " +
                "state $status. Active connections: $connectedCount")
        }

        // Start scanning if dropping down from max
        if (!bleCentralManager.isScanning() && connectedCount < MAX_CONNECTIONS) {
            startScanning()
        }
    }
}
