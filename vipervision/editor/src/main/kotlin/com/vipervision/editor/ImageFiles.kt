package com.vipervision.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Part of the photo to keep, as fractions (0 to 1) of the rotated and mirrored photo, so the same
 * crop works on the screen-sized preview and on the full-size photo.
 */
data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isFull: Boolean get() = left <= 0.0005f && top <= 0.0005f && right >= 0.9995f && bottom >= 0.9995f

    /** The same area after the photo is turned 90° clockwise. */
    fun rotatedClockwise() = CropRect(1f - bottom, left, 1f - top, right)

    /** The same area after the photo is mirrored left-to-right. */
    fun mirrored() = CropRect(1f - right, top, 1f - left, bottom)
}

/** Rotation (in quarter turns clockwise), mirroring and crop, applied before the adjustments. */
data class Geometry(
    val quarterTurns: Int = 0,
    val flipped: Boolean = false,
    val crop: CropRect = CropRect()
) {
    val isIdentity: Boolean get() = quarterTurns % 4 == 0 && !flipped && crop.isFull

    /** Width and height of a [width] x [height] photo after the rotation (before cropping). */
    fun turnedSize(width: Int, height: Int): Pair<Int, Int> =
        if (quarterTurns % 2 == 1) height to width else width to height
}

fun Bitmap.transformed(geometry: Geometry): Bitmap {
    val turned = if (geometry.quarterTurns % 4 == 0 && !geometry.flipped) {
        this
    } else {
        val matrix = Matrix().apply {
            if (geometry.flipped) postScale(-1f, 1f)
            postRotate(90f * (geometry.quarterTurns % 4))
        }
        Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
    val crop = geometry.crop
    if (crop.isFull) return turned
    val x = (crop.left * turned.width).roundToInt().coerceIn(0, turned.width - 1)
    val y = (crop.top * turned.height).roundToInt().coerceIn(0, turned.height - 1)
    val w = (crop.width * turned.width).roundToInt().coerceIn(1, turned.width - x)
    val h = (crop.height * turned.height).roundToInt().coerceIn(1, turned.height - y)
    return Bitmap.createBitmap(turned, x, y, w, h)
}

/**
 * Decodes [uri] upright (EXIF rotation applied) as a software ARGB_8888 bitmap in sRGB, scaled
 * down so its longer side is at most [maxSide] pixels.
 */
fun decodeImage(context: Context, uri: Uri, maxSide: Int): Bitmap {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            val longSide = max(info.size.width, info.size.height)
            if (longSide > maxSide) {
                val scale = maxSide.toFloat() / longSide
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1)
                )
            }
        }
    } else {
        decodeLegacy(context, uri, maxSide)
    }
    return if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
}

private fun decodeLegacy(context: Context, uri: Uri, maxSide: Int): Bitmap {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("Couldn't open the photo")
    val orientation = resolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val upright = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> decoded.transformed(Geometry(1))
        ExifInterface.ORIENTATION_ROTATE_180 -> decoded.transformed(Geometry(2))
        ExifInterface.ORIENTATION_ROTATE_270 -> decoded.transformed(Geometry(3))
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> decoded.transformed(Geometry(0, true))
        else -> decoded
    }
    val longSide = max(upright.width, upright.height)
    if (longSide <= maxSide) return upright
    val scale = maxSide.toFloat() / longSide
    return Bitmap.createScaledBitmap(
        upright,
        (upright.width * scale).roundToInt(),
        (upright.height * scale).roundToInt(),
        true
    )
}

/**
 * The largest long side the full-size render can use without running out of memory. Saving holds
 * about three copies of the photo at once (decoded, working pixels, result).
 */
fun maxSafeSide(context: Context, uri: Uri): Int {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val width = bounds.outWidth.coerceAtLeast(1)
    val height = bounds.outHeight.coerceAtLeast(1)
    val budget = Runtime.getRuntime().maxMemory() * 0.55
    val maxPixels = budget / (4.0 * 3.2)
    val scale = minOf(1.0, sqrt(maxPixels / (width.toDouble() * height)))
    return (max(width, height) * scale).toInt()
}

private val exifTagsToKeep = listOf(
    ExifInterface.TAG_DATETIME,
    ExifInterface.TAG_DATETIME_ORIGINAL,
    ExifInterface.TAG_DATETIME_DIGITIZED,
    ExifInterface.TAG_OFFSET_TIME,
    ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
    ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
    ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
    ExifInterface.TAG_MAKE,
    ExifInterface.TAG_MODEL,
    ExifInterface.TAG_F_NUMBER,
    ExifInterface.TAG_EXPOSURE_TIME,
    ExifInterface.TAG_ISO_SPEED_RATINGS,
    ExifInterface.TAG_FOCAL_LENGTH,
    ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
    ExifInterface.TAG_FLASH,
    ExifInterface.TAG_WHITE_BALANCE,
    ExifInterface.TAG_GPS_LATITUDE,
    ExifInterface.TAG_GPS_LATITUDE_REF,
    ExifInterface.TAG_GPS_LONGITUDE,
    ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE,
    ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_TIMESTAMP,
    ExifInterface.TAG_GPS_DATESTAMP
)

/** Name, folder and date of the photo being edited, used to save the copy next to it. */
class SourceInfo(val displayName: String, val relativeFolder: String?, val absoluteFolder: File?, val dateTaken: Long?)

fun sourceInfo(context: Context, uri: Uri, realPath: String?): SourceInfo {
    val file = realPath?.let(::File)
    var name = file?.name
    if (name == null) {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(0)
        }
    }
    val storageRoot = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/') + "/"
    val folder = file?.parentFile
    val relative = folder?.absolutePath?.let { "$it/" }?.takeIf { it.startsWith(storageRoot) }?.removePrefix(storageRoot)

    var dateTaken: Long? = null
    if (realPath != null) {
        @Suppress("DEPRECATION")
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media.DATE_TAKEN),
            "${MediaStore.Images.Media.DATA} = ?",
            arrayOf(realPath),
            null
        )?.use { if (it.moveToFirst() && !it.isNull(0)) dateTaken = it.getLong(0) }
    }
    return SourceInfo(name ?: "photo.jpg", relative, folder?.takeIf { relative != null }, dateTaken)
}

/**
 * Renders the edits at full size and saves them as a new photo next to the original, named
 * "<original>_edited". The original file is never changed. Returns the new photo's uri.
 */
fun saveEditedCopy(
    context: Context,
    source: Uri,
    realPath: String?,
    geometry: Geometry,
    adjustments: Adjustments,
    renderFrom: Uri = source,
    forcePng: Boolean = false
): Uri {
    val rendered = renderFull(context, renderFrom, geometry, adjustments)
    val info = sourceInfo(context, source, realPath)
    val isPng = forcePng || info.displayName.endsWith(".png", ignoreCase = true)
    val extension = if (isPng) "png" else "jpg"
    val mime = if (isPng) "image/png" else "image/jpeg"
    val baseName = info.displayName.substringBeforeLast('.') + "_edited"
    val format = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
    val resolver = context.contentResolver

    val uri: Uri
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$baseName.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            // Android only allows new photos under DCIM/ or Pictures/.
            val folder = info.relativeFolder?.takeIf { it.startsWith("DCIM/") || it.startsWith("Pictures/") }
            put(MediaStore.Images.Media.RELATIVE_PATH, folder ?: "Pictures/Viper Vision/")
            info.dateTaken?.let { put(MediaStore.Images.Media.DATE_TAKEN, it) }
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Couldn't create the new photo")
        resolver.openOutputStream(uri)?.use { rendered.compress(format, 95, it) }
            ?: error("Couldn't write the new photo")
        if (!isPng) copyExif(context, source, uri)
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    } else {
        @Suppress("DEPRECATION")
        val folder = info.absoluteFolder
            ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Viper Vision")
        folder.mkdirs()
        var file = File(folder, "$baseName.$extension")
        var n = 1
        while (file.exists()) file = File(folder, "$baseName (${n++}).$extension")
        file.outputStream().use { rendered.compress(format, 95, it) }
        uri = Uri.fromFile(file)
        if (!isPng) copyExif(context, source, uri)
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
    }
    return uri
}

/** Renders the edits at full size into a temporary file, for handing over to Image Toolbox. */
fun saveForHandoff(
    context: Context,
    source: Uri,
    geometry: Geometry,
    adjustments: Adjustments,
    renderFrom: Uri = source,
    forcePng: Boolean = false
): Uri {
    val rendered = renderFull(context, renderFrom, geometry, adjustments)
    val folder = File(context.cacheDir, "vv_editor").apply { mkdirs() }
    folder.listFiles()?.forEach { it.delete() }
    val file = File(folder, "edit_${System.currentTimeMillis()}." + if (forcePng) "png" else "jpg")
    file.outputStream().use {
        rendered.compress(if (forcePng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it)
    }
    if (!forcePng) copyExif(context, source, Uri.fromFile(file))
    return FileProvider.getUriForFile(context, "${context.packageName}.vveditor", file)
}

private fun renderFull(
    context: Context,
    source: Uri,
    geometry: Geometry,
    adjustments: Adjustments
): Bitmap {
    val full = decodeImage(context, source, maxSafeSide(context, source))
    val turned = full.transformed(geometry)
    if (turned !== full) full.recycle()
    val result = applyAdjustments(context, turned, adjustments)
    if (result !== turned) turned.recycle()
    return result
}

private fun copyExif(context: Context, from: Uri, to: Uri) {
    runCatching {
        val original = context.contentResolver.openInputStream(from)?.use { ExifInterface(it) } ?: return
        val target = if (to.scheme == "file") {
            ExifInterface(to.path!!)
        } else {
            context.contentResolver.openFileDescriptor(to, "rw")?.use { return writeExif(original, ExifInterface(it.fileDescriptor)) }
            return
        }
        writeExif(original, target)
    }
}

private fun writeExif(original: ExifInterface, target: ExifInterface) {
    for (tag in exifTagsToKeep) original.getAttribute(tag)?.let { target.setAttribute(tag, it) }
    target.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
    target.setAttribute(ExifInterface.TAG_SOFTWARE, "Viper Vision")
    target.saveAttributes()
}
