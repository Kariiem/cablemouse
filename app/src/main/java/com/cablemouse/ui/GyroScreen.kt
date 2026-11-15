package com.cablemouse.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.cablemouse.link.Transport
import com.cablemouse.proto.GyroMath
import com.cablemouse.proto.MouseProtocol
import com.cablemouse.settings.Settings

/**
 * Gyro mode: the cursor follows the phone's rotation. A big round button sits in the middle.
 * - hold-to-aim on:  the cursor only follows while the button is held (Left/Right buttons click).
 * - hold-to-aim off: the cursor always follows; the round button is a left-click button (hold it to drag).
 */
class GyroScreen(
    private val ui: Ui,
    private val transport: Transport,
    private val settings: Settings,
    private val feedback: ButtonFeedback,
    private val onExit: () -> Unit,
) : ModeScreen, SensorEventListener {
    private val pal get() = ui.pal
    private val send: (ByteArray) -> Unit = transport::send
    private val move: (Float, Float) -> Unit = transport::addMove
    private val sm = ui.context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyro: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val gravity: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val accel: Sensor? = if (gravity == null) sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null

    override val view = LinearLayout(ui.context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(14))
    }
    override val keepsScreenOn get() = true
    val available get() = gyro != null && (gravity != null || accel != null)

    private var holdToAim = true
    private var pxPerRad = 1500f
    @Volatile private var aiming = false
    private var lastT = 0L
    private val up = floatArrayOf(0f, 0f, 9.81f)
    private var clicking = false

    override fun enter(mode: Mode) {
        rebuild(settings.gyroHold.value, settings.gyroSpeed.value)
        start()
    }

    override fun exit() = stop()
    override fun onPause() = stop()
    override fun onResume() = start()

    private fun rebuild(holdToAim: Boolean, pxPer10Deg: Float) {
        stop()
        this.holdToAim = holdToAim
        this.pxPerRad = pxPer10Deg / GyroMath.RAD_PER_10_DEG
        view.removeAllViews()

        val bar = LinearLayout(ui.context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bar.addView(ui.pill("Mouse") { onExit() }, LinearLayout.LayoutParams(-2, -2))
        bar.addView(ui.label(if (holdToAim) "Gyro mode  ·  hold the circle to aim" else "Gyro mode  ·  always aiming", 13f, pal.muted).apply {
            setPadding(ui.dp(14), 0, 0, 0)
        })
        view.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(12) })

        view.addView(mouseButtonRow(ui, transport, feedback, 68))

        val area = FrameLayout(ui.context)
        if (!available) {
            area.addView(ui.label("This phone has no gyroscope sensor.", 15f, pal.muted).apply { gravity = Gravity.CENTER },
                FrameLayout.LayoutParams(-1, -1))
        } else {
            val size = ui.dp(230)
            area.addView(circle(), FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        }
        view.addView(area, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun circle() = TextView(ui.context).apply {
        text = if (holdToAim) "Hold\nto aim" else "Click"
        gravity = Gravity.CENTER
        textSize = 20f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(pal.text)
        isSoundEffectsEnabled = false
        isHapticFeedbackEnabled = false
        background = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pal.oval(pal.accent))
            addState(intArrayOf(), pal.oval(pal.card).also { (it as android.graphics.drawable.GradientDrawable).setStroke(ui.dp(2), pal.stroke) })
        }
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true; (v as TextView).setTextColor(pal.onAccent)
                    if (holdToAim) { lastT = 0; aiming = true }
                    else { clicking = true; send(MouseProtocol.button(MouseProtocol.BTN_LEFT, true)); feedback.press() }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false; (v as TextView).setTextColor(pal.text)
                    if (holdToAim) aiming = false
                    else if (clicking) { clicking = false; send(MouseProtocol.button(MouseProtocol.BTN_LEFT, false)); feedback.release() }
                }
            }
            true
        }
    }

    private fun start() {
        if (!available) return
        aiming = !holdToAim
        lastT = 0
        sm.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME)
        (gravity ?: accel)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    private fun stop() {
        sm.unregisterListener(this)
        aiming = false
        if (clicking) { clicking = false; send(MouseProtocol.button(MouseProtocol.BTN_LEFT, false)) }
    }

    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_GRAVITY -> { up[0] = e.values[0]; up[1] = e.values[1]; up[2] = e.values[2] }
            Sensor.TYPE_ACCELEROMETER -> for (i in 0..2) up[i] = 0.9f * up[i] + 0.1f * e.values[i]   // low-pass = gravity
            Sensor.TYPE_GYROSCOPE -> {
                if (!aiming) { lastT = 0; return }
                val t = e.timestamp
                if (lastT == 0L) { lastT = t; return }
                val dt = (t - lastT) * 1e-9f
                lastT = t
                if (dt <= 0f || dt > 0.2f) return
                GyroMath.delta(e.values, up, dt, pxPerRad)?.let { (dx, dy) -> if (dx != 0f || dy != 0f) move(dx, dy) }
            }
        }
    }
}
