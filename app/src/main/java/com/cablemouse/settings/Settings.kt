package com.cablemouse.settings

import android.content.SharedPreferences

/**
 * One setting with two values: the live [value] the app is using right now, and the saved default that is
 * loaded at start-up. Changing [value] affects only this session; [save] makes it the new default. This is
 * what lets the Mode picker flip an option temporarily while Settings changes the defaults.
 */
class Pref<T> internal constructor(
    private val prefs: SharedPreferences,
    val key: String,
    initial: T,
    private val write: SharedPreferences.Editor.(String, T) -> Unit,
) {
    @Volatile var value: T = initial

    fun save() {
        val e = prefs.edit()
        e.write(key, value)
        e.apply()
    }
}

/** Every user setting, typed. Screens read and write [Pref]s; nothing else touches SharedPreferences. */
class Settings(private val prefs: SharedPreferences) {
    private fun bool(key: String, default: Boolean) = Pref(prefs, key, prefs.getBoolean(key, default)) { k, v -> putBoolean(k, v) }
    private fun int(key: String, default: Int) = Pref(prefs, key, prefs.getInt(key, default)) { k, v -> putInt(k, v) }
    private fun float(key: String, default: Float) = Pref(prefs, key, prefs.getFloat(key, default)) { k, v -> putFloat(k, v) }

    // Touchpad
    val pointerSpeed = float("pxPerInch", POINTER_DEFAULT)            // cursor px per inch of finger travel
    val scrollSpeed = float("scrollPerInch", SCROLL_DEFAULT)          // wheel clicks per inch
    val invertScroll = bool("invertScroll", false)

    // Left/Right button feedback. Older versions stored a boolean "sound" setting; honour it until the new one is chosen.
    val clickFeedback = Pref(
        prefs, "clickFeedback",
        if (prefs.contains("clickFeedback")) prefs.getInt("clickFeedback", FEEDBACK_AUDIBLE)
        else if (prefs.getBoolean("sound", true)) FEEDBACK_AUDIBLE else FEEDBACK_SILENT
    ) { k, v -> putInt(k, v) }

    // Modes
    val gyroHold = bool("gyroHold", true)
    val gyroSpeed = float("gyroPer10Deg", GYRO_DEFAULT)               // cursor px per 10 degrees of rotation
    val absAspect = Pref(prefs, "absAspect", prefs.getInt("absAspect", 0).coerceIn(0, ABS_ASPECTS.size - 1)) { k, v -> putInt(k, v) }
    val absTapClick = bool("absTapClick", true)
    val capsAsCtrl = bool("capsCtrl", false)
    val stickyMods = bool("stickyMods", false)

    // App
    val theme = int("theme", THEME_SYSTEM)
    val onboarded = bool("onboarded", false)

    companion object {
        const val POINTER_DEFAULT = 700f
        const val POINTER_MIN = 100
        const val POINTER_MAX = 2500
        const val SCROLL_DEFAULT = 6f
        const val SCROLL_MIN = 1
        const val SCROLL_MAX = 20
        const val GYRO_DEFAULT = 260f
        const val GYRO_MIN = 60
        const val GYRO_MAX = 800

        const val FEEDBACK_SILENT = 0
        const val FEEDBACK_AUDIBLE = 1
        const val FEEDBACK_VIBRATE = 2

        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        /** Pad shapes offered for Absolute mode: the PC screen's width / height. */
        val ABS_ASPECTS = listOf("16:9" to 16f / 9f, "16:10" to 1.6f, "4:3" to 4f / 3f, "21:9" to 21f / 9f)
    }
}
