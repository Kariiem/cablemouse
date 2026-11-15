package com.cablemouse.ui

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.cablemouse.link.Transport
import com.cablemouse.proto.MouseProtocol
import com.cablemouse.settings.Settings
import kotlin.math.hypot

/**
 * The touchpad surface and its gestures. One finger: move. Tap: left click. Tap, then touch again within
 * [Gesture.DRAG_WINDOW_MS]: the left button is held until the finger lifts (drag); a quick second tap is just a
 * double click. Two fingers: vertical drag scrolls; two-finger tap: right click. Gestures are silent.
 */
class Touchpad(private val ui: Ui, private val transport: Transport, private val settings: Settings) {
    // Gesture state (UI thread only); motion is tracked on the centroid of the touching fingers.
    private var lastCx = 0f
    private var lastCy = 0f
    private var gestureStart = 0L
    private var maxPointers = 0
    private var travel = 0f
    private var fingers = 0
    private var lastTapUp = 0L          // eventTime of the last quick one-finger tap (0 = none)
    private var dragging = false        // left button held by a tap-then-touch drag

    /** Releases a drag in progress (the app paused, a second finger arrived, the gesture was cancelled). */
    fun cancel() {
        if (dragging) { dragging = false; transport.send(MouseProtocol.button(MouseProtocol.BTN_LEFT, false)) }
    }

    private fun click(button: Int) {
        transport.send(MouseProtocol.button(button, true))
        transport.send(MouseProtocol.button(button, false))
    }

    @SuppressLint("ClickableViewAccessibility")
    val view: View = FrameLayout(ui.context).apply {
        background = ui.pal.rounded(ui.pal.pad, ui.dpf(22), ui.pal.stroke, 1)
        isSoundEffectsEnabled = false
        isHapticFeedbackEnabled = false
        addView(ui.label("Drag to move  ·  tap to click\ntwo fingers to scroll or right-click", 14f, ui.pal.muted).apply {
            gravity = Gravity.CENTER; alpha = 0.7f; isClickable = false
        }, FrameLayout.LayoutParams(-1, -1))
        setOnTouchListener { v, e -> onTouch(v, e); true }
    }

    private fun onTouch(v: View, e: MotionEvent) {
        v.parent.requestDisallowInterceptTouchEvent(true)
        val metrics = ui.context.resources.displayMetrics
        val skip = if (e.actionMasked == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1
        var n = 0; var cx = 0f; var cy = 0f
        for (i in 0 until e.pointerCount) if (i != skip) { cx += e.getX(i); cy += e.getY(i); n++ }
        if (n > 0) { cx /= n; cy /= n }

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureStart = e.eventTime; maxPointers = 1; travel = 0f; fingers = 1
                lastCx = cx; lastCy = cy
                if (lastTapUp != 0L && e.eventTime - lastTapUp < Gesture.DRAG_WINDOW_MS) {
                    dragging = true
                    transport.send(MouseProtocol.button(MouseProtocol.BTN_LEFT, true))
                }
                lastTapUp = 0L
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                cancel()
                maxPointers = maxOf(maxPointers, e.pointerCount)
                fingers = n
                lastCx = cx; lastCy = cy      // re-anchor so a finger-count change doesn't jump
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = cx - lastCx; val dy = cy - lastCy
                lastCx = cx; lastCy = cy
                val slop = ui.dp(Gesture.TAP_SLOP_DP)
                val wasStill = travel <= slop
                travel += hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (travel > slop && !wasStill) {          // nothing is sent until the slop is exceeded
                    if (fingers >= 2) {
                        val sign = if (settings.invertScroll.value) -1f else 1f
                        transport.addScroll(sign * dy / metrics.ydpi * settings.scrollSpeed.value)
                    } else {
                        val speed = settings.pointerSpeed.value
                        transport.addMove(dx / metrics.xdpi * speed, dy / metrics.ydpi * speed)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val wasDragging = dragging
                cancel()
                val quick = e.eventTime - gestureStart < Gesture.TAP_MS
                if (quick && travel <= ui.dp(Gesture.TAP_SLOP_DP) && !wasDragging) {
                    // (after a drag the press/release pair already made the second click of a double click)
                    click(if (maxPointers >= 2) MouseProtocol.BTN_RIGHT else MouseProtocol.BTN_LEFT)
                    if (maxPointers < 2) lastTapUp = e.eventTime
                }
            }
            MotionEvent.ACTION_CANCEL -> cancel()
        }
    }
}
