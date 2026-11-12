package com.cablemouse.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Opens the phone's "USB peripheral" MIDI port (available when USB mode is MIDI) and sends raw bytes.
 * UNVERIFIED on hardware: Phase 0 must confirm which device/port exposes the USB-peripheral side.
 */
class MidiLink(context: Context) {
    private val manager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager
    private val handler = Handler(Looper.getMainLooper())
    private var device: MidiDevice? = null
    private var port: MidiInputPort? = null
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val callback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) { if (port == null) open(info) }
        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            if (info.id == device?.info?.id) close()
        }
    }

    fun start() {
        manager.registerDeviceCallback(callback, handler)
        manager.devices.firstOrNull(::isCandidate)?.let(::open)
    }

    fun stop() { manager.unregisterDeviceCallback(callback); close() }

    // Phase 0 spike: refine once we see what the USB peripheral device reports on real phones.
    private fun isCandidate(info: MidiDeviceInfo) =
        info.inputPortCount > 0 && info.type == MidiDeviceInfo.TYPE_USB

    private fun open(info: MidiDeviceInfo) {
        if (!isCandidate(info)) return
        manager.openDevice(info, { dev ->
            if (dev == null) return@openDevice
            val p = dev.openInputPort(0)
            if (p == null) { dev.close(); return@openDevice }
            device = dev; port = p; _connected.value = true
        }, handler)
    }

    private fun close() {
        runCatching { port?.close() }; runCatching { device?.close() }
        port = null; device = null; _connected.value = false
    }

    fun send(bytes: ByteArray) {
        try { port?.send(bytes, 0, bytes.size) } catch (_: Exception) { close() }
    }
}
