/*
 * Copyright (c) 2015 Senic. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package com.senic.nuimo

import android.test.AndroidTestCase
import java.util.concurrent.Semaphore

//TODO: Try spek test framework: http://jetbrains.github.io/spek/
//TODO: Add timeouts to each test
open class NuimoDiscoveryManagerTest: AndroidTestCase() {
    val discovery: NuimoDiscoveryManager by lazy { NuimoDiscoveryManager(context) }

    override fun setUp() {
        super.setUp()
        assertTrue("Bluetooth must be present and enabled", discovery.checkBluetoothEnabled())
        assertTrue("Permissions must be granted", discovery.checkPermissions(null))
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
        discovery.addDiscoveryListener(object: NuimoDiscoveryListener {
            override fun onDiscoverNuimoController(nuimoController: NuimoController) {
                println("Bluetooth device found " + nuimoController.address)
                //if (nuimoController.address != "CC:8A:20:D9:E7:3F") { return } //Klapperkiste
                if (nuimoController.address != "F1:22:76:AC:53:58") { return } // Core_04_01_B_DEV
                discovered(discovery, nuimoController, { waitLock.release() })
            }
        })
        assertTrue("Device discovery must start", discovery.startDiscovery())
        //TODO: Add timeout
        waitLock.acquire()
        discovery.stopDiscovery()
    }
}
