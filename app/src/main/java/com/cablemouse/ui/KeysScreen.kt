package com.cablemouse.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.cablemouse.link.Transport
import com.cablemouse.proto.MouseProtocol
import com.cablemouse.settings.Settings

/**
 * Keyboard mode: the whole screen is a PC QWERTY keyboard that sends physical keys (USB HID usages) plus the
 * modifiers that are held. Several fingers work at once, so Ctrl+C is "hold Ctrl, tap C". Modifiers behave like
 * on a real keyboard: down while a finger is on them, up when it lifts. (So a lone tap on Win opens the Start
 * menu on release, a lone tap on Alt focuses the menu bar, exactly as on a PC.)
 * With the "sticky modifiers" option a short tap on a modifier instead keeps it down for the next key (the key
 * press releases it); tapping it again cancels it. Holding it while pressing another key never latches.
 */
class KeysScreen(
    private val ui: Ui,
    private val transport: Transport,
    private val settings: Settings,
    private val onExit: () -> Unit,
) : ModeScreen {
    private val pal get() = ui.pal
    private val send: (ByteArray) -> Unit = transport::send
    private val handler = Handler(Looper.getMainLooper())

    private class Mod { var used = false; var at = 0L }

    private var sticky = false
    private var latched = 0                        // sticky modifiers waiting for the next key
    private val activeMods = HashSet<Mod>()        // modifier keys with a finger on them
    private val heldCount = IntArray(4)            // fingers currently on a key of each modifier (Ctrl, Shift, Alt, Win)
    private var sentMask = 0
    private val modViews = ArrayList<Pair<TextView, Int>>()
    private val keyViews = ArrayList<Pair<TextView, KeySpec>>()

    override val view = LinearLayout(ui.context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8))
    }
    override val needsLandscape get() = true

    override fun enter(mode: Mode) = rebuild(settings.capsAsCtrl.value, settings.stickyMods.value)
    override fun exit() = releaseAll()
    override fun onPause() = releaseAll()

    private fun heldMask() = (0..3).fold(0) { m, i -> if (heldCount[i] > 0) m or (1 shl i) else m }

    private fun push() {
        val m = heldMask() or latched
        if (m != sentMask) { sentMask = m; send(MouseProtocol.mods(m)) }
        refreshLabels()
    }

    /** Releases everything (leaving the screen, app paused). */
    fun releaseAll() {
        heldCount.fill(0); latched = 0; activeMods.clear()
        handler.removeCallbacksAndMessages(null)
        if (sentMask != 0) { sentMask = 0; send(MouseProtocol.mods(0)) }
        refreshLabels()
    }

    private fun refreshLabels() {
        val m = sentMask
        val shift = m and MouseProtocol.MOD_SHIFT != 0
        for ((tv, spec) in keyViews) tv.text = if (shift) spec.shifted else spec.label
        for ((tv, bit) in modViews) {
            val on = m and bit != 0
            tv.background = if (on) pal.rounded(pal.accent, ui.dpf(8)) else pal.pressable(pal.card, pal.pressed, ui.dpf(8), pal.stroke, 1)
            tv.setTextColor(if (on) pal.onAccent else pal.text)
        }
    }

    private fun rebuild(capsAsCtrl: Boolean, sticky: Boolean) {
        releaseAll()
        this.sticky = sticky
        view.removeAllViews(); modViews.clear(); keyViews.clear()
        val bar = LinearLayout(ui.context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bar.addView(ui.pill("Mouse") { onExit() }, LinearLayout.LayoutParams(-2, -2))
        bar.addView(ui.label("Keyboard mode" + (if (capsAsCtrl) "  ·  Caps = Ctrl" else "") + (if (sticky) "  ·  Sticky modifiers" else ""), 13f, pal.muted).apply {
            setPadding(ui.dp(14), 0, 0, 0)
        })
        view.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(6) })
        val rows = KeyLayout.rows(capsAsCtrl)
        for (r in rows) {
            val row = LinearLayout(ui.context).apply { orientation = LinearLayout.HORIZONTAL }
            for (spec in r) row.addView(keyView(spec), LinearLayout.LayoutParams(0, -1, spec.width))
            view.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        refreshLabels()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun keyView(spec: KeySpec): View {
        val tv = TextView(ui.context).apply {
            text = spec.label
            gravity = Gravity.CENTER
            maxLines = 1
            textSize = if (spec.label.length == 1) 18f else 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(pal.text)
            background = pal.pressable(pal.card, pal.pressed, ui.dpf(8), pal.stroke, 1)
            isSoundEffectsEnabled = false
            isHapticFeedbackEnabled = false
        }
        val m = ui.dp(2)
        val holder = android.widget.FrameLayout(ui.context).apply {
            setPadding(m, m, m, m)
            addView(tv, android.widget.FrameLayout.LayoutParams(-1, -1))
        }
        if (spec.mod != 0) {
            val bit = Integer.numberOfTrailingZeros(spec.mod)
            val state = Mod()
            var down = false
            modViews += tv to spec.mod
            tv.setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        down = true; state.used = false; state.at = e.eventTime
                        activeMods += state; heldCount[bit]++; push()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (down) {
                        down = false; activeMods -= state; heldCount[bit]--
                        if (sticky && e.actionMasked == MotionEvent.ACTION_UP && !state.used && e.eventTime - state.at < 300)
                            latched = latched xor spec.mod
                        push()
                    }
                }
                true
            }
        } else {
            keyViews += tv to spec
            val repeat = object : Runnable {
                override fun run() { send(MouseProtocol.keyHid(spec.hid)); handler.postDelayed(this, 55) }
            }
            tv.setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        for (s in activeMods) s.used = true
                        send(MouseProtocol.keyHid(spec.hid))
                        if (latched != 0) { latched = 0; push() }          // sticky modifiers are spent
                        if (spec.repeats) handler.postDelayed(repeat, 450)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.isPressed = false; handler.removeCallbacks(repeat) }
                }
                true
            }
        }
        return holder
    }
}
