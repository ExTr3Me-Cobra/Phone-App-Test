package com.vipervision.editor

import android.graphics.Bitmap
import com.t8rin.neural_tools.bgremover.BgRemover
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

/**
 * Cut out's "Refine edges": Smart select decides *what* is kept, then BiRefNet (the Remove
 * background model) redraws the edge on just that area. That gets hair and fur strands, cleans
 * off background fringes and drops things like shadows that the selection swallowed. BiRefNet is
 * only trusted in a band around the selection's edge, so it can't add or drop whole objects.
 */
object EdgeRefiner {

    val model = BgRemover.Type.BiRefNetTiny

    val isDownloaded get() = BgRemover.getRemover(model).isDownloaded.value

    /** [source] with the alpha of [selection] (Smart select's result on it), edges refined. */
    fun refine(source: Bitmap, selection: Bitmap): Bitmap {
        OpenCVLoader.initLocal()
        val width = source.width
        val height = source.height
        val selected = alphaOf(selection)
        val hard = Mat().also { Imgproc.threshold(selected, it, 127.0, 255.0, Imgproc.THRESH_BINARY) }
        val bounds = boundsOf(hard)
        if (bounds == null) {
            listOf(selected, hard).forEach(Mat::release)
            return selection
        }

        // The selected area plus some room around it, so BiRefNet sees the object whole.
        val size = max(bounds.width, bounds.height)
        val pad = size / 10 + 8
        val x0 = max(0, bounds.x - pad)
        val y0 = max(0, bounds.y - pad)
        val crop = Rect(x0, y0, min(width, bounds.x + bounds.width + pad) - x0, min(height, bounds.y + bounds.height + pad) - y0)
        val cropBitmap = Bitmap.createBitmap(source, crop.x, crop.y, crop.width, crop.height)
        val refinedCrop = try {
            ViperLog.step("REFINE", "BiRefNet on ${crop.width}×${crop.height}") { BgRemover.removeBackground(cropBitmap, model).getOrThrow() }
        } finally {
            // Its session holds gigabytes of native memory; free it now.
            BgRemover.getRemover(model).close()
        }
        val edges = Mat.zeros(height, width, CvType.CV_8U)
        alphaOf(refinedCrop).also { it.copyTo(edges.submat(crop)); it.release() }
        if (cropBitmap !== source) cropBitmap.recycle()
        refinedCrop.recycle()

        // Inside the selection (away from its edge): keep. Near the edge: BiRefNet decides,
        // fading out a little past the edge so stray hair is kept but nothing far away is.
        val band = max(3, size * 3 / 100)
        val inner = Mat().also {
            Imgproc.erode(hard, it, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(2.0 * band + 1, 2.0 * band + 1)))
        }
        val reach = Mat().also {
            Imgproc.dilate(hard, it, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(4.0 * band + 1, 4.0 * band + 1)))
            Imgproc.GaussianBlur(it, it, Size(0.0, 0.0), band / 2.0)
        }
        val alpha = Mat().also {
            Core.multiply(edges, reach, it, 1.0 / 255)
            Core.max(it, inner, it)
        }
        // Keep anything already erased in the cut-out erased.
        val existing = alphaOf(source)
        Core.multiply(alpha, existing, alpha, 1.0 / 255)

        val rgba = Mat().also { Utils.bitmapToMat(source.copy(Bitmap.Config.ARGB_8888, false), it, true) }
        Core.insertChannel(alpha, rgba, 3)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgba, result, true)
        result.setHasAlpha(true)
        listOf(selected, hard, edges, inner, reach, alpha, existing, rgba).forEach(Mat::release)
        return result
    }

    /** The smallest rectangle around the non-zero pixels of [mask], or null if there are none. */
    private fun boundsOf(mask: Mat): Rect? {
        fun span(direction: Int): IntRange? {
            val line = Mat()
            Core.reduce(mask, line, direction, Core.REDUCE_MAX)
            val values = ByteArray(line.total().toInt()).also { line.get(0, 0, it) }
            line.release()
            val first = values.indexOfFirst { it.toInt() != 0 }
            return if (first < 0) null else first..values.indexOfLast { it.toInt() != 0 }
        }
        val columns = span(0) ?: return null
        val rows = span(1) ?: return null
        return Rect(columns.first, rows.first, columns.last - columns.first + 1, rows.last - rows.first + 1)
    }

    private fun alphaOf(bitmap: Bitmap): Mat {
        val rgba = Mat().also { Utils.bitmapToMat(bitmap.copy(Bitmap.Config.ARGB_8888, false), it, true) }
        return Mat().also {
            Core.extractChannel(rgba, it, 3)
            rgba.release()
        }
    }
}
