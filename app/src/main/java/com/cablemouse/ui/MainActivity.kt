package com.cablemouse.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Configuration
import android.graphics.Typeface
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.cablemouse.export.HelperExporter
import com.cablemouse.proto.FrameScheduler
import com.cablemouse.proto.MouseProtocol
import kotlin.concurrent.thread

/**
 * Main screen: a centred "Cable Mouse" pill (opens settings), Left/Right buttons, then the touchpad
 * filling the rest. Everything else lives in the settings overlay (Back closes it).
 */
class MainActivity : Activity(), KeySink {
    private lateinit var manager: MidiManager
    internal lateinit var pal: Palette
    private lateinit var sound: ClickSound
    private lateinit var settings: View
    private lateinit var onboarding: View
    internal lateinit var helperStatus: TextView
    internal var exportMessage = "Not copied yet."
    private lateinit var devicesBox: LinearLayout
    private lateinit var log: TextView
    private lateinit var statusText: TextView
    private lateinit var headerDot: View
    private lateinit var keyCapture: KeyCaptureView
    private lateinit var keyRow: View
    private lateinit var mainScreen: View
    private lateinit var keys: KeysScreen
    private var capsAsCtrl = false
    private var stickyMods = false
    private lateinit var keyboardPill: TextView
    private var keyboardOn = false
    private var imeWasVisible = false
    private val ui = Handler(Looper.getMainLooper())

    private var device: MidiDevice? = null
    private var deviceId = -1
    @Volatile private var port: MidiInputPort? = null
    @Volatile private var streaming = false
    @Volatile private var running = true

    // Settings (persisted)
    @Volatile private var pxPerInch = DEFAULT_PX_PER_INCH            // cursor px per inch of finger travel
    @Volatile private var scrollClicksPerInch = DEFAULT_SCROLL
    @Volatile private var invertScroll = false
    private var soundOn = true
    private var themeMode = THEME_SYSTEM

    private val scheduler = FrameScheduler { b -> runCatching { port?.send(b, 0, b.size) } }

    // Gesture state (UI thread only); motion is tracked on the centroid of the touching fingers.
    private var lastCx = 0f
    private var lastCy = 0f
    private var gestureStart = 0L
    private var maxPointers = 0
    private var travel = 0f
    private var fingers = 0
    private var lastTapUp = 0L          // eventTime of the last quick one-finger tap (0 = none)
    private var dragging = false        // left button held by a tap-then-touch drag

    private fun endDrag() {
        if (dragging) { dragging = false; sendBytes(MouseProtocol.button(MouseProtocol.BTN_LEFT, false)) }
    }

    internal fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    internal fun dpf(v: Int) = v * resources.displayMetrics.density
    private fun prefs() = getSharedPreferences("cablemouse", MODE_PRIVATE)

    private val midiCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) {
            ui.postDelayed({ if (port == null) refresh(autoOpen = true) }, 400)
        }
        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            if (info.id == deviceId) {
                closePort()
                setConnected(false, "Phone disconnected. Reconnect the cable and set USB to MIDI.")
            }
            refresh(autoOpen = false)
        }
    }

    // ---------- lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReport.install(this)
        CrashReport.pending(this)?.let { setContentView(CrashReport.screen(this, it)); return }
        manager = getSystemService(MIDI_SERVICE) as MidiManager
        val p = prefs()
        pxPerInch = p.getFloat("pxPerInch", DEFAULT_PX_PER_INCH)
        scrollClicksPerInch = p.getFloat("scrollPerInch", DEFAULT_SCROLL)
        invertScroll = p.getBoolean("invertScroll", false)
        soundOn = p.getBoolean("sound", true)
        capsAsCtrl = p.getBoolean("capsCtrl", false)
        stickyMods = p.getBoolean("stickyMods", false)
        themeMode = p.getInt("theme", THEME_SYSTEM)

        val dark = when (themeMode) {
            THEME_LIGHT -> false
            THEME_DARK -> true
            else -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        pal = Palette(dark)
        sound = ClickSound()

        val root = FrameLayout(this).apply { setBackgroundColor(pal.bg) }
        mainScreen = buildMain()
        root.addView(mainScreen, FrameLayout.LayoutParams(-1, -1))
        keys = KeysScreen(this, { sendBytes(it) }, { showKeys(false) })
        keys.view.setPadding(dp(10), dp(8), dp(10), dp(8))
        root.addView(keys.view, FrameLayout.LayoutParams(-1, -1))
        keyCapture = KeyCaptureView(this, this) { capsAsCtrl }
        root.addView(keyCapture, FrameLayout.LayoutParams(dp(1), dp(1)))
        settings = buildSettings().apply {
            visibility = if (savedInstanceState?.getBoolean("settings") == true) View.VISIBLE else View.GONE
        }
        root.addView(settings, FrameLayout.LayoutParams(-1, -1))
        onboarding = buildOnboarding(onDone = { prefs().edit().putBoolean("onboarded", true).apply(); onboarding.visibility = View.GONE },
            onCopyAgain = { exportHelpers(force = true) }).apply {
            visibility = if (prefs().getBoolean("onboarded", false)) View.GONE else View.VISIBLE
        }
        root.addView(onboarding, FrameLayout.LayoutParams(-1, -1))
        // targetSdk 35 is edge-to-edge: keep content out from under the status/nav bars and cutout.
        // The on-screen keyboard is an inset too, so the pad shrinks above it instead of being covered.
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime()
                )
                v.setPadding(i.left, i.top, i.right, i.bottom)
                val imeVisible = insets.isVisible(WindowInsets.Type.ime())
                if (imeWasVisible && !imeVisible && keyboardOn) setKeyboard(false)   // user dismissed it (back gesture)
                imeWasVisible = imeVisible
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                )
            }
            insets
        }
        setContentView(root)
        // Must come after setContentView: the window insets controller doesn't exist before the decor view does.
        runCatching { applySystemBars(dark) }

        @Suppress("DEPRECATION")
        manager.registerDeviceCallback(midiCallback, ui)
        startSendLoop()
        refresh(autoOpen = true)
        exportHelpers(force = false)
    }

    override fun onResume() { super.onResume(); if (::settings.isInitialized && port == null) refresh(autoOpen = true) }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::settings.isInitialized) outState.putBoolean("settings", settings.visibility == View.VISIBLE)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        when {
            ::onboarding.isInitialized && onboarding.visibility == View.VISIBLE -> onboarding.visibility = View.GONE
            ::keys.isInitialized && keys.view.visibility == View.VISIBLE && !settingsOpen() -> showKeys(false)
            ::settings.isInitialized && settings.visibility == View.VISIBLE -> showSettings(false)
            else -> super.onBackPressed()
        }
    }

    override fun onPause() { endDrag(); if (::keys.isInitialized) keys.releaseAll(); super.onPause() }

    override fun onDestroy() {
        running = false
        if (::manager.isInitialized) runCatching { manager.unregisterDeviceCallback(midiCallback) }
        closePort()
        if (::sound.isInitialized) sound.release()
        super.onDestroy()
    }

    private fun applySystemBars(dark: Boolean) {
        if (Build.VERSION.SDK_INT >= 30) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (dark) 0 else mask, mask)
        } else {
            @Suppress("DEPRECATION")
            var f = window.decorView.systemUiVisibility
            val light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
            f = if (dark) f and light.inv() else f or light
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = f
        }
    }

    // ---------- main screen ----------

    private fun buildMain(): View {
        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(14)) }

        // Centred title pill; tapping it opens settings.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = pal.pressable(pal.card, pal.pressed, dpf(24), pal.stroke, 1)
            isClickable = true
            isSoundEffectsEnabled = false
            setOnClickListener { showSettings(true) }
        }
        headerDot = View(this).apply { background = pal.oval(pal.off) }
        header.addView(headerDot, LinearLayout.LayoutParams(dp(9), dp(9)).apply { rightMargin = dp(10) })
        header.addView(label("Cable Mouse", 16f, pal.text, bold = true))
        keyboardPill = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(16), dp(10), dp(16), dp(10))
            isClickable = true
            isSoundEffectsEnabled = false
            setOnClickListener { setKeyboard(!keyboardOn) }
        }
        updateKeyboardPill()
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        top.addView(header)
        top.addView(keyboardPill, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(10) })
        top.addView(TextView(this).apply {
            text = "Keys"
            gravity = Gravity.CENTER
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(pal.text)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = pal.pressable(pal.card, pal.pressed, dpf(24), pal.stroke, 1)
            isClickable = true
            isSoundEffectsEnabled = false
            setOnClickListener { showKeys(true) }
        }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(10) })
        main.addView(top, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(mouseButton("Left", MouseProtocol.BTN_LEFT), LinearLayout.LayoutParams(0, dp(68), 1f).apply { rightMargin = dp(6) })
        buttons.addView(mouseButton("Right", MouseProtocol.BTN_RIGHT), LinearLayout.LayoutParams(0, dp(68), 1f).apply { leftMargin = dp(6) })
        main.addView(buttons)

        // Keys the on-screen keyboard lacks; only shown while the keyboard is open.
        keyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            for ((label, key) in listOf(
                "Esc" to MouseProtocol.KEY_ESCAPE, "Tab" to MouseProtocol.KEY_TAB, "Del" to MouseProtocol.KEY_DELETE,
                "Home" to MouseProtocol.KEY_HOME, "End" to MouseProtocol.KEY_END,
                "←" to MouseProtocol.KEY_LEFT, "↑" to MouseProtocol.KEY_UP, "↓" to MouseProtocol.KEY_DOWN, "→" to MouseProtocol.KEY_RIGHT
            )) {
                addView(TextView(this@MainActivity).apply {
                    text = label
                    gravity = Gravity.CENTER
                    textSize = 11f
                    maxLines = 1
                    setPadding(0, 0, 0, 0)
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(pal.text)
                    background = pal.pressable(pal.card, pal.pressed, dpf(12), pal.stroke, 1)
                    isSoundEffectsEnabled = false
                    setOnClickListener { onKey(key) }
                }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(1); marginEnd = dp(1) })
            }
        }
        main.addView(keyRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        main.addView(buildPad(), LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
        return main
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun mouseButton(text: String, btn: Int) = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(pal.text)
        background = pal.pressable(pal.card, pal.pressed, dpf(18), pal.stroke, 1)
        isSoundEffectsEnabled = false
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    if (soundOn) sound.playDown()
                    sendBytes(MouseProtocol.button(btn, true))
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    if (soundOn) sound.playUp()
                    sendBytes(MouseProtocol.button(btn, false))
                }
            }
            true
        }
    }

    // ---------- keyboard ----------

    private fun updateKeyboardPill() {
        keyboardPill.text = if (keyboardOn) "Type: on" else "Type"
        keyboardPill.setTextColor(if (keyboardOn) pal.onAccent else pal.text)
        keyboardPill.background =
            if (keyboardOn) pal.rounded(pal.accent, dpf(24)) else pal.pressable(pal.card, pal.pressed, dpf(24), pal.stroke, 1)
    }

    private fun setKeyboard(on: Boolean) {
        keyboardOn = on
        keyRow.visibility = if (on) View.VISIBLE else View.GONE
        updateKeyboardPill()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        if (on) keyCapture.post { keyCapture.requestFocus(); imm.showSoftInput(keyCapture, 0) }
        else imm.hideSoftInputFromWindow(keyCapture.windowToken, 0)
    }

    /** Keyboard mode: a full-screen PC keyboard. Landscape is forced while it is open so all keys fit. */
    private fun showKeys(show: Boolean) {
        if (show) {
            if (keyboardOn) setKeyboard(false)
            keys.rebuild(capsAsCtrl, stickyMods)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            keys.releaseAll()
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        keys.view.visibility = if (show) View.VISIBLE else View.GONE
        mainScreen.visibility = if (show) View.GONE else View.VISIBLE
    }

    override fun onCombo(mods: Int, hid: Int) =
        sendBytes(MouseProtocol.mods(mods) + MouseProtocol.keyHid(hid) + MouseProtocol.mods(0))

    override fun onChar(codePoint: Int) = sendBytes(MouseProtocol.keyChar(codePoint))
    override fun onKey(key: Int) = sendBytes(MouseProtocol.key(key))

    private fun sendBytes(b: ByteArray) { runCatching { port?.send(b, 0, b.size) } }

    private fun click(button: Int) {
        sendBytes(MouseProtocol.button(button, true))
        sendBytes(MouseProtocol.button(button, false))
    }

    /**
     * One finger: move. Tap: left click. Tap, then touch again within DRAG_WINDOW_MS: the left button is held
     * until the finger lifts (drag); a quick second tap is just a double click.
     * Two fingers: vertical drag scrolls; two-finger tap: right click.
     * A tap is a gesture shorter than TAP_MS that never travelled more than TAP_SLOP_DP.
     * Gestures are silent: no sound or haptics on the pad.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun buildPad(): View = FrameLayout(this).apply {
        background = pal.rounded(pal.pad, dpf(22), pal.stroke, 1)
        isSoundEffectsEnabled = false
        isHapticFeedbackEnabled = false
        addView(label("Drag to move  ·  tap to click\ntwo fingers to scroll or right-click", 14f, pal.muted).apply {
            gravity = Gravity.CENTER; alpha = 0.7f; isClickable = false
        }, FrameLayout.LayoutParams(-1, -1))

        setOnTouchListener { v, e ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            val m = resources.displayMetrics
            val skip = if (e.actionMasked == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1
            var n = 0; var cx = 0f; var cy = 0f
            for (i in 0 until e.pointerCount) if (i != skip) { cx += e.getX(i); cy += e.getY(i); n++ }
            if (n > 0) { cx /= n; cy /= n }

            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gestureStart = e.eventTime; maxPointers = 1; travel = 0f; fingers = 1
                    lastCx = cx; lastCy = cy
                    if (lastTapUp != 0L && e.eventTime - lastTapUp < DRAG_WINDOW_MS) {
                        dragging = true
                        sendBytes(MouseProtocol.button(MouseProtocol.BTN_LEFT, true))
                    }
                    lastTapUp = 0L
                }
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                    endDrag()
                    maxPointers = maxOf(maxPointers, e.pointerCount)
                    fingers = n
                    lastCx = cx; lastCy = cy      // re-anchor so a finger-count change doesn't jump
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = cx - lastCx; val dy = cy - lastCy
                    lastCx = cx; lastCy = cy
                    val slop = dp(TAP_SLOP_DP)
                    val wasStill = travel <= slop
                    travel += Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    if (travel > slop && !wasStill) {          // nothing is sent until the slop is exceeded
                        if (fingers >= 2) {
                            val sign = if (invertScroll) -1f else 1f
                            scheduler.addScroll(sign * dy / m.ydpi * scrollClicksPerInch)
                        } else {
                            scheduler.addMove(dx / m.xdpi * pxPerInch, dy / m.ydpi * pxPerInch)
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val wasDragging = dragging
                    endDrag()
                    val quick = e.eventTime - gestureStart < TAP_MS
                    if (quick && travel <= dp(TAP_SLOP_DP)) {
                        // wasDragging: this was the second tap of a double click (press/release already sent)
                        if (!wasDragging) {
                            click(if (maxPointers >= 2) MouseProtocol.BTN_RIGHT else MouseProtocol.BTN_LEFT)
                            if (maxPointers < 2) lastTapUp = e.eventTime
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> endDrag()
            }
            true
        }
    }

    // ---------- settings overlay ----------

    private fun settingsOpen() = ::settings.isInitialized && settings.visibility == View.VISIBLE

    private fun showSettings(show: Boolean) { settings.visibility = if (show) View.VISIBLE else View.GONE }

    internal fun label(t: String, sizeSp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = sizeSp; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    internal fun pill(t: String, primary: Boolean = false, onClick: () -> Unit) = TextView(this).apply {
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

    private fun card(title: String, build: LinearLayout.() -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            background = pal.rounded(pal.card, dpf(20), pal.stroke, 1)
            addView(label(title.uppercase(), 12f, pal.accent, bold = true).apply { letterSpacing = 0.08f; setPadding(0, 0, 0, dp(10)) })
            build()
        }

    private fun slider(min: Int, max: Int, value: Float, onChange: (Int) -> Unit, onDone: (Int) -> Unit) = SeekBar(this).apply {
        this.max = max - min
        progress = (value.toInt() - min).coerceIn(0, this.max)
        progressTintList = pal.accentTint(); thumbTintList = pal.accentTint()
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { onChange(min + p) }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) { onDone(min + s.progress) }
        })
    }

    private fun toggle(t: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = t; isChecked = checked; setTextColor(pal.text); textSize = 15f
        thumbTintList = pal.toggleTint(); trackTintList = pal.toggleTint()
        setOnCheckedChangeListener { _, c -> onChange(c) }
    }

    private fun buildSettings(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(6), dp(14), dp(24)) }
        fun add(v: View) = col.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(label("←", 26f, pal.accent, bold = true).apply {
            setPadding(dp(12), dp(8), dp(20), dp(8)); isClickable = true; setOnClickListener { showSettings(false) }
        })
        top.addView(label("Settings", 22f, pal.text, bold = true))
        col.addView(top)

        add(card("Connection") {
            statusText = label("Not connected", 15f, pal.text)
            addView(statusText)
            devicesBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(devicesBox)
            addView(pill("Refresh devices") { refresh(autoOpen = true) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            addView(pill("Setup guide") { onboarding.visibility = View.VISIBLE }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        })

        add(card("Touchpad") {
            val sens = label("", 14f, pal.muted)
            fun showSens() { sens.text = "Pointer speed: ${pxPerInch.toInt()} px per inch of finger travel" }
            showSens()
            addView(sens)
            addView(slider(SENS_MIN, SENS_MAX, pxPerInch, { pxPerInch = it.toFloat(); showSens() },
                { prefs().edit().putFloat("pxPerInch", it.toFloat()).apply() }))

            val sc = label("", 14f, pal.muted).apply { setPadding(0, dp(12), 0, 0) }
            fun showScroll() { sc.text = "Scroll speed: ${scrollClicksPerInch.toInt()} wheel clicks per inch" }
            showScroll()
            addView(sc)
            addView(slider(1, SCROLL_MAX, scrollClicksPerInch, { scrollClicksPerInch = it.toFloat(); showScroll() },
                { prefs().edit().putFloat("scrollPerInch", it.toFloat()).apply() }))

            addView(toggle("Invert scroll direction", invertScroll) {
                invertScroll = it; prefs().edit().putBoolean("invertScroll", it).apply()
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        })

        add(card("Sound") {
            addView(toggle("Mouse click sound (Left / Right buttons only)", soundOn) {
                soundOn = it; prefs().edit().putBoolean("sound", it).apply()
            })
        })

        add(card("Keyboard") {
            addView(toggle("Caps Lock acts as Ctrl (keyboard mode, and a hardware keyboard in Type mode)", capsAsCtrl) {
                capsAsCtrl = it; prefs().edit().putBoolean("capsCtrl", it).apply()
            })
            addView(toggle("Sticky modifiers: tap Shift / Ctrl / Alt / Win to keep it on for the next key (keyboard mode)", stickyMods) {
                stickyMods = it; prefs().edit().putBoolean("stickyMods", it).apply()
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        })

        add(card("Appearance") {
            val group = RadioGroup(this@MainActivity).apply { orientation = RadioGroup.HORIZONTAL }
            for ((idx, name) in listOf(THEME_SYSTEM to "System", THEME_LIGHT to "Light", THEME_DARK to "Dark")) {
                group.addView(RadioButton(this@MainActivity).apply {
                    id = 1000 + idx; text = name; setTextColor(pal.text); buttonTintList = pal.accentTint()
                    isChecked = themeMode == idx
                    setPadding(dp(4), dp(4), dp(16), dp(4))
                })
            }
            group.setOnCheckedChangeListener { _, id ->
                val mode = id - 1000
                if (mode != themeMode) { prefs().edit().putInt("theme", mode).apply(); recreate() }
            }
            addView(group)
        })

        add(card("Diagnostics") {
            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(pill("Test note") { sendNote() }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
            row.addView(pill("Stream test") { stream() }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6); leftMargin = dp(6) })
            row.addView(pill("Clear log") { log.text = "" }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
            addView(row)
            log = label("", 12f, pal.muted).apply { typeface = Typeface.MONOSPACE; setPadding(0, dp(10), 0, 0); setTextIsSelectable(true) }
            addView(log)
        })

        return ScrollView(this).apply {
            setBackgroundColor(pal.bg)
            isClickable = true                  // swallow touches so the pad below never sees them
            addView(col)
        }
    }

    // ---------- status / log ----------

    private fun say(s: String) {
        ui.post {
            val t = log.text.toString() + s + "\n"
            log.text = if (t.length > 6000) t.takeLast(5000) else t
        }
    }

    private fun setConnected(connected: Boolean, msg: String) {
        ui.post {
            (headerDot.background as android.graphics.drawable.GradientDrawable).setColor(if (connected) pal.ok else pal.off)
            statusText.text = msg
        }
    }

    // ---------- helper export (MTP delivery) ----------

    private fun versionCode(): Long {
        val info = packageManager.getPackageInfo(packageName, 0)
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    private fun setExportMessage(m: String) {
        exportMessage = m
        ui.post { if (::helperStatus.isInitialized) helperStatus.text = m }
    }

    /** Copies the bundled helper into Download/CableMouse. [force] re-copies even if this version already did. */
    internal fun exportHelpers(force: Boolean) {
        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_STORAGE)
            return
        }
        setExportMessage("Copying helper…")
        thread {
            val code = versionCode()
            val r = if (force) HelperExporter.exportAll(this, code) else HelperExporter.exportIfNeeded(this, code)
            when {
                r == null -> setExportMessage("The helper is in Download/${HelperExporter.DIR} on this phone.")
                r.error != null -> setExportMessage("Couldn't copy the helper: ${r.error}")
                else -> setExportMessage("Copied ${r.copied.joinToString()} to Download/${HelperExporter.DIR} on this phone.")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_STORAGE) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) exportHelpers(force = true)
        else setExportMessage("Storage permission was denied, so the helper wasn't copied. Get it from the GitHub Releases page instead.")
    }

    // ---------- MIDI ----------

    @Suppress("DEPRECATION")
    private fun refresh(autoOpen: Boolean) {
        devicesBox.removeAllViews()
        val infos = manager.devices
        say("--- ${infos.size} MIDI device(s) ---")
        var candidate: MidiDeviceInfo? = null
        for (info in infos) {
            val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
            val desc = "id=${info.id} type=${info.type} in=${info.inputPortCount} out=${info.outputPortCount} \"$name\""
            say(desc)
            if (info.inputPortCount > 0) {
                if (candidate == null && info.type == MidiDeviceInfo.TYPE_USB) candidate = info
                devicesBox.addView(pill("Use: $name (id ${info.id})") { open(info) },
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            }
        }
        if (port != null) return
        if (candidate == null) setConnected(false, "Not connected. Plug in the cable and set USB mode to MIDI.")
        else if (autoOpen) open(candidate)
    }

    private fun open(info: MidiDeviceInfo) {
        closePort()
        manager.openDevice(info, { dev ->
            if (dev == null) { say("openDevice failed"); return@openDevice }
            val p = dev.openInputPort(0)
            if (p == null) { say("openInputPort(0) returned null (busy?)"); dev.close(); return@openDevice }
            device = dev; deviceId = info.id; port = p
            say("OPENED input port 0 of device ${info.id}")
            setConnected(true, "Connected over USB MIDI. Run the helper on the PC.")
        }, ui)
    }

    private fun closePort() {
        streaming = false
        runCatching { port?.close() }; runCatching { device?.close() }
        port = null; device = null; deviceId = -1
    }

    /** Flushes accumulated motion at ~125 Hz and sends a heartbeat every ~500 ms. */
    private fun startSendLoop() {
        thread(isDaemon = true) {
            var tick = 0
            while (running) {
                port?.let { p ->
                    scheduler.flush()
                    if (tick % 63 == 0) runCatching { p.send(MouseProtocol.heartbeat(), 0, 3) }
                }
                tick++
                Thread.sleep(8)
            }
        }
    }

    private fun sendNote() {
        val p = port ?: return say("no port open")
        p.send(byteArrayOf(0x90.toByte(), 0x3C, 0x7F), 0, 3)
        p.send(byteArrayOf(0x80.toByte(), 0x3C, 0x00), 0, 3)
        say("sent note on+off")
    }

    /** Sends CC1 messages with an incrementing value (1..127) every 2 ms so gaps/reordering are visible on the PC. */
    private fun stream() {
        val p = port ?: return say("no port open")
        if (streaming) return
        streaming = true
        thread {
            val start = System.nanoTime()
            var n = 0
            val msg = byteArrayOf(0xB0.toByte(), 0x01, 0)
            while (streaming && n < 5000) {
                msg[2] = (1 + n % 127).toByte()
                try { p.send(msg, 0, 3) } catch (e: Exception) { say("send failed: $e"); break }
                n++
                val due = start + n * 2_000_000L
                while (System.nanoTime() < due) Thread.onSpinWait()
            }
            streaming = false
            say("sent $n msgs in ${(System.nanoTime() - start) / 1_000_000} ms (expect values cycling 1..127)")
        }
    }

    private companion object {
        const val DEFAULT_PX_PER_INCH = 700f
        const val SENS_MIN = 100
        const val SENS_MAX = 2500
        const val DEFAULT_SCROLL = 6f
        const val SCROLL_MAX = 20
        const val TAP_MS = 250L
        const val DRAG_WINDOW_MS = 300L
        const val TAP_SLOP_DP = 10
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
        const val REQ_STORAGE = 7
    }
}
