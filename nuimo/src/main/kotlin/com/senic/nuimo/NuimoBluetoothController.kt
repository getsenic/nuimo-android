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
import android.util.Log
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class NuimoBluetoothController(bluetoothDevice: BluetoothDevice, context: Context): NuimoController(bluetoothDevice.address) {
    private val device = bluetoothDevice
    private val context = context
    private var gattConnected = false
    private var gatt: BluetoothGatt? = null
    // At least for some devices such as Samsung S3, S4, all BLE calls must occur from the main thread, see http://stackoverflow.com/questions/20069507/gatt-callback-fails-to-register
    //TODO: According to another SO answer, we just need another thread. So don't use main thread! See http://stackoverflow.com/questions/17870189/android-4-3-bluetooth-low-energy-unstable
    //TODO: Furthermore, the post says, it's only necessary for the connectGatt() method. Try it out!
    private val mainHandler = Handler(Looper.getMainLooper())
    private var writeQueue = WriteQueue()
    private var matrixWriter: LedMatrixWriter? = null

    override fun connect() {
        if (gatt != null) { return }

        reset()

        connectionState = NuimoConnectionState.CONNECTING

        mainHandler.post {
            //TODO: Figure out if and when to use autoConnect=true
            gatt = device.connectGatt(context, false, GattCallback())
        }
    }

    override fun disconnect() {
        if (gatt == null) return

        connectionState = NuimoConnectionState.DISCONNECTING

        val gattToClose = gatt
        mainHandler.post {
            gattToClose?.disconnect()
        }

        reset()
    }

    private fun reset() {
        gatt = null
        gattConnected = false
        writeQueue.clear()
        matrixWriter = null
    }

    private fun discoverServices() {
        mainHandler.post { gatt?.discoverServices() }
        //TODO: Start timeout that disconnects if services are not discovered and descriptors are not written in time
    }

    override fun displayLedMatrix(matrix: NuimoLedMatrix, displayInterval: Double) {
        matrixWriter?.write(matrix, displayInterval)
    }

    private inner class GattCallback: BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.i("Nuimo", "onConnectionStateChange $status, $newState")

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                gatt?.close()

                val previousConnectionState = connectionState
                connectionState = NuimoConnectionState.DISCONNECTED

                if (previousConnectionState == NuimoConnectionState.CONNECTING) {
                    notifyListeners { it.onFailToConnect() }
                }
                else if (previousConnectionState == NuimoConnectionState.DISCONNECTING ||
                        previousConnectionState == NuimoConnectionState.CONNECTED) {
                    notifyListeners { it.onDisconnect() }
                }
            }

            when {
                status   != BluetoothGatt.GATT_SUCCESS          -> disconnect() //TODO: Pass error code to disconnect (status) that is forwarded to listeners
                newState == BluetoothProfile.STATE_CONNECTED    -> discoverServices()
                newState == BluetoothProfile.STATE_DISCONNECTED -> disconnect()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            gatt.services?.flatMap { it.characteristics }?.forEach {
                if (LED_MATRIX_CHARACTERISTIC_UUID == it.uuid) {
                    matrixWriter = LedMatrixWriter(gatt, it, writeQueue)
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
            val event = characteristic.toNuimoGestureEvent() ?: return
            notifyListeners { it.onGestureEvent(event) }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (!writeQueue.next() && !gattConnected) {
                // When the last characteristic descriptor has been written, then Nuimo is successfully connected
                gattConnected = true
                connectionState = NuimoConnectionState.CONNECTED
                notifyListeners { it.onConnect() }
            }
        }
    }
}

/**
 * Connection states for the Nuimo controller
 */
enum class NuimoConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING
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
private class LedMatrixWriter(gatt: BluetoothGatt, matrixCharacteristic: BluetoothGattCharacteristic, writeQueue: WriteQueue) {
    private var gatt = gatt
    private var matrixCharacteristic = matrixCharacteristic
    private var writeQueue = writeQueue
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
        val gattBytes = (currentMatrix ?: NuimoLedMatrix("")).gattBytes() + byteArrayOf(255.toByte(), Math.min(Math.max(currentMatrixDisplayIntervalSecs * 10.0, 0.0), 255.0).toByte())
        writeQueue.push {
            matrixCharacteristic.value = gattBytes
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
        SENSOR_FLY_CHARACTERISTIC_UUID,
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

private fun BluetoothGattCharacteristic.toNuimoGestureEvent(): NuimoGestureEvent? {
    return when (uuid) {
        SENSOR_BUTTON_CHARACTERISTIC_UUID -> {
            var value = getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) ?: 0
            return NuimoGestureEvent(if (value == 1) NuimoGesture.BUTTON_PRESS else NuimoGesture.BUTTON_RELEASE, value)
        }
        SENSOR_ROTATION_CHARACTERISTIC_UUID -> {
            val value = getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0) ?: 0
            return NuimoGestureEvent(if (value >= 0) NuimoGesture.ROTATE_RIGHT else NuimoGesture.ROTATE_LEFT, Math.abs(value))
        }
        SENSOR_TOUCH_CHARACTERISTIC_UUID -> {
            if (value.size == 1) {
                val gesture = hashMapOf(
                        0 to NuimoGesture.SWIPE_LEFT,
                        1 to NuimoGesture.SWIPE_RIGHT,
                        2 to NuimoGesture.SWIPE_UP,
                        3 to NuimoGesture.SWIPE_DOWN
                    )[getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)]
                return if (gesture != null) NuimoGestureEvent(gesture, 0) else null
            }
            else {
                //TODO: Remove legacy code when we have no Nuimos with old firmware any more
                val button = getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0) ?: 0
                val event = getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 2) ?: 0
                for (i in 0..7) {
                    if (1.shl(i).and(button) == 0) { continue }
                    val touchDownGesture = GATT_TOUCH_DOWN_GESTURES[i / 2]
                    val eventGesture =
                            when (event) {
                                1 -> touchDownGesture
                                2 -> touchDownGesture.touchReleaseGesture()
                                3 -> null //TODO: Do we need to handle double touch gestures here as well?
                                4 -> touchDownGesture.swipeGesture()
                                else -> null
                            }
                    if (eventGesture != null) {
                        return NuimoGestureEvent(eventGesture, i)
                    }
                }
                return null
            }
        }
        SENSOR_FLY_CHARACTERISTIC_UUID -> {
            if (value.size < 2) return null
            val gesture = hashMapOf(
                    0 to NuimoGesture.FLY_LEFT,
                    1 to NuimoGesture.FLY_RIGHT,
                    2 to NuimoGesture.FLY_BACKWARDS,
                    3 to NuimoGesture.FLY_TOWARDS
            )[getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)]
            val speed = getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1)
            return if (gesture != null) NuimoGestureEvent(gesture, speed) else null
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
    descriptor.value = if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
    //TODO: I observed cases where writeDescriptor wasn't followed up by a onDescriptorWrite notification -> We need a timeout here and error handling.
    return writeDescriptor(descriptor);
}
