package com.cablemouse.ui

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** A small "pick one" card shown over the current screen (used by the Mode and Protocol menus). */
class Picker(private val a: Ui) {
    /** A per-item on/off option shown as a toggle button under the item. */
    class Option(val label: String, val isOn: () -> Boolean, val toggle: () -> Unit)

    data class Item(val title: String, val subtitle: String, val enabled: Boolean = true, val options: List<Option> = emptyList())

    val view = FrameLayout(a.context).apply {
        visibility = View.GONE
        setBackgroundColor(0x99000000.toInt())
        isClickable = true
        setOnClickListener { hide() }
    }
    val isShown get() = view.visibility == View.VISIBLE

    fun hide() { view.visibility = View.GONE }

    fun show(title: String, items: List<Item>, selected: Int, onPick: (Int) -> Unit) {
        val pal = a.pal
        view.removeAllViews()
        val card = LinearLayout(a.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(a.dp(16), a.dp(14), a.dp(16), a.dp(10))
        }
        card.addView(a.label(title, 18f, pal.text, bold = true))
        items.forEachIndexed { i, item ->
            val on = i == selected
            val row = LinearLayout(a.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(a.dp(12), a.dp(12), a.dp(12), a.dp(12))
                background = if (on) pal.rounded(pal.pressed, a.dpf(14), pal.accent, 2) else pal.pressable(pal.card, pal.pressed, a.dpf(14))
                alpha = if (item.enabled) 1f else 0.45f
                isClickable = item.enabled
                isSoundEffectsEnabled = false
                if (item.enabled) setOnClickListener { hide(); onPick(i) }
            }
            val head = LinearLayout(a.context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            head.addView(View(a.context).apply { background = if (on) pal.oval(pal.accent) else pal.oval(pal.off) },
                LinearLayout.LayoutParams(a.dp(10), a.dp(10)).apply { rightMargin = a.dp(12) })
            val texts = LinearLayout(a.context).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(a.label(item.title, 16f, pal.text, bold = true))
            texts.addView(a.label(item.subtitle, 13f, pal.muted))
            head.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(head)
            if (item.options.isNotEmpty()) {
                val chips = LinearLayout(a.context).apply { orientation = LinearLayout.HORIZONTAL }
                for (opt in item.options) chips.addView(chip(opt), LinearLayout.LayoutParams(-2, -2).apply { rightMargin = a.dp(8) })
                row.addView(chips, LinearLayout.LayoutParams(-2, -2).apply { topMargin = a.dp(10); marginStart = a.dp(22) })
            }
            card.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = a.dp(8) })
        }
        // The card scrolls when it is taller than the screen (landscape); the rounded background clips it.
        val scroll = ScrollView(a.context).apply {
            background = pal.rounded(pal.card, a.dpf(22), pal.stroke, 1)
            clipToOutline = true
            isClickable = true                                   // taps on the card must not dismiss it
            isVerticalScrollBarEnabled = false
            addView(card, FrameLayout.LayoutParams(-1, -2))
        }
        view.addView(scroll, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            marginStart = a.dp(14); marginEnd = a.dp(14); topMargin = a.dp(64); bottomMargin = a.dp(14)
        })
        view.visibility = View.VISIBLE
    }

    /** A pill that flips an option on tap: accent-filled while on, outlined while off (same look as the header pills). */
    private fun chip(opt: Option) = TextView(a.context).apply {
        val pal = a.pal
        text = opt.label
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(a.dp(14), a.dp(8), a.dp(14), a.dp(8))
        isClickable = true
        isSoundEffectsEnabled = false
        fun paint() {
            val on = opt.isOn()
            setTextColor(if (on) pal.onAccent else pal.text)
            background = if (on) pal.rounded(pal.accent, a.dpf(20)) else pal.pressable(pal.card, pal.pressed, a.dpf(20), pal.stroke, 1)
        }
        paint()
        setOnClickListener { opt.toggle(); paint() }
    }
}
