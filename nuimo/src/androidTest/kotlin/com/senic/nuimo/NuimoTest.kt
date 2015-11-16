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
//TODO: Add timeouts to each test
class NuimoTest: AndroidTestCase() {

    fun testDiscoveryManagerShouldDiscoverOneBluetoothController() {
        val waitLock = Semaphore(0)
        val discovery = NuimoDiscoveryManager(context)
        discover(discovery) { nuimoController ->
            assertEquals(NuimoBluetoothController::class.java, nuimoController.javaClass)
            waitLock.release()
        }
        waitLock.acquire()
        discovery.stopDiscovery()
    }

    fun testNuimoControllerShouldConnect() {
        val waitLock = Semaphore(0)
        val discovery = NuimoDiscoveryManager(context)
        discover(discovery) { nuimoController ->
            nuimoController.addControllerListener(object: NuimoControllerListener() {
                override fun onConnect() {
                    nuimoController.disconnect()
                    waitLock.release()
                }
            })
            discovery.stopDiscovery()
            nuimoController.connect()
        }
        waitLock.acquire()
        discovery.stopDiscovery()
    }

    fun testNuimoControllerShouldDisconnect() {
        val waitLock = Semaphore(0)
        val discovery = NuimoDiscoveryManager(context)
        discover(discovery) { nuimoController ->
            nuimoController.addControllerListener(object: NuimoControllerListener() {
                override fun onConnect() {
                    nuimoController.disconnect()
                }
                override fun onDisconnect() {
                    waitLock.release()
                }
            })
            discovery.stopDiscovery()
            nuimoController.connect()
        }
        waitLock.acquire()
        discovery.stopDiscovery()
    }

    fun testNuimoControllerShouldDiscoverLedMatrixService() {
        val waitLock = Semaphore(0)
        val discovery = NuimoDiscoveryManager(context)
        discover(discovery) { nuimoController ->
            nuimoController.addControllerListener(object: NuimoControllerListener() {
                override fun onLedMatrixFound() {
                    waitLock.release()
                }
            })
            discovery.stopDiscovery()
            nuimoController.connect()
        }
        waitLock.acquire()
        discovery.stopDiscovery()
    }

    fun discover(discovery: NuimoDiscoveryManager, discovered: (nuimoController: NuimoController) -> Unit) {
        discovery.addDiscoveryListener(object: NuimoDiscoveryListener {
            override fun onDiscoverNuimoController(nuimoController: NuimoController) {
                println("Bluetooth device found " + nuimoController.address)
                discovered(nuimoController)
            }
        })
        discovery.startDiscovery()
    }
}
