package com.cablemouse.ui

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable

/** Light/dark colour set plus small drawable helpers (the UI is built from plain Views). */
class Palette(val dark: Boolean) {
    private fun c(light: Long, dk: Long) = (if (dark) dk else light).toInt()

    val bg = c(0xFFF3F4F8, 0xFF0E1014)
    val card = c(0xFFFFFFFF, 0xFF1A1E27)
    val pad = c(0xFFE4E8F3, 0xFF151922)
    val stroke = c(0xFFD3D8E6, 0xFF2A3040)
    val pressed = c(0xFFD9E0F7, 0xFF2D3550)
    val accent = c(0xFF3B5BDB, 0xFF7C9CFF)
    val onAccent = c(0xFFFFFFFF, 0xFF0B1020)
    val text = c(0xFF12151C, 0xFFE8EBF3)
    val muted = c(0xFF5B6377, 0xFF8E96AA)
    val ok = c(0xFF16A34A, 0xFF4ADE80)
    val off = c(0xFF9AA1B2, 0xFF6B7285)

    fun rounded(color: Int, radius: Float, stroke: Int? = null, strokePx: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius
        if (stroke != null) setStroke(strokePx, stroke)
    }

    fun oval(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    fun pressable(normal: Int, pressed: Int, radius: Float, stroke: Int? = null, strokePx: Int = 0) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), rounded(pressed, radius, stroke, strokePx))
        addState(intArrayOf(), rounded(normal, radius, stroke, strokePx))
    }

    fun accentTint(): ColorStateList = ColorStateList.valueOf(accent)
    fun toggleTint() = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(accent, muted)
    )
}
