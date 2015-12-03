/*
 * Copyright (c) 2015 Senic. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package com.senic.nuimo

class NuimoLedMatrix {
    companion object {
        val LED_COUNT = 81
        val LedOffCharacters = " 0".toCharArray()
    }

    val bits: List<Boolean>

    constructor(string: String) {
        bits = string
            .substring(0..Math.min(LED_COUNT, string.length)-1)
            .padEnd(LED_COUNT, ' ')
            .toCharArray()
            .map { !LedOffCharacters.contains(it) }
    }

    override fun equals(other: Any?): Boolean {
        if (other is NuimoLedMatrix) {
            return bits.equals(other.bits)
        }
        return super.equals(other)
    }
}
