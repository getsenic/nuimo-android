/*
 * Copyright (c) 2015 Senic. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package com.senic.nuimo

import android.test.AndroidTestCase
import java.util.concurrent.Semaphore

/**
 * [Testing Fundamentals](http://d.android.com/tools/testing/testing_android.html)
 */
//TODO: Try spek test framework: http://jetbrains.github.io/spek/
class NuimoTest: AndroidTestCase() {

    fun testDiscoveryManagerShouldDiscoverOneBluetoothController() {
        val waitLock = Semaphore(0)
        val discovery = NuimoDiscoveryManager(context)
        discovery.addDiscoveryListener(object : NuimoDiscoveryListener {
            override fun onDiscoverNuimoController(nuimoController: NuimoController) {
                println("Bluetooth device found " + nuimoController.address)
                assertEquals(NuimoBluetoothController::class.java, nuimoController.javaClass)
                waitLock.release()
            }
        })
        discovery.startDiscovery()
        waitLock.acquire()
        discovery.stopDiscovery()
    }

    fun testNuimoControllerShouldConnect() {
        val waitLock = Semaphore(0)
        val discovery = NuimoDiscoveryManager(context)
        discovery.addDiscoveryListener(object : NuimoDiscoveryListener {
            override fun onDiscoverNuimoController(nuimoController: NuimoController) {
                println("Bluetooth device found " + nuimoController.address)
                nuimoController.addControllerListener(object : NuimoControllerListener {
                    override fun onConnect() {
                        waitLock.release()
                    }
                })
                discovery.stopDiscovery()
                nuimoController.connect()
            }
        })
        discovery.startDiscovery()
        waitLock.acquire()
        discovery.stopDiscovery()
    }
}
