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
    val supportsRebootToDfuMode: Boolean
        get() = rebootToDfuModeCharacteristic != null
    val supportsFlyGestureCalibration: Boolean
        get() = flyGestureCalibrationCharacteristic != null
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
    private var hardwareVersion: String? = null
    private var firmwareVersion: String? = null
    private var color: String? = null
    private val rebootToDfuModeCharacteristic: BluetoothGattCharacteristic?
        get() = gatt?.getService(SENSOR_SERVICE_UUID)?.getCharacteristic(REBOOT_TO_DFU_MODE_CHARACTERISTIC_UUID)
    private val flyGestureCalibrationCharacteristic: BluetoothGattCharacteristic?
        get() = gatt?.getService(SENSOR_SERVICE_UUID)?.getCharacteristic(FLY_GESTURE_CALIBRATION_CHARACTERISTIC_UUID)

    override fun connect() {
        if (connectionState != NuimoConnectionState.DISCONNECTED) { return }

        reset()

        connectionState = NuimoConnectionState.CONNECTING

        mainHandler.post {
            // If there is still a reference to gatt we should close it
            gatt?.close()
            gatt = device.connectGatt(context, false, GattCallback())
            // TODO: if there are problems and the following result is false we should call gatt.disconnect() and try the connection again. See http://stackoverflow.com/a/34544263/91226
            refreshGatt()
        }
    }

    override fun disconnect() {
        if (gatt == null) return

        if (connectionState != NuimoConnectionState.DISCONNECTED) {
            connectionState = NuimoConnectionState.DISCONNECTING
        }

        val gattToClose = gatt
        mainHandler.post {
            gattToClose?.disconnect()
        }

        reset()
    }

    fun rebootToDfuMode(): Boolean {
        val rebootToDfuModeCharacteristic = this.rebootToDfuModeCharacteristic ?: return false
        rebootToDfuModeCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        rebootToDfuModeCharacteristic.value = byteArrayOf(0x01)
        writeQueue.push { gatt?.writeCharacteristic(rebootToDfuModeCharacteristic) }
        return true
    }

    fun calibrateFlyGesture(): Boolean {
        val flyGestureCalibrationCharacteristic = this.flyGestureCalibrationCharacteristic ?: return false
        flyGestureCalibrationCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        flyGestureCalibrationCharacteristic.value = byteArrayOf(0x01)
        writeQueue.push { gatt?.writeCharacteristic(flyGestureCalibrationCharacteristic) }
        return true
    }

    private fun refreshGatt(): Boolean {
        try {
            val refreshed = (gatt?.javaClass?.getMethod("refresh")?.invoke(gatt)) ?: false
            if (refreshed is Boolean) {
                if (!refreshed) Log.e("NuimoController", "gatt.refresh() returned false")
                return refreshed
            }
        }
        catch (e: Exception) { Log.e("NuimoController", "Could not refresh gatt", e) }
        return false
    }

    private fun reset() {
        gatt = null
        gattConnected = false
        writeQueue.clear()
        matrixWriter = null
    }

    private fun readCharacteristic(serviceUUID: UUID, characteristicUUID: UUID) {
        val characteristic = gatt?.getService(serviceUUID)?.getCharacteristic(characteristicUUID) ?: return
        writeQueue.push { gatt?.readCharacteristic(characteristic) }
    }

    private fun discoverServices() {
        mainHandler.post { gatt?.discoverServices() }
        //TODO: Start timeout that disconnects if services are not discovered and descriptors are not written in time
    }

    override fun displayLedMatrix(matrix: NuimoLedMatrix, displayInterval: Double, options: Int) {
        matrixWriter?.write(matrix, displayInterval, options)
    }

    private inner class GattCallback: BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.i("Nuimo", "onConnectionStateChange $status, $newState")

            when {
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    gatt.close()

                    val previousConnectionState = connectionState
                    connectionState = NuimoConnectionState.DISCONNECTED
                    disconnect()

                    if (previousConnectionState == NuimoConnectionState.CONNECTING) {
                        notifyListeners { it.onFailToConnect() }
                    }
                    else if (previousConnectionState == NuimoConnectionState.DISCONNECTING ||
                            previousConnectionState == NuimoConnectionState.CONNECTED) {
                        //TODO: in case of CONNECTED we might need to pass an error code
                        notifyListeners { it.onDisconnect() }
                    }
                }
                status != BluetoothGatt.GATT_SUCCESS -> disconnect() //TODO: Pass error code to disconnect (status) that is forwarded to listeners
                newState == BluetoothProfile.STATE_CONNECTED -> discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            gatt.services?.flatMap { it.characteristics }?.forEach {
                if (LED_MATRIX_CHARACTERISTIC_UUID == it.uuid) {
                    matrixWriter = LedMatrixWriter(gatt, it, writeQueue)
                } else if (CHARACTERISTIC_NOTIFICATION_UUIDS.contains(it.uuid)) {
                    writeQueue.push { if (!gatt.setCharacteristicNotification2(it, true)) disconnect() }
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            when (characteristic.uuid) {
                LED_MATRIX_CHARACTERISTIC_UUID -> {
                    if (matrixWriter?.onWrite() ?: false) {
                        notifyListeners { it.onLedMatrixWrite() }
                    }
                }
                REBOOT_TO_DFU_MODE_CHARACTERISTIC_UUID -> {
                    disconnect()
                }
                FLY_GESTURE_CALIBRATION_CHARACTERISTIC_UUID -> {
                    writeQueue.next()
                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (!(matrixWriter?.onWrite() ?: false)) writeQueue.next()

            when (characteristic.uuid) {
                BATTERY_CHARACTERISTIC_UUID -> {
                    val batteryPercentage = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) ?: -1
                    notifyListeners { it.onBatteryPercentageChange(batteryPercentage) }
                }
                HARDWARE_REVISION_CHARACTERISTIC_UUID -> {
                    hardwareVersion = characteristic.getStringValue(0)
                    if (hardwareVersion?.length ?: 0 <= 2) hardwareVersion = null  // For old firmware we ignore the 19 string; It should be "MAJOR.MINOR"
                }
                FIRMWARE_VERSION_CHARACTERISTIC_UUID -> {
                    firmwareVersion = characteristic.getStringValue(0)
                }
                MODEL_NUMBER_CHARACTERISTIC_UUID -> {
                    color = characteristic.getStringValue(0)
                    if (color?.length ?: 0 <= 1) color = null // For old firmware we ignore the 0 character; It should be a string of at least 2 characters
                    notifyListeners { it.onInformationRead(hardwareVersion, firmwareVersion, color) }
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid.equals(BATTERY_CHARACTERISTIC_UUID)) {
                val batteryPercentage = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) ?: 0
                notifyListeners { it.onBatteryPercentageChange(batteryPercentage) }
            }
            else {
                val event = characteristic.toNuimoGestureEvent() ?: return
                notifyListeners { it.onGestureEvent(event) }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (!writeQueue.next() && !gattConnected) {
                // When the last characteristic descriptor has been written, then Nuimo is successfully connected
                gattConnected = true
                connectionState = NuimoConnectionState.CONNECTED
                readCharacteristic(BATTERY_SERVICE_UUID, BATTERY_CHARACTERISTIC_UUID)
                readCharacteristic(DEVICE_INFORMATION_SERVICE_UUID, HARDWARE_REVISION_CHARACTERISTIC_UUID)
                readCharacteristic(DEVICE_INFORMATION_SERVICE_UUID, FIRMWARE_VERSION_CHARACTERISTIC_UUID)
                readCharacteristic(DEVICE_INFORMATION_SERVICE_UUID, MODEL_NUMBER_CHARACTERISTIC_UUID)
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

    fun clear() {
        queue.clear()
        isIdle = true
    }

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
    private var currentMatrixWithOnionSkinningFadeIn = false
    private var lastWrittenMatrix: NuimoLedMatrix? = null
    private var lastWrittenMatrixTime = 0L
    private var lastWrittenMatrixDisplayInterval = 0.0
    private var lastWrittenMatrixWithoutResponseTime = 0L
    private var writeMatrixOnWriteResponseReceived = false
    private var pendingWriteCommandsWithoutResponseCount = 0
    private val writeResponseTimeout = 500L //ms
    private val writeResponseCheckInterval = 100L //ms
    private var writeResponseTimeoutTimer: Timer? = null

    fun write(matrix: NuimoLedMatrix, displayInterval: Double, options: Int) {
        val resendsSameMatrix       = options and NuimoController.OPTION_IGNORE_DUPLICATES           == 0
        val withOnionSkinningFadeIn = options and NuimoController.OPTION_WITH_ONION_SKINNING_FADE_IN != 0
        val writesWithResponse      = options and NuimoController.OPTION_WITHOUT_WRITE_RESPONSE      == 0

        if (!resendsSameMatrix &&
                matrix == lastWrittenMatrix &&
                (lastWrittenMatrixDisplayInterval > 0 &&
                System.currentTimeMillis() < (lastWrittenMatrixTime + lastWrittenMatrixDisplayInterval))) {
            return
        }

        currentMatrix                        = matrix
        currentMatrixDisplayIntervalSecs     = displayInterval
        currentMatrixWithOnionSkinningFadeIn = withOnionSkinningFadeIn

        when (writeQueue.isIdle || !writesWithResponse) {
            true  -> writeNow(writesWithResponse)
            false -> writeMatrixOnWriteResponseReceived = true
        }
    }

    private fun writeNow(withResponse: Boolean) {
        val gattBytes = (currentMatrix ?: NuimoLedMatrix("")).gattBytes() + byteArrayOf(255.toByte(), Math.min(Math.max(currentMatrixDisplayIntervalSecs * 10.0, 0.0), 255.0).toByte())
        gattBytes[10] = (gattBytes[10].toInt() or (if (currentMatrixWithOnionSkinningFadeIn) { 1 shl 4 } else { 0 })).toByte()

        val writeCommand: () -> Unit = {
            matrixCharacteristic.writeType = when (withResponse) {
                true  -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                false -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            matrixCharacteristic.value = gattBytes

            if (!withResponse) {
                pendingWriteCommandsWithoutResponseCount++
                // Sometimes android doesn't call onCharacteristicWrite() callback when you send a lot of write commands fast.
                lastWrittenMatrixWithoutResponseTime = System.currentTimeMillis()

                synchronized(this) {
                    if (writeResponseTimeoutTimer == null) {
                        writeResponseTimeoutTimer = Timer("MatrixWriteTimeoutTimer")
                        writeResponseTimeoutTimer?.schedule(WriteResponseTimeoutTimerTask(), writeResponseCheckInterval, writeResponseCheckInterval)
                    }
                }
            }
            gatt.writeCharacteristic(matrixCharacteristic)
        }

        when (withResponse) {
            true  -> writeQueue.push(writeCommand)
            false -> writeCommand()
        }

        lastWrittenMatrix = currentMatrix
        lastWrittenMatrixTime = System.currentTimeMillis()
        lastWrittenMatrixDisplayInterval = currentMatrixDisplayIntervalSecs * 1000
    }

    /**
     * Returns true when it was handling a matrix write response from a "write with response request", otherwise false
     */
    fun onWrite(): Boolean {
        if (pendingWriteCommandsWithoutResponseCount > 0) {
            pendingWriteCommandsWithoutResponseCount--
            return false
        }

        writeQueue.next()
        if (writeMatrixOnWriteResponseReceived) {
            writeMatrixOnWriteResponseReceived = false
            writeNow(true)
        }

        return true
    }

    private inner class WriteResponseTimeoutTimerTask: TimerTask() {
        override fun run() {
            if (System.currentTimeMillis() < lastWrittenMatrixWithoutResponseTime + writeResponseTimeout) return

            synchronized(this@LedMatrixWriter) {
                writeResponseTimeoutTimer?.cancel()
                writeResponseTimeoutTimer = null
            }

            pendingWriteCommandsWithoutResponseCount = 0

            onWrite()
        }
    }
}

/*
 * Nuimo BLE GATT service and characteristic UUIDs
 */

private val BATTERY_SERVICE_UUID                   = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
private val BATTERY_CHARACTERISTIC_UUID            = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
private val DEVICE_INFORMATION_SERVICE_UUID        = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
private val HARDWARE_REVISION_CHARACTERISTIC_UUID  = UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb")
private val FIRMWARE_VERSION_CHARACTERISTIC_UUID   = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
private val MODEL_NUMBER_CHARACTERISTIC_UUID       = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
private val DEVICE_INFORMATION_CHARACTERISTIC_UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
private val LED_MATRIX_SERVICE_UUID                = UUID.fromString("f29b1523-cb19-40f3-be5c-7241ecb82fd1")
private val LED_MATRIX_CHARACTERISTIC_UUID         = UUID.fromString("f29b1524-cb19-40f3-be5c-7241ecb82fd1")
private val SENSOR_SERVICE_UUID                    = UUID.fromString("f29b1525-cb19-40f3-be5c-7241ecb82fd2")
private val SENSOR_FLY_CHARACTERISTIC_UUID         = UUID.fromString("f29b1526-cb19-40f3-be5c-7241ecb82fd2")
private val SENSOR_TOUCH_CHARACTERISTIC_UUID       = UUID.fromString("f29b1527-cb19-40f3-be5c-7241ecb82fd2")
private val SENSOR_ROTATION_CHARACTERISTIC_UUID    = UUID.fromString("f29b1528-cb19-40f3-be5c-7241ecb82fd2")
private val SENSOR_BUTTON_CHARACTERISTIC_UUID      = UUID.fromString("f29b1529-cb19-40f3-be5c-7241ecb82fd2")
private val REBOOT_TO_DFU_MODE_CHARACTERISTIC_UUID = UUID.fromString("f29b152a-cb19-40f3-be5c-7241ecb82fd2")
private val FLY_GESTURE_CALIBRATION_CHARACTERISTIC_UUID = UUID.fromString("f29b152c-cb19-40f3-be5c-7241ecb82fd2")

val NUIMO_SERVICE_UUIDS = arrayOf(
        BATTERY_SERVICE_UUID,
        DEVICE_INFORMATION_SERVICE_UUID,
        LED_MATRIX_SERVICE_UUID,
        SENSOR_SERVICE_UUID
)

private val CHARACTERISTIC_NOTIFICATION_UUIDS = arrayOf(
        BATTERY_CHARACTERISTIC_UUID,
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
    val chunks = java.util.ArrayList<List<Boolean>>(size / n + 1)
    val chunk = ArrayList<Boolean>(n)
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
            val value = getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) ?: 0
            return NuimoGestureEvent(if (value == 1) NuimoGesture.BUTTON_PRESS else NuimoGesture.BUTTON_RELEASE, value)
        }
        SENSOR_ROTATION_CHARACTERISTIC_UUID -> {
            val value = getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0) ?: 0
            return NuimoGestureEvent(NuimoGesture.ROTATE, value)
        }
        SENSOR_TOUCH_CHARACTERISTIC_UUID -> {
            val gesture = hashMapOf(
                    0 to NuimoGesture.SWIPE_LEFT,
                    1 to NuimoGesture.SWIPE_RIGHT,
                    2 to NuimoGesture.SWIPE_UP,
                    3 to NuimoGesture.SWIPE_DOWN,
                    4 to NuimoGesture.TOUCH_LEFT,
                    5 to NuimoGesture.TOUCH_RIGHT,
                    6 to NuimoGesture.TOUCH_TOP,
                    7 to NuimoGesture.TOUCH_BOTTOM
                )[getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)]
            return if (gesture != null) NuimoGestureEvent(gesture, 0) else null
        }
        SENSOR_FLY_CHARACTERISTIC_UUID -> {
            if (value.size < 2) return null
            val gesture = hashMapOf(
                    0 to NuimoGesture.FLY_LEFT,
                    1 to NuimoGesture.FLY_RIGHT,
                    2 to NuimoGesture.FLY_BACKWARDS,
                    3 to NuimoGesture.FLY_TOWARDS,
                    4 to NuimoGesture.FLY_UP_DOWN
            )[getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)]
            val distance = getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1)
            return if (gesture != null) NuimoGestureEvent(gesture, if (gesture == NuimoGesture.FLY_UP_DOWN) { distance } else { 0 }) else null
        }
        else -> null
    }
}

private val CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

private fun BluetoothGatt.setCharacteristicNotification2(characteristic: BluetoothGattCharacteristic, enable: Boolean): Boolean {
    setCharacteristicNotification(characteristic, enable)
    // http://stackoverflow.com/questions/17910322/android-ble-api-gatt-notification-not-received
    val descriptor = characteristic.getDescriptor(CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID);
    if (descriptor == null) {
        Log.e("Nuimo", "Notification descriptor is null for characteristic " + characteristic.uuid + " for Nuimo " + device.address)
        return false
    }
    descriptor.value = if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
    //TODO: I observed cases where writeDescriptor wasn't followed up by a onDescriptorWrite notification -> We need a timeout here and error handling.
    return writeDescriptor(descriptor)
}
