/*
 * Copyright (c) 2015 Senic. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package com.senic.nuimo

class NuimoGestureEvent(gesture: NuimoGestureEvent.NuimoGesture, value: Int? = null) {
    val gesture: NuimoGesture = gesture
    val value: Int? = value

    enum class NuimoGesture {
        BUTTON_PRESS,
        BUTTON_RELEASE,
        //TODO: Synthesize rotation events into a single type "ROTATE"?
        ROTATE_LEFT,
        ROTATE_RIGHT,
    }
}
