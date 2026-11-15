package com.cablemouse.ui

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import com.cablemouse.link.Transport
import com.cablemouse.proto.MouseProtocol

/** The Left / Right mouse buttons: held down on the PC while a finger is on them, with press/release feedback. */
@SuppressLint("ClickableViewAccessibility")
private fun mouseButton(ui: Ui, transport: Transport, feedback: ButtonFeedback, text: String, button: Int) = TextView(ui.context).apply {
    this.text = text
    gravity = Gravity.CENTER
    textSize = 17f
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(ui.pal.text)
    background = ui.pal.pressable(ui.pal.card, ui.pal.pressed, ui.dpf(18), ui.pal.stroke, 1)
    isSoundEffectsEnabled = false
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.isPressed = true
                feedback.press()
                transport.send(MouseProtocol.button(button, true))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.isPressed = false
                feedback.release()
                transport.send(MouseProtocol.button(button, false))
            }
        }
        true
    }
}

/** A row with the Left and Right buttons side by side, [heightDp] tall. */
fun mouseButtonRow(ui: Ui, transport: Transport, feedback: ButtonFeedback, heightDp: Int): LinearLayout =
    LinearLayout(ui.context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(mouseButton(ui, transport, feedback, "Left", MouseProtocol.BTN_LEFT),
            LinearLayout.LayoutParams(0, ui.dp(heightDp), 1f).apply { rightMargin = ui.dp(6) })
        addView(mouseButton(ui, transport, feedback, "Right", MouseProtocol.BTN_RIGHT),
            LinearLayout.LayoutParams(0, ui.dp(heightDp), 1f).apply { leftMargin = ui.dp(6) })
    }
