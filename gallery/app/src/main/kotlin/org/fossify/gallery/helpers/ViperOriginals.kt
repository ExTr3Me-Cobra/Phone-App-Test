package org.fossify.gallery.helpers

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Viper Vision: keeps a private copy of a photo before it's first changed in place (rotated
 * here, or saved over by the editor), so the editor's "Revert to original" can put it back.
 * Same folder and naming as the editor's Originals (vipervision/editor/.../Originals.kt).
 */
object ViperOriginals {

    fun keep(context: Context, path: String) {
        runCatching {
            val source = File(path)
            if (!source.isFile) return
            val dir = File(context.filesDir, "originals").apply { mkdirs() }
            val key = key(path)
            val backup = File(dir, "$key.orig")
            if (backup.exists()) return
            val partial = File(dir, "$key.part")
            source.inputStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
            partial.renameTo(backup)
            File(dir, "$key.path").writeText(path)
        }
    }

    private fun key(id: String): String =
        MessageDigest.getInstance("SHA-1").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
}
