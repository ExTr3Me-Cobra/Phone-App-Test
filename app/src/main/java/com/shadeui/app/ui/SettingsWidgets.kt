package com.shadeui.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 4.dp),
    )
}

@Composable
fun SwitchRow(title: String, summary: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A row that opens a single-choice dialog. */
@Composable
fun <T> ChoiceRow(title: String, summary: String? = null, value: T, options: List<Pair<T, String>>, onChange: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(
            options.firstOrNull { it.first == value }?.second ?: "",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { (v, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onChange(v); open = false }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = v == value, onClick = { onChange(v); open = false })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun SliderRow(
    title: String,
    value: Int,
    range: IntRange,
    format: (Int) -> String = { it.toString() },
    onChange: (Int) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(format(local.roundToInt()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

@Composable
fun NavRow(title: String, summary: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun BodyText(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

val SWATCHES = listOf(
    0xFF4FC3F7, 0xFF40C4FF, 0xFF448AFF, 0xFF7C4DFF, 0xFFE040FB, 0xFFFF4081, 0xFFFF5252,
    0xFFFF9100, 0xFFFFD740, 0xFFB2FF59, 0xFF69F0AE, 0xFF64FFDA, 0xFFFFFFFF,
).map { it.toInt() }

@Composable
fun ColorSwatches(selected: Int?, allowAuto: Boolean, onPick: (Int?) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (allowAuto) {
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .border(if (selected == null) 3.dp else 1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { onPick(null) },
                contentAlignment = Alignment.Center,
            ) { Text("A", fontWeight = FontWeight.Bold) }
        }
        SWATCHES.forEach { c ->
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color(c))
                    .border(if (selected == c) 3.dp else 1.dp, if (selected == c) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                    .clickable { onPick(c) },
            )
        }
    }
}
