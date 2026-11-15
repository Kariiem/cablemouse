package com.cablemouse.ui

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import com.cablemouse.link.Transport
import com.cablemouse.proto.MouseProtocol

/**
 * A row with the Left and Right mouse buttons side by side, [heightDp] tall. A button is held down on the PC
 * while a finger is on it, with press/release feedback.
 */
class MouseButtons(private val ui: Ui, private val transport: Transport, private val feedback: ButtonFeedback, heightDp: Int) {
    private val held = HashMap<Int, TextView>()        // protocol button -> its view, while a finger is on it

    val view = LinearLayout(ui.context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(button("Left", MouseProtocol.BTN_LEFT), LinearLayout.LayoutParams(0, ui.dp(heightDp), 1f).apply { rightMargin = ui.dp(6) })
        addView(button("Right", MouseProtocol.BTN_RIGHT), LinearLayout.LayoutParams(0, ui.dp(heightDp), 1f).apply { leftMargin = ui.dp(6) })
    }

    /** Lets go of a button that is still held (its screen is being left, or the app paused, mid-press). */
    fun release() {
        for ((button, v) in held) { v.isPressed = false; transport.send(MouseProtocol.button(button, false)) }
        held.clear()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun button(text: String, button: Int) = TextView(ui.context).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(ui.pal.text)
        background = ui.pal.pressable(ui.pal.card, ui.pal.pressed, ui.dpf(18), ui.pal.stroke, 1)
        isSoundEffectsEnabled = false
        setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isPressed = true
                    held[button] = this
                    feedback.press()
                    transport.send(MouseProtocol.button(button, true))
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (held.remove(button) != null) {
                    isPressed = false
                    feedback.release()
                    transport.send(MouseProtocol.button(button, false))
                }
            }
            true
        }
    }
}
