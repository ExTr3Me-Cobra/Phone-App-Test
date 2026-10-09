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
import androidx.compose.foundation.layout.fillMaxWidth
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

/** Hue, colourfulness and brightness sliders, a hex box and quick swatches. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorDialog(title: String, initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { Color.colorToHSV(initial, it) } }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color.HSVToColor(floatArrayOf(h, sat, v))
    var hex by remember { mutableStateOf("%06X".format(initial and 0xFFFFFF)) }
    fun setColor(c: Int) {
        val a = FloatArray(3)
        Color.colorToHSV(c, a)
        h = a[0]; sat = a[1]; v = a[2]
        hex = "%06X".format(c and 0xFFFFFF)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = { onPick(color); onDismiss() }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(44.dp)
                        .background(UiColor(color), RoundedCornerShape(10.dp)),
                )
                Text("Colour")
                Slider(h, { h = it; hex = "%06X".format(Color.HSVToColor(floatArrayOf(h, sat, v)) and 0xFFFFFF) }, valueRange = 0f..360f)
                Text("Strength")
                Slider(sat, { sat = it; hex = "%06X".format(Color.HSVToColor(floatArrayOf(h, sat, v)) and 0xFFFFFF) })
                Text("Brightness")
                Slider(v, { v = it; hex = "%06X".format(Color.HSVToColor(floatArrayOf(h, sat, v)) and 0xFFFFFF) })
                OutlinedTextField(
                    value = hex,
                    onValueChange = { t ->
                        hex = t.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                        if (hex.length == 6) setColor(0xFF000000.toInt() or hex.toInt(16))
                    },
                    label = { Text("Hex  #") },
                    singleLine = true,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

/** A labelled colour dot that opens the picker. */
@Composable
fun ColorRow(label: String, color: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(30.dp).background(UiColor(color), CircleShape).border(1.dp, UiColor(0x55888888), CircleShape))
        Text(label, Modifier.weight(1f))
        Text("#%06X".format(color and 0xFFFFFF))
    }
    if (open) ColorDialog(label, color, { open = false }, onPick)
}
