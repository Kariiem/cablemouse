package com.cablemouse.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/**
 * The shared widget toolkit: sizes in dp and the handful of controls every screen is built from, all coloured
 * from one [Palette]. Screens take a [Ui] instead of reaching into the activity.
 */
class Ui(val context: Context, val pal: Palette) {
    fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
    fun dpf(v: Int) = v * context.resources.displayMetrics.density

    fun label(t: String, sizeSp: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = t; textSize = sizeSp; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    /** A rounded button. [primary] fills it with the accent colour. */
    fun pill(t: String, primary: Boolean = false, onClick: () -> Unit) = TextView(context).apply {
        text = t
        gravity = Gravity.CENTER
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (primary) pal.onAccent else pal.text)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = if (primary) pal.pressable(pal.accent, pal.accent, dpf(14))
        else pal.pressable(pal.pad, pal.pressed, dpf(14), pal.stroke, 1)
        isClickable = true
        setOnClickListener { onClick() }
    }

    /** A card with an upper-case accent title; [build] adds its content. */
    fun card(title: String, build: LinearLayout.() -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            background = pal.rounded(pal.card, dpf(20), pal.stroke, 1)
            addView(label(title.uppercase(), 12f, pal.accent, bold = true).apply { letterSpacing = 0.08f; setPadding(0, 0, 0, dp(10)) })
            build()
        }

    fun slider(min: Int, max: Int, value: Float, onChange: (Int) -> Unit, onDone: (Int) -> Unit) = SeekBar(context).apply {
        this.max = max - min
        progress = (value.toInt() - min).coerceIn(0, this.max)
        progressTintList = pal.accentTint(); thumbTintList = pal.accentTint()
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { onChange(min + p) }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) { onDone(min + s.progress) }
        })
    }

    fun toggle(t: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(context).apply {
        text = t; isChecked = checked; setTextColor(pal.text); textSize = 15f
        thumbTintList = pal.toggleTint(); trackTintList = pal.toggleTint()
        setOnCheckedChangeListener { _, c -> onChange(c) }
    }

    /** A group of radio buttons; [pad] is left, top, right, bottom in dp. [onSelect] gets the index. */
    fun radios(
        options: List<String>, selected: Int, vertical: Boolean, pad: List<Int>, onSelect: (Int) -> Unit,
    ) = RadioGroup(context).apply {
        orientation = if (vertical) RadioGroup.VERTICAL else RadioGroup.HORIZONTAL
        options.forEachIndexed { i, name ->
            addView(RadioButton(context).apply {
                id = RADIO_ID_BASE + i; text = name; setTextColor(pal.text); buttonTintList = pal.accentTint()
                isChecked = i == selected
                setPadding(dp(pad[0]), dp(pad[1]), dp(pad[2]), dp(pad[3]))
            })
        }
        setOnCheckedChangeListener { _, id -> onSelect(id - RADIO_ID_BASE) }
    }

    private companion object { const val RADIO_ID_BASE = 4000 }
}
