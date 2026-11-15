package com.cablemouse.settings

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A SharedPreferences that lives in a map, so settings can be tested on the JVM. */
private class FakePrefs(val data: MutableMap<String, Any> = HashMap()) : SharedPreferences {
    override fun getAll(): MutableMap<String, *> = data
    override fun getString(k: String, d: String?) = data[k] as? String ?: d
    override fun getStringSet(k: String, d: MutableSet<String>?) = d
    override fun getInt(k: String, d: Int) = data[k] as? Int ?: d
    override fun getLong(k: String, d: Long) = data[k] as? Long ?: d
    override fun getFloat(k: String, d: Float) = data[k] as? Float ?: d
    override fun getBoolean(k: String, d: Boolean) = data[k] as? Boolean ?: d
    override fun contains(k: String) = data.containsKey(k)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = HashMap<String, Any>()
        override fun putString(k: String, v: String?) = apply { pending[k] = v ?: "" }
        override fun putStringSet(k: String, v: MutableSet<String>?) = this
        override fun putInt(k: String, v: Int) = apply { pending[k] = v }
        override fun putLong(k: String, v: Long) = apply { pending[k] = v }
        override fun putFloat(k: String, v: Float) = apply { pending[k] = v }
        override fun putBoolean(k: String, v: Boolean) = apply { pending[k] = v }
        override fun remove(k: String) = apply { pending.remove(k) }
        override fun clear() = apply { pending.clear() }
        override fun commit(): Boolean { data.putAll(pending); return true }
        override fun apply() { data.putAll(pending) }
    }
}

class SettingsTest {
    @Test fun defaultsWhenNothingIsSaved() {
        val s = Settings(FakePrefs())
        assertEquals(Settings.POINTER_DEFAULT, s.pointerSpeed.value, 0f)
        assertFalse(s.invertScroll.value)
        assertTrue(s.gyroHold.value)
        assertEquals(Settings.FEEDBACK_AUDIBLE, s.clickFeedback.value)
    }

    @Test fun changingTheLiveValueDoesNotSaveIt() {
        val p = FakePrefs()
        val s = Settings(p)
        s.invertScroll.value = true                 // what the Mode menu's toggle button does
        assertTrue(s.invertScroll.value)
        assertFalse(Settings(p).invertScroll.value) // a new session still starts from the saved default
    }

    @Test fun saveMakesTheLiveValueTheStartUpDefault() {
        val p = FakePrefs()
        val s = Settings(p)
        s.stickyMods.value = true
        s.stickyMods.save()
        assertTrue(Settings(p).stickyMods.value)
    }

    @Test fun oldBooleanSoundSettingIsMigrated() {
        assertEquals(Settings.FEEDBACK_SILENT, Settings(FakePrefs(hashMapOf("sound" to false))).clickFeedback.value)
        assertEquals(Settings.FEEDBACK_AUDIBLE, Settings(FakePrefs(hashMapOf("sound" to true))).clickFeedback.value)
        // once the new setting exists it wins
        assertEquals(Settings.FEEDBACK_VIBRATE,
            Settings(FakePrefs(hashMapOf("sound" to false, "clickFeedback" to Settings.FEEDBACK_VIBRATE))).clickFeedback.value)
    }

    @Test fun savedAspectIndexIsClampedToTheKnownShapes() {
        assertEquals(Settings.ABS_ASPECTS.size - 1, Settings(FakePrefs(hashMapOf("absAspect" to 99))).absAspect.value)
        assertEquals(0, Settings(FakePrefs(hashMapOf("absAspect" to -3))).absAspect.value)
    }
}
