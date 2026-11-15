package com.cablemouse.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

const val RELEASES_URL = "https://github.com/Kariiem/cablemouse/releases/latest"

/** First-run guide: how to get the helper onto the PC and start using the phone as a mouse. */
internal fun MainActivity.buildOnboarding(onDone: () -> Unit, onCopyAgain: () -> Unit): View {
    val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(28)) }
    fun add(v: View, top: Int = 12) = col.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })

    col.addView(label("Get started", 26f, pal.text, bold = true))
    col.addView(label("One-time setup: the PC needs a small helper program. After that, your phone works as a mouse over the USB cable.", 15f, pal.muted)
        .apply { setPadding(0, dp(6), 0, 0) })

    fun step(n: Int, title: String, body: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(14), dp(16), dp(14))
            background = pal.rounded(pal.card, dpf(20), pal.stroke, 1)
        }
        row.addView(TextView(this).apply {
            text = n.toString(); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            setTextColor(pal.onAccent); background = pal.oval(pal.accent)
        }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(14) })
        val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        text.addView(label(title, 16f, pal.text, bold = true))
        text.addView(label(body, 14f, pal.muted).apply { setPadding(0, dp(3), 0, 0) })
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        add(row)
    }

    step(1, "Leave USB on File transfer",
        "Plug the phone into the PC. The helper was copied to Download/CableMouse on this phone.")
    step(2, "Copy the helper to the PC",
        "On the PC, open this phone > Download > CableMouse and copy cablemouse-legacy.exe anywhere on the PC (Windows 10/11). If it doesn't show up, unplug and replug the cable.")
    step(3, "Switch USB to MIDI",
        "Pull down the notification shade, tap the USB notification (or Settings > Connected devices > USB) and choose MIDI.")
    step(4, "Run the helper",
        "Double-click cablemouse-legacy.exe. If Windows SmartScreen warns, choose More info > Run anyway. Then drag on the touchpad.")

    val status = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(16))
        background = pal.rounded(pal.card, dpf(20), pal.stroke, 1)
    }
    status.addView(label("HELPER ON THIS PHONE", 12f, pal.accent, bold = true).apply { letterSpacing = 0.08f })
    helperStatus = label(exportMessage, 14f, pal.text).apply { setPadding(0, dp(8), 0, 0) }
    status.addView(helperStatus)
    status.addView(pill("Copy helper again") { onCopyAgain() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    status.addView(label(
        "File transfer blocked, or no file access on that PC? Download cablemouse-legacy.exe from this project's GitHub Releases page on another device instead:",
        13f, pal.muted
    ).apply { setPadding(0, dp(12), 0, 0) })
    status.addView(label(RELEASES_URL.removePrefix("https://"), 13f, pal.accent, bold = true).apply {
        setPadding(0, dp(6), 0, 0)
        isClickable = true
        setOnClickListener { openReleases() }
    })
    val links = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    links.addView(pill("Open releases page") { openReleases() }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
    links.addView(pill("Copy link") {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Cable Mouse releases", RELEASES_URL))
        Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show()
    }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
    status.addView(links, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    add(status)

    add(pill("Done", primary = true) { onDone() }, top = 18)

    return ScrollView(this).apply {
        setBackgroundColor(pal.bg)
        isClickable = true          // swallow touches so the pad below never sees them
        addView(col)
    }
}

/** Opens the GitHub Releases page in the phone's browser. */
internal fun MainActivity.openReleases() {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "No browser found. The link: $RELEASES_URL", Toast.LENGTH_LONG).show()
    }
}
