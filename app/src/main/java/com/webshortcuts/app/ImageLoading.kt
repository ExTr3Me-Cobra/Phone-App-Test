package com.webshortcuts.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Largest side kept from a picked photo: plenty for zooming in, without wasting memory. */
private const val MAX_SOURCE_PX = 2048

/**
 * Decodes a picked image (correctly rotated, downsized, software bitmap so we can draw it).
 * Always returns 8-bit ARGB, which keeps PNG transparency; 16-bit and wide-colour PNGs would
 * otherwise decode to formats that don't round-trip cleanly.
 */
suspend fun loadPickedImage(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    runCatching { decode(context, uri) }.getOrNull()?.let { bitmap ->
        if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false).also { bitmap.recycle() }
        }
    }
}

private fun decode(context: Context, uri: Uri): Bitmap? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            val w = info.size.width
            val h = info.size.height
            val largest = max(w, h)
            if (largest > MAX_SOURCE_PX) {
                val s = MAX_SOURCE_PX.toFloat() / largest
                decoder.setTargetSize(
                    (w * s).roundToInt().coerceAtLeast(1),
                    (h * s).roundToInt().coerceAtLeast(1),
                )
            }
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SOURCE_PX) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }
