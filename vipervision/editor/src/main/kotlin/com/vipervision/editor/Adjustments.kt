package com.vipervision.editor

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** One slider in the Adjust tab. Every value runs from -100 to 100, with 0 meaning "unchanged". */
enum class Adjustment(val label: String) {
    Brightness("Brightness"),
    Exposure("Exposure"),
    Contrast("Contrast"),
    Highlights("Highlights"),
    Shadows("Shadows"),
    Saturation("Saturation"),
    Warmth("Warmth"),
    Tint("Tint"),
    Sharpness("Sharpness"),
    Vignette("Vignette")
}

/** The current value of every adjustment, plus an optional black-and-white look. */
data class Adjustments(
    val values: Map<Adjustment, Int> = emptyMap(),
    val monochrome: Boolean = false
) {
    operator fun get(adjustment: Adjustment): Int = values[adjustment] ?: 0

    fun with(adjustment: Adjustment, value: Int): Adjustments =
        copy(values = values + (adjustment to value.coerceIn(-100, 100)))

    val isIdentity: Boolean get() = !monochrome && values.values.all { it == 0 }
}

/**
 * Applies [adjustments] to [source] and returns a new bitmap. Runs on the CPU, split into strips
 * across the available cores, so it's quick enough to re-run while a slider is being dragged on a
 * screen-sized preview, and still works on the full-size photo when saving.
 */
suspend fun applyAdjustments(source: Bitmap, adjustments: Adjustments): Bitmap {
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)

    if (!adjustments.isIdentity) {
        val tone = toneCurve(adjustments)
        val params = PixelParams(adjustments, width, height)
        processInStrips(height) { from, to -> adjustRows(pixels, width, from, to, tone, params) }

        val sharpness = adjustments[Adjustment.Sharpness]
        if (sharpness != 0) {
            val copy = pixels.copyOf()
            val amount = if (sharpness > 0) sharpness / 100f * 1.6f else sharpness / 100f * 0.9f
            processInStrips(height) { from, to -> sharpenRows(copy, pixels, width, height, from, to, amount) }
        }
    }

    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private suspend fun processInStrips(height: Int, block: (from: Int, to: Int) -> Unit) = coroutineScope {
    val strips = max(1, min(Runtime.getRuntime().availableProcessors(), height / 32))
    val step = (height + strips - 1) / strips
    (0 until strips).map { i ->
        async(Dispatchers.Default) { block(i * step, min(height, (i + 1) * step)) }
    }.awaitAll()
}

/** Brightness, exposure and contrast only depend on a channel's own value, so they're a lookup table. */
private fun toneCurve(adjustments: Adjustments): FloatArray {
    val exposure = 2f.pow(adjustments[Adjustment.Exposure] / 100f * 1.5f)
    val brightness = adjustments[Adjustment.Brightness] / 100f
    val gamma = 1f / (1f + brightness * 0.8f)
    val contrastT = adjustments[Adjustment.Contrast] / 100f
    val contrast = if (contrastT >= 0) 1f + contrastT * 0.9f else 1f + contrastT * 0.7f

    return FloatArray(256) { i ->
        var c = i / 255f
        c = min(1f, c * exposure)
        c = c.pow(gamma)
        c = (c - 0.5f) * contrast + 0.5f
        c.coerceIn(0f, 1f)
    }
}

private class PixelParams(adjustments: Adjustments, val width: Int, val height: Int) {
    val highlights = adjustments[Adjustment.Highlights] / 100f
    val shadows = adjustments[Adjustment.Shadows] / 100f
    val saturation = 1f + adjustments[Adjustment.Saturation] / 100f
    val warmth = adjustments[Adjustment.Warmth] / 100f
    val tint = adjustments[Adjustment.Tint] / 100f
    val vignette = adjustments[Adjustment.Vignette] / 100f
    val monochrome = adjustments.monochrome
    val cx = width / 2f
    val cy = height / 2f
    val maxDistance = sqrt(cx * cx + cy * cy)
}

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

/** Pushes [c] toward white (amount > 0) or toward black (amount < 0). */
private fun lift(c: Float, amount: Float): Float = if (amount >= 0) c + amount * (1f - c) else c + amount * c

private fun adjustRows(
    pixels: IntArray,
    width: Int,
    from: Int,
    to: Int,
    tone: FloatArray,
    p: PixelParams
) {
    for (y in from until to) {
        val dy = y - p.cy
        for (x in 0 until width) {
            val i = y * width + x
            val argb = pixels[i]
            var r = tone[(argb shr 16) and 0xFF]
            var g = tone[(argb shr 8) and 0xFF]
            var b = tone[argb and 0xFF]

            var luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
            if (p.highlights != 0f) {
                val amount = p.highlights * 0.45f * smoothstep(0.45f, 1f, luma)
                r = lift(r, amount); g = lift(g, amount); b = lift(b, amount)
            }
            if (p.shadows != 0f) {
                val amount = p.shadows * 0.45f * (1f - smoothstep(0f, 0.55f, luma))
                r = lift(r, amount); g = lift(g, amount); b = lift(b, amount)
            }

            if (p.warmth != 0f) {
                r *= 1f + p.warmth * 0.18f
                b *= 1f - p.warmth * 0.18f
            }
            if (p.tint != 0f) {
                // Positive is magenta, negative is green.
                g *= 1f - p.tint * 0.15f
                r *= 1f + p.tint * 0.04f
                b *= 1f + p.tint * 0.04f
            }

            luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
            if (p.monochrome) {
                r = luma; g = luma; b = luma
            } else if (p.saturation != 1f) {
                r = luma + (r - luma) * p.saturation
                g = luma + (g - luma) * p.saturation
                b = luma + (b - luma) * p.saturation
            }

            if (p.vignette != 0f) {
                val dx = x - p.cx
                val d = sqrt(dx * dx + dy * dy) / p.maxDistance
                val factor = 1f - p.vignette * 0.75f * smoothstep(0.35f, 1.05f, d)
                r *= factor; g *= factor; b *= factor
            }

            pixels[i] = (argb and -0x1000000) or
                    ((r.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 16) or
                    ((g.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 8) or
                    (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        }
    }
}

/** Unsharp mask with a 3x3 blur: positive [amount] sharpens, negative softens. */
private fun sharpenRows(
    src: IntArray,
    dst: IntArray,
    width: Int,
    height: Int,
    from: Int,
    to: Int,
    amount: Float
) {
    for (y in from until to) {
        val y0 = max(0, y - 1)
        val y2 = min(height - 1, y + 1)
        for (x in 0 until width) {
            val x0 = max(0, x - 1)
            val x2 = min(width - 1, x + 1)
            var sr = 0; var sg = 0; var sb = 0
            var row = 0
            while (row < 3) {
                val yy = when (row) { 0 -> y0; 1 -> y; else -> y2 }
                var col = 0
                while (col < 3) {
                    val xx = when (col) { 0 -> x0; 1 -> x; else -> x2 }
                    val c = src[yy * width + xx]
                    sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
                    col++
                }
                row++
            }
            val c = src[y * width + x]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val nr = (r + amount * (r - sr / 9f)).toInt().coerceIn(0, 255)
            val ng = (g + amount * (g - sg / 9f)).toInt().coerceIn(0, 255)
            val nb = (b + amount * (b - sb / 9f)).toInt().coerceIn(0, 255)
            dst[y * width + x] = (c and -0x1000000) or (nr shl 16) or (ng shl 8) or nb
        }
    }
}

/**
 * Picks adjustment values that even out a photo: brightens dark shots, darkens bright ones,
 * stretches flat contrast, recovers clipped highlights and opens up deep shadows.
 */
fun autoAdjust(preview: Bitmap): Adjustments {
    val step = max(1, (preview.width * preview.height) / 200_000)
    val pixels = IntArray(preview.width * preview.height)
    preview.getPixels(pixels, 0, preview.width, 0, 0, preview.width, preview.height)
    val histogram = IntArray(256)
    var count = 0
    var saturationSum = 0f
    for (i in pixels.indices step step) {
        val c = pixels[i]
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        histogram[(0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)]++
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        if (mx > 0) saturationSum += (mx - mn) / mx.toFloat()
        count++
    }
    fun percentile(p: Float): Float {
        var seen = 0
        val target = count * p
        for (i in 0..255) {
            seen += histogram[i]
            if (seen >= target) return i / 255f
        }
        return 1f
    }
    val low = percentile(0.01f)
    val high = percentile(0.99f)
    val median = percentile(0.5f)
    val clippedHighlights = histogram.sliceArray(250..255).sum() / count.toFloat()
    val deepShadows = histogram.sliceArray(0..12).sum() / count.toFloat()
    val meanSaturation = saturationSum / count

    var adjustments = Adjustments()
    adjustments = adjustments.with(Adjustment.Brightness, ((0.48f - median) * 140).toInt().coerceIn(-35, 45))
    adjustments = adjustments.with(Adjustment.Contrast, ((0.85f - (high - low)) * 90).toInt().coerceIn(-10, 35))
    if (clippedHighlights > 0.02f) adjustments = adjustments.with(Adjustment.Highlights, -(clippedHighlights * 600).toInt().coerceIn(10, 45))
    if (deepShadows > 0.04f) adjustments = adjustments.with(Adjustment.Shadows, (deepShadows * 300).toInt().coerceIn(10, 40))
    adjustments = adjustments.with(Adjustment.Saturation, ((0.32f - meanSaturation) * 80).toInt().coerceIn(0, 20))
    return adjustments
}
