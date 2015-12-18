/*
 * Copyright (c) 2015 Senic. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package com.senic.nuimo

import java.util.*
import kotlin.concurrent.schedule

//TODO: Try spek test framework: http://jetbrains.github.io/spek/
//TODO: Add timeouts to each test
class NuimoBluetoothControllerTest: NuimoDiscoveryManagerTest() {

    fun testNuimoControllerShouldConnect() {
        connectAndWait(20.0) { nuimoController, completed ->
            nuimoController.disconnect()
            completed()
        }.onTimeout {
            fail("Nuimo controller should connect")
        }
    }

    fun testNuimoControllerShouldDisconnect() {
        connectAndWait(20.0) { nuimoController, completed ->
            nuimoController.addControllerListener(object: BaseNuimoControllerListener() {
                override fun onDisconnect() = completed()
            })
            nuimoController.disconnect()
        }.onTimeout {
            fail("Nuimo controller should disconnect")
        }
    }

    fun testNuimoControllerShouldReceiveLedMatrixWriteResponse() {
        val displayInterval = 2.0
        connectAndWait(20.0) { nuimoController, completed ->
            nuimoController.addControllerListener(object: BaseNuimoControllerListener() {
                override fun onLedMatrixWrite() {
                    // Complete only after display interval as otherwise the LED matrix disappears immediately as completed() disconnects from the device
                    after(displayInterval + 1.0) { completed() }
                }
            })
            nuimoController.displayLedMatrix(NuimoLedMatrix(NuimoLedMatrix.animatableMatrixString()), displayInterval)
        }.onTimeout {
            fail("Nuimo controller should get a LED matrix write response")
        }
    }

    //TODO: Remove this test, the next one makes it obsolete. The x-mas animation is however very fancy. Put it somewhere else.
    fun testNuimoControllerShouldPlayLedMatrixAnimation() {
        connectAndWait(30.0) { nuimoController, completed ->
            val frameCount = 100
            var frameIndex = 0
            val nextFrame = {
                nuimoController.displayLedMatrix(NuimoLedMatrix(NuimoLedMatrix
                    .animatableMatrixString()
                    .toList()
                    .map { if (it == 'o' && Math.random() > 0.8) " " else it.toString() }
                    .reduce { s, c -> s + c }), 20.0)
            }
            nuimoController.addControllerListener(object: BaseNuimoControllerListener() {
                override fun onLedMatrixWrite() {
                    when (++frameIndex) {
                        in 1..frameCount-1 -> nextFrame()
                        else               -> completed()
                    }
                }
            })
            nextFrame()
        }.onTimeout {
            fail("Nuimo controller should play LED matrix animation within timeout")
        }
    }

    fun testNuimoControllerShouldSkipLedMatrixFramesIfDisplayRequestsAreInvokedFasterThanTheControllerCanDisplayThem() {
        val sendFramesIntervalMillis = 10L
        val sendFramesDurationMillis = 30000L
        val maxWriteDurationForSingleFrameMillis = 1000L
        val frameCount = sendFramesDurationMillis / sendFramesIntervalMillis
        var framesWritten = 0
        val expectedAnimationDurationMillis = sendFramesDurationMillis + 2 * maxWriteDurationForSingleFrameMillis
        var actualAnimationDurationMillis = 0L

        val timer = Timer()
        connectAndWait(expectedAnimationDurationMillis / 1000.0 + 30.0) { nuimoController, completed ->
            var frameIndex = 0
            // Send frames faster than the device can handle to force controller to drop frames during the frame animation
            val animationStartedNanos = System.nanoTime()
            timer.schedule(0, sendFramesIntervalMillis, {
                if (++frameIndex <= frameCount) {
                    nuimoController.displayLedMatrix(NuimoLedMatrix("*".repeat(frameIndex % 81 + 1)))
                }
            })
            // Stop frame animation when expected animation duration is reached
            //TODO: Use onTimeout() capability of connect() to implement fail on timeout
            timer.schedule(2 * maxWriteDurationForSingleFrameMillis, 200) {
                if (System.nanoTime() - animationStartedNanos >= expectedAnimationDurationMillis * 1000000L) { completed() }
            }
            nuimoController.addControllerListener(object: BaseNuimoControllerListener() {
                override fun onLedMatrixWrite() {
                    framesWritten++
                    actualAnimationDurationMillis = (System.nanoTime() - animationStartedNanos) / 1000000
                }
            })
        }
        timer.cancel()

        println("Fast animation test has written $framesWritten frames of $frameCount total in $actualAnimationDurationMillis milliseconds (= ${(framesWritten / (actualAnimationDurationMillis / 1000.0)).toInt()} FPS). Maximum expected duration: $expectedAnimationDurationMillis milliseconds")

        assertTrue("Nuimo controller should have written at least one frame", framesWritten > 0)
        assertTrue("Nuimo controller should have finished animation within $expectedAnimationDurationMillis milliseconds but it took $actualAnimationDurationMillis milliseconds", actualAnimationDurationMillis <= expectedAnimationDurationMillis)
        assertTrue("Nuimo controller should have dropped some LED matrix frames but it wrote all $frameCount frames", framesWritten < frameCount)
    }

    fun testNuimoControllerShouldReceiveButtonPressAndReleaseEvents() {
        connectAndWait(30.0) { nuimoController, completed ->
            val UNPRESSED = 0
            val PRESSED = 1
            nuimoController.addControllerListener(object: LedMatrixGuidedNuimoControllerListener(nuimoController, UNPRESSED) {
                override val matrixForState: Map<Int, NuimoLedMatrix>
                    get() = hashMapOf(Pair(UNPRESSED, NuimoLedMatrix.pressButtonMatrix()), Pair(PRESSED, NuimoLedMatrix.releaseButtonMatrix()))
                override fun onGestureEvent(event: NuimoGestureEvent) {
                    when (event.gesture) {
                        NuimoGesture.BUTTON_PRESS   -> state = PRESSED
                        NuimoGesture.BUTTON_RELEASE -> if (state == PRESSED) completed()
                    }
                }
            })
        }.onTimeout {
            fail("Nuimo controller should receive button press and release events")
        }
    }

    fun testNuimoControllerShouldReceiveRotationEvents() {
        val rotationTest = { swipeDirection: NuimoGesture, matrixString: String ->
            var accumulatedRotationValue = 0
            val maxRotationValue = 2000
            gestureRepetitionTest(45.0, swipeDirection, matrixString, 18) { steps, rotationValue ->
                accumulatedRotationValue += rotationValue ?: 0
                println("accumulatedRotationValue: $accumulatedRotationValue")
                (accumulatedRotationValue.toDouble() / maxRotationValue * 18).toInt()
            }
        }

        rotationTest(NuimoGesture.ROTATE_RIGHT, NuimoLedMatrix.rotateRightMatrixString())
        rotationTest(NuimoGesture.ROTATE_LEFT, NuimoLedMatrix.rotateLeftMatrixString())
    }

    fun testNuimoControllerShouldReceiveSwipeEvents() {
        //TODO: Request swipe gesture in arbitrary order an let test fail if user performs the wrong swipe. This recognizes too sensitive devices.
        //TODO: Timeout
        val swipeTest = { swipeDirection: NuimoGesture, matrixString: String ->
            gestureRepetitionTest(45.0, swipeDirection, matrixString, 9) { steps, eventValue -> steps + 1 }
        }

        swipeTest(NuimoGesture.SWIPE_LEFT, NuimoLedMatrix.swipeLeftMatrixString())
        swipeTest(NuimoGesture.SWIPE_RIGHT, NuimoLedMatrix.swipeRightMatrixString())
        swipeTest(NuimoGesture.SWIPE_UP, NuimoLedMatrix.swipeUpMatrixString())
        swipeTest(NuimoGesture.SWIPE_DOWN, NuimoLedMatrix.swipeDownMatrixString())
    }

    /*
     * Private test helper methods
     */

    // Discovers and connects a controller and then stops discovery. Blocks until the "discovered" lambda calls the completed() method. Then disconnects the controller.
    protected fun connectAndWait(timeout: Double, connected: (nuimoController: NuimoController, completed: () -> Unit) -> Unit): TimeoutResult {
        var controller: NuimoController? = null
        val result = discoverAndWait(timeout) { discovery, nuimoController, completed ->
            nuimoController.defaultMatrixDisplayInterval = 20.0
            nuimoController.addControllerListener(object: BaseNuimoControllerListener() {
                override fun onConnect() = connected(nuimoController, completed)
            })
            discovery.stopDiscovery()
            nuimoController.connect()
            controller = nuimoController
        }
        controller?.disconnect()
        return result
    }

    // Connects a controller and expects a given number of gesture event repetitions
    fun gestureRepetitionTest(timeout: Double, gesture: NuimoGesture, matrixString: String, maxRepetitions: Int, updateRepetitions: (repetitions: Int, eventValue: Int?) -> Int) {
        connectAndWait(timeout) { nuimoController, completed ->
            nuimoController.addControllerListener(object: LedMatrixGuidedNuimoControllerListener(nuimoController, 0) {
                override val matrixForState: Map<Int, NuimoLedMatrix>
                    get() = {
                        var matrices = HashMap<Int, NuimoLedMatrix>()
                        (0..maxRepetitions).forEach { matrices[it] = NuimoLedMatrix(matrixString.substring(0..80 - maxRepetitions) + "*".repeat(it) + " ".repeat(maxRepetitions - it)) }
                        matrices
                    }()

                override fun onGestureEvent(event: NuimoGestureEvent) {
                    if (event.gesture != gesture) { return }
                    state = updateRepetitions(state, event.value)
                    if (state == maxRepetitions) completed()
                }
            })
        }.onTimeout {
            fail("Gesture test must complete within timeout")
        }
    }
}

private abstract class LedMatrixGuidedNuimoControllerListener(controller: NuimoController, initialState: Int = 0): BaseNuimoControllerListener() {
    var controller = controller
    var state: Int = initialState
        set(value) {
          if (value != state) {
              field = value
              controller.displayLedMatrix(matrixForState[value] ?: NuimoLedMatrix.emoticonSadMatrix())
          }
        }
    abstract val matrixForState: Map<Int, NuimoLedMatrix>
    init {
        controller.displayLedMatrix(matrixForState[initialState] ?: NuimoLedMatrix.emoticonSadMatrix())
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

private fun NuimoLedMatrix.Companion.swipeLeftMatrixString() =
        "    *    " +
        "   **    " +
        "  ****** " +
        " ******* " +
        "  ****** " +
        "   **    " +
        "    *    " +
        "         " +
        "         "

private fun NuimoLedMatrix.Companion.swipeRightMatrixString() =
        "    *    " +
        "    **   " +
        " ******  " +
        " ******* " +
        " ******  " +
        "    **   " +
        "    *    " +
        "         " +
        "         "

private fun NuimoLedMatrix.Companion.swipeUpMatrixString() =
        "    *    " +
        "   ***   " +
        "  *****  " +
        " ******* " +
        "   ***   " +
        "   ***   " +
        "   ***   " +
        "         " +
        "         "

private fun NuimoLedMatrix.Companion.swipeDownMatrixString() =
        "   ***   " +
        "   ***   " +
        "   ***   " +
        " ******* " +
        "  *****  " +
        "   ***   " +
        "    *    " +
        "         " +
        "         "

private fun NuimoLedMatrix.Companion.emoticonSadMatrix() = NuimoLedMatrix(
        "***   ***" +
        " **   ** " +
        "         " +
        "      *  " +
        "         " +
        "  *****  " +
        " *     * " +
        "*       *" +
        "         ")

private fun NuimoLedMatrix.Companion.animatableMatrixString() =
        "  o *  o " +
        "o  ***  o" +
        "  *****  " +
        "o  ***  o" +
        "  *****  " +
        " ******* " +
        "*********" +
        "   ***   " +
        " o ***  o"

