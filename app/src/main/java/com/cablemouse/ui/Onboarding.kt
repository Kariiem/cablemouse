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
class Onboarding(
    private val ui: Ui,
    private val onDone: () -> Unit,
    private val onCopyAgain: () -> Unit,
) {
    private val context get() = ui.context
    private val pal get() = ui.pal

    private val helperStatus = ui.label("Not copied yet.", 14f, ui.pal.text).apply { setPadding(0, ui.dp(8), 0, 0) }
    val view: View = build()

    /** Shows what happened to the helper copy in Download/CableMouse. */
    fun setStatus(text: String) { helperStatus.text = text }

    private fun build(): View {
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(28)) }
        fun add(v: View, top: Int = 12) = col.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(top) })

        col.addView(ui.label("Get started", 26f, pal.text, bold = true))
        col.addView(ui.label("One-time setup: the PC needs a small helper program. After that, your phone works as a mouse over the USB cable.", 15f, pal.muted)
            .apply { setPadding(0, ui.dp(6), 0, 0) })

        add(step(1, "Leave USB on File transfer",
            "Plug the phone into the PC. The helper was copied to Download/CableMouse on this phone."))
        add(step(2, "Copy the helper to the PC",
            "On the PC, open this phone > Download > CableMouse and copy cablemouse-legacy.exe anywhere on the PC (Windows 10/11). If it doesn't show up, unplug and replug the cable."))
        add(step(3, "Switch USB to MIDI",
            "Pull down the notification shade, tap the USB notification (or Settings > Connected devices > USB) and choose MIDI."))
        add(step(4, "Run the helper",
            "Double-click cablemouse-legacy.exe. If Windows SmartScreen warns, choose More info > Run anyway. Then drag on the touchpad."))
        add(helperCard())
        add(ui.pill("Done", primary = true) { onDone() }, top = 18)

        return ScrollView(context).apply {
            setBackgroundColor(pal.bg)
            isClickable = true          // swallow touches so the screen below never sees them
            addView(col)
        }
    }

    private fun step(n: Int, title: String, body: String): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ui.dp(14), ui.dp(14), ui.dp(16), ui.dp(14))
            background = pal.rounded(pal.card, ui.dpf(20), pal.stroke, 1)
        }
        row.addView(TextView(context).apply {
            text = n.toString(); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            setTextColor(pal.onAccent); background = pal.oval(pal.accent)
        }, LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)).apply { rightMargin = ui.dp(14) })
        val text = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        text.addView(ui.label(title, 16f, pal.text, bold = true))
        text.addView(ui.label(body, 14f, pal.muted).apply { setPadding(0, ui.dp(3), 0, 0) })
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    /** Where the helper is on the phone, plus the GitHub link for PCs where file transfer is blocked. */
    private fun helperCard(): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(16))
            background = pal.rounded(pal.card, ui.dpf(20), pal.stroke, 1)
        }
        card.addView(ui.label("HELPER ON THIS PHONE", 12f, pal.accent, bold = true).apply { letterSpacing = 0.08f })
        card.addView(helperStatus)
        card.addView(ui.pill("Copy helper again") { onCopyAgain() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
        card.addView(ui.label(
            "File transfer blocked, or no file access on that PC? Download cablemouse-legacy.exe from this project's GitHub Releases page on another device instead:",
            13f, pal.muted
        ).apply { setPadding(0, ui.dp(12), 0, 0) })
        card.addView(ui.label(RELEASES_URL.removePrefix("https://"), 13f, pal.accent, bold = true).apply {
            setPadding(0, ui.dp(6), 0, 0)
            isClickable = true
            setOnClickListener { openReleases() }
        })
        val links = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        links.addView(ui.pill("Open releases page") { openReleases() }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = ui.dp(6) })
        links.addView(ui.pill("Copy link") { copyLink() }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = ui.dp(6) })
        card.addView(links, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
        return card
    }

    private fun copyLink() {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Cable Mouse releases", RELEASES_URL))
        Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
    }

    /** Opens the GitHub Releases page in the phone's browser. */
    private fun openReleases() {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No browser found. The link: $RELEASES_URL", Toast.LENGTH_LONG).show()
        }
    }
}
