/*
 * Copyright (c) 2015 Senic. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package com.senic.nuimo

import android.bluetooth.BluetoothAdapter
import android.test.AndroidTestCase
import java.util.concurrent.Semaphore

/**
 * [Testing Fundamentals](http://d.android.com/tools/testing/testing_android.html)
 */
//TODO: Try spek test framework: http://jetbrains.github.io/spek/
//TODO: Add timeouts to each test
class NuimoTest: AndroidTestCase() {

    override fun setUp() {
        super.setUp()
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        assertTrue("Bluetooth not present or not enabled", bluetoothAdapter?.isEnabled ?: false)
    }

    fun testDiscoveryManagerShouldDiscoverOneBluetoothController() {
        discover { discovery, nuimoController, completed ->
            assertEquals(NuimoBluetoothController::class.java, nuimoController.javaClass)
            completed()
        }
    }

    fun testNuimoControllerShouldConnect() {
        connect { nuimoController, completed ->
            nuimoController.disconnect()
            completed()
        }
    }

    fun testNuimoControllerShouldDisconnect() {
        connect { nuimoController, completed ->
            nuimoController.addControllerListener(object: NuimoControllerListener() {
                override fun onDisconnect() {
                    completed()
                }
            })
            nuimoController.disconnect()
        }
    }

    fun testNuimoControllerShouldDiscoverGattServices() {
        connectServices { nuimoController, completed ->
            completed()
        }
    }

    /*
     * Private test helper methods
     */

    // Discovers a controller. Blocks until the "discovered" lambda calls the completed() method
    private fun discover(discovered: (discovery: NuimoDiscoveryManager, nuimoController: NuimoController, completed: () -> Unit) -> Unit) {
        val waitLock = Semaphore(0)
        val discovery = NuimoDiscoveryManager(context)
        discovery.addDiscoveryListener(object: NuimoDiscoveryListener {
            override fun onDiscoverNuimoController(nuimoController: NuimoController) {
                println("Bluetooth device found " + nuimoController.address)
                discovered(discovery, nuimoController, { waitLock.release() })
            }
        })
        discovery.startDiscovery()
        //TODO: Add timeout
        waitLock.acquire()
        discovery.stopDiscovery()
    }

    // Discovers and connects a controller and then stops discovery. Blocks until the "discovered" lambda calls the completed() method
    private fun connect(connected: (nuimoController: NuimoController, completed: () -> Unit) -> Unit) {
        //TODO: Add timeout
        discover { discovery, nuimoController, completed ->
            nuimoController.addControllerListener(object: NuimoControllerListener() {
                override fun onConnect() {
                    connected(nuimoController, completed)
                }
            })
            discovery.stopDiscovery()
            nuimoController.connect()
        }
    }

    // Discovers, connects, stops discovery and waits until all controller GATT services are found. Blocks until the "discovered" lambda calls the completed() method
    private fun connectServices(connected: (nuimoController: NuimoController, completed: () -> Unit) -> Unit) {
        //TODO: Add timeout
        connect { nuimoController, completed ->
            nuimoController.addControllerListener(object: NuimoControllerListener() {
                override fun onReady() {
                    connected(nuimoController, completed)
                }
            })
        }
    }
}
