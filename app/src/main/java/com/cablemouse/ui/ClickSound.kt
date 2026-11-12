package com.cablemouse.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build

/**
 * Synthesised mouse-button clicks (no asset files): a short noisy "tick" for press, a softer one
 * for release. Only the physical Left/Right buttons use this; touchpad touches and gestures stay silent.
 */
class ClickSound {
    private val rate = 44100
    private val down = build(freq = 2100.0, ms = 16, gain = 0.9, noise = 0.55)
    private val up = build(freq = 1500.0, ms = 11, gain = 0.5, noise = 0.45)

    private fun build(freq: Double, ms: Int, gain: Double, noise: Double): AudioTrack? = try {
        val n = rate * ms / 1000
        val rnd = java.util.Random(7)
        val pcm = ShortArray(n) { i ->
            val t = i / rate.toDouble()
            val env = Math.exp(-t / (ms / 1000.0 * 0.3)) * minOf(1.0, t / 0.0003)   // fast decay, tiny attack ramp
            val v = (Math.sin(2 * Math.PI * freq * t) * (1 - noise) + (rnd.nextDouble() * 2 - 1) * noise) * env * gain
            (v * Short.MAX_VALUE).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(n * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .apply { if (Build.VERSION.SDK_INT >= 26) setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) }
            .build()
        track.write(pcm, 0, n)
        track
    } catch (_: Exception) { null }

    private fun play(t: AudioTrack?) {
        try { t?.stop(); t?.setPlaybackHeadPosition(0); t?.play() } catch (_: Exception) {}
    }

    fun playDown() = play(down)
    fun playUp() = play(up)
    fun release() { runCatching { down?.release() }; runCatching { up?.release() } }
}
