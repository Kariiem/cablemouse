package com.cablemouse.ui

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.cablemouse.proto.MouseProtocol
import kotlin.math.hypot
import kotlin.math.min

/**
 * Absolute mode: a pad shaped like the PC screen. Where a finger touches the pad is where the cursor goes on the
 * PC's primary screen (like a drawing tablet). The phone can't know the PC's screen shape, so the pad's aspect
 * ratio is a setting (16:9 by default). Left/Right buttons sit below; an optional quick tap clicks.
 */
class AbsoluteScreen(
    private val activity: MainActivity,
    private val send: (ByteArray) -> Unit,
    private val onExit: () -> Unit,
) {
    private val pal get() = activity.pal
    val view = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }

    private var tapClick = true
    private var downAt = 0L
    private var startX = 0f
    private var startY = 0f
    private var travel = 0f

    @SuppressLint("ClickableViewAccessibility")
    fun rebuild(aspect: Float, tapClick: Boolean) {
        this.tapClick = tapClick
        view.removeAllViews()

        val bar = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bar.addView(activity.pill("Mouse") { onExit() }, LinearLayout.LayoutParams(-2, -2))
        bar.addView(activity.label("Absolute mode  ·  touch = cursor position" + if (tapClick) "  ·  tap clicks" else "", 13f, pal.muted).apply {
            setPadding(activity.dp(14), 0, 0, 0)
        })
        view.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = activity.dp(8) })

        val area = FrameLayout(activity)
        val pad = FrameLayout(activity).apply {
            background = pal.rounded(pal.pad, activity.dpf(18), pal.stroke, 1)
            isSoundEffectsEnabled = false
            isHapticFeedbackEnabled = false
            addView(activity.label("Touch to place the cursor", 14f, pal.muted).apply { gravity = Gravity.CENTER; alpha = 0.7f },
                FrameLayout.LayoutParams(-1, -1))
        }
        area.addView(pad, FrameLayout.LayoutParams(10, 10, Gravity.CENTER))
        // Fit the biggest rectangle with the chosen aspect ratio into the free area.
        area.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val aw = r - l; val ah = b - t
            if (aw <= 0 || ah <= 0) return@addOnLayoutChangeListener
            val w = min(aw.toFloat(), ah * aspect).toInt()
            val h = (w / aspect).toInt()
            val lp = pad.layoutParams
            if (lp.width != w || lp.height != h) { lp.width = w; lp.height = h; pad.layoutParams = lp }
        }
        pad.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downAt = e.eventTime; startX = e.x; startY = e.y; travel = 0f; place(v, e) }
                MotionEvent.ACTION_MOVE -> {
                    travel = maxOf(travel, hypot(e.x - startX, e.y - startY))
                    place(v, e)
                }
                MotionEvent.ACTION_UP -> {
                    if (tapClick && e.eventTime - downAt < MainActivity.TAP_MS && travel < activity.dp(MainActivity.TAP_SLOP_DP)) {
                        send(MouseProtocol.button(MouseProtocol.BTN_LEFT, true))
                        send(MouseProtocol.button(MouseProtocol.BTN_LEFT, false))
                    }
                }
            }
            true
        }
        view.addView(area, LinearLayout.LayoutParams(-1, 0, 1f))

        val buttons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(activity.mouseButton("Left", MouseProtocol.BTN_LEFT), LinearLayout.LayoutParams(0, activity.dp(60), 1f).apply { rightMargin = activity.dp(6) })
        buttons.addView(activity.mouseButton("Right", MouseProtocol.BTN_RIGHT), LinearLayout.LayoutParams(0, activity.dp(60), 1f).apply { leftMargin = activity.dp(6) })
        view.addView(buttons, LinearLayout.LayoutParams(-1, -2).apply { topMargin = activity.dp(8) })
    }

    private fun place(v: View, e: MotionEvent) {
        if (v.width <= 0 || v.height <= 0) return
        val x = (e.x / v.width * MouseProtocol.ABS_MAX).toInt()
        val y = (e.y / v.height * MouseProtocol.ABS_MAX).toInt()
        send(MouseProtocol.moveAbs(x, y))
    }
}
