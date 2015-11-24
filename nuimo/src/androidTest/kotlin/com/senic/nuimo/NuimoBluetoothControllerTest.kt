/*
 * Copyright (c) 2015 Senic. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package com.senic.nuimo

import java.util.*

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
            nuimoController.addControllerListener(object: LedMatrixGuidedNuimoControllerListener(nuimoController, "unpressed") {
                override val matrixForState: Map<String, NuimoLedMatrix>
                    get() = hashMapOf(Pair("unpressed", NuimoLedMatrix.pressButtonMatrix()), Pair("pressed", NuimoLedMatrix.releaseButtonMatrix()))
                override fun onGestureEvent(event: NuimoGestureEvent) {
                    when (event.gesture) {
                        NuimoGesture.BUTTON_PRESS -> state = "pressed"
                        NuimoGesture.BUTTON_RELEASE -> if(state == "pressed") completed()
                    }
                }
            })
        }
    }

    fun testNuimoControllerShouldReceiveRotationEvents() {
        var rotationTest = { rotationDirection: NuimoGesture, matrixString: String ->
            connectServices { nuimoController, completed ->
                nuimoController.addControllerListener(object : LedMatrixGuidedNuimoControllerListener(nuimoController, "0") {
                    var maxRotationValue = 2000
                    var rotationValue = 0
                    val steps = 18
                    var matrixWritesCount = 0
                    override val matrixForState: Map<String, NuimoLedMatrix>
                        get() = {
                            var matrices = HashMap<String, NuimoLedMatrix>()
                            (0..steps).forEach { matrices[it.toString()] = NuimoLedMatrix(matrixString.substring(0..80 - steps) + "*".repeat(it) + " ".repeat(steps - it)) }
                            matrices
                        }()

                    override fun onGestureEvent(event: NuimoGestureEvent) {
                        if (event.gesture == rotationDirection) {
                            rotationValue += event.value ?: 0
                            println("RV = $rotationValue")
                            state = Math.floor(rotationValue.toDouble() / maxRotationValue * steps).toInt().toString()
                            if (matrixWritesCount > steps) completed()
                        }
                    }

                    override fun onLedMatrixWrite() {
                        matrixWritesCount++
                    }
                })
            }
        }
        rotationTest(NuimoGesture.ROTATE_RIGHT, NuimoLedMatrix.rotateRightMatrixString())
        rotationTest(NuimoGesture.ROTATE_LEFT, NuimoLedMatrix.rotateLeftMatrixString())
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

private abstract class LedMatrixGuidedNuimoControllerListener(controller: NuimoController, initialState: String = ""): NuimoControllerListener() {
    var controller = controller
    var state: String = initialState
        set(value) {
          if (value != state) {
              field = value
              controller.displayLedMatrix(matrixForState[value]!!)
          }
        }
    abstract val matrixForState: Map<String, NuimoLedMatrix>
    init {
        controller.displayLedMatrix(matrixForState[initialState]!!)
    }
}

private fun NuimoLedMatrix.Companion.pressButtonMatrix() = NuimoLedMatrix(
        "         " +
        "***  *** " +
        "*  * *  *" +
        "*  * *  *" +
        "***  *** " +
        "*    *  *" +
        "*    *  *" +
        "*    *  *" +
        "         ")

private fun NuimoLedMatrix.Companion.releaseButtonMatrix() = NuimoLedMatrix(
        "         " +
        "***  ****" +
        "*  * *   " +
        "*  * *   " +
        "***  *** " +
        "*  * *   " +
        "*  * *   " +
        "*  * ****" +
        "         ")

private fun NuimoLedMatrix.Companion.rotateRightMatrixString() =
        "  ***    " +
        " *   *   " +
        "*     *  " +
        "*     *  " +
        "*   *****" +
        " *   *** " +
        "      *  " +
        "         " +
        "         "

private fun NuimoLedMatrix.Companion.rotateLeftMatrixString() =
        "    ***  " +
        "   *   * " +
        "  *     *" +
        "  *     *" +
        "*****   *" +
        " ***   * " +
        "  *      " +
        "         " +
        "         "
