package com.shadeui.app.overlay

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.RoundedCorner
import android.view.VelocityTracker
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import kotlin.math.abs
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import com.shadeui.app.ShadeApp
import com.shadeui.app.data.AppRule
import com.shadeui.app.data.LockStyle
import com.shadeui.app.data.LockVisibility
import com.shadeui.app.data.PopupStyle
import com.shadeui.app.data.PullArea
import com.shadeui.app.data.LightStyle
import com.shadeui.app.notif.NotifAction
import com.shadeui.app.notif.NotifItem
import com.shadeui.app.notif.NotifListener
import com.shadeui.app.tiles.TileKind
import com.shadeui.app.tiles.Tiles
import com.shadeui.app.ui.EdgeLighting
import com.shadeui.app.ui.LockNotifications
import com.shadeui.app.ui.MainActivity
import com.shadeui.app.ui.PopupView
import com.shadeui.app.ui.ShadePanel
import com.shadeui.app.ui.ShadeTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class PopupState(val item: NotifItem, val style: PopupStyle, val hideContent: Boolean, val id: Long)

data class LightState(
    val colors: List<Int>,
    val style: LightStyle,
    val thicknessPx: Float,
    val opacity: Float,
    val repeats: Int,
    val cornerPx: Float,
    val id: Long,
)

/** Compose-observable state shared by all overlay windows. */
class OverlayUi {
    /** How far the panel is pulled out: 0 = hidden, 1 = fully open. Follows the finger. */
    var progress by mutableFloatStateOf(0f)
    var expanded by mutableStateOf(false)
    var locked by mutableStateOf(false)
    var replyKey by mutableStateOf<String?>(null)
    var popup by mutableStateOf<PopupState?>(null)
    var lighting by mutableStateOf<LightState?>(null)
    var message by mutableStateOf<String?>(null)
}

/**
 * The always-running service that draws Shade: the swipe-down trigger strip, the panel, pop-ups,
 * edge lighting and lock screen notifications, all as accessibility overlay windows (which sit
 * above the status bar and the lock screen). Android restarts it after every reboot.
 */
class OverlayService : AccessibilityService() {
    val ui = OverlayUi()
    private val app get() = ShadeApp.instance
    private lateinit var wm: WindowManager
    private val owner = OverlayOwner()
    private val scope = MainScope()
    private var counter = 0L

    private var strip: View? = null
    private var stripParams: WindowManager.LayoutParams? = null
    private var shadeView: View? = null
    private var popupView: View? = null
    private var edgeView: View? = null
    private var lockView: View? = null
    private var popupJob: Job? = null
    private var closing = false
    private var lockDismissed = false

    /** App in the foreground, to skip pop-ups for the app you're already looking at. */
    var foregroundPackage: String? = null
        private set
    var statusBarVisible = true
        private set
    val isShadeOpen get() = shadeView != null && !closing

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    lockDismissed = false
                    closeShade(immediate = true)
                    hidePopup()
                }
                Intent.ACTION_USER_PRESENT -> ui.locked = false
            }
            updateLock()
        }
    }

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(WindowManager::class.java)
        owner.start()
        addStrip()
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        scope.launch {
            var lastArea: PullArea? = null
            app.settings.flow.collectLatest { s ->
                if (!s.enabled) {
                    // Master switch off: remove everything; the service just idles.
                    lastArea = null
                    turnEverythingOff()
                    return@collectLatest
                }
                if (strip == null || s.pullArea != lastArea) {
                    lastArea = s.pullArea
                    addStrip()
                }
                updateStripTouchable()
                updateLock()
            }
        }
        scope.launch { app.notifs.flow.collectLatest { updateLock() } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            // On the lock screen Samsung's lock screen *is* the panel window, so only trust the
            // window check while unlocked.
            val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
            if (!locked && samsungPanelWindowOpen()) replaceSamsungPanel("window")
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg.contains("systemui")) {
            val text = (event.text.joinToString(" ") + " " + (event.contentDescription ?: "")).trim()
            Diagnostics.add("state: ${event.className} \"$text\"")
            if (isPanelText(text)) replaceSamsungPanel("event")
            return
        }
        if (pkg == packageName || pkg == currentKeyboard()) return
        foregroundPackage = pkg
        // Home, recents or another app came up: close the panel, like the real one.
        if (isShadeOpen && !SamsungTileTapper.busy) closeShade()
    }

    override fun onInterrupt() {}

    // ---- Keeping Samsung's own panel hidden ----

    private var lastDismiss = 0L

    /** Samsung's panel names, as Samsung announces them to accessibility. */
    private fun isPanelText(text: String): Boolean {
        val t = text.lowercase()
        return PANEL_WORDS.any { it in t }
    }

    private fun samsungPanelWindowOpen(): Boolean = runCatching {
        windows.any { w ->
            val title = w.title?.toString().orEmpty()
            val root = w.root
            val isSystemUi = root?.packageName?.contains("systemui") == true
            if (isSystemUi && title.isNotEmpty()) Diagnostics.add("window: \"$title\"")
            isSystemUi && isPanelText(title)
        }
    }.getOrDefault(false)

    /**
     * Samsung's panel opened (a swipe on the status bar, or the home-screen swipe-down gesture):
     * close it straight away and show Shade's instead.
     */
    private fun replaceSamsungPanel(reason: String) {
        val s = app.settings.value
        if (!s.enabled || !s.replaceSamsungPanel || SamsungTileTapper.busy) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastDismiss < 250) return
        lastDismiss = now
        Diagnostics.add("replacing Samsung's panel ($reason)")
        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        if (!isShadeOpen) openShade()
    }

    /** Samsung's panel can follow the same swipe a moment later; close it a few times. */
    private fun keepSamsungPanelClosed() {
        scope.launch {
            for (wait in listOf(80L, 220L, 450L)) {
                delay(wait)
                if (!isShadeOpen || SamsungTileTapper.busy) return@launch
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        addStrip()
        updateLock(force = true)
    }

    override fun onDestroy() {
        instance = null
        runCatching { unregisterReceiver(receiver) }
        listOf(strip, shadeView, popupView, edgeView, lockView).forEach { v ->
            v?.let { runCatching { wm.removeView(it) } }
        }
        scope.cancel()
        owner.stop()
        super.onDestroy()
    }

    private fun turnEverythingOff() {
        strip?.let { runCatching { wm.removeView(it) } }
        strip = null
        closeShade(immediate = true)
        hidePopup()
        ui.lighting?.let { lightingFinished(it.id) }
        updateLock()
    }

    // ---- Windows ----

    private fun params(width: Int, height: Int, flags: Int, gravity: Int = Gravity.TOP or Gravity.START) =
        WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = gravity
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            setFitInsetsTypes(0)
            setFitInsetsSides(0)
        }

    private fun composeView(content: @Composable () -> Unit): ComposeView =
        ComposeView(this).apply {
            id = View.generateViewId()
            setContent { ShadeTheme { content() } }
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun statusBarHeight(): Int {
        @Suppress("DiscouragedApi", "InternalInsetResource")
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(32)
    }

    /** Invisible strip over the status bar: swiping down on it opens Shade. */
    private fun addStrip() {
        strip?.let { runCatching { wm.removeView(it) } }
        strip = null
        val s = app.settings.value
        if (!s.enabled) return
        val screenWidth = wm.currentWindowMetrics.bounds.width()
        val (width, gravity) = when (s.pullArea) {
            PullArea.FULL -> WindowManager.LayoutParams.MATCH_PARENT to (Gravity.TOP or Gravity.START)
            PullArea.LEFT_HALF -> screenWidth / 2 to (Gravity.TOP or Gravity.START)
            PullArea.RIGHT_HALF -> screenWidth / 2 to (Gravity.TOP or Gravity.END)
        }
        val view = View(this)
        var downY = 0f
        var tracking = false
        var velocity: VelocityTracker? = null
        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = e.rawY
                    tracking = false
                    velocity?.recycle()
                    velocity = VelocityTracker.obtain().also { it.addMovement(e) }
                }
                MotionEvent.ACTION_MOVE -> {
                    velocity?.addMovement(e)
                    val dy = e.rawY - downY
                    if (!tracking && dy > dp(6)) {
                        tracking = true
                        openShade(expanded = false, animate = false)
                    }
                    if (tracking) {
                        // The panel follows the finger.
                        setPanelProgress(dy / pullRange())
                        // Keep pulling well past "fully open" for the full quick settings.
                        if (dy > pullRange() * 1.4f) ui.expanded = true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    velocity?.addMovement(e)
                    if (tracking) {
                        velocity?.computeCurrentVelocity(1000)
                        releasePanel(velocity?.yVelocity ?: 0f)
                    }
                    velocity?.recycle()
                    velocity = null
                    tracking = false
                }
            }
            true
        }
        val lp = params(
            width,
            statusBarHeight() + dp(4),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            gravity,
        )
        runCatching { wm.addView(view, lp) }
        strip = view
        stripParams = lp
        updateStripTouchable()
    }

    private fun updateStripTouchable() {
        val v = strip ?: return
        val lp = stripParams ?: return
        val s = app.settings.value
        val passThrough = isShadeOpen || !s.enabled
        val flags = if (passThrough) {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        if (flags != lp.flags) {
            lp.flags = flags
            runCatching { wm.updateViewLayout(v, lp) }
        }
    }

    /** Root that turns the Back key/gesture into "close" (or "collapse") for the panel. */
    private inner class BackCatcher : FrameLayout(this@OverlayService) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) {
                    when {
                        ui.replyKey != null -> ui.replyKey = null
                        ui.expanded -> ui.expanded = false
                        else -> closeShade()
                    }
                }
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    // ---- Panel position: dragged by the finger, then settles open or closed ----

    private var animator: ValueAnimator? = null
    private var shadeParams: WindowManager.LayoutParams? = null
    private var lastBlur = -1

    /** Finger travel that pulls the panel fully out (about half the screen). */
    fun pullRange(): Float = wm.currentWindowMetrics.bounds.height() * 0.5f

    /** True while a finger is moving the panel (and it hasn't settled yet). */
    var panelDragging = false
        private set

    private fun setPanelProgress(p: Float) {
        animator?.cancel()
        ui.progress = p.coerceIn(0f, 1f)
        updateBlur()
    }

    /** Moves the panel by a finger movement from inside it (negative = up, towards closing). */
    fun dragPanelBy(deltaPx: Float) {
        if (shadeView == null) return
        panelDragging = true
        setPanelProgress(ui.progress + deltaPx / pullRange())
    }

    /** Finger lifted: open fully if past halfway (or flicked down), otherwise close. */
    fun releasePanel(velocityY: Float) {
        panelDragging = false
        val open = when {
            velocityY > 1000f -> true
            velocityY < -1000f -> false
            else -> ui.progress >= 0.5f
        }
        settle(open)
        updateStripTouchable()
    }

    private fun settle(open: Boolean) {
        animator?.cancel()
        val from = ui.progress
        val to = if (open) 1f else 0f
        if (!open) closing = true
        animator = ValueAnimator.ofFloat(from, to).apply {
            duration = (300 * abs(to - from)).toLong().coerceIn(90, 300)
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                ui.progress = it.animatedValue as Float
                updateBlur()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled && !open) removeShade()
                }
            })
            start()
        }
    }

    /** Background blur grows with the panel, like Samsung's. */
    private fun updateBlur() {
        val v = shadeView ?: return
        val lp = shadeParams ?: return
        if (!wm.isCrossWindowBlurEnabled) return
        val target = (app.settings.value.blurRadius * ui.progress).toInt()
        if (kotlin.math.abs(target - lastBlur) < 6 && target != 0 && target != app.settings.value.blurRadius) return
        lastBlur = target
        lp.blurBehindRadius = target
        runCatching { wm.updateViewLayout(v, lp) }
    }

    private fun removeShade() {
        animator?.cancel()
        shadeView?.let { runCatching { wm.removeView(it) } }
        shadeView = null
        shadeParams = null
        closing = false
        panelDragging = false
        ui.progress = 0f
        ui.replyKey = null
        lastBlur = -1
        updateStripTouchable()
        updateLock()
    }

    fun openShade(expanded: Boolean = false, animate: Boolean = true) {
        if (!app.settings.value.enabled) return
        if (shadeView != null && !closing) {
            ui.expanded = ui.expanded || expanded
            if (animate) settle(true)
            return
        }
        shadeView?.let { runCatching { wm.removeView(it) } }
        animator?.cancel()
        closing = false
        ui.progress = 0f
        hidePopup()
        haptic()
        app.tiles.refresh()
        ui.locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        ui.expanded = expanded
        ui.replyKey = null
        val root = BackCatcher()
        root.id = View.generateViewId()
        owner.attach(root)
        root.addView(composeView { ShadePanel(this) })
        val s = app.settings.value
        val lp = params(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            if (wm.isCrossWindowBlurEnabled && s.blurRadius > 0) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = 0
            }
        }
        runCatching { wm.addView(root, lp) }
        shadeView = root
        shadeParams = lp
        lastBlur = -1
        // While a finger is pulling from the top strip, leave the strip alone until release.
        if (animate) {
            settle(true)
            updateStripTouchable()
        }
        updateLock()
        keepSamsungPanelClosed()
    }

    fun closeShade(immediate: Boolean = false) {
        if (shadeView == null) return
        ui.replyKey = null
        if (immediate) removeShade() else if (!closing) settle(false)
    }

    fun showPopup(item: NotifItem, rule: AppRule) {
        if (isShadeOpen || !app.settings.value.enabled) return
        val s = app.settings.value
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val hide = locked && (s.lockHideContent || rule.lockScreen == LockVisibility.HIDE_CONTENT)
        ui.popup = PopupState(item, rule.popupStyle ?: s.popupStyle, hide, ++counter)
        if (popupView == null) {
            val root = FrameLayout(this)
            root.id = View.generateViewId()
            owner.attach(root)
            root.addView(composeView { PopupView(this) })
            val lp = params(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            )
            runCatching { wm.addView(root, lp) }
            popupView = root
        }
        popupJob?.cancel()
        popupJob = scope.launch {
            delay(s.popupSeconds * 1000L)
            hidePopup()
        }
    }

    fun hidePopup() {
        popupJob?.cancel()
        if (ui.popup == null && popupView == null) return
        ui.popup = null
        val v = popupView ?: return
        scope.launch {
            delay(300)
            if (ui.popup == null && popupView === v) {
                runCatching { wm.removeView(v) }
                popupView = null
            }
        }
    }

    fun showLighting(item: NotifItem, rule: AppRule) {
        val s = app.settings.value
        if (!s.enabled) return
        val corner = wm.currentWindowMetrics.windowInsets
            .getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: dp(40).toFloat()
        ui.lighting = LightState(
            colors = AlertController.lightColors(item, rule, s),
            style = s.lightStyle,
            thicknessPx = dp(s.lightThicknessDp).toFloat(),
            opacity = s.lightOpacity / 100f,
            repeats = s.lightRepeats.coerceAtLeast(1),
            cornerPx = corner,
            id = ++counter,
        )
        if (edgeView == null) {
            val root = FrameLayout(this)
            root.id = View.generateViewId()
            owner.attach(root)
            root.addView(composeView { EdgeLighting(this) })
            val lp = params(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            )
            runCatching { wm.addView(root, lp) }
            edgeView = root
        }
    }

    fun lightingFinished(id: Long) {
        if (ui.lighting?.id != id) return
        ui.lighting = null
        edgeView?.let { runCatching { wm.removeView(it) } }
        edgeView = null
    }

    /** Lock screen notification cards: shown while locked, hidden as soon as you start unlocking. */
    fun updateLock(force: Boolean = false) {
        val s = app.settings.value
        val screenOn = getSystemService(PowerManager::class.java).isInteractive
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val hasAny = lockItems().isNotEmpty()
        val show = s.enabled && screenOn && locked && s.lockStyle != LockStyle.OFF && !lockDismissed &&
            !isShadeOpen && hasAny
        if (!show || force) {
            lockView?.let { runCatching { wm.removeView(it) } }
            lockView = null
        }
        if (!show || lockView != null) return
        val root = object : FrameLayout(this) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    // A swipe somewhere else (e.g. to unlock): get out of the way.
                    lockDismissed = true
                    updateLock()
                    return false
                }
                return super.dispatchTouchEvent(ev)
            }
        }
        root.id = View.generateViewId()
        owner.attach(root)
        root.addView(composeView { LockNotifications(this) })
        val lp = params(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        ).apply {
            y = wm.currentWindowMetrics.bounds.height() * s.lockTopPercent / 100
        }
        runCatching { wm.addView(root, lp) }
        lockView = root
    }

    /** Notifications allowed on the lock screen, newest first. */
    fun lockItems(): List<NotifItem> = app.notifs.value.filter { n ->
        val rule = app.rules.get(n.pkg)
        !n.isGroupSummary && !n.isMedia && !n.ongoing &&
            rule.alert != com.shadeui.app.data.AlertMode.HIDDEN &&
            rule.lockScreen != LockVisibility.HIDE &&
            !n.isSilent
    }.sortedByDescending { it.time }

    fun onNotificationRemoved(key: String) {
        if (ui.popup?.item?.key == key) hidePopup()
    }

    fun lockScreenNow() {
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    // ---- Actions used by the UI ----

    fun open(item: NotifItem) {
        hidePopup()
        if (app.settings.value.closeAfterOpen) closeShade()
        TrampolineActivity.open(this, item.contentIntent, item.key, item.autoCancel)
    }

    fun runAction(item: NotifItem, action: NotifAction) {
        if (action.isReply) {
            hidePopup()
            openShade()
            ui.replyKey = item.key
            return
        }
        hidePopup()
        closeShade()
        TrampolineActivity.open(this, action.intent)
    }

    fun reply(item: NotifItem, action: NotifAction, text: String) {
        val pi = action.intent ?: return
        val intent = Intent()
        val results = Bundle()
        action.remoteInputs.forEach { results.putCharSequence(it.resultKey, text) }
        RemoteInput.addResultsToIntent(action.remoteInputs.toTypedArray(), intent, results)
        TrampolineActivity.send(this, pi, intent)
        ui.replyKey = null
        toast("Sent")
    }

    fun dismiss(item: NotifItem) {
        NotifListener.instance?.dismiss(item.key)
        app.notifs.remove(item.key)
    }

    fun clearAll() {
        NotifListener.instance?.dismissAll()
    }

    fun snooze(item: NotifItem, minutes: Int) {
        NotifListener.instance?.snooze(item.key, minutes * 60_000L)
        toast("Snoozed for ${if (minutes >= 60) "${minutes / 60} h" else "$minutes min"}")
    }

    /** Opens any screen, unlocking first if needed, and closes the panel. */
    fun launch(intent: Intent) {
        closeShade()
        hidePopup()
        val pi = PendingIntent.getActivity(
            this, (++counter).toInt(),
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        TrampolineActivity.open(this, pi)
    }

    fun openAppNotificationSettings(pkg: String) = launch(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
    )

    fun openShadeRules(pkg: String) = launch(
        Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_APP, pkg),
    )

    fun openTileEditor() = launch(
        Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_SCREEN, "tiles"),
    )

    fun openSystemSettings() = launch(Intent(Settings.ACTION_SETTINGS))

    fun openNotificationSettings() = launch(Intent("android.settings.NOTIFICATION_SETTINGS"))

    fun powerMenu() {
        closeShade(immediate = true)
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    }

    fun toggleTile(id: String) {
        val def = Tiles.get(id) ?: return
        haptic()
        scope.launch {
            if (def.kind == TileKind.DIRECT) {
                app.tiles.toggleDirect(id)?.let { toast(it) }
            } else {
                if (def.momentary) {
                    closeShade(immediate = true)
                    delay(150)
                }
                when (val r = SamsungTileTapper.tap(this@OverlayService, def)) {
                    SamsungTileTapper.Result.Failed -> fallbackToggle(id, def.label)
                    is SamsungTileTapper.Result.Done -> app.tiles.learn(id, r.newState)
                }
            }
            app.tiles.refresh()
            if (app.settings.value.closeAfterTile && !def.momentary) closeShade()
        }
    }

    /**
     * Samsung's tile couldn't be pressed: open Android's own small switch panel for it instead
     * (one extra tap), or the setting's page.
     */
    private fun fallbackToggle(id: String, label: String) {
        val intent = when (id) {
            "wifi" -> Intent(Settings.Panel.ACTION_WIFI)
            "mobile_data", "airplane", "hotspot" -> Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            "nfc" -> Intent(Settings.Panel.ACTION_NFC)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            else -> null
        }
        if (intent != null) {
            toast("Tap the $label switch")
            launch(intent)
        } else {
            toast("Couldn't reach Samsung's $label tile. Make sure it's in Samsung's quick panel.")
        }
    }

    fun openTileSettings(id: String) {
        val action = when (id) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "mobile_data" -> Settings.ACTION_DATA_USAGE_SETTINGS
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "dnd" -> "android.settings.ZEN_MODE_SETTINGS"
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "hotspot" -> Settings.ACTION_WIRELESS_SETTINGS
            "power_saving" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "nfc" -> Settings.ACTION_NFC_SETTINGS
            "smart_view" -> Settings.ACTION_CAST_SETTINGS
            "rotate", "dark_mode", "eye_comfort" -> Settings.ACTION_DISPLAY_SETTINGS
            else -> return
        }
        launch(Intent(action))
    }

    fun brightness(): Int =
        Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)

    fun setBrightness(value: Int): Boolean {
        if (!Settings.System.canWrite(this)) return false
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(1, 255))
        return true
    }

    fun adaptiveBrightness(): Boolean = Settings.System.getInt(
        contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0,
    ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    fun setAdaptiveBrightness(on: Boolean) {
        if (!Settings.System.canWrite(this)) {
            toast("Allow \"Modify system settings\" for Shade in its setup screen.")
            return
        }
        Settings.System.putInt(
            contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
    }

    fun openMediaOutput(pkg: String?) {
        val panel = Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
            .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", pkg ?: "")
        val usable = panel.resolveActivity(packageManager) != null
        launch(if (usable) panel else Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    fun toast(text: String) {
        if (isShadeOpen) {
            ui.message = text
            scope.launch {
                delay(2500)
                if (ui.message == text) ui.message = null
            }
        } else {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
    }

    fun haptic() {
        if (!app.settings.value.haptics) return
        runCatching {
            getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
    }

    private fun currentKeyboard(): String? =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')

    @Suppress("unused")
    private fun appUri(): Uri = Uri.parse("package:$packageName")

    companion object {
        private val PANEL_WORDS = listOf(
            "notification shade", "notification panel", "quick settings", "quick panel",
            "notifications panel", "notification centre", "notification center",
        )

        @Volatile
        var instance: OverlayService? = null
            private set
    }
}
