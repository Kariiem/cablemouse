package com.cablemouse.ui

import android.view.View

/** The ways the phone can be used as an input device, in the order the Mode picker lists them. */
enum class Mode(val title: String, val subtitle: String) {
    MOUSE("Mouse", "Touchpad with Left / Right buttons"),
    MOUSE_TYPING("Mouse + typing", "Touchpad plus the phone's own keyboard"),
    KEYBOARD("Keyboard", "Full-screen PC keyboard (landscape)"),
    GYRO("Gyro", "Move the cursor by turning the phone"),
    ABSOLUTE("Absolute", "Touch a spot and the cursor jumps there (landscape)"),
}

/** One full-screen way of using the phone. The [ModeController] shows exactly one at a time. */
interface ModeScreen {
    val view: View

    /** Landscape is forced while this screen is active. */
    val needsLandscape: Boolean get() = false

    /** The display stays on while this screen is active (no touches arrive, e.g. gyro). */
    val keepsScreenOn: Boolean get() = false

    /** The screen became active for [mode] (a screen may serve several modes, e.g. Mouse and Mouse + typing). */
    fun enter(mode: Mode)

    /** The user left the screen. Must release everything this screen holds down on the PC. */
    fun exit()

    /** The app went to the background / came back while this screen is active. */
    fun onPause() {}
    fun onResume() {}
}

/** What the controller needs from the window. */
interface ModeHost {
    fun setLandscape(on: Boolean)
    fun setKeepScreenOn(on: Boolean)
}

/** Switches between the [ModeScreen]s and applies their window needs. Mouse is the start mode and the way back. */
class ModeController(
    private val screens: Map<Mode, ModeScreen>,
    private val host: ModeHost,
) {
    var mode = Mode.MOUSE
        private set

    init {
        val all = screens.values.toSet()
        all.forEach { it.view.visibility = View.GONE }
        current().view.visibility = View.VISIBLE
        current().enter(mode)
    }

    private fun current() = screens.getValue(mode)

    fun switchTo(next: Mode) {
        if (next == mode) return
        val old = current()
        val new = screens.getValue(next)
        old.exit()
        if (old !== new) old.view.visibility = View.GONE
        mode = next
        if (old !== new) new.view.visibility = View.VISIBLE
        host.setLandscape(new.needsLandscape)
        host.setKeepScreenOn(new.keepsScreenOn)
        new.enter(next)
    }

    fun onPause() = current().onPause()
    fun onResume() = current().onResume()
}
