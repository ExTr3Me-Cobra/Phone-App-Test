package com.glowalerts.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.View
import kotlin.math.max

/**
 * Shows the camera-hole ring live on the real screen while you adjust it in the settings, so you
 * can line it up exactly. Sits over the app's own window and ignores touches.
 */
@SuppressLint("ViewConstructor")
class CamRingView(context: Context) : View(context) {
    var settings: Settings? = null
        set(value) {
            field = value
            invalidate()
        }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val where = IntArray(2)

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        val s = settings ?: return
        if (!s.camRing) return
        val density = resources.displayMetrics.density
        val hole = runCatching {
            context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
                ?.cutout?.boundingRects?.filter { !it.isEmpty }?.minByOrNull { it.width() * it.height() }
        }.getOrNull()
        // Same position maths as the real lighting, in screen pixels…
        val camX = hole?.exactCenterX() ?: (resources.displayMetrics.widthPixels / 2f)
        val camY = hole?.exactCenterY() ?: (34f * density)
        val camR = hole?.let { max(it.width(), it.height()) / 2f } ?: (14f * density)
        // …moved into this view's own coordinates.
        getLocationOnScreen(where)
        val cx = camX + s.camOffsetX * density - where[0]
        val cy = camY + s.camOffsetY * density - where[1]
        val rw = max(1f, s.camRingDp * density)
        val radius = max(1f, camR + s.camSizeAdjust * density + rw * 0.5f)
        val color = s.color1 or 0xFF000000.toInt()

        if (s.camRingGlow > 0.05f) {
            glowPaint.color = color
            glowPaint.alpha = 180
            glowPaint.strokeWidth = rw * 2.5f
            glowPaint.maskFilter = BlurMaskFilter(max(1f, rw * 2f * s.camRingGlow), BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(cx, cy, radius, glowPaint)
        }
        linePaint.color = color
        linePaint.strokeWidth = rw
        canvas.drawCircle(cx, cy, radius, linePaint)
    }
}
