package com.cablemouse.ui

import android.content.Context
import android.text.InputType
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.cablemouse.proto.MouseProtocol

/** Receives what the user types; the activity turns it into protocol messages. */
interface KeySink {
    fun onChar(codePoint: Int)
    fun onKey(key: Int)          // one of MouseProtocol.KEY_*
    fun onCombo(mods: Int, hid: Int)   // modifiers (MouseProtocol.MOD_*) + a physical key, e.g. Ctrl+C
}

/**
 * A 1 dp invisible view that owns the soft keyboard. It is not a real text field: the input connection never
 * stores text, it forwards every typed character, backspace and Enter straight to the [KeySink].
 * Suggestions are switched off (visible-password style) so the keyboard commits characters one by one; if a
 * keyboard composes text anyway, the composing region is turned into backspaces plus the changed tail.
 */
class KeyCaptureView(
    context: Context,
    private val sink: KeySink,
    private val capsAsCtrl: () -> Boolean,
) : View(context) {
    private var capsDown = false
    init { isFocusable = true; isFocusableInTouchMode = true }

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_NONE
        return Connection()
    }

    // Hardware keyboards (OTG) and `adb shell input` arrive here.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_CAPS_LOCK && capsAsCtrl()) { capsDown = true; return true }
        return handleKey(event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_CAPS_LOCK && capsAsCtrl()) { capsDown = false; return true }
        return super.onKeyUp(keyCode, event)
    }

    /** USB HID usage for a hardware key, 0 if not mapped. */
    private fun hidFor(code: Int): Int = when (code) {
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> 0x04 + (code - KeyEvent.KEYCODE_A)
        in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> 0x1E + (code - KeyEvent.KEYCODE_1)
        KeyEvent.KEYCODE_0 -> 0x27
        in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> 0x3A + (code - KeyEvent.KEYCODE_F1)
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> 0x28
        KeyEvent.KEYCODE_ESCAPE -> 0x29
        KeyEvent.KEYCODE_DEL -> 0x2A
        KeyEvent.KEYCODE_TAB -> 0x2B
        KeyEvent.KEYCODE_SPACE -> 0x2C
        KeyEvent.KEYCODE_MINUS -> 0x2D
        KeyEvent.KEYCODE_EQUALS -> 0x2E
        KeyEvent.KEYCODE_LEFT_BRACKET -> 0x2F
        KeyEvent.KEYCODE_RIGHT_BRACKET -> 0x30
        KeyEvent.KEYCODE_BACKSLASH -> 0x31
        KeyEvent.KEYCODE_SEMICOLON -> 0x33
        KeyEvent.KEYCODE_APOSTROPHE -> 0x34
        KeyEvent.KEYCODE_GRAVE -> 0x35
        KeyEvent.KEYCODE_COMMA -> 0x36
        KeyEvent.KEYCODE_PERIOD -> 0x37
        KeyEvent.KEYCODE_SLASH -> 0x38
        KeyEvent.KEYCODE_INSERT -> 0x49
        KeyEvent.KEYCODE_MOVE_HOME -> 0x4A
        KeyEvent.KEYCODE_PAGE_UP -> 0x4B
        KeyEvent.KEYCODE_FORWARD_DEL -> 0x4C
        KeyEvent.KEYCODE_MOVE_END -> 0x4D
        KeyEvent.KEYCODE_PAGE_DOWN -> 0x4E
        KeyEvent.KEYCODE_DPAD_RIGHT -> 0x4F
        KeyEvent.KEYCODE_DPAD_LEFT -> 0x50
        KeyEvent.KEYCODE_DPAD_DOWN -> 0x51
        KeyEvent.KEYCODE_DPAD_UP -> 0x52
        else -> 0
    }

    private fun handleKey(e: KeyEvent): Boolean {
        // Shortcuts: with Ctrl (or Caps acting as Ctrl), Alt or Win held, send the physical key plus modifiers.
        val ctrl = e.isCtrlPressed || capsDown
        if (ctrl || e.isAltPressed || e.isMetaPressed) {
            val hid = hidFor(e.keyCode)
            if (hid == 0) return false
            sink.onCombo(
                (if (ctrl) MouseProtocol.MOD_CTRL else 0) or (if (e.isShiftPressed) MouseProtocol.MOD_SHIFT else 0) or
                    (if (e.isAltPressed) MouseProtocol.MOD_ALT else 0) or (if (e.isMetaPressed) MouseProtocol.MOD_WIN else 0),
                hid
            )
            return true
        }
        val special = when (e.keyCode) {
            KeyEvent.KEYCODE_DEL -> MouseProtocol.KEY_BACKSPACE
            KeyEvent.KEYCODE_FORWARD_DEL -> MouseProtocol.KEY_DELETE
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> MouseProtocol.KEY_ENTER
            KeyEvent.KEYCODE_TAB -> MouseProtocol.KEY_TAB
            KeyEvent.KEYCODE_ESCAPE -> MouseProtocol.KEY_ESCAPE
            KeyEvent.KEYCODE_DPAD_LEFT -> MouseProtocol.KEY_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> MouseProtocol.KEY_RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> MouseProtocol.KEY_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> MouseProtocol.KEY_DOWN
            KeyEvent.KEYCODE_MOVE_HOME -> MouseProtocol.KEY_HOME
            KeyEvent.KEYCODE_MOVE_END -> MouseProtocol.KEY_END
            KeyEvent.KEYCODE_PAGE_UP -> MouseProtocol.KEY_PAGE_UP
            KeyEvent.KEYCODE_PAGE_DOWN -> MouseProtocol.KEY_PAGE_DOWN
            else -> 0
        }
        if (special != 0) { sink.onKey(special); return true }
        val u = e.unicodeChar
        if (u <= 0 || u and KeyCharacterMap.COMBINING_ACCENT != 0 || u < 0x20) return false
        sink.onChar(u)
        return true
    }

    private fun typeChar(cp: Int) {
        when {
            cp == '\n'.code -> sink.onKey(MouseProtocol.KEY_ENTER)
            cp == '\t'.code -> sink.onKey(MouseProtocol.KEY_TAB)
            cp >= 0x20 && cp != 0x7F -> sink.onChar(cp)
        }
    }

    private inner class Connection : BaseInputConnection(this, false) {
        private var composing = ""

        /** Make the PC show [text] instead of the current composing text: erase what differs, type the rest. */
        private fun replaceComposing(text: String) {
            val old = composing
            var i = 0
            while (i < old.length && i < text.length && old[i] == text[i]) i++
            if (i > 0 && Character.isHighSurrogate(text[i - 1])) i--            // never split a surrogate pair
            repeat(old.codePointCount(i, old.length)) { sink.onKey(MouseProtocol.KEY_BACKSPACE) }
            var j = i
            while (j < text.length) { val cp = text.codePointAt(j); typeChar(cp); j += Character.charCount(cp) }
        }

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            replaceComposing(text.toString()); composing = ""; return true
        }

        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
            replaceComposing(text.toString()); composing = text.toString(); return true
        }

        override fun finishComposingText(): Boolean { composing = ""; return true }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            composing = ""
            repeat(beforeLength.coerceIn(0, 64)) { sink.onKey(MouseProtocol.KEY_BACKSPACE) }
            repeat(afterLength.coerceIn(0, 64)) { sink.onKey(MouseProtocol.KEY_DELETE) }
            return true
        }

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int) =
            deleteSurroundingText(beforeLength, afterLength)

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN) { composing = ""; handleKey(event) }
            return true
        }

        override fun performEditorAction(editorAction: Int): Boolean {
            sink.onKey(MouseProtocol.KEY_ENTER); return true
        }
    }
}
