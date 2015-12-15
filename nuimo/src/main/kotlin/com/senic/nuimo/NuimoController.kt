/*
 * Copyright (c) 2015 Senic. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package com.senic.nuimo

import java.util.*

abstract class NuimoController(address: String) {
    val address: String = address
    var defaultMatrixDisplayInterval = 2.0

    private val listeners = ArrayList<NuimoControllerListener>()

    abstract fun connect()

    abstract fun disconnect()

    abstract fun displayLedMatrix(matrix: NuimoLedMatrix, displayInterval: Double = defaultMatrixDisplayInterval)

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
    fun onDisconnect() {}
    fun onLedMatrixWrite() {}
    fun onGestureEvent(event: NuimoGestureEvent) {}
}

abstract class BaseNuimoControllerListener: NuimoControllerListener {
    override fun onConnect() {}
    override fun onDisconnect() {}
    override fun onLedMatrixWrite() {}
    override fun onGestureEvent(event: NuimoGestureEvent) {}
}
