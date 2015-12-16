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
import java.util.concurrent.ConcurrentLinkedQueue

//TODO: This class is not yet thread-safe. writeQueue, listeners, matrixCharacteristic are being accessed from different threads.
class NuimoBluetoothController(bluetoothDevice: BluetoothDevice, context: Context): NuimoController(bluetoothDevice.address) {
    //TODO: Make this val and retrieve from the device itself
    var firmwareVersion = 0.1

    private val device = bluetoothDevice
    private val context = context
    private var gatt: BluetoothGatt? = null
    // At least for some devices such as Samsung S3, S4, all BLE calls must occur from the main thread, see http://stackoverflow.com/questions/20069507/gatt-callback-fails-to-register
    private val mainHandler = Handler(Looper.getMainLooper())
    private var writeQueue = WriteQueue()
    private var matrixWriter: LedMatrixWriter? = null

    override fun connect() {
        if (gatt != null) { return }

        mainHandler.post {
            //TODO: Figure out if and when to use autoConnect=true
            gatt = device.connectGatt(context, false, GattCallback())
        }
    }

    override fun disconnect() {
        if (gatt == null) { return }

        val gattToClose = gatt
        gatt = null
        matrixWriter = null

        mainHandler.post {
            gattToClose.disconnect()
            gattToClose.close()
            notifyListeners { it.onDisconnect() }
        }
    }

    override fun displayLedMatrix(matrix: NuimoLedMatrix, displayInterval: Double) {
        matrixWriter?.write(matrix, displayInterval)
    }

    private inner class GattCallback: BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            writeQueue.clear()
            matrixWriter = null

            println("Connection state changed " + newState)
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    mainHandler.post {
                        gatt.discoverServices()
                    }
                    //TODO: onDescriptorWrite() will fire onConnect(). In case it doesn't happen we need a timeout here that then calls onConnectFailure().
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    disconnect()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            gatt.services?.flatMap { it.characteristics }?.forEach {
                if (LED_MATRIX_CHARACTERISTIC_UUID == it.uuid) {
                    matrixWriter = LedMatrixWriter(gatt, it, writeQueue, firmwareVersion)
                } else if (CHARACTERISTIC_NOTIFICATION_UUIDS.contains(it.uuid)) {
                    writeQueue.push { gatt.setCharacteristicNotification2(it, true) }
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            when (characteristic.uuid) {
                LED_MATRIX_CHARACTERISTIC_UUID -> {
                    matrixWriter?.onWrite()
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        notifyListeners { it.onLedMatrixWrite() }
                    }
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            when (characteristic.uuid) {
                else -> {
                    val event = characteristic.toNuimoGestureEvent(firmwareVersion)
                    if (event != null) {
                        notifyListeners { it.onGestureEvent(event) }
                    }
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            if (!writeQueue.next()) {
                // When the last characteristic descriptor has been written, then Nuimo is successfully connected
                notifyListeners { it.onConnect() }
            }
        }
    }
}

/**
 * All write requests to Bluetooth GATT must be happen serialized. This said, write requests must not be invoked before the response of the previous write request is received.
 */
private class WriteQueue {
    var isIdle = true
        private set
    private var queue = ConcurrentLinkedQueue<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun push(request: () -> Unit) {
        when (isIdle) {
            true  -> performWriteRequest(request)
            false -> queue.add(request)
        }
        isIdle = false
    }

    fun next(): Boolean {
        val request = queue.poll()
        when (request) {
            null -> isIdle = true
            else -> performWriteRequest(request)
        }
        return !isIdle
    }

    fun clear() = queue.clear()

    private fun performWriteRequest(request: () -> Unit) {
        mainHandler.post { request() }
    }
}

/**
 * Send LED matrices to the controller. When the writer receives write commands faster than the controller can actually handle
 * (thus write commands come in before write responses are received), it will send only the matrix of the very last write command.
 */
private class LedMatrixWriter(gatt: BluetoothGatt, matrixCharacteristic: BluetoothGattCharacteristic, writeQueue: WriteQueue, firmwareVersion: Double) {
    private var gatt = gatt
    private var matrixCharacteristic = matrixCharacteristic
    private var writeQueue = writeQueue
    private var firmwareVersion = firmwareVersion
    private var currentMatrix: NuimoLedMatrix? = null
    private var currentMatrixDisplayIntervalSecs = 0.0
    private var writeMatrixOnWriteResponseReceived = false

    fun write(matrix: NuimoLedMatrix, displayInterval: Double) {
        currentMatrix = matrix
        currentMatrixDisplayIntervalSecs = displayInterval

        when (writeQueue.isIdle) {
            true  -> writeNow()
            false -> writeMatrixOnWriteResponseReceived = true
        }
    }

    private fun writeNow() {
        var gattBytes = (currentMatrix ?: NuimoLedMatrix("")).gattBytes()
        //TODO: Remove test for firmware version when we use latest version on every Nuimo
        if (firmwareVersion >= 0.1) {
            gattBytes += byteArrayOf(255.toByte(), Math.min(Math.max(currentMatrixDisplayIntervalSecs * 10.0, 0.0), 255.0).toByte())
        }
        //TODO: Synchronize access to matrixCharacteristic, writeQueue executes lambda on different thread
        writeQueue.push {
            matrixCharacteristic.setValue(gattBytes)
            gatt.writeCharacteristic(matrixCharacteristic)
        }
    }

    fun onWrite() {
        writeQueue.next()
        if (writeMatrixOnWriteResponseReceived) {
            writeMatrixOnWriteResponseReceived = false
            writeNow()
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
    //TODO: I observed cases where writeDescripter wasn't followed up by a onDescripterWrite notification -> We need a timeout here and error handling.
    return writeDescriptor(descriptor);
}
