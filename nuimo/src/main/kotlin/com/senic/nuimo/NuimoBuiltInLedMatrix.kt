/*
 * Copyright (c) 2016 Senic GmbH. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package com.senic.nuimo

class NuimoBuiltInLedMatrix : NuimoLedMatrix {
    companion object {
        @JvmField
        val BUSY = NuimoBuiltInLedMatrix(1)
    }

    private constructor(byte: Byte) : super(byte.toMatrixBits())

    override fun equals(other: Any?): Boolean {
        if (other is NuimoBuiltInLedMatrix) {
            return super.equals(other)
        }
        return super.equals(other)
    }

    override fun hashCode(): Int = super.hashCode() * 31
}

private fun Byte.toMatrixBits() : Array<Boolean> {
    val bits = Array(NuimoLedMatrix.LED_COUNT, { false })
    var n = toInt()
    var i = 0
    while (n > 0) {
        bits[i] = n % 2 > 0
        n /= 2
        i += 1
    }
    return bits
}
