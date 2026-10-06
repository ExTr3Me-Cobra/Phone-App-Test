package com.popdown.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

private val SWATCHES = listOf(
    0xFFFFFFFF, 0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00, 0xFF8BE33B, 0xFF34C759, 0xFF00E5D4,
    0xFF00B7FF, 0xFF3D8BFF, 0xFF5E5CE6, 0xFFB04DFF, 0xFFFF4FD8, 0xFFFF2D55,
).map { it.toInt() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LightingSection(l: LightSettings, tick: Int) {
    val context = LocalContext.current
    fun set(change: (LightSettings) -> LightSettings) = Prefs.updateLight(context, change)
    val serviceOn = remember(tick) { LightService.isEnabled(context) }

    LightSwitchRow("Edge lighting", "Pop Down's own lighting around the screen edges for every new notification", l.enabled) { v ->
        set { it.copy(enabled = v) }
    }
    if (!l.enabled) return

    // Setup: the accessibility switch.
    Row(
        Modifier.fillMaxWidth().clickable { openSafe(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (serviceOn) "✅" else "⬜", Modifier.padding(end = 12.dp))
        Column {
            Text("Switch on \"Pop Down Lighting\"", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Settings → Accessibility → Installed apps → Pop Down Lighting → On. It's needed to draw over the lock screen; " +
                    "it only draws the light and reads nothing on your screen.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (!serviceOn) {
        LightNote(
            "If the switch is greyed out (\"Restricted setting\"): open Pop Down's App info → ⋮ (top right) → " +
                "Allow restricted settings, then try again.",
        )
        OutlinedButton(
            onClick = { openSafe(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) { Text("Open Pop Down's App info") }
    }

    // Live preview + full-screen test.
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        MiniPreview(l)
        Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Live preview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                if (l.colorMode == LightColorMode.APP) "Shows your first colour here; real notifications use each app's colour." else "Updates as you change settings.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { playPreview(context, l) }) { Text("Play full screen") }
        }
    }

    LightLabel("Effect")
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LightEffect.entries.forEach { e ->
            FilterChip(selected = l.effect == e, onClick = { set { it.copy(effect = e) } }, label = { Text(e.label) })
        }
    }

    if (l.effect != LightEffect.RAINBOW) {
        LightLabel("Colour")
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LightColorMode.entries.forEach { m ->
                FilterChip(selected = l.colorMode == m, onClick = { set { it.copy(colorMode = m) } }, label = { Text(m.label) })
            }
        }
        if (l.colorMode == LightColorMode.APP) {
            LightNote("Uses each notification's own colour (or its app icon's colour). The colour below is used when an app has none.")
        }
        ColorPicker(if (l.colorMode == LightColorMode.TWO) "First colour" else "Colour", l.color1) { c -> set { it.copy(color1 = c) } }
        if (l.colorMode == LightColorMode.TWO) {
            ColorPicker("Second colour", l.color2) { c -> set { it.copy(color2 = c) } }
        }
    }

    LightSlider("Speed", "%.2fx".format(l.speed), l.speed, 0.25f..3f) { v -> set { it.copy(speed = (v * 20).roundToInt() / 20f) } }
    LightSlider("Thickness", "${l.thicknessDp.roundToInt()} dp", l.thicknessDp, 1f..30f) { v -> set { it.copy(thicknessDp = v.roundToInt().toFloat()) } }
    LightSlider("Brightness", "${l.brightness} %", l.brightness.toFloat(), 20f..100f) { v -> set { it.copy(brightness = v.roundToInt()) } }
    LightSlider("Plays for", "${l.seconds} s", l.seconds.toFloat(), 1f..20f) { v -> set { it.copy(seconds = v.roundToInt()) } }

    LightSwitchRow(
        "Match my screen's corners",
        "Follows the exact curve of your S26 Ultra's screen corners",
        l.matchCorners,
    ) { v -> set { it.copy(matchCorners = v) } }
    if (!l.matchCorners) {
        LightSlider("Corner curve", "${l.cornerDp.roundToInt()} dp", l.cornerDp, 0f..120f) { v -> set { it.copy(cornerDp = v.roundToInt().toFloat()) } }
    }

    LightLabel("When")
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LightWhen.entries.forEach { w ->
            FilterChip(selected = l.whenMode == w, onClick = { set { it.copy(whenMode = w) } }, label = { Text(w.label) })
        }
    }
    LightNote(
        "Tip: to avoid lighting twice, turn off Samsung's own lighting effect (Settings → Notifications → Notification pop-up style). " +
            "Pop Down's lighting works with Detailed pop-ups.",
    )
}

/** A small phone-shaped preview that plays the effect in a loop. */
@Composable
private fun MiniPreview(l: LightSettings) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthDp = 120.dp
    val screenW = context.resources.displayMetrics.widthPixels.toFloat()
    val screenH = context.resources.displayMetrics.heightPixels.toFloat()
    val boxW = with(density) { widthDp.toPx() }
    val scale = boxW / screenW
    val heightDp = with(density) { (screenH * scale).toDp() }
    val full = LightService.spec(context, l, null, forever = true)
    val spec = full.copy(thicknessPx = full.thicknessPx * scale * 2f, cornerPx = full.cornerPx * scale)
    val cornerDp = with(density) { spec.cornerPx.toDp() }
    Box(
        Modifier.size(widthDp, heightDp).clip(RoundedCornerShape(cornerDp)).background(Color(0xFF0B0B0F))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(cornerDp)),
    ) {
        AndroidView(
            factory = { EdgeLightView(it, spec) },
            update = { if (it.spec != spec) it.spec = spec },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(title: String, color: Int, onChange: (Int) -> Unit) {
    Row(Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Color(color)).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 10.dp))
        Text(
            "#%06X".format(color and 0xFFFFFF),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    FlowRow(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SWATCHES.forEach { c ->
            val selected = (c and 0xFFFFFF) == (color and 0xFFFFFF)
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                    .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable { onChange(c) },
            )
        }
    }
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF
    fun rgb(nr: Int, ng: Int, nb: Int) = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
    ChannelSlider("R", r, Color(0xFFFF453A)) { onChange(rgb(it, g, b)) }
    ChannelSlider("G", g, Color(0xFF32D74B)) { onChange(rgb(r, it, b)) }
    ChannelSlider("B", b, Color(0xFF0A84FF)) { onChange(rgb(r, g, it)) }
}

@Composable
private fun ChannelSlider(name: String, value: Int, tint: Color, onChange: (Int) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = tint, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
        Slider(
            value = value.toFloat(), onValueChange = { onChange(it.roundToInt()) }, valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
        )
        Text("$value", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(36.dp).padding(start = 8.dp))
    }
}

@Composable
private fun LightSlider(title: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = current, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun LightSwitchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LightLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
private fun LightNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

private fun openSafe(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
}

/** Plays the effect over the whole screen: through the service if it's on, else inside the app. */
private fun playPreview(context: Context, l: LightSettings) {
    val spec = LightService.spec(context, l, null)
    val service = LightService.instance
    if (service != null) {
        service.play(spec)
        return
    }
    val activity = context as? Activity ?: return
    val root = activity.window.decorView as? ViewGroup ?: return
    lateinit var view: EdgeLightView
    view = EdgeLightView(activity, spec) { root.removeView(view) }
    root.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
}
