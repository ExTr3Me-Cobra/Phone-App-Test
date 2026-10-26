package com.vipervision.editor

import android.content.Context
import android.graphics.Bitmap

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
    ): Bitmap {
        val finished = applyCurves(context, applyAdjustments(context, turned, state.adjustments), state.curves)
        return if (turned.hasAlpha() && finished !== turned) finished.withAlphaOf(turned) else finished
    }
}

/**
 * The dials and curves run through GPU filters that can fill transparent areas (after a
 * background remover or a shaped crop) with black. This puts [source]'s transparency back.
 */
private fun Bitmap.withAlphaOf(source: Bitmap): Bitmap {
    if (width != source.width || height != source.height) return this
    val result = if (isMutable && config == Bitmap.Config.ARGB_8888) this else copy(Bitmap.Config.ARGB_8888, true)
    result.setHasAlpha(true)
    // Row by row with plain pixels (an alpha-only bitmap drawn through DST_IN did nothing on a
    // Galaxy phone, see AiRunner.removeBackground).
    val row = IntArray(width)
    val alphaRow = IntArray(width)
    for (y in 0 until height) {
        result.getPixels(row, 0, width, 0, y, width, 1)
        source.getPixels(alphaRow, 0, width, 0, y, width, 1)
        for (x in row.indices) row[x] = (row[x] and 0x00FFFFFF) or (alphaRow[x] and -0x1000000)
        result.setPixels(row, 0, width, 0, y, width, 1)
    }
    return result
}
