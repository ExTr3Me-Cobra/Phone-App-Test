package com.shadeui.app.overlay

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.PowerManager
import androidx.compose.ui.graphics.asAndroidBitmap
import com.shadeui.app.ShadeApp
import com.shadeui.app.data.AlertMode
import com.shadeui.app.data.AppRule
import com.shadeui.app.data.LightColorMode
import com.shadeui.app.data.ShadeSettings
import com.shadeui.app.notif.NotifItem
import java.util.Calendar

/** What should happen for a new notification. */
data class AlertPlan(
    val popup: Boolean,
    val lightUnlocked: Boolean,
    val lightLocked: Boolean,
    val wake: Boolean,
)

/** Applies the global settings and per-app rules to each incoming notification. */
object AlertController {
    fun plan(item: NotifItem, rule: AppRule, s: ShadeSettings): AlertPlan? {
        if (rule.alert == AlertMode.HIDDEN || rule.alert == AlertMode.SILENT) return null
        if (!item.matchesFilter) return null // Do not disturb is holding it back
        if (item.isGroupSummary || item.isMedia) return null
        val popup = when (rule.alert) {
            AlertMode.POPUP -> true
            else -> item.importance >= NotificationManager.IMPORTANCE_HIGH
        }
        // Lighting follows anything that would normally make a sound or pop up.
        val interruptive = rule.alert == AlertMode.POPUP ||
            item.importance >= NotificationManager.IMPORTANCE_DEFAULT
        if (!interruptive) return null
        val quiet = s.quietHoursEnabled && inQuietHours(s)
        return AlertPlan(
            popup = popup,
            lightUnlocked = !quiet && rule.lightUnlocked.resolve(s.defaultLightUnlocked),
            lightLocked = !quiet && rule.lightLocked.resolve(s.defaultLightLocked),
            wake = !quiet && rule.wakeScreen.resolve(s.defaultWakeScreen),
        )
    }

    fun onPosted(context: Context, item: NotifItem, isUpdate: Boolean) {
        if (isUpdate && item.onlyAlertOnce) return
        if (isUpdate && item.ongoing) return
        val app = ShadeApp.instance
        val s = app.settings.value
        if (!s.enabled) return
        val rule = app.rules.get(item.pkg)
        val plan = plan(item, rule, s) ?: return
        val service = OverlayService.instance ?: return

        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val screenOn = power.isInteractive
        val locked = keyguard.isKeyguardLocked

        when {
            !screenOn -> {
                if (plan.wake) {
                    WakeActivity.start(context, s.wakeSeconds)
                    if (plan.lightLocked) service.showLighting(item, rule)
                    if (s.previewWithLighting && rule.lockScreen != com.shadeui.app.data.LockVisibility.HIDE) {
                        service.showPopup(item, rule)
                    }
                }
            }
            locked -> {
                if (plan.lightLocked) service.showLighting(item, rule)
                if (plan.popup && s.popupOnLockScreen &&
                    rule.lockScreen != com.shadeui.app.data.LockVisibility.HIDE
                ) {
                    service.showPopup(item, rule)
                }
            }
            else -> {
                val inApp = s.popupSkipForegroundApp && service.foregroundPackage == item.pkg
                val fullscreenBlocked = !s.popupInFullscreen && !service.statusBarVisible
                if (plan.popup && !inApp && !fullscreenBlocked && !service.isShadeOpen) {
                    service.showPopup(item, rule)
                }
                if (plan.lightUnlocked && !inApp) service.showLighting(item, rule)
            }
        }
    }

    fun onRemoved(key: String) {
        OverlayService.instance?.onNotificationRemoved(key)
    }

    /** Colours for the edge lighting of this notification. */
    fun lightColors(item: NotifItem, rule: AppRule, s: ShadeSettings): List<Int> {
        rule.lightColor?.let { return listOf(it) }
        return when (s.lightColorMode) {
            LightColorMode.CUSTOM -> listOf(s.lightCustomColor)
            LightColorMode.RAINBOW -> listOf(
                0xFFFF5252.toInt(), 0xFFFFD740.toInt(), 0xFF69F0AE.toInt(),
                0xFF40C4FF.toInt(), 0xFFE040FB.toInt(),
            )
            LightColorMode.NOTIFICATION ->
                listOf(item.accentColor.takeIf { Color.alpha(it) > 0 } ?: iconColor(item) ?: s.lightCustomColor)
            LightColorMode.APP_ICON -> listOf(iconColor(item) ?: s.lightCustomColor)
        }
    }

    private val colorCache = HashMap<String, Int?>()

    /** The most vivid colour in the app's icon. */
    private fun iconColor(item: NotifItem): Int? = colorCache.getOrPut(item.pkg) {
        val bmp = item.appIcon?.asAndroidBitmap() ?: return@getOrPut null
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0L
        val hsv = FloatArray(3)
        val step = maxOf(1, bmp.width / 24)
        for (y in 0 until bmp.height step step) {
            for (x in 0 until bmp.width step step) {
                val p = bmp.getPixel(x, y)
                if (Color.alpha(p) < 200) continue
                Color.colorToHSV(p, hsv)
                if (hsv[1] < 0.35f || hsv[2] < 0.35f) continue
                r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++
            }
        }
        if (n == 0L) null else {
            Color.colorToHSV(Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt()), hsv)
            hsv[1] = maxOf(hsv[1], 0.6f)
            hsv[2] = maxOf(hsv[2], 0.9f)
            Color.HSVToColor(hsv)
        }
    }

    private fun inQuietHours(s: ShadeSettings): Boolean {
        val c = Calendar.getInstance()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val start = s.quietStartMinutes
        val end = s.quietEndMinutes
        return if (start <= end) now in start until end else now >= start || now < end
    }
}
