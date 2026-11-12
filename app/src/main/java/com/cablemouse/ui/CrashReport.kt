package com.cablemouse.ui

import android.app.Activity
import android.graphics.Typeface
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/**
 * Debug aid: saves the stack trace of an uncaught crash and shows it on the next launch, so a
 * start-up crash can be reported without adb/logcat. Deliberately uses no app theme or palette.
 */
object CrashReport {
    private const val FILE = "last_crash.txt"

    fun install(a: Activity) {
        val dir = a.applicationContext.filesDir
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { File(dir, FILE).writeText(Log.getStackTraceString(e)) }
            prev?.uncaughtException(t, e)
        }
    }

    fun pending(a: Activity): String? = File(a.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun screen(a: Activity, trace: String): View {
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 96, 32, 32) }
        col.addView(TextView(a).apply { text = "Cable Mouse crashed last time. Please send this text:"; textSize = 16f })
        col.addView(Button(a).apply {
            text = "Dismiss and try again"
            setOnClickListener { File(a.filesDir, FILE).delete(); a.recreate() }
        })
        col.addView(TextView(a).apply {
            text = trace; textSize = 11f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true)
        })
        return ScrollView(a).apply { addView(col) }
    }
}
