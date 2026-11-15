package com.cablemouse.ui

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.cablemouse.link.LinkDevice
import com.cablemouse.link.LinkListener
import com.cablemouse.link.MidiLink
import com.cablemouse.settings.Settings

/**
 * The settings overlay. Every switch and slider here saves the *default* that is loaded at start-up (the
 * Mode menu flips the same options for the current session only); the Connection card shows the link state.
 */
class SettingsScreen(
    private val ui: Ui,
    private val settings: Settings,
    private val link: MidiLink,
    private val feedback: ButtonFeedback,
    private val actions: Actions,
) : LinkListener {

    interface Actions {
        fun closeSettings()
        fun showSetupGuide()
        fun themeChanged()
    }

    private val pal get() = ui.pal
    private val statusText = ui.label("Not connected", 15f, ui.pal.text)
    private val devicesBox = LinearLayout(ui.context).apply { orientation = LinearLayout.VERTICAL }
    private val log = ui.label("", 12f, ui.pal.muted).apply {
        typeface = Typeface.MONOSPACE; setPadding(0, ui.dp(10), 0, 0); setTextIsSelectable(true)
    }

    val view: View = build()
    val isOpen get() = view.visibility == View.VISIBLE

    fun show(open: Boolean) { view.visibility = if (open) View.VISIBLE else View.GONE }

    // ---- LinkListener ----

    override fun onLinkState(connected: Boolean, message: String) { statusText.text = message }

    override fun onLinkDevices(devices: List<LinkDevice>) {
        devicesBox.removeAllViews()
        for (d in devices) {
            devicesBox.addView(ui.pill("Use: ${d.name} (id ${d.id})") { link.open(d.id) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
        }
    }

    override fun onLinkLog(line: String) {
        val t = log.text.toString() + line + "\n"
        log.text = if (t.length > 6000) t.takeLast(5000) else t
    }

    // ---- building ----

    private fun build(): View {
        val col = LinearLayout(ui.context).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dp(14), ui.dp(6), ui.dp(14), ui.dp(24)) }
        fun add(v: View) = col.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(12) })

        val top = LinearLayout(ui.context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(ui.label("←", 26f, pal.accent, bold = true).apply {
            setPadding(ui.dp(12), ui.dp(8), ui.dp(20), ui.dp(8)); isClickable = true; setOnClickListener { actions.closeSettings() }
        })
        top.addView(ui.label("Settings", 22f, pal.text, bold = true))
        col.addView(top)
        add(ui.label("The switches here are the start-up defaults. The Mode button can flip them for the current session.", 13f, pal.muted))

        add(connectionCard())
        add(touchpadCard())
        add(feedbackCard())
        add(gyroCard())
        add(absoluteCard())
        add(keyboardCard())
        add(appearanceCard())
        add(diagnosticsCard())

        return ScrollView(ui.context).apply {
            setBackgroundColor(pal.bg)
            isClickable = true                  // swallow touches so the screen below never sees them
            addView(col)
        }
    }

    private fun connectionCard() = ui.card("Connection") {
        addView(statusText)
        addView(devicesBox)
        addView(ui.pill("Refresh devices") { link.refresh(autoOpen = true) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
        addView(ui.pill("Setup guide") { actions.showSetupGuide() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
    }

    private fun touchpadCard() = ui.card("Touchpad") {
        val speed = ui.label("", 14f, pal.muted)
        fun showSpeed() { speed.text = "Pointer speed: ${settings.pointerSpeed.value.toInt()} px per inch of finger travel" }
        showSpeed()
        addView(speed)
        addView(ui.slider(Settings.POINTER_MIN, Settings.POINTER_MAX, settings.pointerSpeed.value,
            { settings.pointerSpeed.value = it.toFloat(); showSpeed() }, { settings.pointerSpeed.save() }))

        val scroll = ui.label("", 14f, pal.muted).apply { setPadding(0, ui.dp(12), 0, 0) }
        fun showScroll() { scroll.text = "Scroll speed: ${settings.scrollSpeed.value.toInt()} wheel clicks per inch" }
        showScroll()
        addView(scroll)
        addView(ui.slider(Settings.SCROLL_MIN, Settings.SCROLL_MAX, settings.scrollSpeed.value,
            { settings.scrollSpeed.value = it.toFloat(); showScroll() }, { settings.scrollSpeed.save() }))

        addView(switch("Invert scroll direction", settings.invertScroll), LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(14) })
    }

    private fun feedbackCard() = ui.card("Click feedback (Left / Right buttons)") {
        addView(ui.radios(
            listOf("Silent", "Audible clicks", "Vibration (physical click effect)"),
            settings.clickFeedback.value, vertical = true, pad = listOf(4, 8, 16, 8),
        ) {
            settings.clickFeedback.value = it; settings.clickFeedback.save()
            feedback.press()                                  // let the user feel / hear the choice
        })
    }

    private fun gyroCard() = ui.card("Gyro mode") {
        addView(switch("Hold the center button to aim (off: the cursor always follows the phone and the button clicks)", settings.gyroHold))
        val speed = ui.label("", 14f, pal.muted).apply { setPadding(0, ui.dp(12), 0, 0) }
        fun showSpeed() { speed.text = "Gyro speed: ${settings.gyroSpeed.value.toInt()} px per 10° of rotation" }
        showSpeed()
        addView(speed)
        addView(ui.slider(Settings.GYRO_MIN, Settings.GYRO_MAX, settings.gyroSpeed.value,
            { settings.gyroSpeed.value = it.toFloat(); showSpeed() }, { settings.gyroSpeed.save() }))
    }

    private fun absoluteCard() = ui.card("Absolute mode") {
        addView(ui.label("Shape of the PC screen (the pad is drawn in this shape):", 14f, pal.muted))
        addView(ui.radios(Settings.ABS_ASPECTS.map { it.first }, settings.absAspect.value, vertical = false, pad = listOf(2, 6, 10, 6)) {
            settings.absAspect.value = it; settings.absAspect.save()
        })
        addView(switch("Tap the pad to click", settings.absTapClick), LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
    }

    private fun keyboardCard() = ui.card("Keyboard") {
        addView(switch("Caps Lock acts as Ctrl (keyboard mode, and a hardware keyboard in typing mode)", settings.capsAsCtrl))
        addView(switch("Sticky modifiers: tap Shift / Ctrl / Alt / Win to keep it on for the next key (keyboard mode)", settings.stickyMods),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(14) })
    }

    private fun appearanceCard() = ui.card("Appearance") {
        addView(ui.radios(listOf("System", "Light", "Dark"), settings.theme.value, vertical = false, pad = listOf(4, 4, 16, 4)) {
            if (it != settings.theme.value) { settings.theme.value = it; settings.theme.save(); actions.themeChanged() }
        })
    }

    private fun diagnosticsCard() = ui.card("Diagnostics") {
        val row = LinearLayout(ui.context).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(ui.pill("Test note") { link.sendTestNote() }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = ui.dp(6) })
        row.addView(ui.pill("Stream test") { link.startStreamTest() }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = ui.dp(6); leftMargin = ui.dp(6) })
        row.addView(ui.pill("Clear log") { log.text = "" }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = ui.dp(6) })
        addView(row)
        addView(log)
    }

    /** A switch bound to a boolean setting: flipping it changes the live value and saves it as the default. */
    private fun switch(text: String, pref: com.cablemouse.settings.Pref<Boolean>) =
        ui.toggle(text, pref.value) { pref.value = it; pref.save() }
}
