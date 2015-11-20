/*
 * Copyright (c) 2015 Senic. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package com.senic.nuimo

//TODO: Try spek test framework: http://jetbrains.github.io/spek/
//TODO: Add timeouts to each test
class NuimoBluetoothControllerTest: NuimoDiscoveryManagerTest() {

    fun testNuimoControllerShouldConnect() {
        connect { nuimoController, completed ->
            nuimoController.disconnect()
            completed()
        }
    }

    fun testNuimoControllerShouldDisconnect() {
        connect { nuimoController, completed ->
            nuimoController.addControllerListener(object: NuimoControllerListener() {
                override fun onDisconnect() = completed()
            })
            nuimoController.disconnect()
        }
    }

    fun testNuimoControllerShouldDiscoverGattServices() {
        connectServices { nuimoController, completed ->
            completed()
        }
    }

    fun testNuimoControllerShouldSendLedMatrix() {
        connectServices { nuimoController, completed ->
            nuimoController.addControllerListener(object: NuimoControllerListener() {
                override fun onLedMatrixWrite() = completed()
            })
            nuimoController.displayLedMatrix(NuimoLedMatrix((
                    "  o *  o " +
                    "o  ***  o" +
                    "  *****  " +
                    "o  ***  o" +
                    "  *****  " +
                    " ******* " +
                    "*********" +
                    "   ***   " +
                    " o ***  o")/*.toCharList().map { if (it == 'o' && Math.random() > 0.8) " " else it.toString()}.reduce { s, c -> s + c }*/))
        }

    }

    fun testNuimoControllerShouldReceiveButtonPressAndReleaseEvents() {
        connectServices { nuimoController, completed ->
            nuimoController.addControllerListener(object: NuimoControllerListener() {
                var pressed = false
                override fun onGestureEvent(event: NuimoGestureEvent) {
                    println(event.gesture.name)
                    when (event.gesture) {
                        NuimoGestureEvent.NuimoGesture.BUTTON_PRESS -> pressed = true
                        NuimoGestureEvent.NuimoGesture.BUTTON_RELEASE -> if(pressed) completed()
                    }
                }
            })
            //TODO: Show matrix that tells tester what gesture to perform
        }
    }

    /*
     * Private test helper methods
     */

    // Discovers and connects a controller and then stops discovery. Blocks until the "discovered" lambda calls the completed() method. Then disconnects the controller.
    protected fun connect(connected: (nuimoController: NuimoController, completed: () -> Unit) -> Unit) {
        //TODO: Add timeout
        var controller: NuimoController? = null
        discover { discovery, nuimoController, completed ->
            controller = nuimoController
            nuimoController.addControllerListener(object: NuimoControllerListener() {
                override fun onConnect() = connected(nuimoController, completed)
            })
            discovery.stopDiscovery()
            nuimoController.connect()
        }
        controller?.disconnect()
    }

    // Discovers, connects, stops discovery and waits until all controller GATT services are found. Blocks until the "discovered" lambda calls the completed() method. Then disconnects the controller.
    protected fun connectServices(connected: (nuimoController: NuimoController, completed: () -> Unit) -> Unit) {
        //TODO: Add timeout
        connect { nuimoController, completed ->
            nuimoController.addControllerListener(object: NuimoControllerListener() {
                override fun onReady() = connected(nuimoController, completed)
            })
        }
    }
}