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
import java.util.*

//TODO: This class is not yet thread-safe. writeQueue, listeners, matrixCharacteristic are being accessed from different threads.
public class NuimoBluetoothController(bluetoothDevice: BluetoothDevice, context: Context): NuimoController(bluetoothDevice.address) {
    //TODO: Make this val and retrieve from the device itself
    var firmwareVersion = 0.1

    private val device = bluetoothDevice
    private val context = context
    private var gatt: BluetoothGatt? = null
    private var matrixCharacteristic: BluetoothGattCharacteristic? = null
    // At least for some devices such as Samsung S3, S4, all BLE calls must occur from the main thread, see http://stackoverflow.com/questions/20069507/gatt-callback-fails-to-register
    private val mainHandler = Handler(Looper.getMainLooper())
    private var writeQueue = WriteQueue()

    override fun connect() {
        mainHandler.post {
            //TODO: Figure out if and when to use autoConnect=true
            gatt = device.connectGatt(context, false, GattCallback())
        }
    }

    override fun disconnect() {
        mainHandler.post {
            gatt?.disconnect()
            //TODO: What if onConnectionStateChange with STATE_DISCONNECTED is not called? We definitely need to call gatt.close() at some point!
        }
    }

    override fun displayLedMatrix(matrix: NuimoLedMatrix, displayInterval: Double) {
        if (gatt == null || matrixCharacteristic == null) { return }
        //TODO: Avoid write requests queuing up. Have only one write request queued and, if write request is not already running, only update the characteristic value
        writeQueue.push {
            var gattBytes = matrix.gattBytes()
            //TODO: Remove test for firmware version when we use latest version on every Nuimo
            if (firmwareVersion >= 0.1) {
                gattBytes += byteArrayOf(255.toByte(), Math.min(Math.max(displayInterval * 10.0, 0.0), 255.0).toByte())
            }
            //TODO: Synchronize access to matrixCharacteristic, writeQueue executes lambda on different thread
            matrixCharacteristic?.setValue(gattBytes)
            gatt?.writeCharacteristic(matrixCharacteristic)
        }
    }

    private inner class GattCallback: BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            writeQueue.clear()

            println("Connection state changed " + newState)
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    mainHandler.post {
                        gatt.discoverServices()
                    }
                    listeners.forEach { it.onConnect() }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    gatt.close()
                    this@NuimoBluetoothController.gatt = null
                    listeners.forEach { it.onDisconnect() }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            gatt.services?.flatMap { it.characteristics }?.forEach {
                if (LED_MATRIX_CHARACTERISTIC_UUID == it.uuid) {
                    matrixCharacteristic = it
                } else if (CHARACTERISTIC_NOTIFICATION_UUIDS.contains(it.uuid)) {
                    writeQueue.push { gatt.setCharacteristicNotification2(it, true) }
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            writeQueue.next()
            listeners.forEach { it.onLedMatrixWrite() }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            when (characteristic.uuid) {
                else -> {
                    val event = characteristic.toNuimoGestureEvent(firmwareVersion)
                    if (event != null) {
                        listeners.forEach { it.onGestureEvent(event) }
                    }
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            if (!writeQueue.next()) {
                listeners.forEach { it.onReady() }
            }
        }
    }

    private inner class WriteQueue {
        private var queue = LinkedList<() -> Unit>()
        private var idle = true

        //TODO: Synchronize access
        fun push(request: () -> Unit) {
            when (idle) {
                true  -> { idle = false; performWriteRequest(request) }
                false -> queue.addLast(request)
            }
        }

        fun next(): Boolean {
            when (queue.size) {
                0    -> idle = true
                else -> performWriteRequest(queue.removeFirst())
            }
            return !idle
        }

        fun clear() = queue.clear()

        private fun performWriteRequest(request: () -> Unit) {
            mainHandler.post { request() }
        }
    }
}

/*
 * Nuimo BLE GATT service and characteristic UUIDs
 */

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

private val CHARACTERISTIC_NOTIFICATION_UUIDS = arrayOf(
        //BATTERY_CHARACTERISTIC_UUID,
        //SENSOR_FLY_CHARACTERISTIC_UUID,
        SENSOR_TOUCH_CHARACTERISTIC_UUID,
        SENSOR_ROTATION_CHARACTERISTIC_UUID,
        SENSOR_BUTTON_CHARACTERISTIC_UUID
)

/*
 * Private extensions
 */

//TODO: Should be only visible in this module but then it's not seen by the test
fun NuimoLedMatrix.gattBytes(): ByteArray {
    return bits
            .chunk(8)
            .map { it
                    .mapIndexed { i, b -> if (b) { 1 shl i } else { 0 } }
                    .reduce { n, i -> n + i }
            }
            .map { it.toByte() }
            .toByteArray()
}

//TODO: Convert into generic function
private fun List<Boolean>.chunk(n: Int): List<List<Boolean>> {
    var chunks = java.util.ArrayList<List<Boolean>>(size / n + 1)
    var chunk = ArrayList<Boolean>(n)
    var i = n
    forEach {
        chunk.add(it)
        if (--i == 0) {
            chunks.add(ArrayList<Boolean>(chunk))
            chunk.clear()
            i = n
        }
    }
    if (chunk.isNotEmpty()) { chunks.add(chunk) }
    return chunks
}

private fun BluetoothGattCharacteristic.toNuimoGestureEvent(firmwareVersion: Double): NuimoGestureEvent? {
    return when (uuid) {
        SENSOR_BUTTON_CHARACTERISTIC_UUID -> {
            var value = getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) ?: 0
            if (firmwareVersion < 0.1) { value = 1 - value /* Press and release swapped */ }
            return NuimoGestureEvent(if (value == 1) NuimoGesture.BUTTON_PRESS else NuimoGesture.BUTTON_RELEASE, value)
        }
        SENSOR_ROTATION_CHARACTERISTIC_UUID -> {
            val value = getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0) ?: 0
            return NuimoGestureEvent(if (value >= 0) NuimoGesture.ROTATE_RIGHT else NuimoGesture.ROTATE_LEFT, Math.abs(value))
        }
        SENSOR_TOUCH_CHARACTERISTIC_UUID -> {
            val button = getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0) ?: 0
            val event = getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 2) ?: 0
            for (i in 0..7) {
                if (1.shl(i).and(button) == 0) { continue }
                val touchDownGesture = GATT_TOUCH_DOWN_GESTURES[i / 2]
                val eventGesture =
                    when (event) {
                        1    -> touchDownGesture
                        2    -> touchDownGesture.touchReleaseGesture()
                        3    -> null //TODO: Do we need to handle double touch gestures here as well?
                        4    -> touchDownGesture.swipeGesture()
                        else -> null
                    }
                if (eventGesture != null) {
                    return NuimoGestureEvent(eventGesture, i)
                }
            }
            return null
        }
        else -> null
    }
}

private val GATT_TOUCH_DOWN_GESTURES = arrayOf(NuimoGesture.TOUCH_LEFT_DOWN, NuimoGesture.TOUCH_TOP_DOWN, NuimoGesture.TOUCH_RIGHT_DOWN, NuimoGesture.TOUCH_BOTTOM_DOWN)

fun NuimoGesture.touchReleaseGesture(): NuimoGesture? {
    return when(this) {
        NuimoGesture.TOUCH_LEFT_DOWN      -> NuimoGesture.TOUCH_LEFT_RELEASE
        NuimoGesture.TOUCH_LEFT_RELEASE   -> NuimoGesture.TOUCH_LEFT_RELEASE
        NuimoGesture.TOUCH_RIGHT_DOWN     -> NuimoGesture.TOUCH_RIGHT_RELEASE
        NuimoGesture.TOUCH_RIGHT_RELEASE  -> NuimoGesture.TOUCH_RIGHT_RELEASE
        NuimoGesture.TOUCH_TOP_DOWN       -> NuimoGesture.TOUCH_TOP_RELEASE
        NuimoGesture.TOUCH_TOP_RELEASE    -> NuimoGesture.TOUCH_TOP_RELEASE
        NuimoGesture.TOUCH_BOTTOM_DOWN    -> NuimoGesture.TOUCH_BOTTOM_RELEASE
        NuimoGesture.TOUCH_BOTTOM_RELEASE -> NuimoGesture.TOUCH_BOTTOM_RELEASE
        else                              -> null
    }
}

fun NuimoGesture.swipeGesture(): NuimoGesture? {
    return when(this) {
        NuimoGesture.TOUCH_LEFT_DOWN      -> NuimoGesture.SWIPE_LEFT
        NuimoGesture.TOUCH_LEFT_RELEASE   -> NuimoGesture.SWIPE_LEFT
        NuimoGesture.TOUCH_RIGHT_DOWN     -> NuimoGesture.SWIPE_RIGHT
        NuimoGesture.TOUCH_RIGHT_RELEASE  -> NuimoGesture.SWIPE_RIGHT
        NuimoGesture.TOUCH_TOP_DOWN       -> NuimoGesture.SWIPE_UP
        NuimoGesture.TOUCH_TOP_RELEASE    -> NuimoGesture.SWIPE_UP
        NuimoGesture.TOUCH_BOTTOM_DOWN    -> NuimoGesture.SWIPE_DOWN
        NuimoGesture.TOUCH_BOTTOM_RELEASE -> NuimoGesture.SWIPE_DOWN
        else                              -> null
    }
}

private val CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

private fun BluetoothGatt.setCharacteristicNotification2(characteristic: BluetoothGattCharacteristic, enable: Boolean): Boolean {
    setCharacteristicNotification(characteristic, enable)
    // http://stackoverflow.com/questions/17910322/android-ble-api-gatt-notification-not-received
    val descriptor = characteristic.getDescriptor(CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID);
    descriptor.setValue(if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
    return writeDescriptor(descriptor);
}
