package com.vipervision.editor

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Heal bubble, done like a healing brush: for each painted spot, look around it for a patch
 * whose surroundings match the spot's surroundings, then blend that patch's real texture in with
 * OpenCV's seamless clone (which matches its colour and brightness to the edges). Smearing the
 * edges inwards (OpenCV's plain inpaint) leaves a smooth blob or streaks on anything with texture
 * or lines; this keeps skin grain, fabric, wood and straight edges going through the spot. Spots
 * with no usable patch nearby (e.g. very large ones) fall back to the plain inpaint.
 */
object PatchHealer {

    fun heal(image: Bitmap, mask: Bitmap): Bitmap {
        OpenCVLoader.initLocal()
        val rgba = Mat().also { Utils.bitmapToMat(image.copy(Bitmap.Config.ARGB_8888, false), it) }
        val out = Mat().also { Imgproc.cvtColor(rgba, it, Imgproc.COLOR_RGBA2RGB) }
        val alpha = Mat().also { Core.extractChannel(rgba, it, 3) }
        val width = out.cols()
        val height = out.rows()

        // Painted area as 0/255, grown a little so the spot's soft edge is covered too.
        val painted = Mat().also { m ->
            val maskRgba = Mat().also { Utils.bitmapToMat(mask.copy(Bitmap.Config.ARGB_8888, false), it) }
            Imgproc.cvtColor(maskRgba, m, Imgproc.COLOR_RGBA2GRAY)
            if (m.cols() != width || m.rows() != height) Imgproc.resize(m, m, Size(width.toDouble(), height.toDouble()))
            Imgproc.threshold(m, m, 127.0, 255.0, Imgproc.THRESH_BINARY)
            Imgproc.dilate(m, m, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0)))
            maskRgba.release()
        }

        val lab = Mat().also { l ->
            Imgproc.cvtColor(out, l, Imgproc.COLOR_RGB2Lab)
            l.convertTo(l, CvType.CV_32FC3)
        }
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val count = Imgproc.connectedComponentsWithStats(painted, labels, stats, centroids, 8)

        for (i in 1 until count) {
            val x = stats.get(i, Imgproc.CC_STAT_LEFT)[0].toInt()
            val y = stats.get(i, Imgproc.CC_STAT_TOP)[0].toInt()
            val w = stats.get(i, Imgproc.CC_STAT_WIDTH)[0].toInt()
            val h = stats.get(i, Imgproc.CC_STAT_HEIGHT)[0].toInt()
            val size = max(w, h)
            val ringWidth = max(4, (size * 0.2f).roundToInt())
            val pad = ringWidth + 2
            val x0 = max(0, x - pad)
            val y0 = max(0, y - pad)
            val x1 = min(width, x + w + pad)
            val y1 = min(height, y + h + pad)
            val region = Rect(x0, y0, x1 - x0, y1 - y0)

            // This spot only, and the ring of untouched pixels around it.
            val spot = Mat().also { Core.compare(labels.submat(region), Scalar(i.toDouble()), it, Core.CMP_EQ) }
            val ring = Mat().also { r ->
                val k = 2 * ringWidth + 1
                Imgproc.dilate(spot, r, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(k.toDouble(), k.toDouble())))
                Core.subtract(r, spot, r)
            }

            val source = findSource(lab, painted, spot, ring, region, size, width, height)
            if (source == null) {
                val filled = Mat()
                Photo.inpaint(out.submat(region), spot, filled, 3.0, Photo.INPAINT_TELEA)
                filled.copyTo(out.submat(region))
                filled.release()
            } else {
                blendPatch(out, source, region, spot, width, height)
            }
            spot.release()
            ring.release()
        }

        val result = Mat().also { Imgproc.cvtColor(out, it, Imgproc.COLOR_RGB2RGBA) }
        Core.insertChannel(alpha, result, 3)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(result, bitmap)
        listOf(rgba, out, alpha, painted, lab, labels, stats, centroids, result).forEach(Mat::release)
        return bitmap
    }

    /**
     * Tries patches in 16 directions at a few distances from the spot and returns the one whose
     * ring of pixels best matches the spot's ring (colour difference in Lab), preferring nearer
     * ones. Patches overlapping any painted spot are skipped.
     */
    private fun findSource(
        lab: Mat,
        painted: Mat,
        spot: Mat,
        ring: Mat,
        region: Rect,
        size: Int,
        width: Int,
        height: Int
    ): Rect? {
        val target = lab.submat(region)
        val diff = Mat()
        val overlap = Mat()
        var best: Rect? = null
        var bestScore = Double.MAX_VALUE
        for (distance in floatArrayOf(1.2f, 1.6f, 2.1f, 2.8f, 3.6f)) {
            for (k in 0 until 16) {
                val angle = 2 * PI * k / 16
                val dx = (cos(angle) * distance * size).roundToInt()
                val dy = (sin(angle) * distance * size).roundToInt()
                val sx = region.x + dx
                val sy = region.y + dy
                if (sx < 0 || sy < 0 || sx + region.width > width || sy + region.height > height) continue
                val candidate = Rect(sx, sy, region.width, region.height)
                Core.bitwise_and(painted.submat(candidate), spot, overlap)
                if (Core.countNonZero(overlap) > 0) continue
                Core.subtract(lab.submat(candidate), target, diff)
                Core.multiply(diff, diff, diff)
                val mean = Core.mean(diff, ring).`val`
                val score = (mean[0] + mean[1] + mean[2]) * (1 + 0.05 * distance)
                if (score < bestScore) {
                    bestScore = score
                    best = candidate
                }
            }
        }
        diff.release()
        overlap.release()
        return best
    }

    /** Clones the source patch's texture over the spot, matching colour to the spot's edges. */
    private fun blendPatch(out: Mat, source: Rect, region: Rect, spot: Mat, width: Int, height: Int) {
        val patch = out.submat(source).clone()
        val cloneMask = Mat().also { m ->
            Imgproc.dilate(spot, m, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)))
            // Seamless clone needs a clear border inside the patch.
            m.row(0).setTo(Scalar(0.0))
            m.row(m.rows() - 1).setTo(Scalar(0.0))
            m.col(0).setTo(Scalar(0.0))
            m.col(m.cols() - 1).setTo(Scalar(0.0))
        }
        // Work in a window slightly larger than the spot's region, then copy it back.
        val wx0 = max(0, region.x - 4)
        val wy0 = max(0, region.y - 4)
        val wx1 = min(width, region.x + region.width + 4)
        val wy1 = min(height, region.y + region.height + 4)
        val window = Rect(wx0, wy0, wx1 - wx0, wy1 - wy0)
        val target = out.submat(window).clone()
        val blended = Mat()
        val center = Point(
            (region.x - wx0 + region.width / 2).toDouble(),
            (region.y - wy0 + region.height / 2).toDouble()
        )
        runCatching {
            Photo.seamlessClone(patch, target, cloneMask, center, blended, Photo.NORMAL_CLONE)
            blended.copyTo(out.submat(window))
        }.onFailure {
            // If cloning can't place the patch, fall back to smearing the edges in.
            val filled = Mat()
            Photo.inpaint(out.submat(region), spot, filled, 3.0, Photo.INPAINT_TELEA)
            filled.copyTo(out.submat(region))
            filled.release()
        }
        listOf(patch, cloneMask, target, blended).forEach(Mat::release)
    }
}
