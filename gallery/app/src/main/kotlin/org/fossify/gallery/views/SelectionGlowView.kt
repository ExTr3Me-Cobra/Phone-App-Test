package org.fossify.gallery.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.core.graphics.ColorUtils

/**
 * Viper Vision: marks a selected photo or album tile with an outline in the app's colour, a light
 * tint, and a shine sweeping across. The sweep's position comes from one clock and the tile's
 * place on screen, so it moves over all selected tiles together as a single wave.
 */
class SelectionGlowView(context: Context) : View(context) {

    var color: Int = Color.WHITE
        set(value) {
            field = value
            outline.color = value
            tint.color = ColorUtils.setAlphaComponent(value, 46)
            invalidate()
        }

    var cornerRadius = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val strokeWidth = 3.5f * density
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = this@SelectionGlowView.strokeWidth }
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bandWidth = 90f * density
    private val shader = LinearGradient(
        0f, 0f, bandWidth, 0f,
        intArrayOf(Color.TRANSPARENT, Color.argb(90, 255, 255, 255), Color.TRANSPARENT),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP
    ).also { shine.shader = it }
    private val shaderMatrix = Matrix()
    private val rect = RectF()
    private val clip = Path()
    private val onScreen = IntArray(2)

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val inset = strokeWidth / 2f
        rect.set(inset, inset, width - inset, height - inset)
        val radius = (cornerRadius - inset).coerceAtLeast(0f)
        clip.reset()
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)

        canvas.save()
        canvas.clipPath(clip)
        canvas.drawRect(rect, tint)
        // The band runs along the screen's diagonal (x + y); this tile shows its part of it.
        getLocationOnScreen(onScreen)
        val screen = resources.displayMetrics
        val travel = screen.widthPixels + screen.heightPixels + 2 * bandWidth
        val phase = (SystemClock.uptimeMillis() % PERIOD_MS).toFloat() / SWEEP_MS
        if (phase < 1f) {
            val bandAt = -bandWidth + phase * travel - (onScreen[0] + onScreen[1])
            shaderMatrix.setRotate(45f)
            shaderMatrix.preTranslate(bandAt / SQRT2, 0f)
            shader.setLocalMatrix(shaderMatrix)
            canvas.drawRect(rect, shine)
        }
        canvas.restore()
        canvas.drawRoundRect(rect, radius, radius, outline)
        if (isAttachedToWindow && visibility == VISIBLE) postInvalidateOnAnimation()
    }

    companion object {
        private const val SWEEP_MS = 1400L
        private const val PERIOD_MS = 2600L
        private const val SQRT2 = 1.4142135f

        /**
         * Shows or hides the glow on a grid tile ([root], a RelativeLayout), over [thumbnail] and
         * under [keepOnTop] (the check mark).
         */
        fun apply(root: ViewGroup, thumbnail: View, selected: Boolean, color: Int, cornerRadius: Float, keepOnTop: View?) {
            if (root !is RelativeLayout || thumbnail.id == NO_ID) return
            var glow = root.findViewWithTag<SelectionGlowView>(TAG)
            if (!selected) {
                glow?.visibility = GONE
                return
            }
            if (glow == null) {
                glow = SelectionGlowView(root.context).apply { tag = TAG }
                val params = RelativeLayout.LayoutParams(0, 0).apply {
                    addRule(RelativeLayout.ALIGN_LEFT, thumbnail.id)
                    addRule(RelativeLayout.ALIGN_TOP, thumbnail.id)
                    addRule(RelativeLayout.ALIGN_RIGHT, thumbnail.id)
                    addRule(RelativeLayout.ALIGN_BOTTOM, thumbnail.id)
                }
                root.addView(glow, params)
                keepOnTop?.bringToFront()
            }
            glow.color = color
            glow.cornerRadius = cornerRadius
            glow.visibility = VISIBLE
            glow.invalidate()
        }

        private const val TAG = "vv_selection_glow"
    }
}
