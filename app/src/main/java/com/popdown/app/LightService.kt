package com.popdown.app

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.view.Display
import android.view.RoundedCorner
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.graphics.drawable.toBitmap

/**
 * Draws Pop Down's own edge lighting. It's an accessibility service only because that's the one
 * kind of window Android lets an app draw over the lock screen and the status bar; it reads
 * nothing from the screen. The window ignores all touches.
 */
class LightService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private var view: EdgeLightView? = null
    private val safetyRemove = Runnable { remove() }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        remove()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        remove()
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    /** Turn the screen back off when the current lighting ends (set per notification). */
    private var offAfter = false

    fun play(spec: LightSpec, turnOffAfter: Boolean = false) = main.post {
        offAfter = turnOffAfter
        val existing = view
        if (existing != null) {
            existing.spec = spec
            existing.restart()
        } else {
            val v = EdgeLightView(this, spec) { finished() }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
                title = "Pop Down lighting"
            }
            runCatching { getSystemService(WindowManager::class.java).addView(v, lp) }
                .onSuccess { view = v }
                .onFailure { Log.add("Lighting: couldn't draw (${it.message})") }
        }
        // If the screen never turns on, don't leave the window waiting forever.
        main.removeCallbacks(safetyRemove)
        main.postDelayed(safetyRemove, if (spec.durationMs == 0L) 10 * 60_000L else spec.durationMs + 15_000)
    }

    private fun finished() {
        remove()
        if (offAfter && isLocked(this) && getSystemService(PowerManager::class.java).isInteractive) {
            offAfter = false
            if (performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)) Log.add("Lighting done: turned the screen back off")
        }
    }

    private fun remove() {
        main.removeCallbacks(safetyRemove)
        val v = view ?: return
        view = null
        runCatching { getSystemService(WindowManager::class.java).removeView(v) }
    }

    companion object {
        @Volatile
        var instance: LightService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, LightService::class.java)
            return flat.split(":").any { ComponentName.unflattenFromString(it) == me }
        }

        /** The phone's own screen corner radius in pixels (the S26 Ultra reports its real curve). */
        fun screenCornerPx(context: Context): Float? = runCatching {
            val display = context.getSystemService(android.hardware.display.DisplayManager::class.java)
                .getDisplay(Display.DEFAULT_DISPLAY)
            listOf(
                RoundedCorner.POSITION_TOP_LEFT, RoundedCorner.POSITION_TOP_RIGHT,
                RoundedCorner.POSITION_BOTTOM_LEFT, RoundedCorner.POSITION_BOTTOM_RIGHT,
            ).mapNotNull { display.getRoundedCorner(it)?.radius }.maxOrNull()?.toFloat()
        }.getOrNull()

        private fun lockedAndOn(app: Context): () -> Boolean =
            { isLocked(app) && screenVisible(app) }

        private fun displayState(context: Context): Int =
            context.getSystemService(android.hardware.display.DisplayManager::class.java)
                .getDisplay(Display.DEFAULT_DISPLAY)?.state ?: Display.STATE_UNKNOWN

        /** The Always On Display is showing (screen "off" but lit in low-power mode). */
        fun onAod(context: Context): Boolean = displayState(context).let {
            it == Display.STATE_DOZE || it == Display.STATE_DOZE_SUSPEND
        }

        /** Always On Display is switched on in settings (Samsung's switch, or Android's own). */
        fun aodEnabled(context: Context): Boolean {
            val cr = context.contentResolver
            val samsung = runCatching { Settings.System.getInt(cr, "aod_mode", 0) == 1 }.getOrDefault(false)
            val android = runCatching { Settings.Secure.getInt(cr, "doze_always_on", 0) == 1 }.getOrDefault(false)
            return samsung || android
        }

        /** Something is on screen: normal use, lock screen or the Always On Display. */
        fun screenVisible(context: Context): Boolean = displayState(context) != Display.STATE_OFF

        fun isLocked(context: Context) = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked

        fun spec(context: Context, l: LightSettings, appColor: Int?, forever: Boolean = false, untilUnlocked: Boolean = false): LightSpec {
            val density = context.resources.displayMetrics.density
            val colors = when (l.colorMode) {
                LightColorMode.APP -> intArrayOf(appColor ?: l.color1)
                LightColorMode.ONE -> intArrayOf(l.color1)
                LightColorMode.TWO -> intArrayOf(l.color1, l.color2)
            }
            val corner = if (l.matchCorners) screenCornerPx(context) ?: (l.cornerDp * density) else l.cornerDp * density
            return LightSpec(
                effect = l.effect,
                colors = colors,
                speed = l.speed,
                thicknessPx = l.thicknessDp * density,
                cornerPx = corner,
                brightness = l.brightness / 100f,
                durationMs = if (forever || untilUnlocked) 0 else l.seconds * 1000L,
                keepGoing = if (untilUnlocked) lockedAndOn(context.applicationContext) else null,
            )
        }

        /**
         * Plays the lighting for a notification; returns why not, or null when it played.
         * [woke] = Pop Down is turning the screen on for this notification.
         */
        fun playFor(context: Context, sbn: StatusBarNotification?, locked: Boolean, woke: Boolean): String? {
            val l = Prefs.get(context).light
            if (!l.enabled) return "lighting is off"
            if (locked && !l.onLocked) return "lighting on the lock screen is off"
            if (!locked && !l.onUnlocked) return "lighting while unlocked is off"
            val service = instance ?: return "Pop Down Lighting isn't switched on in Accessibility"
            val color = sbn?.let { appColor(context, it) }
            val endless = locked && l.untilUnlocked
            service.play(spec(context, l, color, untilUnlocked = endless), turnOffAfter = locked && woke && l.screenOffAfter && !endless)
            return null
        }

        /** The notification's own accent colour, or else the main colour of the app's icon. */
        fun appColor(context: Context, sbn: StatusBarNotification): Int? {
            val c = sbn.notification.color
            if (c != 0 && isColourful(c)) return c or 0xFF000000.toInt()
            return runCatching {
                iconColor(context.packageManager.getApplicationIcon(sbn.packageName).toBitmap(32, 32, Bitmap.Config.ARGB_8888))
            }.getOrNull()
        }

        private fun isColourful(c: Int): Boolean {
            val hsv = FloatArray(3)
            Color.colorToHSV(c, hsv)
            return hsv[1] > 0.25f && hsv[2] > 0.25f
        }

        /** Most common colourful hue in the icon, at full brightness. */
        private fun iconColor(bmp: Bitmap): Int? {
            val buckets = FloatArray(36)
            val sat = FloatArray(36)
            val hsv = FloatArray(3)
            for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
                val p = bmp.getPixel(x, y)
                if (Color.alpha(p) < 128) continue
                Color.colorToHSV(p, hsv)
                if (hsv[1] < 0.3f || hsv[2] < 0.3f) continue
                val b = (hsv[0] / 10f).toInt().coerceIn(0, 35)
                buckets[b] += hsv[1] * hsv[2]
                sat[b] += hsv[1]
            }
            val best = buckets.indices.maxByOrNull { buckets[it] } ?: return null
            if (buckets[best] < 3f) return null
            return Color.HSVToColor(floatArrayOf(best * 10f + 5f, 0.85f, 1f))
        }
    }
}
