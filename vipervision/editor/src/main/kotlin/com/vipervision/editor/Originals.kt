package com.vipervision.editor

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

/**
 * The untouched photo, kept privately the first time it's changed in place (Save over the
 * original here, or a rotation in the gallery, see ViperOriginals there), so "Revert to original"
 * can put it back. Kept until reverted, or until the photo itself is deleted.
 */
object Originals {

    private fun dir(context: Context) = File(context.filesDir, "originals")

    /** Same key as the gallery's ViperOriginals: the file path, or the uri when there's none. */
    private fun key(source: Uri, realPath: String?): String =
        MessageDigest.getInstance("SHA-1").digest((realPath ?: source.toString()).toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun backup(context: Context, source: Uri, realPath: String?): File? =
        File(dir(context), "${key(source, realPath)}.orig").takeIf { it.isFile }

    /** Copies the photo as it is now, unless a copy from before an earlier change is already kept. */
    fun keep(context: Context, source: Uri, realPath: String?) {
        if (backup(context, source, realPath) != null) return
        val dir = dir(context).apply { mkdirs() }
        val key = key(source, realPath)
        val partial = File(dir, "$key.part")
        val input = realPath?.let(::File)?.takeIf { it.canRead() }?.inputStream()
            ?: context.contentResolver.openInputStream(source)
            ?: error("Couldn't read the original photo")
        input.use { from -> partial.outputStream().use { from.copyTo(it) } }
        check(partial.renameTo(File(dir, "$key.orig"))) { "Couldn't keep the original photo" }
        File(dir, "$key.path").writeText(realPath ?: source.toString())
    }

    fun forget(context: Context, source: Uri, realPath: String?) {
        val key = key(source, realPath)
        File(dir(context), "$key.orig").delete()
        File(dir(context), "$key.path").delete()
    }

    /** Drops kept originals whose photo file no longer exists. */
    fun prune(context: Context) {
        dir(context).listFiles { file -> file.name.endsWith(".path") }?.forEach { pathFile ->
            val path = runCatching { pathFile.readText() }.getOrNull() ?: return@forEach
            if (path.startsWith("/") && !File(path).exists()) {
                File(pathFile.parentFile, pathFile.name.removeSuffix(".path") + ".orig").delete()
                pathFile.delete()
            }
        }
    }
}
