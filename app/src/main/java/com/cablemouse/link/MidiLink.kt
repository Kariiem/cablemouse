package com.cablemouse.link

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.os.Handler
import android.os.Looper
import com.cablemouse.proto.FrameScheduler
import com.cablemouse.proto.MouseProtocol
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * The USB-cable [Transport]: writes the protocol to the phone's USB-peripheral MIDI input port, which the PC sees
 * as a MIDI input. Owns the device lifecycle (auto-open, replug), the ~125 Hz motion flush and the 500 ms heartbeat.
 */
class MidiLink(context: Context, private val main: Handler = Handler(Looper.getMainLooper())) : Transport {
    private val manager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager
    private val listeners = CopyOnWriteArrayList<LinkListener>()

    private var device: MidiDevice? = null
    private var deviceId = -1
    private var openingId = -1                 // device whose asynchronous open is in flight (-1 = none)
    @Volatile private var port: MidiInputPort? = null
    @Volatile private var running = false
    @Volatile private var streaming = false

    private val scheduler = FrameScheduler { b -> runCatching { port?.send(b, 0, b.size) } }

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) {
            main.postDelayed({ if (port == null) refresh(autoOpen = true) }, 400)
        }
        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            if (info.id == openingId) openingId = -1
            if (info.id == deviceId) {
                closePort()
                state(false, "Phone disconnected. Reconnect the cable and set USB to MIDI.")
            }
            refresh(autoOpen = false)
        }
    }

    fun addListener(l: LinkListener) { listeners += l }

    val isOpen: Boolean get() = port != null

    // ---- Transport ----

    override fun send(bytes: ByteArray) { runCatching { port?.send(bytes, 0, bytes.size) } }
    override fun addMove(dx: Float, dy: Float) = scheduler.addMove(dx, dy)
    override fun addScroll(clicks: Float) = scheduler.addScroll(clicks)

    // ---- lifecycle ----

    /** Starts watching for the MIDI device, opens it when it appears, and starts the flush/heartbeat loop. */
    fun start() {
        if (running) return
        running = true
        @Suppress("DEPRECATION")
        manager.registerDeviceCallback(deviceCallback, main)
        startSendLoop()
        refresh(autoOpen = true)
    }

    fun stop() {
        running = false
        runCatching { manager.unregisterDeviceCallback(deviceCallback) }
        openingId = -1
        closePort()
    }

    /** Re-reads the device list; with [autoOpen] it opens the USB peripheral port if none is open yet. */
    @Suppress("DEPRECATION")
    fun refresh(autoOpen: Boolean) {
        val infos = manager.devices
        log("--- ${infos.size} MIDI device(s) ---")
        var candidate: MidiDeviceInfo? = null
        val usable = ArrayList<LinkDevice>()
        for (info in infos) {
            val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "MIDI device"
            log("id=${info.id} type=${info.type} in=${info.inputPortCount} out=${info.outputPortCount} \"$name\"")
            if (info.inputPortCount > 0) {
                if (candidate == null && info.type == MidiDeviceInfo.TYPE_USB) candidate = info
                usable += LinkDevice(info.id, name)
            }
        }
        listeners.forEach { it.onLinkDevices(usable) }
        if (port != null) return
        if (candidate == null) state(false, "Not connected. Plug in the cable and set USB mode to MIDI.")
        else if (autoOpen) open(candidate)
    }

    /** Opens the device with this id (one of the last [LinkListener.onLinkDevices]). */
    @Suppress("DEPRECATION")
    fun open(id: Int) {
        manager.devices.firstOrNull { it.id == id }?.let { open(it) }
    }

    /**
     * Opening is asynchronous, and both start-up and onResume ask for it, so a request for the device that is
     * already being opened is dropped, and a result that is no longer wanted (a newer request, or [stop]) is closed.
     */
    private fun open(info: MidiDeviceInfo) {
        if (openingId == info.id) return
        closePort()
        openingId = info.id
        manager.openDevice(info, { dev ->
            val wanted = running && openingId == info.id
            if (openingId == info.id) openingId = -1
            if (dev == null) { log("openDevice failed"); return@openDevice }
            if (!wanted) { dev.close(); return@openDevice }
            val p = dev.openInputPort(0)
            if (p == null) { log("openInputPort(0) returned null (busy?)"); dev.close(); return@openDevice }
            device = dev; deviceId = info.id; port = p
            log("OPENED input port 0 of device ${info.id}")
            state(true, "Connected over USB MIDI. Run the helper on the PC.")
        }, main)
    }

    private fun closePort() {
        streaming = false
        runCatching { port?.close() }; runCatching { device?.close() }
        port = null; device = null; deviceId = -1
    }

    /** Flushes accumulated motion at ~125 Hz and sends a heartbeat every ~500 ms. */
    private fun startSendLoop() {
        thread(isDaemon = true) {
            var tick = 0
            while (running) {
                port?.let { p ->
                    scheduler.flush()
                    if (tick % 63 == 0) runCatching { p.send(MouseProtocol.heartbeat(), 0, 3) }
                }
                tick++
                Thread.sleep(8)
            }
        }
    }

    // ---- diagnostics ----

    fun sendTestNote() {
        val p = port ?: return log("no port open")
        runCatching {
            p.send(byteArrayOf(0x90.toByte(), 0x3C, 0x7F), 0, 3)
            p.send(byteArrayOf(0x80.toByte(), 0x3C, 0x00), 0, 3)
            log("sent note on+off")
        }.onFailure { log("send failed: $it") }
    }

    /**
     * Sends 5000 CC1 messages, one every 2 ms, with the value cycling 1..127. The helper's `--stream-check`
     * verifies that none are lost or repeated. The stream stops early if the port closes.
     */
    fun startStreamTest() {
        val p = port ?: return log("no port open")
        if (streaming) return
        streaming = true
        thread {
            val start = System.nanoTime()
            var n = 0
            val msg = byteArrayOf(0xB0.toByte(), 0x01, 0)
            while (streaming && n < 5000) {
                msg[2] = (1 + n % 127).toByte()
                try { p.send(msg, 0, 3) } catch (e: Exception) { log("send failed: $e"); break }
                n++
                val due = start + n * 2_000_000L
                while (System.nanoTime() < due) { /* busy-wait: sleep() is too coarse to pace 2 ms steps */ }
            }
            streaming = false
            log("sent $n msgs in ${(System.nanoTime() - start) / 1_000_000} ms (expect values cycling 1..127)")
        }
    }

    // ---- events (always delivered on the main thread) ----

    private fun state(connected: Boolean, message: String) {
        main.post { listeners.forEach { it.onLinkState(connected, message) } }
    }

    private fun log(line: String) {
        main.post { listeners.forEach { it.onLinkLog(line) } }
    }
}
