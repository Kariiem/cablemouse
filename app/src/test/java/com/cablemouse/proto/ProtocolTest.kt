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
                else -> error(ev)
            }
            assertEquals(bytes, hex(got))
        }
    }

    @Test fun schedulerKeepsFractions() {
        val sent = ArrayList<ByteArray>()
        val s = FrameScheduler { sent += it }
        repeat(4) { s.addMove(0.5f, 0f); s.flush() }   // 2.0 px total over 4 frames
        assertEquals(listOf("B0 01 41", "B0 01 41"), sent.map(::hex))
    }
}
