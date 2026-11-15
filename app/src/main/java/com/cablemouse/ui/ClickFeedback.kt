package com.cablemouse.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.cablemouse.settings.Settings

/** What the Left/Right buttons do when pressed and released. */
interface ButtonFeedback {
    fun press()
    fun release()
}

/** Silent, the synthesised click, or a short vibration, as chosen in [Settings.clickFeedback] (read live). */
class ClickFeedback(context: Context, private val settings: Settings) : ButtonFeedback {
    private val sound = ClickSound()
    @Suppress("DEPRECATION")
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    override fun press() = play(down = true)
    override fun release() = play(down = false)

    fun close() = sound.release()

    private fun play(down: Boolean) {
        when (settings.clickFeedback.value) {
            Settings.FEEDBACK_AUDIBLE -> if (down) sound.playDown() else sound.playUp()
            Settings.FEEDBACK_VIBRATE -> vibrator?.takeIf { it.hasVibrator() }?.let { vibrate(it, down) }
        }
    }

    /** A firm pulse on press and a lighter one on release (the stock CLICK effect is faint on many phones). */
    private fun vibrate(v: Vibrator, down: Boolean) {
        if (Build.VERSION.SDK_INT >= 26) {
            val strong = v.hasAmplitudeControl()
            val effect = when {
                down && strong -> VibrationEffect.createOneShot(30L, 255)
                down && Build.VERSION.SDK_INT >= 29 -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                down -> VibrationEffect.createOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE)
                strong -> VibrationEffect.createOneShot(12L, 140)
                Build.VERSION.SDK_INT >= 29 -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                else -> VibrationEffect.createOneShot(12L, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            v.vibrate(effect)
        } else {
            @Suppress("DEPRECATION") v.vibrate(if (down) 30L else 12L)
        }
    }
}
