package com.cablemouse.ui

import com.cablemouse.settings.Pref
import com.cablemouse.settings.Settings

/**
 * The Mode and Protocol menus. Each mode lists its on/off options as toggle buttons; tapping one changes the
 * live value for this session only (Settings holds the defaults).
 */
class ModeMenu(
    private val picker: Picker,
    private val settings: Settings,
    private val modes: ModeController,
) {
    /** Which transport is selected. Only [WIRED] exists so far; see [com.cablemouse.link.Transport]. */
    private var protocol = WIRED

    fun showModes() = picker.show(
        "Mode",
        Mode.values().map { Picker.Item(it.title, it.subtitle, options = optionsFor(it)) },
        modes.mode.ordinal,
    ) { modes.switchTo(Mode.values()[it]) }

    fun showProtocols() = picker.show(
        "Protocol",
        listOf(
            Picker.Item("Wired", "USB cable, MIDI (what you use now)"),
            Picker.Item("Bluetooth", "Coming later: the same modes without a cable", enabled = false),
        ),
        protocol,
    ) { protocol = it }

    private fun optionsFor(mode: Mode): List<Picker.Option> = when (mode) {
        Mode.MOUSE -> listOf(option("Invert scroll", settings.invertScroll))
        Mode.MOUSE_TYPING -> listOf(option("Caps = Ctrl", settings.capsAsCtrl))
        Mode.KEYBOARD -> listOf(option("Caps = Ctrl", settings.capsAsCtrl), option("Sticky modifiers", settings.stickyMods))
        Mode.GYRO -> listOf(option("Hold to aim", settings.gyroHold))
        Mode.ABSOLUTE -> listOf(option("Tap to click", settings.absTapClick))
    }

    private fun option(label: String, pref: Pref<Boolean>) = Picker.Option(label, { pref.value }) { pref.value = !pref.value }

    private companion object { const val WIRED = 0 }
}
