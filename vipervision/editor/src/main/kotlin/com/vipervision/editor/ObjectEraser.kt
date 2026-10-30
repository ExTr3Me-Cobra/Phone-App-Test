package com.vipervision.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import com.t8rin.neural_tools.inpaint.LaMaProcessor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The Object eraser: LaMa fills in what was painted over, with two fixes for how it's usually
 * painted by hand.
 *
 * - The painted area grows a little (about 1% of the photo) before LaMa sees it. Strokes rarely
 *   cover an object's very edge or its soft shadow, and LaMa copies whatever dark rim it can still
 *   see into the fill, which leaves a ghost outline.
 * - The fill is blended in with a soft edge instead of a hard one, so there's no visible seam.
 */
object ObjectEraser {

    fun erase(image: Bitmap, strokes: List<BrushStroke>): Bitmap {
        val longSide = max(image.width, image.height)
        val grow = GROW_FRACTION * longSide
        // LaMa fills the widest area; the blend fades in from halfway between that and the strokes,
        // so it is fully the fill before the original pixels around the object come back.
        val fillMask = renderMask(strokes.grown(2 * grow / longSide), image.width, image.height)
        val filled = try {
            fill(image, fillMask)
        } finally {
            fillMask.recycle()
        }
        val blendMask = renderMask(strokes.grown(grow / longSide), image.width, image.height)
        val soft = blendMask.softened(grow)
        if (soft !== blendMask) blendMask.recycle()
        try {
            // filled keeps only where the soft mask is white, then goes over the original. It's a
            // copy of the photo, so opaque for a JPEG: then the cut-away part turned black instead
            // of see-through, and covered the whole photo but the fill.
            filled.setHasAlpha(true)
            Canvas(filled).drawBitmap(soft, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = RED_TO_ALPHA
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            })
        } finally {
            soft.recycle()
        }
        val result = image.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(result).drawBitmap(filled, 0f, 0f, null)
        filled.recycle()
        return result
    }

    /**
     * LaMa's fill of [mask]. Small areas go through Image Toolbox's full-resolution LaMa as before.
     * For big areas (the photo region LaMa needs is over [LARGE_REGION] px), that path tiles the
     * photo and leaves a dark smudge with fine stripes; LaMa does far better at about the size it
     * was trained on. So the region is filled at [LARGE_FILL_SIZE] px (which also takes seconds,
     * not minutes), enlarged, and given the photo's own grain so it isn't plasticky smooth.
     * Tested on a real photo: lighter, matches the floor, keeps lines like a wall edge going.
     */
    private fun fill(image: Bitmap, mask: Bitmap): Bitmap {
        val bounds = maskBounds(mask) ?: return image.copy(Bitmap.Config.ARGB_8888, true)
        // Same context LaMaProcessor takes: the area plus its own size (at least 256 px) around it.
        val pad = maxOf(256, bounds.width(), bounds.height())
        val region = Rect(
            (bounds.left - pad).coerceAtLeast(0),
            (bounds.top - pad).coerceAtLeast(0),
            (bounds.right + pad).coerceAtMost(image.width),
            (bounds.bottom + pad).coerceAtMost(image.height)
        )
        if (max(region.width(), region.height()) <= LARGE_REGION) {
            val out = LaMaProcessor.inpaint(image = image, mask = mask) ?: error("the AI model couldn't process this photo")
            return if (out.isMutable) out else out.copy(Bitmap.Config.ARGB_8888, true).also { out.recycle() }
        }
        val scale = LARGE_FILL_SIZE.toFloat() / max(region.width(), region.height())
        val width = (region.width() * scale).roundToInt().coerceAtLeast(1)
        val height = (region.height() * scale).roundToInt().coerceAtLeast(1)
        val target = Rect(0, 0, width, height)
        val small = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawBitmap(image, region, target, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val smallMask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawBitmap(mask, region, target, null)
        }
        val smallOut = try {
            LaMaProcessor.inpaint(image = small, mask = smallMask) ?: error("the AI model couldn't process this photo")
        } finally {
            small.recycle()
            smallMask.recycle()
        }
        val patch = Bitmap.createScaledBitmap(smallOut, region.width(), region.height(), true)
        if (patch !== smallOut) smallOut.recycle()
        addGrain(patch, grainOf(image, mask, region))
        val result = image.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(result).drawBitmap(patch, region.left.toFloat(), region.top.toFloat(), null)
        patch.recycle()
        return result
    }

    /** The painted area's bounding box, or null if nothing is painted. */
    private fun maskBounds(mask: Bitmap): Rect? {
        val row = IntArray(mask.width)
        var left = mask.width
        var top = mask.height
        var right = -1
        var bottom = -1
        for (y in 0 until mask.height) {
            mask.getPixels(row, 0, mask.width, 0, y, mask.width, 1)
            for (x in row.indices) {
                if (Color.red(row[x]) > 0) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    bottom = y
                }
            }
        }
        return if (right < 0) null else Rect(left, top, right + 1, bottom + 1)
    }

    /**
     * How grainy the photo is around the area: the spread of each pixel minus its neighbours'
     * average, over random unpainted pixels in [region].
     */
    private fun grainOf(image: Bitmap, mask: Bitmap, region: Rect): Float {
        if (region.width() < 5 || region.height() < 5) return 0f
        val random = Random(1)
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        repeat(40_000) {
            val x = region.left + 2 + random.nextInt(region.width() - 4)
            val y = region.top + 2 + random.nextInt(region.height() - 4)
            if (Color.red(mask.getPixel(x, y)) > 0) return@repeat
            val c = luma(image.getPixel(x, y))
            val around = (luma(image.getPixel(x - 2, y)) + luma(image.getPixel(x + 2, y)) +
                    luma(image.getPixel(x, y - 2)) + luma(image.getPixel(x, y + 2))) / 4f
            val d = (c - around).toDouble()
            sum += d
            sumSq += d * d
            count++
        }
        if (count < 100) return 0f
        val variance = sumSq / count - (sum / count).let { it * it }
        // A pixel minus the mean of 4 independent neighbours has 1.25× the noise variance.
        // Capped so edges and textures around the area don't turn into heavy noise.
        return sqrt(variance / 1.25).toFloat().coerceAtMost(12f)
    }

    private fun luma(color: Int) = 0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)

    /** Adds fine grey noise of strength [sigma] to [bitmap]. */
    private fun addGrain(bitmap: Bitmap, sigma: Float) {
        if (sigma < 0.5f) return
        val random = java.util.Random(7)
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, row.size, 0, y, row.size, 1)
            for (x in row.indices) {
                val c = row[x]
                val n = (random.nextGaussian() * sigma).roundToInt()
                row[x] = Color.argb(
                    Color.alpha(c),
                    (Color.red(c) + n).coerceIn(0, 255),
                    (Color.green(c) + n).coerceIn(0, 255),
                    (Color.blue(c) + n).coerceIn(0, 255)
                )
            }
            bitmap.setPixels(row, 0, row.size, 0, y, row.size, 1)
        }
    }

    private fun List<BrushStroke>.grown(by: Float) = map { it.copy(radius = it.radius + by) }

    /** Blurs the mask's edge over about [width] pixels (shrink, then enlarge smoothly). */
    private fun Bitmap.softened(width: Float): Bitmap {
        val factor = (width / 2f).coerceAtLeast(1f)
        val small = Bitmap.createScaledBitmap(this, (this.width / factor).toInt().coerceAtLeast(1), (height / factor).toInt().coerceAtLeast(1), true)
        val soft = Bitmap.createScaledBitmap(small, this.width, height, true)
        if (small !== soft && small !== this) small.recycle()
        return soft
    }

    /** Above this (px, long side of the region LaMa looks at) the area is filled at [LARGE_FILL_SIZE]. */
    private const val LARGE_REGION = 1024

    /** LaMa's training size, where it fills big areas best. */
    private const val LARGE_FILL_SIZE = 512

    /** About 1% of the photo's long side (40 px on a 4000 px photo). */
    private const val GROW_FRACTION = 0.01f

    private val RED_TO_ALPHA = ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                1f, 0f, 0f, 0f, 0f
            )
        )
    )
}
