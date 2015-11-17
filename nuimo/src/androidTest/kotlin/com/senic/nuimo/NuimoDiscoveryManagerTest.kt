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

//TODO: Try spek test framework: http://jetbrains.github.io/spek/
//TODO: Add timeouts to each test
open class NuimoDiscoveryManagerTest: AndroidTestCase() {
    override fun setUp() {
        super.setUp()
        assertTrue("Bluetooth not present or not enabled", BluetoothAdapter.getDefaultAdapter()?.isEnabled ?: false)
    }

    fun testDiscoveryManagerShouldDiscoverOneBluetoothController() {
        discover { discovery, nuimoController, completed ->
            assertEquals(NuimoBluetoothController::class.java, nuimoController.javaClass)
            completed()
        }
    }

    /*
     * Private test helper methods
     */

    // Discovers a controller. Blocks until the "discovered" lambda calls the completed() method
    protected fun discover(discovered: (discovery: NuimoDiscoveryManager, nuimoController: NuimoController, completed: () -> Unit) -> Unit) {
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
}
