package com.vipervision.editor

import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** The three Samsung photo editor tools the AI bubbles can open (Galaxy AI's Photo Assist). */
enum class SamsungTool(val label: String, val samsungLabels: List<String>) {
    Eraser("Samsung AI Eraser", listOf("AI eraser", "Object eraser")),
    Move("Samsung Move", listOf("Move")),
    Create("Samsung Create", listOf("Create", "Sketch to image"))
}

/**
 * Sends the photo, with every edit so far, to Samsung's own photo editor and brings its result
 * back as a step in Viper Vision's editor.
 *
 * - Out: the edited photo is rendered and added to the gallery as a temporary picture (Samsung's
 *   Galaxy AI tools work on gallery pictures), then opened in Samsung's editor in its own window.
 * - In Samsung's editor: the accessibility service (the camera shortcut's, see
 *   CameraThumbnailService) taps Galaxy AI and the chosen tool, and floats a "Back to Viper Vision"
 *   button that taps Samsung's Done/Save and returns.
 * - Back: when Viper's editor shows again, the picture Samsung saved (the temporary one
 *   overwritten, or a new copy) is taken in and the temporary picture deleted.
 *
 * Samsung documents none of this, so every step is logged (Settings → Diagnostic log) for tuning.
 */
object SamsungHandoff {

    /** Samsung's photo editor and gallery, in order of preference. */
    val PACKAGES = listOf("com.sec.android.mimage.photoretouching", "com.sec.android.gallery3d")

    class Session(
        val tool: SamsungTool,
        val exported: Uri,
        val exportedId: Long,
        val startedAtSec: Long,
        val exportedModifiedSec: Long
    ) {
        @Volatile var galaxyAiTapped = false
        @Volatile var editTapped = false
        @Volatile var toolOpened = false
        @Volatile var returnRequested = false
        @Volatile var tapAttempts = 0
    }

    @Volatile
    var active: Session? = null

    /** On a Samsung phone the Samsung bubbles always show (they explain it if the editor is missing). */
    val isSamsung: Boolean get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    /**
     * Settings → Samsung AI tools in the editor (Gallery's settings file, key "vv_samsung_ai").
     * On by default on Samsung phones, off elsewhere.
     */
    fun enabled(context: Context): Boolean =
        context.getSharedPreferences("Prefs", Context.MODE_PRIVATE).getBoolean("vv_samsung_ai", isSamsung)

    /**
     * The apps that can edit a photo. Asked with a sample photo address: editors that only accept
     * real photos (content:// addresses, like Samsung's) don't answer a question without one.
     */
    private fun editHandlers(context: Context) = listOf("image/jpeg", "image/*").flatMap { type ->
        val intent = Intent(Intent.ACTION_EDIT)
            .setDataAndType(Uri.parse("content://media/external/images/media/1"), type)
        runCatching { context.packageManager.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
    }.distinctBy { it.activityInfo.packageName + "/" + it.activityInfo.name }

    /** How a photo gets into Samsung's tools: straight into an editor, or into Gallery's viewer. */
    class Target(val component: ComponentName, val action: String)

    /**
     * Where to send the photo. Samsung's editor doesn't take "edit" requests from other apps on
     * One UI 8 (the diagnostic log listed only Google Photos and ChatGPT), but Samsung Gallery's
     * viewer opens any photo, and its Galaxy AI / Edit buttons lead to the tools (tapped by the
     * accessibility service). An editor that does accept "edit" is still preferred.
     */
    fun editor(context: Context): Target? {
        fun isSamsung(pkg: String) = pkg in PACKAGES || pkg.startsWith("com.sec.") || pkg.startsWith("com.samsung.")
        editHandlers(context).firstOrNull { isSamsung(it.activityInfo.packageName) }?.activityInfo?.let {
            return Target(ComponentName(it.packageName, it.name), Intent.ACTION_EDIT)
        }
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("content://media/external/images/media/1"), "image/jpeg")
            .setPackage(GALLERY)
        return runCatching { context.packageManager.queryIntentActivities(view, 0) }.getOrDefault(emptyList())
            .firstOrNull()?.activityInfo?.let { Target(ComponentName(it.packageName, it.name), Intent.ACTION_VIEW) }
    }

    /** Samsung Gallery's and photo editor's screens (for the log, to find a direct way in). */
    fun describeSamsungApps(context: Context): String = PACKAGES.joinToString(" ; ") { pkg ->
        runCatching {
            val info = context.packageManager.getPackageInfo(pkg, android.content.pm.PackageManager.GET_ACTIVITIES)
            "$pkg ${info.versionName}: " + info.activities.orEmpty()
                .filter { it.exported }
                .joinToString { it.name.removePrefix(pkg) }
        }.getOrElse { "$pkg: not found (${it.javaClass.simpleName})" }
    }

    const val GALLERY = "com.sec.android.gallery3d"

    /** Every app that can edit photos (for the log, to see what Samsung offers). */
    fun describeEditors(context: Context): String =
        editHandlers(context).joinToString { "${it.activityInfo.packageName}/${it.activityInfo.name}" }.ifEmpty { "none" }

    /** Adds [image] to the gallery as a temporary picture for Samsung's editor. */
    fun export(context: Context, image: Bitmap): Pair<Uri, Long> {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "ViperVision_to_Samsung_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Viper Vision/")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("couldn't create the picture for Samsung's editor")
        resolver.openOutputStream(uri)?.use { image.compress(Bitmap.CompressFormat.JPEG, 97, it) }
            ?: error("couldn't write the picture for Samsung's editor")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
        return uri to modifiedSec(context, uri)
    }

    /** Opens [uri] in Samsung's editor or viewer, in its own window (so Back there doesn't strand Viper's). */
    fun open(context: Context, target: Target, uri: Uri) {
        context.startActivity(
            Intent(target.action)
                .setDataAndType(uri, "image/jpeg")
                .setComponent(target.component)
                .addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                )
        )
    }

    /**
     * What Samsung saved: a new picture added since the handoff (Save as copy), or the temporary
     * one if it was overwritten. Null if nothing was saved.
     */
    fun result(context: Context, session: Session): Uri? {
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.DISPLAY_NAME)
        val newest = runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Images.Media.DATE_ADDED} >= ? AND ${MediaStore.Images.Media._ID} != ?",
                arrayOf((session.startedAtSec - 1).toString(), session.exportedId.toString()),
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    ViperLog.i("SAMSUNG", "New picture since the handoff: ${cursor.getString(2)}")
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0))
                } else null
            }
        }.getOrNull()
        if (newest != null) return newest
        val modified = modifiedSec(context, session.exported)
        return if (modified > session.exportedModifiedSec) {
            ViperLog.i("SAMSUNG", "The temporary picture was overwritten ($modified > ${session.exportedModifiedSec})")
            session.exported
        } else null
    }

    /** Deletes the temporary picture. */
    fun cleanUp(context: Context, session: Session) {
        runCatching { context.contentResolver.delete(session.exported, null, null) }
            .onFailure { ViperLog.error("SAMSUNG", "couldn't delete the temporary picture", it) }
    }

    private fun modifiedSec(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATE_MODIFIED), null, null, null)?.use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        } ?: 0L
    }.getOrDefault(0L)
}
