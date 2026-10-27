package com.vipervision.editor

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import java.io.File

/**
 * Viper Vision's screenshot toolbar, in place of Samsung's (turned off in the phone's Settings →
 * Advanced features → Screenshots). Samsung still takes the screenshot; when a new one lands in
 * a Screenshots folder this shows a card over the screen with:
 *
 * - Share (shares it, then deletes it once an app to share to was picked), Save (keeps it), and
 *   Share & Save (shares and keeps it);
 * - Crop and Draw, which open it in Viper Vision's editor on that tool.
 *
 * It covers the whole screen and stays until one of the three is picked. Runs inside
 * [CameraThumbnailService]; Settings → "Screenshot toolbar" turns it off.
 */
class ScreenshotToolbar(private val service: AccessibilityService) {

    private val handler = Handler(Looper.getMainLooper())
    private val resolver get() = service.contentResolver
    private var card: View? = null
    private var lastShownId = -1L
    private val check = Runnable { checkForScreenshot() }

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            // Samsung writes the file in steps; look once it has settled.
            handler.removeCallbacks(check)
            handler.postDelayed(check, 500)
        }
    }

    fun start() {
        runCatching {
            resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        }.onFailure { ViperLog.error("SCREENSHOT", "couldn't watch for screenshots", it) }
    }

    fun stop() {
        runCatching { resolver.unregisterContentObserver(observer) }
        handler.removeCallbacksAndMessages(null)
        hide()
    }

    private fun enabled() =
        service.getSharedPreferences("Prefs", Context.MODE_PRIVATE).getBoolean(PREF_ENABLED, true)

    /** The newest screenshot, if it was just taken and not shown yet. */
    private fun checkForScreenshot() {
        if (!enabled()) return
        if (service.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) return
        val shot = runCatching { newestScreenshot() }.getOrNull() ?: return
        if (shot.id == lastShownId) return
        lastShownId = shot.id
        ViperLog.i("SCREENSHOT", "New screenshot: ${shot.path ?: shot.uri}")
        show(shot)
    }

    private class Shot(val id: Long, val uri: Uri, val path: String?, val name: String)

    private fun newestScreenshot(): Shot? {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        @Suppress("DEPRECATION")
        val projection = arrayOf(
            MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED
        ) + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) arrayOf(MediaStore.Images.Media.OWNER_PACKAGE_NAME) else emptyArray()
        val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.IS_PENDING} = 0" to arrayOf("%Screenshots%")
        } else {
            @Suppress("DEPRECATION")
            "${MediaStore.Images.Media.DATA} LIKE ?" to arrayOf("%/Screenshots/%")
        }
        resolver.query(collection, projection, selection, args, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val added = cursor.getLong(3)
            // Only one taken just now, not an old screenshot being renamed or edited.
            if (System.currentTimeMillis() / 1000 - added > MAX_AGE_SECONDS) return null
            // Not copies Viper Vision itself saves there (Save as copy of a screenshot).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cursor.getString(4) == service.packageName) return null
            if (cursor.getString(2)?.contains("_edited") == true) return null
            val id = cursor.getLong(0)
            return Shot(id, ContentUris.withAppendedId(collection, id), cursor.getString(1), cursor.getString(2) ?: "Screenshot.png")
        }
        return null
    }

    private fun show(shot: Shot) {
        hide()
        val density = service.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val accent = appAccentColor(service).toArgb()
        val onAccent = appAccentColor(service).contentOn().toArgb()
        val grey = BubbleGrey.toArgb()

        fun pill(text: String, filled: Boolean, onClick: () -> Unit) = TextView(service).apply {
            this.text = text
            gravity = Gravity.CENTER
            maxLines = 1
            // Shrinks to fit ("Share & Save" with a large system font size).
            setAutoSizeTextTypeUniformWithConfiguration(10, 16, 1, TypedValue.COMPLEX_UNIT_SP)
            setTextColor(if (filled) onAccent else android.graphics.Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(if (filled) accent else grey) }
            setPadding(dp(6), 0, dp(6), 0)
            setOnClickListener { onClick() }
        }

        fun bubble(icon: Int, label: String, onClick: () -> Unit) = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(FrameLayout(service).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(grey) }
                addView(ImageView(service).apply {
                    setImageResource(icon)
                    setColorFilter(android.graphics.Color.WHITE)
                }, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
                setOnClickListener { onClick() }
            }, LinearLayout.LayoutParams(dp(64), dp(64)))
            addView(TextView(service).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(android.graphics.Color.WHITE)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }

        // The screenshot itself, as large as fits between the buttons.
        val metrics = service.resources.displayMetrics
        val preview = ImageView(service).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setImageBitmap(resolver.loadThumbnail(shot.uri, Size(metrics.widthPixels, metrics.heightPixels), null))
                } else {
                    setImageURI(shot.uri)
                }
            }
        }

        fun systemBar(name: String): Int {
            val id = service.resources.getIdentifier(name, "dimen", "android")
            return if (id != 0) service.resources.getDimensionPixelSize(id) else 0
        }

        // Full screen and opaque; it stays until Share, Save or Share & Save (Crop and Draw open
        // the editor, which takes over from it).
        val page = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.BLACK)
            setPadding(dp(16), systemBar("status_bar_height") + dp(12), dp(16), systemBar("navigation_bar_height") + dp(16))
            // Touches don't fall through to the app underneath.
            isClickable = true
            addView(LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                fun weighted(end: Int) = LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = end }
                addView(pill("Share", false) { share(shot, keep = false) }, weighted(dp(8)))
                addView(pill("Save", true) { save() }, weighted(dp(8)))
                addView(pill("Share & Save", false) { share(shot, keep = true) }, weighted(0))
            })
            addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(16)
                bottomMargin = dp(16)
            })
            addView(LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                addView(bubble(R.drawable.vv_ic_crop, "Crop") { openEditor(shot, EditorActivity.START_CROP) },
                    LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(bubble(R.drawable.vv_ic_draw, "Draw") { openEditor(shot, EditorActivity.START_DRAW) },
                    LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        runCatching {
            service.getSystemService(WindowManager::class.java).addView(page, params)
            card = page
        }.onFailure { ViperLog.error("SCREENSHOT", "couldn't show the toolbar", it) }
    }

    fun hide() {
        card?.let { view -> runCatching { service.getSystemService(WindowManager::class.java)?.removeView(view) } }
        card = null
    }

    /** Save: the screenshot is already saved, so this just closes the card. */
    private fun save() {
        hide()
        Toast.makeText(service, "Screenshot saved", Toast.LENGTH_SHORT).show()
    }

    /**
     * Shares a private copy (so the app shared to can still read it after the screenshot itself
     * is deleted). Without [keep], the screenshot is deleted once an app is picked; backing out of
     * the share sheet keeps it.
     */
    private fun share(shot: Shot, keep: Boolean) {
        hide()
        runCatching {
            val folder = File(service.filesDir, SHARE_FOLDER).apply { mkdirs() }
            // Copies from earlier shares are only needed while those apps read them.
            folder.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > KEEP_SHARED_COPY_MS }?.forEach { it.delete() }
            val copy = File(folder, shot.name)
            resolver.openInputStream(shot.uri)?.use { input -> copy.outputStream().use { input.copyTo(it) } }
                ?: error("couldn't read the screenshot")
            val shared = FileProvider.getUriForFile(service, service.getString(com.t8rin.imagetoolbox.core.resources.R.string.file_provider), copy)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = resolver.getType(shot.uri) ?: "image/png"
                putExtra(Intent.EXTRA_STREAM, shared)
                clipData = ClipData.newRawUri(null, shared)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val picked = PendingIntent.getBroadcast(
                service,
                shot.id.toInt(),
                Intent(service, ScreenshotSharedReceiver::class.java)
                    .putExtra(EXTRA_URI, shot.uri)
                    .putExtra(EXTRA_PATH, shot.path)
                    .putExtra(EXTRA_DELETE, !keep),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            service.startActivity(
                Intent.createChooser(send, null, picked.intentSender)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
            ViperLog.i("SCREENSHOT", if (keep) "Share & Save" else "Share (delete after)")
        }.onFailure {
            ViperLog.error("SCREENSHOT", "share failed", it)
            Toast.makeText(service, "Couldn't share: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openEditor(shot: Shot, start: String) {
        hide()
        runCatching {
            service.startActivity(
                Intent(service, EditorActivity::class.java)
                    .setData(shot.uri)
                    .putExtra(EditorActivity.EXTRA_REAL_PATH, shot.path)
                    .putExtra(EditorActivity.EXTRA_START, start)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }.onFailure { ViperLog.error("SCREENSHOT", "couldn't open the editor", it) }
    }

    companion object {
        const val PREF_ENABLED = "vv_screenshot_toolbar"
        private const val MAX_AGE_SECONDS = 15
        private const val SHARE_FOLDER = "vv_shared_screenshots"
        private const val KEEP_SHARED_COPY_MS = 24L * 60 * 60 * 1000
        internal const val EXTRA_URI = "com.vipervision.editor.SCREENSHOT_URI"
        internal const val EXTRA_PATH = "com.vipervision.editor.SCREENSHOT_PATH"
        internal const val EXTRA_DELETE = "com.vipervision.editor.SCREENSHOT_DELETE"

        /**
         * Deletes the screenshot: through the media library, or its file; if Android wants the
         * user's OK, [ScreenshotDeleteActivity] asks for it.
         */
        internal fun delete(context: Context, uri: Uri, path: String?) {
            val deleted = runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false) ||
                    (path != null && runCatching {
                        File(path).delete().also { if (it) MediaScannerConnection.scanFile(context, arrayOf(path), null, null) }
                    }.getOrDefault(false))
            if (deleted) {
                ViperLog.i("SCREENSHOT", "Deleted after sharing")
                Toast.makeText(context, "Shared, screenshot deleted", Toast.LENGTH_SHORT).show()
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.startActivity(
                    Intent(context, ScreenshotDeleteActivity::class.java)
                        .putExtra(EXTRA_URI, uri)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } else {
                ViperLog.i("SCREENSHOT", "Couldn't delete the screenshot after sharing")
            }
        }
    }
}

/** Told by the share sheet which app was picked: then the screenshot is deleted (Share only). */
class ScreenshotSharedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!intent.getBooleanExtra(ScreenshotToolbar.EXTRA_DELETE, false)) return
        @Suppress("DEPRECATION")
        val uri = intent.getParcelableExtra<Uri>(ScreenshotToolbar.EXTRA_URI) ?: return
        ScreenshotToolbar.delete(context, uri, intent.getStringExtra(ScreenshotToolbar.EXTRA_PATH))
    }
}

/**
 * Invisible: shows Android's "Allow Viper Vision to delete this screenshot?" when the app may not
 * delete it on its own (no "All files access" / "Media management"), then closes.
 */
class ScreenshotDeleteActivity : ComponentActivity() {

    private val request = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ViperLog.i("SCREENSHOT", "Deleted after sharing (with permission)")
            Toast.makeText(this, "Shared, screenshot deleted", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val uri = intent.getParcelableExtra<Uri>(ScreenshotToolbar.EXTRA_URI)
        if (uri == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R || savedInstanceState != null) {
            if (savedInstanceState == null) finish()
            return
        }
        runCatching {
            val sender = MediaStore.createDeleteRequest(contentResolver, listOf(uri)).intentSender
            request.launch(IntentSenderRequest.Builder(sender).build())
        }.onFailure {
            ViperLog.error("SCREENSHOT", "couldn't ask to delete the screenshot", it)
            finish()
        }
    }
}
