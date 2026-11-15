package com.cablemouse.ui

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.cablemouse.link.Transport
import com.cablemouse.proto.MouseProtocol
import com.cablemouse.settings.Settings
import kotlin.math.hypot
import kotlin.math.min

/**
 * Absolute mode: a pad shaped like the PC screen. Where a finger touches the pad is where the cursor goes on the
 * PC's primary screen (like a drawing tablet). The phone can't know the PC's screen shape, so the pad's aspect
 * ratio is a setting (16:9 by default). Left/Right buttons sit below; an optional quick tap clicks.
 */
class AbsoluteScreen(
    private val ui: Ui,
    private val transport: Transport,
    private val settings: Settings,
    private val feedback: ButtonFeedback,
    private val onExit: () -> Unit,
) : ModeScreen {
    private val pal get() = ui.pal
    private val send: (ByteArray) -> Unit = transport::send

    override val view = LinearLayout(ui.context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(14))
    }
    override val needsLandscape get() = true

    override fun enter(mode: Mode) = rebuild(Settings.ABS_ASPECTS[settings.absAspect.value].second, settings.absTapClick.value)
    override fun exit() { buttons?.release() }
    override fun onPause() { buttons?.release() }

    private var buttons: MouseButtons? = null
    private var tapClick = true
    private var downAt = 0L
    private var startX = 0f
    private var startY = 0f
    private var travel = 0f

    @SuppressLint("ClickableViewAccessibility")
    private fun rebuild(aspect: Float, tapClick: Boolean) {
        this.tapClick = tapClick
        view.removeAllViews()

        val bar = LinearLayout(ui.context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bar.addView(ui.pill("Mouse") { onExit() }, LinearLayout.LayoutParams(-2, -2))
        bar.addView(ui.label("Absolute mode  ·  touch = cursor position" + if (tapClick) "  ·  tap clicks" else "", 13f, pal.muted).apply {
            setPadding(ui.dp(14), 0, 0, 0)
        })
        view.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(8) })

        val area = FrameLayout(ui.context)
        val pad = FrameLayout(ui.context).apply {
            background = pal.rounded(pal.pad, ui.dpf(18), pal.stroke, 1)
            isSoundEffectsEnabled = false
            isHapticFeedbackEnabled = false
            addView(ui.label("Touch to place the cursor", 14f, pal.muted).apply { gravity = Gravity.CENTER; alpha = 0.7f },
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
                    if (tapClick && e.eventTime - downAt < Gesture.TAP_MS && travel < ui.dp(Gesture.TAP_SLOP_DP)) {
                        send(MouseProtocol.button(MouseProtocol.BTN_LEFT, true))
                        send(MouseProtocol.button(MouseProtocol.BTN_LEFT, false))
                    }
                }
            }
            true
        }
        view.addView(area, LinearLayout.LayoutParams(-1, 0, 1f))

        view.addView(MouseButtons(ui, transport, feedback, 60).also { buttons = it }.view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
    }

    private fun place(v: View, e: MotionEvent) {
        if (v.width <= 0 || v.height <= 0) return
        val x = (e.x / v.width * MouseProtocol.ABS_MAX).toInt()
        val y = (e.y / v.height * MouseProtocol.ABS_MAX).toInt()
        send(MouseProtocol.moveAbs(x, y))
    }
}
