package com.cablemouse.proto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
                "move_abs" -> MouseProtocol.moveAbs(value shr 14, value and 0x3FFF)
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

    @Test fun moveAbsClampsToRange() {
        assertEquals("B0 16 7F B0 17 7F B0 18 00 B0 19 00", hex(MouseProtocol.moveAbs(99999, -5)))
    }

    @Test fun rejectsValuesTheHelperWouldDrop() {
        for (bad in listOf<() -> Any>(
            { MouseProtocol.keyChar(0) }, { MouseProtocol.keyChar(0x110000) }, { MouseProtocol.key(14) },
            { MouseProtocol.mods(16) }, { MouseProtocol.keyHid(0x03) }, { MouseProtocol.keyHid(0x74) },
        )) assertThrows(IllegalArgumentException::class.java) { bad() }
    }
}
