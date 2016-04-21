/*
 * Copyright (c) 2015 Senic. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package com.senic.nuimo

class NuimoGestureEvent(gesture: NuimoGesture, value: Int? = null) {
    val gesture: NuimoGesture = gesture
    val value: Int? = value
}

enum class NuimoGesture {
    BUTTON_PRESS,
    BUTTON_RELEASE,
    ROTATE,
    TOUCH_LEFT_DOWN,
    TOUCH_LEFT_RELEASE,
    TOUCH_RIGHT_DOWN,
    TOUCH_RIGHT_RELEASE,
    TOUCH_TOP_DOWN,
    TOUCH_TOP_RELEASE,
    TOUCH_BOTTOM_DOWN,
    TOUCH_BOTTOM_RELEASE,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    SWIPE_UP,
    SWIPE_DOWN,
    FLY_LEFT,
    FLY_RIGHT,
    FLY_BACKWARDS,
    FLY_TOWARDS,
    FLY_UP_DOWN
}
