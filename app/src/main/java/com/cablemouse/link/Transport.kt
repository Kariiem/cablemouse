package com.cablemouse.link

/**
 * How the phone talks to the PC. The screens depend only on this interface, so a second transport (Bluetooth,
 * see the Protocol button) can be added without touching any of them. [MidiLink] is the USB-cable implementation.
 */
interface Transport {
    /** Sends already-encoded protocol bytes in one write, in order. Never throws; drops the bytes if not connected. */
    fun send(bytes: ByteArray)

    /** Queues relative pointer motion in pixels. Fractions are kept and sent in ~125 Hz batches. */
    fun addMove(dx: Float, dy: Float)

    /** Queues wheel motion in clicks (fractions are kept). */
    fun addScroll(clicks: Float)
}

/** One MIDI output the phone could use to reach the PC. */
data class LinkDevice(val id: Int, val name: String)

/** Link events. Always delivered on the main thread; every method is optional. */
interface LinkListener {
    fun onLinkState(connected: Boolean, message: String) {}
    fun onLinkDevices(devices: List<LinkDevice>) {}
    fun onLinkLog(line: String) {}
}
