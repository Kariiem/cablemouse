package com.cablemouse.ui

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import com.cablemouse.export.HelperExporter
import com.cablemouse.link.MidiLink
import com.cablemouse.settings.Settings
import kotlin.concurrent.thread

/**
 * The only Activity. It builds the pieces and wires them together; the work lives elsewhere:
 *   [MidiLink] (transport) · [Settings] · the [ModeScreen]s switched by [ModeController] · [SettingsScreen],
 *   [Onboarding], [Picker]/[ModeMenu]. What stays here is the window (insets, orientation, back, permissions)
 *   and delivering the helper to Download/CableMouse.
 */
class MainActivity : Activity(), ModeHost {
    private lateinit var settings: Settings
    private lateinit var link: MidiLink
    private lateinit var feedback: ClickFeedback
    private lateinit var modes: ModeController
    private lateinit var modeMenu: ModeMenu
    private lateinit var picker: Picker
    private lateinit var settingsScreen: SettingsScreen
    private lateinit var onboarding: Onboarding
    private var ready = false              // false when the crash-report screen replaced the app
    private var imeWasVisible = false

    // ---------- lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReport.install(this)
        CrashReport.pending(this)?.let { setContentView(CrashReport.screen(this, it)); return }

        settings = Settings(getSharedPreferences("cablemouse", MODE_PRIVATE))
        val dark = when (settings.theme.value) {
            Settings.THEME_LIGHT -> false
            Settings.THEME_DARK -> true
            else -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        val ui = Ui(this, Palette(dark))
        feedback = ClickFeedback(this, settings)
        link = MidiLink(this)
        picker = Picker(ui)

        val mouse = MouseScreen(ui, link, settings, feedback, object : MouseScreen.Actions {
            override fun openSettings() = settingsScreen.show(true)
            override fun openModeMenu() = modeMenu.showModes()
            override fun openProtocolMenu() = modeMenu.showProtocols()
        })
        val backToMouse = { modes.switchTo(Mode.MOUSE) }
        val keyboard = KeysScreen(ui, link, settings, backToMouse)
        val gyro = GyroScreen(ui, link, settings, feedback, backToMouse)
        val absolute = AbsoluteScreen(ui, link, settings, feedback, backToMouse)
        modes = ModeController(
            mapOf(
                Mode.MOUSE to mouse, Mode.MOUSE_TYPING to mouse,
                Mode.KEYBOARD to keyboard, Mode.GYRO to gyro, Mode.ABSOLUTE to absolute,
            ),
            this,
        )
        modeMenu = ModeMenu(picker, settings, modes)

        settingsScreen = SettingsScreen(ui, settings, link, feedback, object : SettingsScreen.Actions {
            override fun closeSettings() = settingsScreen.show(false)
            override fun showSetupGuide() { onboarding.view.visibility = View.VISIBLE }
            override fun themeChanged() = recreate()
        }).apply { show(savedInstanceState?.getBoolean(STATE_SETTINGS) == true) }
        onboarding = Onboarding(
            ui,
            onDone = { settings.onboarded.value = true; settings.onboarded.save(); onboarding.view.visibility = View.GONE },
            onCopyAgain = { exportHelpers(force = true) },
        ).apply { view.visibility = if (settings.onboarded.value) View.GONE else View.VISIBLE }

        link.addListener(mouse)
        link.addListener(settingsScreen)

        val root = FrameLayout(this).apply { setBackgroundColor(ui.pal.bg) }
        for (v in listOf(mouse.view, keyboard.view, gyro.view, absolute.view, picker.view, settingsScreen.view, onboarding.view)) {
            root.addView(v, FrameLayout.LayoutParams(-1, -1))
        }
        root.setOnApplyWindowInsetsListener { v, insets -> applyInsets(v, insets) }
        setContentView(root)
        // Must come after setContentView: the window insets controller doesn't exist before the decor view does.
        runCatching { applySystemBars(dark) }

        ready = true
        link.start()
        exportHelpers(force = false)
    }

    override fun onResume() {
        super.onResume()
        if (!ready) return
        if (!link.isOpen) link.refresh(autoOpen = true)
        modes.onResume()
    }

    override fun onPause() {
        if (ready) modes.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (ready) { link.stop(); feedback.close() }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (ready) outState.putBoolean(STATE_SETTINGS, settingsScreen.isOpen)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        when {
            !ready -> super.onBackPressed()
            onboarding.view.visibility == View.VISIBLE -> onboarding.view.visibility = View.GONE
            picker.isShown -> picker.hide()
            modes.mode != Mode.MOUSE && !settingsScreen.isOpen -> modes.switchTo(Mode.MOUSE)
            settingsScreen.isOpen -> settingsScreen.show(false)
            else -> super.onBackPressed()
        }
    }

    // ---------- window ----------

    override fun setLandscape(on: Boolean) {
        requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun setKeepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * targetSdk 35 is edge-to-edge: keep content out from under the status/nav bars and cutout. The on-screen
     * keyboard is an inset too, so the pad shrinks above it instead of being covered.
     */
    private fun applyInsets(v: View, insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= 30) {
            val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
            v.setPadding(i.left, i.top, i.right, i.bottom)
            val imeVisible = insets.isVisible(WindowInsets.Type.ime())
            // The user dismissed the phone's keyboard (back gesture): leave typing mode.
            if (imeWasVisible && !imeVisible && modes.mode == Mode.MOUSE_TYPING) modes.switchTo(Mode.MOUSE)
            imeWasVisible = imeVisible
        } else {
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
        }
        return insets
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

    // ---------- helper delivery (copy to Download/CableMouse for MTP) ----------

    private fun versionCode(): Long {
        val info = packageManager.getPackageInfo(packageName, 0)
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    private fun setExportMessage(m: String) = runOnUiThread { onboarding.setStatus(m) }

    /** Copies the bundled helper into Download/CableMouse. [force] re-copies even if this version already did. */
    private fun exportHelpers(force: Boolean) {
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

    private companion object {
        const val STATE_SETTINGS = "settings"
        const val REQ_STORAGE = 7
    }
}
