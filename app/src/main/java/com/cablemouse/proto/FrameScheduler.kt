package com.cablemouse.proto

/**
 * Accumulates touch deltas (floats) and emits whole-pixel messages on [flush],
 * keeping fractional remainders so slow movements aren't lost.
 * Call [flush] at ~125 Hz; pure logic, no threading or Android dependencies.
 */
class FrameScheduler(private val send: (ByteArray) -> Unit) {
    private var accX = 0f
    private var accY = 0f
    private var accScroll = 0f

    @Synchronized fun addMove(dx: Float, dy: Float) { accX += dx; accY += dy }
    @Synchronized fun addScroll(d: Float) { accScroll += d }

    @Synchronized fun flush() {
        val ix = accX.toInt(); val iy = accY.toInt(); val isc = accScroll.toInt()
        accX -= ix; accY -= iy; accScroll -= isc
        MouseProtocol.moveX(ix).forEach(send)
        MouseProtocol.moveY(iy).forEach(send)
        MouseProtocol.scroll(isc).forEach(send)
    }
}
