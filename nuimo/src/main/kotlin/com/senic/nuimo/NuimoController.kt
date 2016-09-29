/*
 * Copyright (c) 2015 Senic. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package com.senic.nuimo

import java.util.*

abstract class NuimoController(address: String) {
    companion object {
        @JvmField
        val OPTION_IGNORE_DUPLICATES           = 1 shl 0
        @JvmField
        val OPTION_WITH_ONION_SKINNING_FADE_IN = 1 shl 1
        @JvmField
        val OPTION_WITHOUT_WRITE_RESPONSE      = 1 shl 2
        /**
         * BluetoothDevice.connectGatt() returns null
         */
        @JvmField
        val REASON_GATT_ERROR                  = 1
        @JvmField
        val REASON_CONNECTION_NOT_ESTABLISHED  = 2
    }

    val address: String = address
    var defaultMatrixDisplayInterval = 2.0

    var connectionState = NuimoConnectionState.DISCONNECTED
        protected set
    var batteryPercentage: Int? = null
        protected set
    var firmwareVersion: String? = null
        protected set
    var hardwareVersion: String? = null
        protected set
    var color: String? = null
        protected set

    private val listeners = ArrayList<NuimoControllerListener>()

    abstract fun connect()

    abstract fun disconnect()

    fun displayLedMatrix(matrix: NuimoLedMatrix) {
        displayLedMatrix(matrix, defaultMatrixDisplayInterval, 0)
    }

    fun displayLedMatrix(matrix: NuimoLedMatrix, displayInterval: Double) {
        displayLedMatrix(matrix, displayInterval, 0)
    }

    fun displayLedMatrix(matrix: NuimoLedMatrix, options: Int) {
        displayLedMatrix(matrix, defaultMatrixDisplayInterval, options)
    }

    abstract fun displayLedMatrix(matrix: NuimoLedMatrix, displayInterval: Double, options: Int)

    fun addControllerListener(controllerListener: NuimoControllerListener) {
        listeners.add(controllerListener)
    }

    fun removeControllerListener(controllerListener: NuimoControllerListener) {
        listeners.remove(controllerListener)
    }

    /**
     * Calls "event" lambda on every listener. It does so by copying the list of listeners before to
     * avoid ConcurrentModificationExceptions that occur if the actual listener tries to modify the
     * listeners.
     */
    protected fun notifyListeners(event: (listener: NuimoControllerListener) -> Unit) {
        ArrayList(listeners).forEach { event(it) }
    }
}

interface NuimoControllerListener {
    fun onConnect() {}
    /**
     * @see {@link NuimoController.REASON_GATT_ERROR}
     * @see {@link NuimoController.REASON_CONNECTION_NOT_ESTABLISHED}
     */
    fun onFailToConnect(reason: Int) {}
    fun onDisconnect() {}
    fun onLedMatrixWrite() {}
    fun onGestureEvent(event: NuimoGestureEvent) {}
    fun onBatteryPercentageChange(batteryPercentage: Int) {}
}

abstract class BaseNuimoControllerListener: NuimoControllerListener {
    override fun onConnect() {}
    override fun onDisconnect() {}
    override fun onLedMatrixWrite() {}
    override fun onGestureEvent(event: NuimoGestureEvent) {}
    override fun onBatteryPercentageChange(batteryPercentage: Int) {}
}
