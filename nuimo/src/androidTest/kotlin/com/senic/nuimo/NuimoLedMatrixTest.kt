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
        assertEquals(booleanArrayOf(
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false).toList(),
            NuimoLedMatrix(zeroMatrixString).bits)
    }

    fun testOneMatrixShouldConvertToItsBitRepresentation() {
        assertEquals(booleanArrayOf(
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true).toList(),
            NuimoLedMatrix(oneMatrixString).bits)
    }

    fun testTooShortMatrixShouldConvertToItsBitRepresentation() {
        assertEquals(booleanArrayOf(
                true, true, true, true, true, true, true, true, true,
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false).toList(),
            NuimoLedMatrix("111111111" + "000000000").bits)
    }

    fun testTooLongMatrixShouldConvertToItsBitRepresentation() {
        assertEquals(booleanArrayOf(
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, true, true).toList(),
            NuimoLedMatrix(oneMatrixString + "000000000").bits)
    }

    fun testNuimoLedMatrixShouldConvertToItsGattByteRepresentation() {
        assertEquals(arrayOf(-1, 0, 85, 0, 0, 0, 0, 0, 0, 0, 0).map { it.toByte() }.toList(),
                NuimoLedMatrix("********        * * * * ").gattBytes().toList())
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
