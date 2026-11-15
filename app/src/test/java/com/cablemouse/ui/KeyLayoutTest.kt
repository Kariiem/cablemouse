package com.cablemouse.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyLayoutTest {
    private val layouts = listOf(false, true).map { caps -> caps to KeyLayout.rows(caps) }

    @Test fun rowsAreFifteenUnitsWide() {
        for ((caps, rows) in layouts) rows.forEachIndexed { i, row ->
            assertEquals("row $i caps=$caps", 15f, row.sumOf { it.width.toDouble() }.toFloat(), 0.001f)
        }
    }

    @Test fun everyKeyIsEitherAModifierOrAHidUsageTheProtocolAccepts() {
        for ((_, rows) in layouts) for (key in rows.flatten()) {
            if (key.mod != 0) {
                assertEquals(key.label, 0, key.hid)
                assertTrue(key.label, key.mod in listOf(1, 2, 4, 8))
            } else {
                assertTrue(key.label, key.hid in 0x04..0x73)
            }
        }
    }

    @Test fun capsAsCtrlReplacesOnlyTheCapsKey() {
        val (plain, ctrl) = layouts.map { it.second.flatten() }
        assertEquals(plain.size, ctrl.size)
        val changed = plain.indices.filter { plain[it] != ctrl[it] }
        assertEquals(1, changed.size)
        assertEquals(KeyLayout.CAPS_HID, plain[changed[0]].hid)
        assertEquals(com.cablemouse.proto.MouseProtocol.MOD_CTRL, ctrl[changed[0]].mod)
    }
}
