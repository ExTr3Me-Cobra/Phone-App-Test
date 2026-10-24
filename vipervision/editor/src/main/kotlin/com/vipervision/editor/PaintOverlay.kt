package com.vipervision.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val BrushColor = Color(0xFFFF3B30).copy(alpha = 0.55f)

/**
 * Lets the user paint over a photo shown with ContentScale.Fit in the same box. Finished strokes
 * are added to [session] with points as fractions of the photo and the radius in screen pixels.
 */
@Composable
internal fun PaintOverlay(
    imageWidth: Int,
    imageHeight: Int,
    session: PaintSession,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { session.brushRadiusDp.dp.toPx() }
    val currentRadius by rememberUpdatedState(radiusPx)
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(imageWidth, imageHeight, session) {
                detectTapGestures { tap ->
                    val image = fittedImageRect(size.width.toFloat(), size.height.toFloat(), imageWidth, imageHeight)
                    if (image.contains(tap)) {
                        val p = Offset((tap.x - image.left) / image.width, (tap.y - image.top) / image.height)
                        session.strokes.add(ScreenStroke(listOf(p), currentRadius))
                    }
                }
            }
            .pointerInput(imageWidth, imageHeight, session) {
                detectDragGestures(
                    onDragStart = { start ->
                        val image = fittedImageRect(size.width.toFloat(), size.height.toFloat(), imageWidth, imageHeight)
                        current = listOf(
                            Offset(
                                ((start.x - image.left) / image.width).coerceIn(0f, 1f),
                                ((start.y - image.top) / image.height).coerceIn(0f, 1f)
                            )
                        )
                    },
                    onDragEnd = {
                        if (current.isNotEmpty()) session.strokes.add(ScreenStroke(current, currentRadius))
                        current = emptyList()
                    },
                    onDragCancel = { current = emptyList() },
                    onDrag = { change, _ ->
                        change.consume()
                        val image = fittedImageRect(size.width.toFloat(), size.height.toFloat(), imageWidth, imageHeight)
                        current = current + Offset(
                            ((change.position.x - image.left) / image.width).coerceIn(0f, 1f),
                            ((change.position.y - image.top) / image.height).coerceIn(0f, 1f)
                        )
                    }
                )
            }
    ) {
        val image = fittedImageRect(size.width, size.height, imageWidth, imageHeight)
        session.shownWidthPx = image.width
        fun toScreen(p: Offset) = Offset(image.left + p.x * image.width, image.top + p.y * image.height)

        val all = session.strokes + listOfNotNull(current.takeIf { it.isNotEmpty() }?.let { ScreenStroke(it, radiusPx) })
        for (stroke in all) {
            if (stroke.points.size == 1) {
                drawCircle(BrushColor, stroke.radiusPx, toScreen(stroke.points.first()))
            } else {
                val path = Path()
                stroke.points.forEachIndexed { i, p ->
                    val s = toScreen(p)
                    if (i == 0) path.moveTo(s.x, s.y) else path.lineTo(s.x, s.y)
                }
                drawPath(
                    path,
                    BrushColor,
                    style = Stroke(width = stroke.radiusPx * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}
