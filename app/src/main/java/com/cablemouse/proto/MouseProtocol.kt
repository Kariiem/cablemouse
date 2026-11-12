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

    fun heartbeat(): ByteArray = cc(CC_HEARTBEAT, VERSION)
}
