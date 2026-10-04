package com.webshortcuts.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Icon files the home screen reads directly, through this app's FileProvider.
 *
 * When a shortcut is given a bitmap, Android re-saves it in its own storage, and on some phones
 * (seen on Samsung) in a format without transparency, so see-through areas turn black. Giving the
 * shortcut a link to a PNG instead (Android 11+) lets the launcher load the original file.
 *
 * Files are never deleted when an entry is removed from the list, because the icon may still be
 * on the home screen and would go blank.
 */
object SharedIcons {
    private const val DIR = "shared_icons"

    /** Writes the icon as a PNG and returns a content:// link the launcher can be granted. */
    fun publish(context: Context, shortcut: WebShortcut, icon: Bitmap): Uri {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        // A new name per version, so launchers can't keep showing a cached older icon.
        dir.listFiles { f -> f.name.startsWith("${shortcut.id}-") }?.forEach { it.delete() }
        val file = File(dir, "${shortcut.id}-${shortcut.updatedAt}.png")
        file.outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.icons", file)
    }
}
