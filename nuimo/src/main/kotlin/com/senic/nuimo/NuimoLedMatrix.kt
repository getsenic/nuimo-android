/*
 * Copyright (c) 2015 Senic. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package com.senic.nuimo

open class NuimoLedMatrix {
    companion object {
        val LED_COUNT = 81
        val LedOffCharacters = " 0".toCharArray()
    }

    val leds: Array<Boolean>

    constructor(string: String) {
        leds = string
            .substring(0..Math.min(LED_COUNT, string.length)-1)
            .padEnd(LED_COUNT, ' ')
            .toCharArray()
            .map { !LedOffCharacters.contains(it) }
            .toTypedArray()
    }

    constructor(leds: Array<Boolean>) {
        this.leds = leds
            .slice(0..Math.min(LED_COUNT, leds.size) - 1)
            .plus(if (leds.size < LED_COUNT) Array(LED_COUNT - leds.size, { false }) else arrayOf())
            .toTypedArray()
    }

    override fun equals(other: Any?): Boolean {
        if (other is NuimoLedMatrix) {
            return leds.equals(other.leds)
        }
        return super.equals(other)
    }

    override fun hashCode(): Int = leds.hashCode()
}
