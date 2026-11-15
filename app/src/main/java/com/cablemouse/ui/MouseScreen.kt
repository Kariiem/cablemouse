package com.cablemouse.ui

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.cablemouse.link.LinkListener
import com.cablemouse.link.Transport
import com.cablemouse.proto.MouseProtocol
import com.cablemouse.settings.Settings

/**
 * The start screen, for [Mode.MOUSE] and [Mode.MOUSE_TYPING]: header (settings, Mode and Protocol buttons), the
 * Left/Right buttons, the touchpad, and in typing mode the phone's own keyboard with a row of extra keys.
 */
class MouseScreen(
    private val ui: Ui,
    private val transport: Transport,
    private val settings: Settings,
    feedback: ButtonFeedback,
    private val actions: Actions,
) : ModeScreen, KeySink, LinkListener {

    /** What the header buttons do (the activity owns settings and the pickers). */
    interface Actions {
        fun openSettings()
        fun openModeMenu()
        fun openProtocolMenu()
    }

    private val touchpad = Touchpad(ui, transport, settings)
    private val statusDot = View(ui.context).apply { background = ui.pal.oval(ui.pal.off) }
    private val keyRow: View = buildKeyRow()
    private val keyCapture = KeyCaptureView(ui.context, this) { settings.capsAsCtrl.value }

    override val view: View = FrameLayout(ui.context).apply {
        addView(buildMain(feedback), FrameLayout.LayoutParams(-1, -1))
        addView(keyCapture, FrameLayout.LayoutParams(ui.dp(1), ui.dp(1)))
    }

    // ---- ModeScreen ----

    override fun enter(mode: Mode) { if (mode == Mode.MOUSE_TYPING) setKeyboard(true) }

    override fun exit() {
        touchpad.cancel()
        setKeyboard(false)
    }

    override fun onPause() = touchpad.cancel()

    // ---- LinkListener ----

    override fun onLinkState(connected: Boolean, message: String) {
        (statusDot.background as android.graphics.drawable.GradientDrawable).setColor(if (connected) ui.pal.ok else ui.pal.off)
    }

    // ---- KeySink: what the phone's keyboard produces ----

    override fun onChar(codePoint: Int) = transport.send(MouseProtocol.keyChar(codePoint))
    override fun onKey(key: Int) = transport.send(MouseProtocol.key(key))
    override fun onCombo(mods: Int, hid: Int) =
        transport.send(MouseProtocol.mods(mods) + MouseProtocol.keyHid(hid) + MouseProtocol.mods(0))

    // ---- building ----

    private fun setKeyboard(on: Boolean) {
        keyRow.visibility = if (on) View.VISIBLE else View.GONE
        val imm = ui.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (on) keyCapture.post { keyCapture.requestFocus(); imm.showSoftInput(keyCapture, 0) }
        else imm.hideSoftInputFromWindow(keyCapture.windowToken, 0)
    }

    private fun buildMain(feedback: ButtonFeedback): View {
        val main = LinearLayout(ui.context).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(14)) }
        main.addView(buildHeader(), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(12) })
        main.addView(mouseButtonRow(ui, transport, feedback, 68))
        main.addView(keyRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
        main.addView(touchpad.view, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = ui.dp(12) })
        return main
    }

    private fun buildHeader(): View {
        // The title pill (with the connection dot) opens settings.
        val title = LinearLayout(ui.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
            background = ui.pal.pressable(ui.pal.card, ui.pal.pressed, ui.dpf(24), ui.pal.stroke, 1)
            isClickable = true
            isSoundEffectsEnabled = false
            setOnClickListener { actions.openSettings() }
        }
        title.addView(statusDot, LinearLayout.LayoutParams(ui.dp(9), ui.dp(9)).apply { rightMargin = ui.dp(10) })
        title.addView(ui.label("Cable Mouse", 15f, ui.pal.text, bold = true).apply { maxLines = 1 })

        fun headerPill(t: String, onClick: () -> Unit) = TextView(ui.context).apply {
            text = t
            gravity = Gravity.CENTER
            maxLines = 1
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ui.pal.text)
            setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(10))
            background = ui.pal.pressable(ui.pal.card, ui.pal.pressed, ui.dpf(24), ui.pal.stroke, 1)
            isClickable = true
            isSoundEffectsEnabled = false
            setOnClickListener { onClick() }
        }
        return LinearLayout(ui.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(title)
            addView(headerPill("Mode") { actions.openModeMenu() }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = ui.dp(8) })
            addView(headerPill("Protocol") { actions.openProtocolMenu() }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = ui.dp(8) })
        }
    }

    /** Keys the phone's keyboard lacks; only shown in typing mode. */
    private fun buildKeyRow(): View = LinearLayout(ui.context).apply {
        orientation = LinearLayout.HORIZONTAL
        visibility = View.GONE
        for ((label, key) in listOf(
            "Esc" to MouseProtocol.KEY_ESCAPE, "Tab" to MouseProtocol.KEY_TAB, "Del" to MouseProtocol.KEY_DELETE,
            "Home" to MouseProtocol.KEY_HOME, "End" to MouseProtocol.KEY_END,
            "←" to MouseProtocol.KEY_LEFT, "↑" to MouseProtocol.KEY_UP, "↓" to MouseProtocol.KEY_DOWN, "→" to MouseProtocol.KEY_RIGHT,
        )) {
            addView(TextView(ui.context).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 11f
                maxLines = 1
                setPadding(0, 0, 0, 0)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ui.pal.text)
                background = ui.pal.pressable(ui.pal.card, ui.pal.pressed, ui.dpf(12), ui.pal.stroke, 1)
                isSoundEffectsEnabled = false
                setOnClickListener { onKey(key) }
            }, LinearLayout.LayoutParams(0, ui.dp(44), 1f).apply { marginStart = ui.dp(1); marginEnd = ui.dp(1) })
        }
    }
}
