package com.glowalerts.app

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.view.Display
import android.view.RoundedCorner
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.graphics.drawable.toBitmap

/**
 * Draws the lighting. It's an accessibility service only because that's the one kind of window
 * Android lets an app draw over the lock screen, the Always On Display and the status bar; it
 * reads nothing from the screen. The window ignores all touches.
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

    fun play(spec: LightSpec) = main.post {
        val existing = view
        if (existing != null) {
            existing.spec = spec
            existing.restart()
        } else {
            val v = EdgeLightView(this, spec) { remove() }
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
                title = "Glow Alerts lighting"
            }
            runCatching { getSystemService(WindowManager::class.java).addView(v, lp) }
                .onSuccess { view = v }
                .onFailure { Log.add("Lighting: couldn't draw (${it.message})") }
        }
        // If the screen never lights up, don't leave the window waiting forever.
        main.removeCallbacks(safetyRemove)
        main.postDelayed(safetyRemove, spec.durationMs + 20_000)
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
            val flat = AndroidSettings.Secure.getString(
                context.contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = ComponentName(context, LightService::class.java)
            return flat.split(":").any { ComponentName.unflattenFromString(it) == me }
        }

        /** The phone's own screen corner radius in pixels (the S26 Ultra reports its real curve). */
        fun screenCornerPx(context: Context): Float? = runCatching {
            val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            listOf(
                RoundedCorner.POSITION_TOP_LEFT, RoundedCorner.POSITION_TOP_RIGHT,
                RoundedCorner.POSITION_BOTTOM_LEFT, RoundedCorner.POSITION_BOTTOM_RIGHT,
            ).mapNotNull { display.getRoundedCorner(it)?.radius }.maxOrNull()?.toFloat()
        }.getOrNull()

        private fun displayState(context: Context): Int =
            context.getSystemService(DisplayManager::class.java)
                .getDisplay(Display.DEFAULT_DISPLAY)?.state ?: Display.STATE_UNKNOWN

        /** The Always On Display is showing (screen "off" but lit in low-power mode). */
        fun onAod(context: Context): Boolean = displayState(context).let {
            it == Display.STATE_DOZE || it == Display.STATE_DOZE_SUSPEND
        }

        /** Always On Display is switched on in settings (Samsung's switch, or Android's own). */
        fun aodEnabled(context: Context): Boolean {
            val cr = context.contentResolver
            val samsung = runCatching { AndroidSettings.System.getInt(cr, "aod_mode", 0) == 1 }.getOrDefault(false)
            val android = runCatching { AndroidSettings.Secure.getInt(cr, "doze_always_on", 0) == 1 }.getOrDefault(false)
            return samsung || android
        }

        /** Something is on screen: normal use, lock screen or the Always On Display. */
        fun screenVisible(context: Context): Boolean = displayState(context) != Display.STATE_OFF

        fun isLocked(context: Context) = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked

        /** Turns the settings into what the drawing needs. [forever] = the in-app preview. */
        fun spec(context: Context, s: Settings, appColor: Int?, forever: Boolean = false): LightSpec {
            val density = context.resources.displayMetrics.density
            val colors = when (s.colorMode) {
                ColorMode.APP -> intArrayOf(appColor ?: s.color1)
                ColorMode.ONE, ColorMode.RAINBOW -> intArrayOf(s.color1)
                ColorMode.TWO -> intArrayOf(s.color1, s.color2)
            }
            val corner = if (s.matchCorners) screenCornerPx(context) ?: (s.cornerDp * density) else s.cornerDp * density
            return LightSpec(
                effect = s.effect,
                colors = colors,
                speed = s.speed,
                thicknessPx = s.thicknessDp * density,
                cornerPx = corner,
                brightness = s.brightness / 100f,
                durationMs = if (forever) 0 else s.seconds * 1000L,
                glow = s.glow,
                rainbow = s.colorMode == ColorMode.RAINBOW,
                crack = CrackSpec(
                    origin = s.crackOrigin,
                    allAtOnce = s.crackAllAtOnce,
                    lineWidthPx = s.crackLineDp * density,
                    detail = s.crackDetail,
                    flash = s.crackFlash,
                    edgeGlow = s.crackEdgeGlow,
                ),
            )
        }

        /** The notification's own accent colour, or else the main colour of the app's icon. */
        fun appColor(context: Context, color: Int, pkg: String): Int? {
            if (color != 0 && isColourful(color)) return color or 0xFF000000.toInt()
            return runCatching {
                iconColor(context.packageManager.getApplicationIcon(pkg).toBitmap(32, 32, Bitmap.Config.ARGB_8888))
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
            val hsv = FloatArray(3)
            for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
                val p = bmp.getPixel(x, y)
                if (Color.alpha(p) < 128) continue
                Color.colorToHSV(p, hsv)
                if (hsv[1] < 0.3f || hsv[2] < 0.3f) continue
                buckets[(hsv[0] / 10f).toInt().coerceIn(0, 35)] += hsv[1] * hsv[2]
            }
            val best = buckets.indices.maxByOrNull { buckets[it] } ?: return null
            if (buckets[best] < 3f) return null
            return Color.HSVToColor(floatArrayOf(best * 10f + 5f, 0.85f, 1f))
        }
    }
}
