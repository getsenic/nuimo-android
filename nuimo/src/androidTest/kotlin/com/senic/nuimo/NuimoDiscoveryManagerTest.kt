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
    override fun setUp() {
        super.setUp()
        val discovery: NuimoDiscoveryManager = NuimoDiscoveryManager(context)
        assertTrue("Bluetooth must be present and enabled", discovery.checkBluetoothEnabled())
        assertTrue("Permissions must be granted", discovery.checkPermissions(null))
    }

    fun testDiscoveryManagerShouldDiscoverOneBluetoothController() {
        discoverAndWait(20.0) { discovery, nuimoController, completed ->
            assertEquals(NuimoBluetoothController::class.java, nuimoController.javaClass)
            completed()
        }.onTimeout {
            fail("Discovery manager must discover a nuimo controller")
        }
    }

    fun testDiscoveryManagerShouldSendOnlyOneDiscoveryEventForTheSameDevice() {
        var discoveredDevices = HashSet<String>()
        discoverAndWait(60.0) { discoveryManager, nuimoController, completed ->
            if (discoveredDevices.contains(nuimoController.address)) {
                completed()
            }
            else {
                discoveredDevices.add(nuimoController.address)
            }
        }.onComplete {
            fail("Discovery manager must report a device discovery for each device only once")
        }
    }

    fun testDiscoveryShouldStopEmittingDiscoveryEventsWhenStopped() {
        var deviceFound = false
        //TODO: This test fails when there are two Nuimos nearby
        discoverAndWait(30.0) { discovery, nuimoController, completed ->
            if (deviceFound) { completed() }
            deviceFound = true
            discovery.stopDiscovery()
        }.onComplete {
            fail("Discovery should stop discovering devices when stopped")
        }
    }

    fun testDiscoveryShouldContinueDiscoveringDevicesWhenRestarted() {
        var isFirstDiscovery = true
        discoverAndWait(30.0) { discovery, nuimoController, completed ->
            discovery.stopDiscovery()
            when (isFirstDiscovery) {
                true  -> discovery.startDiscovery() // Restarts discovery
                false -> completed()
            }
            isFirstDiscovery = false
        }.onTimeout {
            fail("Discovery should continue discovering devices when restarted")
        }
    }

    /*
     * Private test helper methods
     */

    // Discovers a controller. Blocks until the "discovered" lambda calls the completed() method or until the timeout has reached
    protected fun discoverAndWait(timeout: Double, discovered: (discovery: NuimoDiscoveryManager, nuimoController: NuimoController, completed: () -> Unit) -> Unit): TimeoutResult {
        val waitLock = Semaphore(0)
        var timedOut = false
        val timeoutTimer = after(timeout) {
            timedOut = true
            waitLock.release()
        }
        val discovery: NuimoDiscoveryManager = NuimoDiscoveryManager(context)
        discovery.addDiscoveryListener(object: NuimoDiscoveryListener {
            override fun onDiscoverNuimoController(nuimoController: NuimoController) {
                println("Bluetooth device found " + nuimoController.address)
                //if (nuimoController.address != "CC:8A:20:D9:E7:3F") { return } //Klapperkiste
                discovered(discovery, nuimoController, {
                    timeoutTimer.cancel()
                    waitLock.release()
                })
            }
        })
        val started = discovery.startDiscovery()
        assertTrue("Device discovery must start", started)
        waitLock.acquire()
        discovery.stopDiscovery()
        return TimeoutResult(timedOut)
    }
}

fun after(delay: Double, block: () -> Unit): Timer {
    return Timer().apply {
        schedule((delay * 1000.0).toLong()) {
            block()
        }
    }
}

class TimeoutResult(val timedOut: Boolean) {
    fun onTimeout(block: () -> Unit) {
        if (timedOut) { block() }
    }
    fun onComplete(block: () -> Unit) {
        if (!timedOut) { block() }
    }
}
