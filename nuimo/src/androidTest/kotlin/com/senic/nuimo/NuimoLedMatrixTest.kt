/*
 * Copyright (c) 2015 Senic. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package com.senic.nuimo

import android.test.AndroidTestCase

open class NuimoLedMatrixTest: AndroidTestCase() {

    fun testZeroMatrixShouldConvertToItsBitRepresentation() {
        assertTrue(booleanArrayEquals(booleanArrayOf(
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false),
            NuimoLedMatrix(zeroMatrixString).bits))
    }

    fun testOneMatrixShouldConvertToItsBitRepresentation() {
        assertTrue(booleanArrayEquals(booleanArrayOf(
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true),
            NuimoLedMatrix(oneMatrixString).bits))
    }
}

private val zeroMatrixString =
        "         " +
        "         " +
        "         " +
        "         " +
        "         " +
        "         " +
        "         " +
        "         " +
        "         "

private val oneMatrixString =
        "111111111" +
        "111111111" +
        "111111111" +
        "111111111" +
        "111111111" +
        "111111111" +
        "111111111" +
        "111111111" +
        "111111111"

//TODO: http://stackoverflow.com/questions/33758880/can-i-add-operators-to-existing-classes
private fun booleanArrayEquals(a1: BooleanArray, a2: BooleanArray): Boolean {
    return (a1.size === a2.size) && !(0..a1.size-1).map { a1[it] === a2[it] }.contains(false)
}
