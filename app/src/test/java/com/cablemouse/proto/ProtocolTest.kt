package com.cablemouse.proto

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ProtocolTest {
    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it) }

    @Test fun splitsLargeDeltas() {
        val msgs = MouseProtocol.moveX(130)
        assertEquals(listOf("B0 01 7F", "B0 01 7F", "B0 01 44"), msgs.map(::hex))
        assertEquals(listOf("B0 01 00", "B0 01 00", "B0 01 3E"), MouseProtocol.moveX(-130).map(::hex))
    }

    @Test fun zeroEmitsNothing() = assertEquals(0, MouseProtocol.moveY(0).size)

    @Test fun sharedVectorsEncode() {
        // Rows with a single-message encoding must round-trip through the encoder.
        val file = File("../testvectors/protocol.txt")
        file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val (bytes, ev, v) = line.split("|").map { it.trim() }
            val value = v.toInt()
            val got = when (ev) {
                "move_x" -> MouseProtocol.moveX(value).single()
                "move_y" -> MouseProtocol.moveY(value).single()
                "scroll" -> MouseProtocol.scroll(value).single()
                "down" -> MouseProtocol.button(value, true)
                "up" -> if (bytes.startsWith("80")) MouseProtocol.button(value, false) else return@forEach
                "heartbeat" -> MouseProtocol.heartbeat()
                "key_char" -> MouseProtocol.keyChar(value)
                "key_special" -> MouseProtocol.key(value)
                "mods" -> MouseProtocol.mods(value)
                "key_hid" -> MouseProtocol.keyHid(value)
                else -> error(ev)
            }
            assertEquals(bytes, hex(got))
        }
    }

    @Test fun keyCharRoundTripsEveryPlane() {
        // Decode the three groups back the way the C helper does and compare.
        for (cp in listOf(0x20, 0x7F, 0x80, 0x3FFF, 0x4000, 0xFFFF, 0x10000, 0x10FFFF)) {
            val b = MouseProtocol.keyChar(cp)
            assertEquals(9, b.size)
            assertEquals(cp, ((b[2].toInt() shl 14) or (b[5].toInt() shl 7) or b[8].toInt()))
            assertEquals(true, b.indices.filter { it % 3 == 2 }.all { b[it] in 0..0x7F })
        }
    }

    @Test fun layoutRowsAreFifteenUnitsWide() {
        for (caps in listOf(false, true))
            com.cablemouse.ui.KeyLayout.rows(caps).forEachIndexed { i, row ->
                assertEquals("row $i caps=$caps", 15f, row.sumOf { it.width.toDouble() }.toFloat(), 0.001f)
            }
    }

    @Test fun gyroTurnLeftMovesCursorLeft() {
        val flatUp = floatArrayOf(0f, 0f, 9.81f)          // phone lying flat, screen up
        // Turning left is a counter-clockwise rotation seen from above: +z rate when flat.
        val (dx, dy) = GyroMath.delta(floatArrayOf(0f, 0f, 1f), flatUp, 0.02f, 1000f)!!
        assertEquals(-20f, dx, 0.01f); assertEquals(0f, dy, 0.001f)
        // Same turn with the phone held upright (gravity along +y): rotation about the device y axis.
        val (ux, _) = GyroMath.delta(floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 9.81f, 0f), 0.02f, 1000f)!!
        assertEquals(-20f, ux, 0.01f)
    }

    @Test fun gyroTiltUpMovesCursorUp() {
        val (_, dy) = GyroMath.delta(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, 9.81f), 0.02f, 1000f)!!
        assertEquals(-20f, dy, 0.01f)
    }

    @Test fun gyroIgnoresNoiseAndBadGravity() {
        val (dx, dy) = GyroMath.delta(floatArrayOf(0.01f, 0.01f, 0.01f), floatArrayOf(0f, 0f, 9.81f), 0.02f, 1000f)!!
        assertEquals(0f, dx, 0f); assertEquals(0f, dy, 0f)
        assertEquals(null, GyroMath.delta(floatArrayOf(1f, 1f, 1f), floatArrayOf(0f, 0f, 0f), 0.02f, 1000f))
    }

    @Test fun schedulerKeepsFractions() {
        val sent = ArrayList<ByteArray>()
        val s = FrameScheduler { sent += it }
        repeat(4) { s.addMove(0.5f, 0f); s.flush() }   // 2.0 px total over 4 frames
        assertEquals(listOf("B0 01 41", "B0 01 41"), sent.map(::hex))
    }
}
