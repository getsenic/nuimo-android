/*
 * Copyright (c) 2015 Senic. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package com.senic.nuimo

import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.UUID

public class NuimoBluetoothController(bluetoothDevice: BluetoothDevice, context: Context): NuimoController(bluetoothDevice.address) {
    private val device = bluetoothDevice
    private val context = context
    private var gatt: BluetoothGatt? = null
    private var matrixCharacteristic: BluetoothGattCharacteristic? = null
    // At least some devices such as Samsung S3, S4, all BLE calls must occur from the main thread, see http://stackoverflow.com/questions/20069507/gatt-callback-fails-to-register
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun connect() {
        mainHandler.post {
            device.connectGatt(context, true, GattCallback())
        }
    }

    override fun disconnect() {
        mainHandler.post {
            gatt?.disconnect()
        }
    }

    override fun displayLedMatrix(matrix: NuimoLedMatrix) {
        if (matrixCharacteristic == null) { return }
        matrixCharacteristic?.setValue(matrix.gattBytes())
        mainHandler.post {
            gatt?.writeCharacteristic(matrixCharacteristic)
        }
    }

    private inner class GattCallback: BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            println("Connection state changed " + newState)
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    this@NuimoBluetoothController.gatt = gatt
                    mainHandler.post {
                        gatt.discoverServices()
                    }
                    listeners.forEach { it.onConnect() }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    this@NuimoBluetoothController.gatt = null
                    listeners.forEach { it.onDisconnect() }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            println("On services discovered: " + status + (if (status == BluetoothGatt.GATT_SUCCESS) " success" else " failed"))
            gatt.services?.forEach {
                println(device.name + "(" + device.address + "): " + it.uuid.toString());
                matrixCharacteristic = matrixCharacteristic ?: it.characteristics.find { it.uuid == LED_MATRIX_CHARACTERISTIC_UUID }
                if (matrixCharacteristic != null) {
                    listeners.forEach { it.onReady() }
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            println("onCharacteristicWrite " + characteristic.uuid + ": " + status)
            listeners.forEach { it.onLedMatrixWrite() }
        }
    }
}

private val BATTERY_SERVICE_UUID                   = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
private val BATTERY_CHARACTERISTIC_UUID            = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
private val DEVICE_INFORMATION_SERVICE_UUID        = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
private val DEVICE_INFORMATION_CHARACTERISTIC_UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
private val LED_MATRIX_SERVICE_UUID                = UUID.fromString("f29b1523-cb19-40f3-be5c-7241ecb82fd1")
private val LED_MATRIX_CHARACTERISTIC_UUID         = UUID.fromString("f29b1524-cb19-40f3-be5c-7241ecb82fd1")
private val SENSOR_SERVICE_UUID                    = UUID.fromString("f29b1525-cb19-40f3-be5c-7241ecb82fd2")
private val SENSOR_FLY_CHARACTERISTIC_UUID         = UUID.fromString("f29b1526-cb19-40f3-be5c-7241ecb82fd2")
private val SENSOR_TOUCH_CHARACTERISTIC_UUID       = UUID.fromString("f29b1527-cb19-40f3-be5c-7241ecb82fd2")
private val SENSOR_ROTATION_CHARACTERISTIC_UUID    = UUID.fromString("f29b1528-cb19-40f3-be5c-7241ecb82fd2")
private val SENSOR_BUTTON_CHARACTERISTIC_UUID      = UUID.fromString("f29b1529-cb19-40f3-be5c-7241ecb82fd2")

val NUIMO_SERVICE_UUIDS = arrayOf(
        BATTERY_SERVICE_UUID,
        DEVICE_INFORMATION_SERVICE_UUID,
        LED_MATRIX_SERVICE_UUID,
        SENSOR_SERVICE_UUID
)

private fun NuimoLedMatrix.gattBytes(): ByteArray {
    return arrayOf(255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255)
            .map { it.toByte() }
            .toByteArray()
}
