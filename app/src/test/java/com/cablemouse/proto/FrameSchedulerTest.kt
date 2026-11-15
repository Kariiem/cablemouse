package com.cablemouse.proto

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameSchedulerTest {
    private val sent = ArrayList<String>()
    private val scheduler = FrameScheduler { b -> sent += b.joinToString(" ") { "%02X".format(it) } }

    @Test fun keepsFractions() {
        repeat(4) { scheduler.addMove(0.5f, 0f); scheduler.flush() }   // 2.0 px total over 4 frames
        assertEquals(listOf("B0 01 41", "B0 01 41"), sent)
    }

    @Test fun keepsNegativeFractions() {
        repeat(3) { scheduler.addMove(0f, -0.4f); scheduler.flush() }  // -1.2 px: one whole pixel up, -0.2 kept
        assertEquals(listOf("B0 02 3F"), sent)
    }

    @Test fun idleFlushSendsNothing() {
        scheduler.flush()
        scheduler.addMove(0.3f, 0.3f); scheduler.addScroll(0.3f); scheduler.flush()
        assertEquals(emptyList<String>(), sent)
    }

    @Test fun oneFlushSendsXThenYThenScrollAndSplitsLargeMoves() {
        scheduler.addMove(100f, -3f); scheduler.addScroll(2f); scheduler.flush()
        assertEquals(listOf("B0 01 7F", "B0 01 65", "B0 02 3D", "B0 03 42"), sent)
    }
}
