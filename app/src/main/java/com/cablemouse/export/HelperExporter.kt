package com.cablemouse.export

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Copies the bundled PC helper files (the files in assets/helpers) into Download/CableMouse on the phone, so they
 * show up over USB File Transfer (MTP) and can be copied to the PC.
 * API 29+: MediaStore (no permission). API 23-28: direct file write (needs WRITE_EXTERNAL_STORAGE).
 */
object HelperExporter {
    const val DIR = "CableMouse"
    private const val ASSET_DIR = "helpers"
    private const val PREFS = "helper_export"

    class Result(val copied: List<String>, val error: String?)

    /** Exports only when this app version hasn't exported yet. Returns null when already up to date. */
    fun exportIfNeeded(ctx: Context, versionCode: Long): Result? {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong("version", -1) == versionCode) return null
        return exportAll(ctx, versionCode)
    }

    fun exportAll(ctx: Context, versionCode: Long): Result {
        val names = ctx.assets.list(ASSET_DIR)?.toList().orEmpty()
        if (names.isEmpty()) return Result(emptyList(), "No helper files are bundled in this build.")
        val copied = ArrayList<String>()
        try {
            for (name in names) {
                val bytes = ctx.assets.open("$ASSET_DIR/$name").use { it.readBytes() }
                if (Build.VERSION.SDK_INT >= 29) writeMediaStore(ctx, name, bytes) else writeLegacy(ctx, name, bytes)
                copied += name
            }
        } catch (e: Exception) {
            return Result(copied, e.message ?: e.toString())
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("version", versionCode).apply()
        return Result(copied, null)
    }

    private fun mime(name: String) = when (name.substringAfterLast('.').lowercase()) {
        "exe" -> "application/x-msdownload"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    private fun writeMediaStore(ctx: Context, name: String, bytes: ByteArray) {
        val resolver = ctx.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val rel = "${Environment.DIRECTORY_DOWNLOADS}/$DIR/"
        // Remove an earlier copy of ours so updates don't pile up as "name (1).exe".
        resolver.query(
            collection, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(name, rel), null
        )?.use { c ->
            while (c.moveToNext()) runCatching { resolver.delete(ContentUris.withAppendedId(collection, c.getLong(0)), null, null) }
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime(name))
            put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("Could not create $name in Downloads.")
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Could not write $name.")
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }

    @Suppress("DEPRECATION")
    private fun writeLegacy(ctx: Context, name: String, bytes: ByteArray) {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIR)
        if (!dir.isDirectory && !dir.mkdirs()) error("Could not create ${dir.path}.")
        val f = File(dir, name)
        f.writeBytes(bytes)
        MediaScannerConnection.scanFile(ctx, arrayOf(f.path), null, null)   // make it visible over MTP
    }
}
