package com.webshortcuts.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** How the picked image is placed on the icon. */
enum class FitMode {
    /** The image covers the whole 108dp adaptive canvas (edges get masked off by the launcher). */
    FILL,

    /** The image is scaled into the 66dp safe zone on a solid background colour. */
    FIT,
}

/**
 * Framing of the image on the icon.
 *
 * [zoom] multiplies the mode's base scale. [offsetX]/[offsetY] move the image's centre, as a
 * fraction of the full canvas width (so framing is independent of output resolution).
 */
data class IconStyle(
    val mode: FitMode = FitMode.FILL,
    val backgroundColor: Int = Color.WHITE,
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

/**
 * Draws icons in the adaptive icon layout launchers expect.
 *
 * An adaptive icon is a 108x108dp square. The launcher scales it so the central 72x72dp region
 * fills the icon slot and cuts it with its mask (circle, squircle, ...). Only the central 66dp
 * circle is guaranteed to be visible under any mask: that is the "safe zone".
 */
object AdaptiveIcon {
    const val CANVAS_DP = 108f
    const val VISIBLE_DP = 72f
    const val SAFE_ZONE_DP = 66f
    const val MIN_ZOOM = 0.25f
    const val MAX_ZOOM = 8f

    /** Draws the full 108dp canvas at [sizePx] x [sizePx], starting at the canvas origin. */
    fun draw(canvas: Canvas, sizePx: Float, source: Bitmap, style: IconStyle) {
        val unit = sizePx / CANVAS_DP
        // A transparent background is left transparent, so see-through PNGs stay see-through.
        if (Color.alpha(style.backgroundColor) != 0) {
            val background = Paint().apply { color = style.backgroundColor }
            canvas.drawRect(0f, 0f, sizePx, sizePx, background)
        }

        val frame = (if (style.mode == FitMode.FILL) CANVAS_DP else SAFE_ZONE_DP) * unit
        val scaleX = frame / source.width
        val scaleY = frame / source.height
        val base = if (style.mode == FitMode.FILL) max(scaleX, scaleY) else min(scaleX, scaleY)
        val scale = base * style.zoom
        val w = source.width * scale
        val h = source.height * scale
        val cx = sizePx / 2f + style.offsetX * sizePx
        val cy = sizePx / 2f + style.offsetY * sizePx

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        canvas.drawBitmap(source, null, RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f), paint)
    }

    /**
     * Renders the final icon bitmap. Drawn at twice the size and scaled down once, which keeps
     * detail crisp when a large photo is shrunk a lot.
     */
    fun render(source: Bitmap, style: IconStyle, sizePx: Int): Bitmap {
        val work = sizePx * 2
        val big = Bitmap.createBitmap(work, work, Bitmap.Config.ARGB_8888)
        draw(Canvas(big), work.toFloat(), source, style)
        val out = Bitmap.createScaledBitmap(big, sizePx, sizePx, true)
        if (out !== big) big.recycle()
        return out
    }

    fun circlePath(size: Float): Path = Path().apply {
        addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
    }

    /** A superellipse ("squircle"), close to One UI's icon shape. */
    fun squirclePath(size: Float, exponent: Double = 4.0): Path {
        val path = Path()
        val r = size / 2.0
        val steps = 144
        for (i in 0..steps) {
            val t = 2.0 * PI * i / steps
            val c = cos(t)
            val s = sin(t)
            val x = (r + r * sign(c) * abs(c).pow(2.0 / exponent)).toFloat()
            val y = (r + r * sign(s) * abs(s).pow(2.0 / exponent)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    /** True if any part of the image is see-through (e.g. a logo PNG). */
    fun hasTransparency(source: Bitmap): Boolean {
        if (!source.hasAlpha()) return false
        val stride = max(1, max(source.width, source.height) / 128)
        for (y in 0 until source.height step stride) {
            for (x in 0 until source.width step stride) {
                if (Color.alpha(source.getPixel(x, y)) < 250) return true
            }
        }
        return false
    }

    /** Grey checkerboard that shows where the icon is transparent (preview only). */
    fun drawCheckerboard(canvas: Canvas, width: Float, height: Float, cell: Float) {
        val light = Paint().apply { color = 0xFFE6E6E6.toInt() }
        val dark = Paint().apply { color = 0xFFBDBDBD.toInt() }
        canvas.drawRect(0f, 0f, width, height, light)
        var row = 0
        var y = 0f
        while (y < height) {
            var x = if (row % 2 == 0) 0f else cell
            while (x < width) {
                canvas.drawRect(x, y, x + cell, y + cell, dark)
                x += cell * 2
            }
            y += cell
            row++
        }
    }

    /** Average colour around the image's border: a good automatic background for Fit mode. */
    fun edgeColor(source: Bitmap): Int {
        val w = source.width
        val h = source.height
        val stride = max(1, max(w, h) / 64)
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0L
        fun sample(x: Int, y: Int) {
            val p = source.getPixel(x, y)
            if (Color.alpha(p) < 128) return
            r += Color.red(p)
            g += Color.green(p)
            b += Color.blue(p)
            n++
        }
        for (x in 0 until w step stride) {
            sample(x, 0)
            sample(x, h - 1)
        }
        for (y in 0 until h step stride) {
            sample(0, y)
            sample(w - 1, y)
        }
        if (n == 0L) return Color.WHITE
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }
}
