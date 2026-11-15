package com.cablemouse.proto

/** Wire protocol encoder. Must stay in sync with helper/common/protocol.c. */
object MouseProtocol {
    const val VERSION = 1
    const val BTN_LEFT = 0
    const val BTN_RIGHT = 1
    const val BTN_MIDDLE = 2

    private const val CC_X = 0x01
    private const val CC_Y = 0x02
    private const val CC_SCROLL = 0x03
    private const val CC_HEARTBEAT = 0x7F
    private const val CC_CHAR_HI = 0x10      // code point bits 20..14
    private const val CC_CHAR_MID = 0x11     // bits 13..7
    private const val CC_CHAR_LO = 0x12      // bits 6..0, commits the character
    private const val CC_KEY = 0x13          // special key, value = KEY_*
    private const val CC_MODS = 0x14         // held modifiers, bit mask of MOD_*
    private const val CC_ABS_XH = 0x16       // absolute position: x bits 13..7, x bits 6..0, y bits 13..7, y bits 6..0 (commits)
    private const val CC_ABS_XL = 0x17
    private const val CC_ABS_YH = 0x18
    private const val CC_ABS_YL = 0x19
    const val ABS_MAX = 16383
    private const val CC_HID = 0x15          // tap the key with this USB HID usage (0x04..0x73)

    const val MOD_CTRL = 1
    const val MOD_SHIFT = 2
    const val MOD_ALT = 4
    const val MOD_WIN = 8

    const val KEY_BACKSPACE = 1
    const val KEY_ENTER = 2
    const val KEY_TAB = 3
    const val KEY_ESCAPE = 4
    const val KEY_DELETE = 5
    const val KEY_LEFT = 6
    const val KEY_RIGHT = 7
    const val KEY_UP = 8
    const val KEY_DOWN = 9
    const val KEY_HOME = 10
    const val KEY_END = 11
    const val KEY_PAGE_UP = 12
    const val KEY_PAGE_DOWN = 13

    private fun cc(controller: Int, value: Int) =
        byteArrayOf(0xB0.toByte(), controller.toByte(), value.toByte())

    /** Splits [delta] into messages of -64..63 each, encoded as v = delta + 64. */
    private fun axis(controller: Int, delta: Int): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        var left = delta
        while (left != 0) {
            val step = left.coerceIn(-64, 63)
            out += cc(controller, step + 64)
            left -= step
        }
        return out
    }

    fun moveX(dx: Int) = axis(CC_X, dx)
    fun moveY(dy: Int) = axis(CC_Y, dy)
    fun scroll(clicks: Int) = axis(CC_SCROLL, clicks)

    fun button(button: Int, down: Boolean): ByteArray =
        if (down) byteArrayOf(0x90.toByte(), button.toByte(), 0x7F)
        else byteArrayOf(0x80.toByte(), button.toByte(), 0x00)

    /** One Unicode code point as three messages (9 bytes), meant to be sent in a single write. */
    fun keyChar(codePoint: Int): ByteArray {
        require(codePoint in 1..0x10FFFF)
        return cc(CC_CHAR_HI, (codePoint shr 14) and 0x7F) +
            cc(CC_CHAR_MID, (codePoint shr 7) and 0x7F) +
            cc(CC_CHAR_LO, codePoint and 0x7F)
    }

    /** A special key press (down and up), [key] is one of the KEY_* constants. */
    fun key(key: Int): ByteArray {
        require(key in KEY_BACKSPACE..KEY_PAGE_DOWN)
        return cc(CC_KEY, key)
    }

    /** Sets the set of modifiers the PC holds down (replaces the previous set; 0 releases all). */
    fun mods(mask: Int): ByteArray {
        require(mask in 0..15)
        return cc(CC_MODS, mask)
    }

    /** Taps a physical key given by its USB HID keyboard usage, with whatever [mods] are held. */
    fun keyHid(usage: Int): ByteArray {
        require(usage in 0x04..0x73)
        return cc(CC_HID, usage)
    }

    /** Puts the cursor at a fraction of the primary screen: x, y in 0..[ABS_MAX]. 12 bytes, send in one write. */
    fun moveAbs(x: Int, y: Int): ByteArray {
        val cx = x.coerceIn(0, ABS_MAX); val cy = y.coerceIn(0, ABS_MAX)
        return cc(CC_ABS_XH, cx shr 7) + cc(CC_ABS_XL, cx and 0x7F) + cc(CC_ABS_YH, cy shr 7) + cc(CC_ABS_YL, cy and 0x7F)
    }

    fun heartbeat(): ByteArray = cc(CC_HEARTBEAT, VERSION)
}
