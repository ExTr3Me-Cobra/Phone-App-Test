package com.vipervision.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.max

/**
 * Everything that happens after rotate/crop: the Adjust dials, then the tone curves. Used for the
 * preview and for saving, so both look the same.
 */
class EditFinisher {
    @Suppress("UNUSED_PARAMETER")
    suspend fun finish(
        context: Context,
        turned: Bitmap,
        state: EditState,
        uprightWidth: Int,
        uprightHeight: Int
    ): Bitmap = applyCurves(context, applyAdjustments(context, turned, state.adjustments), state.curves)
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
