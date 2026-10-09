package com.glowalerts.app

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import android.graphics.Color as AColor

/** Ready-made colours, as ARGB ints. */
val SWATCHES = listOf(
    0xFFFFFFFF, 0xFFFF3B30, 0xFFFF6A00, 0xFFFF9500, 0xFFFFCC00, 0xFFC6FF00, 0xFF34C759, 0xFF00E676,
    0xFF00E5D4, 0xFF00B7FF, 0xFF3D8BFF, 0xFF2962FF, 0xFF5E5CE6, 0xFFB04DFF, 0xFFE040FB, 0xFFFF4FD8,
    0xFFFF2D55, 0xFFFF8A80, 0xFFFFD180, 0xFFB9F6CA, 0xFF80D8FF, 0xFFB388FF, 0xFFFFE0B2, 0xFF9E9E9E,
).map { it.toInt() }

fun hex(color: Int) = "#%06X".format(color and 0xFFFFFF)

fun parseHex(text: String): Int? {
    val t = text.trim().removePrefix("#")
    if (t.length != 6) return null
    return t.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
}

/**
 * A full colour picker: a saturation / brightness square, a hue bar, a hex box, ready-made and
 * recent colours, and an eyedropper that takes a colour from any picture or screenshot.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(title: String, initial: Int, recent: List<Int>, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val start = remember { FloatArray(3).also { AColor.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val color = AColor.HSVToColor(floatArrayOf(hue, sat, value))
    var hexText by remember { mutableStateOf(hex(initial)) }

    fun setColor(c: Int) {
        val hsv = FloatArray(3)
        AColor.colorToHSV(c, hsv)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
        hexText = hex(c)
    }

    val eyedropper = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { if (it.hasExtra(EyedropperActivity.EXTRA_COLOR)) setColor(it.getIntExtra(EyedropperActivity.EXTRA_COLOR, 0)) }
        }
    }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = { onPick(color or 0xFF000000.toInt()) }) { Text("Use colour") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Saturation (across) and brightness (down) for the current hue.
                val hueColor = Color(AColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                var svSize by remember { mutableStateOf(IntSize(1, 1)) }
                fun pickSv(p: Offset) {
                    sat = (p.x / svSize.width).coerceIn(0f, 1f)
                    value = 1f - (p.y / svSize.height).coerceIn(0f, 1f)
                    hexText = hex(AColor.HSVToColor(floatArrayOf(hue, sat, value)))
                }
                Box(
                    Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp))
                        .pointerInput(Unit) { svSize = size; detectTapGestures { pickSv(it) } }
                        .pointerInput(Unit) { svSize = size; detectDragGestures { change, _ -> pickSv(change.position) } }
                        .drawBehind {
                            drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                            val c = Offset(sat * size.width, (1f - value) * size.height)
                            drawCircle(Color.Black, radius = 11.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
                            drawCircle(Color.White, radius = 9.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
                        },
                )
                // Hue bar.
                var hueSize by remember { mutableStateOf(IntSize(1, 1)) }
                fun pickHue(p: Offset) {
                    hue = (p.x / hueSize.width).coerceIn(0f, 1f) * 359.9f
                    hexText = hex(AColor.HSVToColor(floatArrayOf(hue, sat, value)))
                }
                Box(
                    Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(15.dp))
                        .pointerInput(Unit) { hueSize = size; detectTapGestures { pickHue(it) } }
                        .pointerInput(Unit) { hueSize = size; detectDragGestures { change, _ -> pickHue(change.position) } }
                        .drawBehind {
                            drawRect(Brush.horizontalGradient((0..6).map { Color(AColor.HSVToColor(floatArrayOf(it * 60f % 360f, 1f, 1f))) }))
                            val x = hue / 360f * size.width
                            drawCircle(Color.White, radius = size.height / 2.4f, center = Offset(x, size.height / 2), style = Stroke(3.dp.toPx()))
                        },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Color(initial)))
                    Text("→")
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(color)).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { t ->
                            hexText = t
                            parseHex(t)?.let { c ->
                                val hsv = FloatArray(3)
                                AColor.colorToHSV(c, hsv)
                                hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                            }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        label = { Text("Hex") },
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedButton(
                    onClick = { eyedropper.launch(Intent(context, EyedropperActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("🎯  Pick from a picture or screenshot") }
                if (recent.isNotEmpty()) {
                    Text("Recent", style = MaterialTheme.typography.labelLarge)
                    SwatchRow(recent) { setColor(it) }
                }
                Text("Colours", style = MaterialTheme.typography.labelLarge)
                SwatchRow(SWATCHES) { setColor(it) }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwatchRow(colors: List<Int>, onClick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEach { c ->
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable { onClick(c) },
            )
        }
    }
}

/** A tappable colour row: swatch, name and hex. */
@Composable
fun ColorRow(title: String, color: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(color)).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hex(color), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Change", color = MaterialTheme.colorScheme.primary)
    }
}
