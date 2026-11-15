package com.cablemouse.proto

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Turns gyroscope readings into cursor movement. Pure maths, unit tested.
 *
 * Horizontal movement uses the rotation rate about the world's vertical axis (found from the gravity vector),
 * so turning the phone left/right moves the cursor left/right however the phone is held. Vertical movement uses
 * the rotation about the device's x axis (tilting the top edge up/down), which is "pointing up/down" both when
 * the phone lies flat like a remote and when it is held upright.
 */
object GyroMath {
    /** Rotation rates below this (rad/s) are treated as sensor noise so a still phone doesn't drift. */
    const val DEADZONE = 0.02f

    /**
     * @param gyro angular velocity (x, y, z) in rad/s, device frame
     * @param up   gravity-sensor reading (points up in the device frame; any non-zero length)
     * @param dt   seconds since the previous gyro event
     * @return cursor movement in pixels, or null when [up] is unusable
     */
    fun delta(gyro: FloatArray, up: FloatArray, dt: Float, pxPerRad: Float): Pair<Float, Float>? {
        val n = sqrt(up[0] * up[0] + up[1] * up[1] + up[2] * up[2])
        if (n < 1f) return null
        val wUp = (gyro[0] * up[0] + gyro[1] * up[1] + gyro[2] * up[2]) / n   // > 0: turning left
        val wX = gyro[0]                                                       // > 0: tilting the top edge up
        val dx = if (abs(wUp) < DEADZONE) 0f else -wUp * dt * pxPerRad
        val dy = if (abs(wX) < DEADZONE) 0f else -wX * dt * pxPerRad
        return dx to dy
    }

    const val RAD_PER_10_DEG = 0.17453293f
}
