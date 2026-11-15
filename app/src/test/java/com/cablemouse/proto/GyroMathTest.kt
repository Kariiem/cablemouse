package com.cablemouse.proto

import org.junit.Assert.assertEquals
import org.junit.Test

class GyroMathTest {
    private val flatUp = floatArrayOf(0f, 0f, 9.81f)          // phone lying flat, screen up

    @Test fun turnLeftMovesCursorLeft() {
        // Turning left is a counter-clockwise rotation seen from above: +z rate when flat.
        val (dx, dy) = GyroMath.delta(floatArrayOf(0f, 0f, 1f), flatUp, 0.02f, 1000f)!!
        assertEquals(-20f, dx, 0.01f); assertEquals(0f, dy, 0.001f)
        // Same turn with the phone held upright (gravity along +y): rotation about the device y axis.
        val (ux, _) = GyroMath.delta(floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 9.81f, 0f), 0.02f, 1000f)!!
        assertEquals(-20f, ux, 0.01f)
    }

    @Test fun tiltUpMovesCursorUp() {
        val (_, dy) = GyroMath.delta(floatArrayOf(1f, 0f, 0f), flatUp, 0.02f, 1000f)!!
        assertEquals(-20f, dy, 0.01f)
    }

    @Test fun ignoresNoiseAndBadGravity() {
        val (dx, dy) = GyroMath.delta(floatArrayOf(0.01f, 0.01f, 0.01f), flatUp, 0.02f, 1000f)!!
        assertEquals(0f, dx, 0f); assertEquals(0f, dy, 0f)
        assertEquals(null, GyroMath.delta(floatArrayOf(1f, 1f, 1f), floatArrayOf(0f, 0f, 0f), 0.02f, 1000f))
    }
}
