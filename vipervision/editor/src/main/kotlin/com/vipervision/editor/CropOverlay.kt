package com.vipervision.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min

/** What a crop drag is moving. */
private enum class CropHandle { TopLeft, TopRight, BottomLeft, BottomRight, Left, Top, Right, Bottom, Move }

private const val MIN_SIZE = 0.06f

/**
 * Crop frame drawn over a photo shown with ContentScale.Fit in the same box. Drag a corner or an
 * edge to resize, or inside the frame to move it. With [aspectRatio] set (width / height of the
 * frame in pixels), corners keep that shape and edges don't move on their own.
 */
@Composable
internal fun CropOverlay(
    imageWidth: Int,
    imageHeight: Int,
    crop: CropRect,
    aspectRatio: Float?,
    onCropChange: (CropRect) -> Unit,
    onCropChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val touchSlop = with(density) { 28.dp.toPx() }
    // Gesture handlers are created once; these keep them reading the latest values.
    val currentCrop by rememberUpdatedState(crop)
    val currentAspect by rememberUpdatedState(aspectRatio)
    val currentOnChange by rememberUpdatedState(onCropChange)
    val currentOnFinished by rememberUpdatedState(onCropChangeFinished)
    var dragging by remember { mutableStateOf(false) }

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(imageWidth, imageHeight) {
                var handle: CropHandle? = null
                var working = currentCrop
                detectDragGestures(
                    onDragStart = { start ->
                        val image = fittedImageRect(size.width.toFloat(), size.height.toFloat(), imageWidth, imageHeight)
                        working = currentCrop
                        handle = pickHandle(start, working.toScreen(image), touchSlop, currentAspect != null)
                        dragging = handle != null
                    },
                    onDragEnd = {
                        if (handle != null) currentOnFinished()
                        handle = null
                        dragging = false
                    },
                    onDragCancel = {
                        if (handle != null) currentOnFinished()
                        handle = null
                        dragging = false
                    },
                    onDrag = { change, amount ->
                        val h = handle ?: return@detectDragGestures
                        change.consume()
                        val image = fittedImageRect(size.width.toFloat(), size.height.toFloat(), imageWidth, imageHeight)
                        val dx = amount.x / image.width
                        val dy = amount.y / image.height
                        // Aspect ratio in fractions of the image: (w * imageWidth) / (h * imageHeight) = ratio.
                        val fractionRatio = currentAspect?.let { it * imageHeight / imageWidth }
                        working = working.dragged(h, dx, dy, fractionRatio)
                        currentOnChange(working)
                    }
                )
            }
    ) {
        val image = fittedImageRect(size.width, size.height, imageWidth, imageHeight)
        val frame = crop.toScreen(image)

        // Dim everything outside the frame.
        clipRect(frame.left, frame.top, frame.right, frame.bottom, clipOp = ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.6f), topLeft = image.topLeft, size = image.size)
        }

        // Rule-of-thirds grid while dragging.
        if (dragging) {
            val grid = Color.White.copy(alpha = 0.45f)
            for (i in 1..2) {
                val x = frame.left + frame.width * i / 3f
                val y = frame.top + frame.height * i / 3f
                drawLine(grid, Offset(x, frame.top), Offset(x, frame.bottom), strokeWidth = 1.dp.toPx())
                drawLine(grid, Offset(frame.left, y), Offset(frame.right, y), strokeWidth = 1.dp.toPx())
            }
        }

        drawRect(
            Color.White,
            topLeft = frame.topLeft,
            size = frame.size,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Thick L-shaped corner handles.
        val arm = min(22.dp.toPx(), min(frame.width, frame.height) / 3f)
        val thick = 4.dp.toPx()
        val corners = listOf(
            frame.topLeft to Offset(1f, 1f),
            frame.topRight to Offset(-1f, 1f),
            frame.bottomLeft to Offset(1f, -1f),
            frame.bottomRight to Offset(-1f, -1f)
        )
        for ((corner, dir) in corners) {
            drawLine(Color.White, corner, corner + Offset(dir.x * arm, 0f), strokeWidth = thick)
            drawLine(Color.White, corner, corner + Offset(0f, dir.y * arm), strokeWidth = thick)
        }
    }
}

/** Where a photo of [imageWidth] x [imageHeight] lands when fitted into a [boxWidth] x [boxHeight] box. */
internal fun fittedImageRect(boxWidth: Float, boxHeight: Float, imageWidth: Int, imageHeight: Int): Rect {
    val scale = min(boxWidth / imageWidth, boxHeight / imageHeight)
    val w = imageWidth * scale
    val h = imageHeight * scale
    val left = (boxWidth - w) / 2f
    val top = (boxHeight - h) / 2f
    return Rect(left, top, left + w, top + h)
}

private fun CropRect.toScreen(image: Rect) = Rect(
    image.left + left * image.width,
    image.top + top * image.height,
    image.left + right * image.width,
    image.top + bottom * image.height
)

private fun pickHandle(point: Offset, frame: Rect, slop: Float, locked: Boolean): CropHandle? {
    fun near(a: Float, b: Float) = abs(a - b) <= slop
    val nearLeft = near(point.x, frame.left)
    val nearRight = near(point.x, frame.right)
    val nearTop = near(point.y, frame.top)
    val nearBottom = near(point.y, frame.bottom)
    val withinX = point.x in (frame.left - slop)..(frame.right + slop)
    val withinY = point.y in (frame.top - slop)..(frame.bottom + slop)
    return when {
        nearLeft && nearTop -> CropHandle.TopLeft
        nearRight && nearTop -> CropHandle.TopRight
        nearLeft && nearBottom -> CropHandle.BottomLeft
        nearRight && nearBottom -> CropHandle.BottomRight
        !locked && nearLeft && withinY -> CropHandle.Left
        !locked && nearRight && withinY -> CropHandle.Right
        !locked && nearTop && withinX -> CropHandle.Top
        !locked && nearBottom && withinX -> CropHandle.Bottom
        frame.contains(point) -> CropHandle.Move
        else -> null
    }
}

/**
 * Moves the dragged side(s) by ([dx], [dy]) fractions of the image, keeping the frame inside the
 * photo, at least [MIN_SIZE] across, and at [ratio] (in image fractions) when one is set.
 */
private fun CropRect.dragged(handle: CropHandle, dx: Float, dy: Float, ratio: Float?): CropRect {
    if (handle == CropHandle.Move) {
        val mx = dx.coerceIn(-left, 1f - right)
        val my = dy.coerceIn(-top, 1f - bottom)
        return CropRect(left + mx, top + my, right + mx, bottom + my)
    }

    var l = left
    var t = top
    var r = right
    var b = bottom
    val movesLeft = handle == CropHandle.Left || handle == CropHandle.TopLeft || handle == CropHandle.BottomLeft
    val movesRight = handle == CropHandle.Right || handle == CropHandle.TopRight || handle == CropHandle.BottomRight
    val movesTop = handle == CropHandle.Top || handle == CropHandle.TopLeft || handle == CropHandle.TopRight
    val movesBottom = handle == CropHandle.Bottom || handle == CropHandle.BottomLeft || handle == CropHandle.BottomRight

    if (movesLeft) l = (l + dx).coerceIn(0f, r - MIN_SIZE)
    if (movesRight) r = (r + dx).coerceIn(l + MIN_SIZE, 1f)
    if (movesTop) t = (t + dy).coerceIn(0f, b - MIN_SIZE)
    if (movesBottom) b = (b + dy).coerceIn(t + MIN_SIZE, 1f)

    if (ratio != null) {
        // Corner drag with a fixed shape: the width decides, the height follows from the
        // corner opposite the one being dragged. If that doesn't fit, shrink the width instead.
        var w = r - l
        var h = w / ratio
        val maxH = if (movesTop) b else 1f - t
        if (h > maxH) {
            h = maxH
            w = h * ratio
            if (movesLeft) l = r - w else r = l + w
        }
        if (movesTop) t = b - h else b = t + h
    }
    return CropRect(l, t, r, b)
}
