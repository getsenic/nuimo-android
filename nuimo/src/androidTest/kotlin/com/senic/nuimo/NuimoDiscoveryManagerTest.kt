/*
 * Copyright (c) 2015 Senic. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package com.senic.nuimo

import android.test.AndroidTestCase
import java.util.*
import java.util.concurrent.Semaphore
import kotlin.concurrent.schedule

//TODO: Try spek test framework: http://jetbrains.github.io/spek/
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

    fun testDiscoveryManagerShouldSendOnlyOneDiscoveryEventForTheSameDevice() {
        var receivedDuplicateDiscoveryEvent = false
        var discoveredDevices = HashSet<String>()
        discover { discoveryManager, nuimoController, completed ->
            if (discoveredDevices.contains(nuimoController.address)) {
                receivedDuplicateDiscoveryEvent = true
                completed()
            }
            else {
                discoveredDevices.add(nuimoController.address)
            }
        }
        assertFalse("Discovery manager must report a device discovery for each device only once", receivedDuplicateDiscoveryEvent)
    }

    fun testDiscoveryShouldStopEmittingDiscoveryEventsWhenStopped() {
        var devicesFound = 0
        discover(20.0) { discovery, nuimoController, completed ->
            devicesFound += 1
            if (devicesFound == 2) { completed() }
            discovery.stopDiscovery()
        }
        assertTrue("Discovery should not discover devices when stopped", devicesFound == 1)
    }

    fun testDiscoveryShouldContinueDiscoveringDevicesWhenRestarted() {
        //TODO: This test needs to Nuimos being discoverable. Write another test that asserts that the discovery can actually discover two devices.
        var firstDiscoveredAddress: String? = null
        discover { discoveryManager, nuimoController, completed ->
            when (firstDiscoveredAddress) {
                null                    -> firstDiscoveredAddress = nuimoController.address
                nuimoController.address -> completed()
            }
            discovery.stopDiscovery()
            discovery.startDiscovery()
        }
    }

    /*
     * Private test helper methods
     */

    // Discovers a controller. Blocks until the "discovered" lambda calls the completed() method
    protected fun discover(timeout: Double = 30.0, discovered: (discovery: NuimoDiscoveryManager, nuimoController: NuimoController, completed: () -> Unit) -> Unit) {
        val waitLock = Semaphore(0)
        discovery.addDiscoveryListener(object: NuimoDiscoveryListener {
            override fun onDiscoverNuimoController(nuimoController: NuimoController) {
                println("Bluetooth device found " + nuimoController.address)
                //if (nuimoController.address != "CC:8A:20:D9:E7:3F") { return } //Klapperkiste
                if (nuimoController.address != "F1:22:76:AC:53:58") { return } // Core_04_01_B_DEV
                discovered(discovery, nuimoController, { waitLock.release() })
            }
        })
        val started = discovery.startDiscovery()
        assertTrue("Device discovery must start", started)
        after(timeout) { waitLock.release() }
        waitLock.acquire()
        discovery.stopDiscovery()
    }
}

fun after(delay: Double, block: () -> Unit): Timer {
    return Timer().apply {
        schedule((delay * 1000.0).toLong()) {
            block()
        }
    }
}
