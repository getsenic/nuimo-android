/*
 * Copyright (c) 2015 Senic. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package com.senic.nuimo

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.content.Context
import java.util.UUID

public class NuimoBluetoothController(bluetoothDevice: BluetoothDevice, context: Context): NuimoController {
    public override val address = bluetoothDevice.address

    private val bluetoothDevice = bluetoothDevice
    private val context = context

    override fun connect() {
        bluetoothDevice.connectGatt(context, true, GattCallback())
    }

    private inner class GattCallback: BluetoothGattCallback() {
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            println("On services discovered: " + status + (if (status == BluetoothGatt.GATT_SUCCESS) " success" else " failed"))
            gatt?.services?.forEach { println(it.uuid.toString()) }
        }
    }
}

private val BATTERY_SERVICE_UUID                   = UUID.fromString("180F")
private val BATTERY_CHARACTERISTIC_UUID            = UUID.fromString("2A19")
private val DEVICE_INFORMATION_SERVICE_UUID        = UUID.fromString("180A")
private val DEVICE_INFORMATION_CHARACTERISTIC_UUID = UUID.fromString("2A29")
private val LED_MATRIX_SERVICE_UUID                = UUID.fromString("F29B1523-CB19-40F3-BE5C-7241ECB82FD1")
private val LED_MATRIX_CHARACTERISTI_CUUID         = UUID.fromString("F29B1524-CB19-40F3-BE5C-7241ECB82FD1")
private val SENSOR_SERVICE_UUID                    = UUID.fromString("F29B1525-CB19-40F3-BE5C-7241ECB82FD2")
private val SENSOR_FLY_CHARACTERISTIC_UUID         = UUID.fromString("F29B1526-CB19-40F3-BE5C-7241ECB82FD2")
private val SENSOR_TOUCH_CHARACTERISTIC_UUID       = UUID.fromString("F29B1527-CB19-40F3-BE5C-7241ECB82FD2")
private val SENSOR_ROTATION_CHARACTERISTIC_UUID    = UUID.fromString("F29B1528-CB19-40F3-BE5C-7241ECB82FD2")
private val SENSOR_BUTTON_CHARACTERISTIC_UUID      = UUID.fromString("F29B1529-CB19-40F3-BE5C-7241ECB82FD2")

val NUIMO_SERVICE_UUIDS = arrayOf(
        BATTERY_SERVICE_UUID,
        DEVICE_INFORMATION_SERVICE_UUID,
        LED_MATRIX_SERVICE_UUID,
        SENSOR_SERVICE_UUID
)
