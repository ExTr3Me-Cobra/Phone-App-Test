package com.webshortcuts.app.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import com.webshortcuts.app.AdaptiveIcon
import com.webshortcuts.app.IconStyle

enum class MaskShape { CIRCLE, SQUIRCLE }

/**
 * Shows an icon the way a launcher does: the central 72dp of the 108dp canvas fills the slot,
 * cut by the mask. [drawCanvas] draws the full 108dp canvas at the given pixel size.
 */
@Composable
private fun MaskedIcon(
    shape: MaskShape,
    size: Dp,
    drawCanvas: (android.graphics.Canvas, Float) -> Unit,
) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width
        val mask = if (shape == MaskShape.CIRCLE) AdaptiveIcon.circlePath(s) else AdaptiveIcon.squirclePath(s)
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            nc.save()
            nc.clipPath(mask)
            AdaptiveIcon.drawCheckerboard(nc, s, s, s / 8f)
            val total = s * AdaptiveIcon.CANVAS_DP / AdaptiveIcon.VISIBLE_DP
            val inset = (total - s) / 2f
            nc.translate(-inset, -inset)
            drawCanvas(nc, total)
            nc.restore()
        }
    }
}

/** Live preview from the source image and framing. */
@Composable
fun IconPreview(source: Bitmap, style: IconStyle, shape: MaskShape, size: Dp) {
    MaskedIcon(shape, size) { canvas, total -> AdaptiveIcon.draw(canvas, total, source, style) }
}

/** Preview of an already rendered icon bitmap (used for list thumbnails). */
@Composable
fun RenderedIconPreview(icon: Bitmap, shape: MaskShape, size: Dp) {
    MaskedIcon(shape, size) { canvas, total ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(icon, null, RectF(0f, 0f, total, total), paint)
    }
}

/**
 * The full 108dp canvas, with what the launcher can cut away dimmed, the visible area outlined
 * and the 66dp safe zone dashed. Drag to move the image, pinch to zoom.
 */
@Composable
fun FramingEditor(
    source: Bitmap,
    style: IconStyle,
    onStyleChange: (IconStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentStyle by rememberUpdatedState(style)
    val currentOnChange by rememberUpdatedState(onStyleChange)
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(source) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val s = currentStyle
                    val width = size.width.toFloat()
                    currentOnChange(
                        s.copy(
                            zoom = (s.zoom * zoom).coerceIn(AdaptiveIcon.MIN_ZOOM, AdaptiveIcon.MAX_ZOOM),
                            offsetX = (s.offsetX + pan.x / width).coerceIn(-1f, 1f),
                            offsetY = (s.offsetY + pan.y / width).coerceIn(-1f, 1f),
                        ),
                    )
                }
            },
    ) {
        val s = size.width
        drawIntoCanvas {
            AdaptiveIcon.drawCheckerboard(it.nativeCanvas, s, s, s / 24f)
            AdaptiveIcon.draw(it.nativeCanvas, s, source, style)
        }
        drawGuides(s)
    }
}

private fun DrawScope.drawGuides(s: Float) {
    val unit = s / AdaptiveIcon.CANVAS_DP
    val inset = (AdaptiveIcon.CANVAS_DP - AdaptiveIcon.VISIBLE_DP) / 2f * unit
    val visible = s - 2 * inset
    val shade = Color.Black.copy(alpha = 0.55f)
    // Dim the outer band that launchers never show.
    drawRect(shade, Offset.Zero, Size(s, inset))
    drawRect(shade, Offset(0f, s - inset), Size(s, inset))
    drawRect(shade, Offset(0f, inset), Size(inset, visible))
    drawRect(shade, Offset(s - inset, inset), Size(inset, visible))
    // Typical visible shape, and the safe zone every mask keeps.
    translate(inset, inset) {
        drawPath(
            AdaptiveIcon.squirclePath(visible).asComposePath(),
            Color.White.copy(alpha = 0.9f),
            style = Stroke(width = 2f * density),
        )
    }
    drawCircle(
        Color.White.copy(alpha = 0.9f),
        radius = AdaptiveIcon.SAFE_ZONE_DP / 2f * unit,
        center = Offset(s / 2f, s / 2f),
        style = Stroke(
            width = 1.5f * density,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f * density, 6f * density)),
        ),
    )
}
