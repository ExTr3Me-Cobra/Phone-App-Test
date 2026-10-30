package com.vipervision.editor

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb

/**
 * Samsung Camera's thumbnail (bottom left) always opens Samsung Gallery. While Samsung Camera is
 * on screen, this puts an invisible button exactly over that thumbnail which opens the newest
 * photo or video from the Camera folder in Viper Vision's viewer instead.
 *
 * It's an accessibility service because that's the only way an app may see which app is on
 * screen and where its buttons are, and draw over it. It only looks at Samsung Camera, only
 * reads the thumbnail and shutter buttons' positions, and does nothing on the lock screen
 * (Samsung keeps handling the thumbnail there, so nothing opens without unlocking).
 */
class CameraThumbnailService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var button: View? = null
    private var shownAt: Rect? = null
    private var cameraOnScreen = false
    private val refresh = Runnable { update() }

    /** Viper Vision's screenshot toolbar (see ScreenshotToolbar), shown after each screenshot. */
    private val screenshots = ScreenshotToolbar(this)

    /**
     * Samsung's own screenshot toolbar showed up while Viper Vision's is on: the gallery's setup
     * asks again to turn it off (MainActivity.showViperSetup, key "vv_samsung_toolbar_off").
     */
    private fun noticedSamsungToolbar() {
        // Smart capture also shows a flash and a small preview after every screenshot; only its
        // toolbar counts.
        val toolbar = runCatching {
            windows.any { window -> window.root?.let(ScreenshotToolbar::isSamsungToolbar) == true }
        }.getOrDefault(false)
        if (!toolbar) return
        screenshots.samsungToolbarSeenAt = System.currentTimeMillis()
        val prefs = getSharedPreferences("Prefs", MODE_PRIVATE)
        if (!prefs.getBoolean(ScreenshotToolbar.PREF_ENABLED, true) || !prefs.getBoolean(SAMSUNG_TOOLBAR_OFF, false)) return
        ViperLog.i("SCREENSHOT", "Samsung's screenshot toolbar is still on; the setup will ask again")
        prefs.edit().putBoolean(SAMSUNG_TOOLBAR_OFF, false).remove(ScreenshotToolbar.CHECK_RESULT).apply()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        screenshots.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Windows opening or closing (e.g. Back from Viper Vision's viewer to the camera): look
        // again at what's in front.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (!refreshPending) schedule(60)
            return
        }
        val pkg = event.packageName?.toString() ?: return
        handoffEvent(pkg, event)
        if (pkg == SAMSUNG_CAPTURE_PACKAGE) {
            noticedSamsungToolbar()
        }
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // The system UI (notifications, keyboard) and our own overlays (plain views) don't
                // count as leaving the camera; Viper Vision's own screens do.
                if (pkg == "com.android.systemui") return
                if (pkg == packageName && event.className?.toString().orEmpty().startsWith("android.")) return
                // Not on the camera's own settings screens, only the viewfinder.
                val screenName = event.className?.toString().orEmpty()
                cameraOnScreen = pkg == CAMERA_PACKAGE && "setting" !in screenName.lowercase()
                if (pkg == CAMERA_PACKAGE) onCameraSettings = "setting" in screenName.lowercase()
                loadingRetries = 0
                // Straight away at the thumbnail's last known spot (it doesn't move between
                // visits), so a quick tap after opening the camera already lands on it.
                if (cameraOnScreen && button == null && !isLocked()) lastBounds()?.let(::show)
                schedule(0)
            }
            // The viewfinder changes constantly; look at most every 60 ms instead of waiting for
            // it to settle (which kept putting the button off while the camera started).
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> if (pkg == CAMERA_PACKAGE && !refreshPending) {
                // Back at the camera after the button was tapped: put it back at once.
                if (button == null && !onCameraSettings && !isLocked() &&
                    rootInActiveWindow?.packageName == CAMERA_PACKAGE
                ) {
                    cameraOnScreen = true
                    lastBounds()?.let(::show)
                }
                schedule(60)
            }
        }
    }

    private var refreshPending = false
    private var onCameraSettings = false

    private fun schedule(delayMs: Long) {
        handler.removeCallbacks(refresh)
        refreshPending = true
        handler.postDelayed(refresh, delayMs)
    }

    private fun isLocked() = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    /** Where the button was last put, kept so it can be shown before the camera's layout is readable. */
    private fun lastBounds(): Rect? = getSharedPreferences("Prefs", MODE_PRIVATE)
        .getString(LAST_BOUNDS, null)?.let(Rect::unflattenFromString)

    private var loadingRetries = 0

    /** Puts the button over the thumbnail, moves it, or removes it. */
    private fun update() {
        refreshPending = false
        // Follows the window in front: the camera's viewfinder (also when coming back to it, e.g.
        // with Back from Viper Vision's viewer, which doesn't always announce the camera again),
        // and not Viper Vision's viewer over it.
        val front = rootInActiveWindow?.packageName?.toString()
        if (front != null) cameraOnScreen = front == CAMERA_PACKAGE && !onCameraSettings
        if (!cameraOnScreen || isLocked()) {
            hide()
            return
        }
        val bounds = findThumbnail()
        if (bounds == null) {
            // The camera's layout isn't readable yet: keep the last known spot and look again soon.
            if (loadingRetries++ < 40) {
                if (button == null) lastBounds()?.let(::show)
                schedule(50)
            } else hide()
            return
        }
        loadingRetries = 0
        if (bounds == shownAt) return
        show(bounds)
        getSharedPreferences("Prefs", MODE_PRIVATE).edit().putString(LAST_BOUNDS, bounds.flattenToString()).apply()
    }

    /**
     * Where the thumbnail is: the camera's own thumbnail button if it describes itself, otherwise
     * the spot left of the shutter where Samsung always puts it (same row, same size).
     */
    private fun findThumbnail(): Rect? {
        val root = windows.firstOrNull { it.root?.packageName == CAMERA_PACKAGE }?.root
            ?: rootInActiveWindow?.takeIf { it.packageName == CAMERA_PACKAGE }
            ?: return null
        // Samsung Camera keeps its layout portrait however the phone is held (only the icons
        // turn), so measure against the camera's own window, not the phone's current orientation
        // (which swaps width and height when the phone is turned).
        val area = Rect().also(root::getBoundsInScreen).takeIf { it.width() > 0 && it.height() > 0 } ?: run {
            val metrics = resources.displayMetrics
            Rect(0, 0, minOf(metrics.widthPixels, metrics.heightPixels), maxOf(metrics.widthPixels, metrics.heightPixels))
        }
        var thumbnail: Rect? = null
        var shutter: Rect? = null
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 40 || !node.isVisibleToUser) return
            val id = node.viewIdResourceName.orEmpty().lowercase()
            val label = node.contentDescription?.toString().orEmpty().lowercase()
            val bounds = Rect().also(node::getBoundsInScreen)
            val inLowerLeft = bounds.centerY() > area.centerY() && bounds.centerX() < area.centerX()
            if (thumbnail == null && inLowerLeft && bounds.width() in 1 until area.width() / 3 &&
                ("thumbnail" in id || "thumbnail" in label || label == "gallery" || "preview" in label)
            ) {
                thumbnail = bounds
            }
            if (shutter == null && ("shutter" in id || "take picture" in label || "shutter" in label || "capture" in label)) {
                shutter = bounds
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it, depth + 1) }
        }
        visit(root, 0)
        thumbnail?.let { return it }
        // Samsung Camera (One UI, portrait): the round thumbnail is ~13% of the screen width
        // across, centred 16% in from the left, on the shutter's row (83% down the screen).
        val size = (area.width() * 0.15f).toInt()
        val centerX = area.left + (area.width() * 0.16f).toInt()
        val centerY = shutter?.centerY() ?: (area.top + area.height() * 0.83f).toInt()
        return Rect(centerX - size / 2, centerY - size / 2, centerX + size / 2, centerY + size / 2)
    }

    private fun show(bounds: Rect) {
        val windowManager = getSystemService(WindowManager::class.java) ?: return
        val params = WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.left
            y = bounds.top
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val view = button
        if (view == null) {
            val created = View(this).apply {
                contentDescription = "Open in Viper Vision"
                setOnClickListener {
                    // Gone the moment it's pressed, so repeated taps can't open more viewers;
                    // it comes back only when the camera comes back.
                    cameraOnScreen = false
                    hide()
                    openNewest()
                }
            }
            runCatching { windowManager.addView(created, params) }.onSuccess { button = created }
        } else {
            runCatching { windowManager.updateViewLayout(view, params) }
        }
        shownAt = bounds
    }

    private fun hide() {
        button?.let { view -> runCatching { getSystemService(WindowManager::class.java)?.removeView(view) } }
        button = null
        shownAt = null
    }

    /** Opens the newest photo or video in DCIM/Camera in Viper Vision's viewer. */
    private fun openNewest() {
        val path = newestCameraFile()
        if (path == null) {
            Toast.makeText(this, "No photos or videos in the Camera folder yet", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent().apply {
            setClassName(packageName, VIEWER_ACTIVITY)
            putExtra("path", path)
            putExtra("show_all", false)
            // The viewer closes Viper Vision when you leave it with Back or Home.
            putExtra("vv_from_camera_shortcut", true)
            // Its own window, so closing it never closes a Gallery you already had open.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        runCatching { startActivity(intent) }.onFailure {
            ViperLog.error("CAMERA", "openNewest failed", it)
            Toast.makeText(this, "Couldn't open Viper Vision: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun newestCameraFile(): String? {
        val collection = MediaStore.Files.getContentUri("external")
        @Suppress("DEPRECATION")
        val data = MediaStore.MediaColumns.DATA
        val where = "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} OR " +
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}) AND " +
                "$data LIKE '%/DCIM/Camera/%'"
        return runCatching {
            contentResolver.query(
                collection,
                arrayOf(MediaStore.Files.FileColumns._ID, data),
                where,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC, ${MediaStore.Files.FileColumns._ID} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(1) else null
            }
        }.getOrNull()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        schedule(300)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        screenshots.stop()
        handler.removeCallbacks(refresh)
        handler.removeCallbacks(handoffRefresh)
        hide()
        hideReturnButton()
        super.onDestroy()
    }

    // ── Samsung editor handoff (see SamsungHandoff) ─────────────────────────────────────────

    private val handoffRefresh = Runnable { handoffUpdate() }
    private var returnButton: View? = null
    private var lastScreenDump = ""

    private fun handoffEvent(pkg: String, event: AccessibilityEvent) {
        val session = SamsungHandoff.active
        if (session == null) {
            hideReturnButton()
            return
        }
        when {
            pkg in SamsungHandoff.PACKAGES -> {
                handler.removeCallbacks(handoffRefresh)
                handler.postDelayed(handoffRefresh, if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) 300 else 400)
            }
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != packageName && pkg != "com.android.systemui" ->
                hideReturnButton()
        }
    }

    private fun samsungRoot(): AccessibilityNodeInfo? =
        windows.firstNotNullOfOrNull { window -> window.root?.takeIf { it.packageName in SamsungHandoff.PACKAGES } }
            ?: rootInActiveWindow?.takeIf { it.packageName in SamsungHandoff.PACKAGES }

    /** In Samsung's editor: show the Back button, and tap Galaxy AI then the chosen tool once. */
    private fun handoffUpdate() {
        val session = SamsungHandoff.active ?: return hideReturnButton()
        val root = samsungRoot() ?: return
        logScreen(root)
        // The button appears only on Photo assist's last screen, exactly over Samsung's Save copy
        // (it does that job, then comes back). Anywhere else it stays hidden.
        val saveArea = findNode(root, SAVE_LABELS, exact = true)?.let(::tappableBounds)
        if (saveArea != null) showReturnButton(saveArea) else hideReturnButton()
        if (session.toolOpened || session.returnRequested || session.tapAttempts >= MAX_TAP_ATTEMPTS) return
        session.tapAttempts++
        val tool = findNode(root, session.tool.samsungLabels, exact = true)
        if (tool != null) {
            ViperLog.i("SAMSUNG", "Tapping the tool: ${describe(tool)} (tapped: ${click(tool)})")
            // Once only: the label stays on screen afterwards, and tapping again does nothing useful.
            session.toolOpened = true
            return
        }
        if (!session.galaxyAiTapped) {
            val galaxyAi = findNode(root, GALAXY_AI_LABELS, exact = false)
            if (galaxyAi != null) {
                ViperLog.i("SAMSUNG", "Tapping Galaxy AI: ${describe(galaxyAi)}")
                session.galaxyAiTapped = click(galaxyAi)
                return
            }
            // In Gallery's viewer without a Galaxy AI button: open Samsung's editor first.
            if (!session.editTapped) {
                val edit = findNode(root, EDIT_LABELS, exact = true)
                if (edit != null) {
                    ViperLog.i("SAMSUNG", "Tapping Edit: ${describe(edit)}")
                    session.editTapped = click(edit)
                }
            }
        }
    }

    /** Writes what's on Samsung's screen to the diagnostic log (once per screen), for tuning. */
    private fun logScreen(root: AccessibilityNodeInfo) {
        val lines = mutableListOf<String>()
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 30 || lines.size >= 80) return
            val text = node.text?.toString().orEmpty()
            val desc = node.contentDescription?.toString().orEmpty()
            val id = node.viewIdResourceName?.substringAfter(":id/").orEmpty()
            if (text.isNotEmpty() || desc.isNotEmpty() || (id.isNotEmpty() && node.isClickable)) {
                lines += "[${text}|${desc}|${id}${if (node.isClickable) "|tap" else ""}]"
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it, depth + 1) }
        }
        visit(root, 0)
        val dump = lines.joinToString(" ")
        if (dump != lastScreenDump) {
            lastScreenDump = dump
            ViperLog.i("SAMSUNG", "Screen (${root.packageName}): $dump")
        }
    }

    private fun findNode(root: AccessibilityNodeInfo, labels: List<String>, exact: Boolean): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        fun matches(value: CharSequence?): Boolean {
            val v = value?.toString()?.trim()?.lowercase() ?: return false
            return labels.any { label -> if (exact) v == label.lowercase() else label.lowercase() in v }
        }
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (found != null || depth > 30) return
            if (node.isVisibleToUser && (matches(node.text) || matches(node.contentDescription))) {
                found = node
                return
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it, depth + 1) }
        }
        visit(root, 0)
        return found
    }

    /** Taps [node], or the nearest tappable thing around it. */
    private fun click(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        repeat(6) {
            val current = target ?: return false
            if (current.isClickable) return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            target = current.parent
        }
        return false
    }

    private fun describe(node: AccessibilityNodeInfo) =
        "${node.text ?: ""}|${node.contentDescription ?: ""}|${node.viewIdResourceName ?: ""}"

    /** Where [node] can be tapped: its own bounds, or those of the tappable thing around it. */
    private fun tappableBounds(node: AccessibilityNodeInfo): Rect {
        var target: AccessibilityNodeInfo? = node
        repeat(6) {
            val current = target ?: return Rect().also(node::getBoundsInScreen)
            if (current.isClickable) return Rect().also(current::getBoundsInScreen)
            target = current.parent
        }
        return Rect().also(node::getBoundsInScreen)
    }

    private var returnButtonArea: Rect? = null
    private var returnButtonShown = false

    /**
     * The Back to Viper Vision button, in the app's colour (Customise colours). Over [cover]
     * exactly when given (Samsung's Save copy button), otherwise centred near the top.
     */
    private fun showReturnButton(cover: Rect?) {
        if (returnButtonShown && cover == returnButtonArea) return
        val windowManager = getSystemService(WindowManager::class.java) ?: return
        val density = resources.displayMetrics.density
        val accent = appAccentColor(this)
        val button = (returnButton as? android.widget.TextView) ?: android.widget.TextView(this).apply {
            text = "← Back to Viper Vision"
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding((14 * density).toInt(), (8 * density).toInt(), (14 * density).toInt(), (8 * density).toInt())
            elevation = 8 * density
            setOnClickListener { returnToViper() }
        }
        button.setTextColor(accent.contentOn().toArgb())
        button.textSize = 15f
        if (cover != null) {
            // Shrink the text if Samsung's button is narrower than the label.
            button.setAutoSizeTextTypeUniformWithConfiguration(10, 16, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        } else {
            button.setAutoSizeTextTypeWithDefaults(android.widget.TextView.AUTO_SIZE_TEXT_TYPE_NONE)
            button.textSize = 15f
        }
        button.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = cover?.height()?.div(2f) ?: (50 * density)
            setColor(accent.toArgb())
        }
        val params = WindowManager.LayoutParams(
            cover?.width() ?: WindowManager.LayoutParams.WRAP_CONTENT,
            cover?.height() ?: WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (cover != null) {
                gravity = Gravity.TOP or Gravity.START
                x = cover.left
                y = cover.top
            } else {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                // Below the status bar, clear of Samsung's top buttons.
                y = (90 * density).toInt()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        runCatching {
            if (returnButton == null) windowManager.addView(button, params) else windowManager.updateViewLayout(button, params)
        }.onSuccess {
            returnButton = button
            returnButtonShown = true
            returnButtonArea = cover
            if (cover != null) ViperLog.i("SAMSUNG", "Back button placed over Save copy at $cover")
        }.onFailure { ViperLog.error("SAMSUNG", "couldn't show the Back button", it) }
    }

    private fun hideReturnButton() {
        returnButton?.let { view -> runCatching { getSystemService(WindowManager::class.java)?.removeView(view) } }
        returnButton = null
        returnButtonShown = false
        returnButtonArea = null
    }

    /**
     * The Back button: taps Samsung's Done (finishes the AI edit) and Save, then brings Viper
     * Vision's editor back, which takes in what Samsung saved.
     */
    private fun returnToViper() {
        val session = SamsungHandoff.active ?: return hideReturnButton()
        val root = samsungRoot()
        // Photo assist's last screen offers "Save copy" (and "Keep editing"); the main editor has Save.
        val save = root?.let { findNode(it, SAVE_LABELS, exact = true) }
        if (save == null) {
            ViperLog.i("SAMSUNG", "Back button tapped, but there's no Save copy / Save on screen yet")
            android.widget.Toast.makeText(
                this,
                "Finish the edit in Samsung first (tap Erase or Generate), then tap Back to Viper Vision",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
        session.returnRequested = true
        hideReturnButton()
        ViperLog.i("SAMSUNG", "Back button tapped; tapping ${describe(save)} (tapped: ${click(save)})")
        // Some versions then ask how to save; prefer a copy (Viper deletes it after taking it in).
        handler.postDelayed({
            samsungRoot()?.let { findNode(it, listOf("Save as copy", "Save copy"), exact = true) }?.let {
                ViperLog.i("SAMSUNG", "Then tapping ${describe(it)} (tapped: ${click(it)})")
            }
            handler.postDelayed({ bringViperBack() }, 1500)
        }, 1200)
    }

    private fun bringViperBack() {
        ViperLog.i("SAMSUNG", "Bringing Viper Vision's editor back")
        runCatching {
            startActivity(
                Intent().setClassName(packageName, EditorActivity::class.java.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }.onFailure { ViperLog.error("SAMSUNG", "couldn't bring the editor back", it) }
    }

    private companion object {
        const val MAX_TAP_ATTEMPTS = 40
        val GALAXY_AI_LABELS = listOf("Galaxy AI", "Photo Assist", "Generative edit", "AI edit")
        val EDIT_LABELS = listOf("Edit")
        val SAVE_LABELS = listOf("Save copy", "Save as copy", "Save", "Done")
        const val CAMERA_PACKAGE = "com.sec.android.app.camera"
        const val LAST_BOUNDS = "vv_camera_thumbnail_bounds"
        const val SAMSUNG_CAPTURE_PACKAGE = ScreenshotToolbar.SAMSUNG_CAPTURE_PACKAGE
        const val SAMSUNG_TOOLBAR_OFF = "vv_samsung_toolbar_off"
        const val VIEWER_ACTIVITY = "org.fossify.gallery.activities.ViewPagerActivity"
    }
}
