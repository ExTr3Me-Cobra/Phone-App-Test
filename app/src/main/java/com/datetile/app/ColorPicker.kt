package com.datetile.app

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.unit.dp

val SWATCHES = listOf(
    0xFFFFFFFF, 0xFFBDBDBD, 0xFF757575, 0xFF1C1B1F, 0xFF000000,
    0xFFE5484D, 0xFFFF7A45, 0xFFF5A524, 0xFFFFC53D, 0xFF8BC34A,
    0xFF30A46C, 0xFF12A594, 0xFF3E9BFF, 0xFF3451B2, 0xFF8E4EC6,
    0xFFD6409F, 0xFFB0763B, 0xFFFFD6E0, 0xFFD5F5E3, 0xFFD6E9FF,
).map { it.toInt() }

/** Hue, colourfulness, brightness and opacity sliders, a hex box and quick swatches. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorDialog(title: String, initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { Color.colorToHSV(initial, it) } }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    var a by remember { mutableFloatStateOf(Color.alpha(initial) / 255f) }
    fun current() = Color.HSVToColor((a * 255).roundToInt(), floatArrayOf(h, sat, v))
    var hex by remember { mutableStateOf("%08X".format(initial)) }
    fun refreshHex() { hex = "%08X".format(current()) }
    fun setColor(c: Int) {
        val f = FloatArray(3)
        Color.colorToHSV(c, f)
        h = f[0]; sat = f[1]; v = f[2]; a = Color.alpha(c) / 255f
        hex = "%08X".format(c)
    }
    val color = current()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = { onPick(color); onDismiss() }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(44.dp).checkers(RoundedCornerShape(10.dp))) {
                    Box(Modifier.fillMaxSize().background(UiColor(color), RoundedCornerShape(10.dp)))
                }
                Text("Colour")
                Slider(h, { h = it; refreshHex() }, valueRange = 0f..360f)
                Text("Strength")
                Slider(sat, { sat = it; refreshHex() })
                Text("Brightness")
                Slider(v, { v = it; refreshHex() })
                Text("Opacity  ${(a * 100).roundToInt()} %  (0 = fully transparent)")
                Slider(a, { a = it; refreshHex() })
                OutlinedTextField(
                    value = hex,
                    onValueChange = { t ->
                        hex = t.uppercase().filter { it in "0123456789ABCDEF" }.take(8)
                        when (hex.length) {
                            6 -> setColor(0xFF000000.toInt() or hex.toLong(16).toInt()).also { hex = "FF$hex" }
                            8 -> setColor(hex.toLong(16).toInt())
                        }
                    },
                    label = { Text("Hex  #AARRGGBB") },
                    singleLine = true,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Transparent first.
                    Box(
                        Modifier.size(30.dp).checkers(CircleShape).border(1.dp, UiColor(0x55888888), CircleShape)
                            .clickable { setColor(Color.TRANSPARENT) },
                    )
                    SWATCHES.forEach { c ->
                        Box(
                            Modifier.size(30.dp).background(UiColor(c), CircleShape)
                                .border(1.dp, UiColor(0x55888888), CircleShape)
                                .clickable { setColor(c) },
                        )
                    }
                }
            }
        },
    )
}

/** A grey checkerboard, so see-through colours show as see-through. */
fun Modifier.checkers(shape: androidx.compose.ui.graphics.Shape): Modifier = this.clip(shape).drawBehind {
    val cell = 6.dp.toPx()
    val cols = (size.width / cell).toInt() + 1
    val rows = (size.height / cell).toInt() + 1
    drawRect(UiColor(0xFFDDDDDD))
    for (y in 0 until rows) for (x in 0 until cols) {
        if ((x + y) % 2 == 0) drawRect(UiColor(0xFF999999), Offset(x * cell, y * cell), Size(cell, cell))
    }
}

/** A labelled colour dot that opens the picker. */
@Composable
fun ColorRow(label: String, color: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(30.dp).checkers(CircleShape)) {
            Box(Modifier.fillMaxSize().background(UiColor(color), CircleShape).border(1.dp, UiColor(0x55888888), CircleShape))
        }
        Text(label, Modifier.weight(1f))
        val alpha = Color.alpha(color)
        Text(
            when (alpha) {
                0 -> "Transparent"
                255 -> "#%06X".format(color and 0xFFFFFF)
                else -> "#%06X · %d %%".format(color and 0xFFFFFF, (alpha * 100 + 127) / 255)
            },
        )
    }
    if (open) ColorDialog(label, color, { open = false }, onPick)
}
