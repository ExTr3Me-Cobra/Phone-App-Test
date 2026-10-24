package com.vipervision.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.t8rin.imagetoolbox.core.domain.image.ImageTransformer
import com.t8rin.imagetoolbox.core.filters.domain.FilterProvider
import com.t8rin.imagetoolbox.core.filters.presentation.model.UiFilter
import kotlin.math.max

/**
 * One of Image Toolbox's filters in the Effects tab, with its own settings. With a [mask] (painted
 * strokes on the upright photo, like Heal's) it only changes the painted area, or everything but
 * that area when [invertMask] is set.
 */
data class Effect(
    val filter: UiFilter<*>,
    val mask: List<BrushStroke>? = null,
    val invertMask: Boolean = false,
    /** Stays the same while the settings change, so the UI can follow one effect. */
    val id: Long = nextEffectId()
)

private var effectIds = 0L
private fun nextEffectId() = ++effectIds

/**
 * Everything that happens after rotate/crop: the Adjust dials, then the effects in order. Used for
 * the preview and for saving, so both look the same.
 */
class EditFinisher(
    private val filterProvider: FilterProvider<Bitmap>,
    private val imageTransformer: ImageTransformer<Bitmap>
) {
    suspend fun finish(
        context: Context,
        turned: Bitmap,
        state: EditState,
        uprightWidth: Int,
        uprightHeight: Int
    ): Bitmap {
        var image = applyAdjustments(context, turned, state.adjustments)
        for (effect in state.effects) {
            val filtered = runCatching {
                imageTransformer.transform(
                    image = image,
                    transformations = listOf(filterProvider.filterToTransformation(effect.filter))
                )
            }.getOrNull() ?: continue
            image = if (effect.mask.isNullOrEmpty() || filtered.width != image.width || filtered.height != image.height) {
                filtered
            } else {
                // The mask is painted on the upright photo; turn and crop it the same way.
                val mask = renderAlphaMask(effect.mask, uprightWidth, uprightHeight)
                    .transformed(state.geometry)
                    .let { if (it.width != image.width || it.height != image.height) Bitmap.createScaledBitmap(it, image.width, image.height, true) else it }
                blendThroughMask(image, filtered, mask, effect.invertMask)
            }
        }
        return image
    }
}

/** White strokes on a transparent background, at [width] x [height]. */
fun renderAlphaMask(strokes: List<BrushStroke>, width: Int, height: Int): Bitmap {
    val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(mask)
    val longSide = max(width, height)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    for (stroke in strokes) {
        val radius = stroke.radius * longSide
        if (stroke.points.size == 1) {
            paint.style = Paint.Style.FILL
            val p = stroke.points.first()
            canvas.drawCircle(p.x * width, p.y * height, radius, paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = radius * 2f
            val path = Path()
            stroke.points.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(p.x * width, p.y * height) else path.lineTo(p.x * width, p.y * height)
            }
            canvas.drawPath(path, paint)
        }
    }
    return mask
}

/** [base] with [filtered] showing through where [mask] is painted (or where it isn't, if [invert]). */
private fun blendThroughMask(base: Bitmap, filtered: Bitmap, mask: Bitmap, invert: Boolean): Bitmap {
    val cut = filtered.copy(Bitmap.Config.ARGB_8888, true)
    Canvas(cut).drawBitmap(
        mask, 0f, 0f,
        Paint().apply { xfermode = PorterDuffXfermode(if (invert) PorterDuff.Mode.DST_OUT else PorterDuff.Mode.DST_IN) }
    )
    val out = base.copy(Bitmap.Config.ARGB_8888, true)
    Canvas(out).drawBitmap(cut, 0f, 0f, null)
    return out
}
