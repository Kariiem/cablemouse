package com.cablemouse.ui

import com.cablemouse.proto.MouseProtocol

/** One key of the on-screen PC keyboard. [hid] is the USB HID usage; modifier keys have [mod] != 0 instead. */
data class KeySpec(
    val label: String,
    val width: Float,
    val hid: Int = 0,
    val mod: Int = 0,
    val shifted: String = label,       // label shown while Shift is active
    val repeats: Boolean = false,
)

/** ANSI QWERTY layout, 6 rows of 15 units each. Letters are shown upper case like on a real keyboard. */
object KeyLayout {
    const val CAPS_HID = 0x39

    private fun k(label: String, hid: Int, w: Float = 1f, shifted: String = label, repeats: Boolean = true) =
        KeySpec(label, w, hid = hid, shifted = shifted, repeats = repeats)

    private fun m(label: String, mod: Int, w: Float) = KeySpec(label, w, mod = mod)

    /** @param capsAsCtrl show the Caps Lock position as a Ctrl modifier instead. */
    fun rows(capsAsCtrl: Boolean): List<List<KeySpec>> {
        val fn = (1..12).map { k("F$it", 0x3A + it - 1, 1f, repeats = false) }
        val digits = "1234567890"
        val digitShift = "!@#$%^&*()"
        fun alpha(c: Char) = k(c.toString(), 0x04 + (c - 'A'))
        return listOf(
            listOf(k("Esc", 0x29, 1.5f, repeats = false)) + fn + k("Del", 0x4C, 1.5f),
            listOf(k("`", 0x35, 1f, "~")) +
                digits.mapIndexed { i, c -> k(c.toString(), if (c == '0') 0x27 else 0x1E + i, 1f, digitShift[i].toString()) } +
                listOf(k("-", 0x2D, 1f, "_"), k("=", 0x2E, 1f, "+"), k("Bksp", 0x2A, 2f)),
            listOf(k("Tab", 0x2B, 1.5f, repeats = false)) + "QWERTYUIOP".map(::alpha) +
                listOf(k("[", 0x2F, 1f, "{"), k("]", 0x30, 1f, "}"), k("\\", 0x31, 1.5f, "|")),
            listOf(if (capsAsCtrl) m("Ctrl", MouseProtocol.MOD_CTRL, 1.75f) else k("Caps", CAPS_HID, 1.75f, repeats = false)) +
                "ASDFGHJKL".map(::alpha) +
                listOf(k(";", 0x33, 1f, ":"), k("'", 0x34, 1f, "\""), k("Enter", 0x28, 2.25f)),
            listOf(m("Shift", MouseProtocol.MOD_SHIFT, 2.25f)) + "ZXCVBNM".map(::alpha) +
                listOf(k(",", 0x36, 1f, "<"), k(".", 0x37, 1f, ">"), k("/", 0x38, 1f, "?"), m("Shift", MouseProtocol.MOD_SHIFT, 2.75f)),
            listOf(
                m("Ctrl", MouseProtocol.MOD_CTRL, 1.5f), m("Win", MouseProtocol.MOD_WIN, 1.25f), m("Alt", MouseProtocol.MOD_ALT, 1.25f),
                k("Space", 0x2C, 5.75f), m("Alt", MouseProtocol.MOD_ALT, 1.25f),
                k("←", 0x50), k("↓", 0x51), k("↑", 0x52), k("→", 0x4F),
            ),
        )
    }
}
