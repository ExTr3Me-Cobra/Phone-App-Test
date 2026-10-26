package com.vipervision.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.t8rin.imagetoolbox.core.resources.Icons
import com.t8rin.imagetoolbox.core.resources.icons.BrightnessHigh
import com.t8rin.imagetoolbox.core.resources.icons.Contrast
import com.t8rin.imagetoolbox.core.resources.icons.Highlight
import com.t8rin.imagetoolbox.core.resources.icons.InvertColors
import com.t8rin.imagetoolbox.core.resources.icons.Palette
import com.t8rin.imagetoolbox.core.resources.icons.Shadow
import com.t8rin.imagetoolbox.core.resources.icons.Thermostat

/** Icons Image Toolbox's set doesn't have, drawn on a 24x24 grid. */
internal object EditorIcons {

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit) = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply(block).build()

    private fun ImageVector.Builder.stroke(block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, pathBuilder = block)

    /** Circle split diagonally, with + and −. */
    val Exposure: ImageVector by lazy {
        icon("Exposure") {
            stroke {
                moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 11.99f, 3f); close()
                moveTo(5.6f, 18.4f); lineTo(18.4f, 5.6f)
                moveTo(6.5f, 9f); lineTo(10.5f, 9f); moveTo(8.5f, 7f); lineTo(8.5f, 11f)
                moveTo(13.5f, 15f); lineTo(17.5f, 15f)
            }
        }
    }

    /** Four arrows out from the centre (Samsung's Move). */
    val Move: ImageVector by lazy {
        icon("Move") {
            stroke {
                moveTo(12f, 3f); lineTo(12f, 21f); moveTo(3f, 12f); lineTo(21f, 12f)
                moveTo(9f, 6f); lineTo(12f, 3f); lineTo(15f, 6f)
                moveTo(9f, 18f); lineTo(12f, 21f); lineTo(15f, 18f)
                moveTo(6f, 9f); lineTo(3f, 12f); lineTo(6f, 15f)
                moveTo(18f, 9f); lineTo(21f, 12f); lineTo(18f, 15f)
            }
        }
    }

    /** Triangle (sharp edges). */
    val Sharpness: ImageVector by lazy {
        icon("Sharpness") {
            stroke { moveTo(12f, 3.5f); lineTo(21f, 19.5f); lineTo(3f, 19.5f); close() }
        }
    }

    /** Frame darkening toward the edges. */
    val Vignette: ImageVector by lazy {
        icon("Vignette") {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(3f, 4f); lineTo(21f, 4f); lineTo(21f, 20f); lineTo(3f, 20f); close()
                moveTo(12f, 7f); arcTo(6f, 5f, 0f, true, false, 12.01f, 7f); close()
            }
        }
    }

    /** Crop corners with a rotate arrow. */
    val CropRotate: ImageVector by lazy {
        icon("CropRotate") {
            stroke {
                moveTo(7f, 3f); lineTo(7f, 17f); lineTo(21f, 17f)
                moveTo(3f, 7f); lineTo(17f, 7f); lineTo(17f, 21f)
                moveTo(14f, 2.5f); arcTo(8f, 8f, 0f, false, true, 21.5f, 10f)
                moveTo(19.5f, 8.5f); lineTo(21.5f, 10.5f); lineTo(23f, 8f)
            }
        }
    }
}

internal val Adjustment.icon: ImageVector
    get() = when (this) {
        Adjustment.Brightness -> Icons.Outlined.BrightnessHigh
        Adjustment.Exposure -> EditorIcons.Exposure
        Adjustment.Contrast -> Icons.Rounded.Contrast
        Adjustment.Highlights -> Icons.Rounded.Highlight
        Adjustment.Shadows -> Icons.Outlined.Shadow
        Adjustment.Saturation -> Icons.Rounded.InvertColors
        Adjustment.Warmth -> Icons.Outlined.Thermostat
        Adjustment.Tint -> Icons.Rounded.Palette
        Adjustment.Sharpness -> EditorIcons.Sharpness
        Adjustment.Vignette -> EditorIcons.Vignette
    }
