package com.datetile.app

import android.app.Activity
import android.app.DatePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)) {
                Surface(Modifier.fillMaxSize()) { Screen() }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Screen() {
    val context = LocalContext.current
    Prefs.get(context)
    val s by Prefs.flow.collectAsState()
    fun set(change: (Style) -> Style) = Prefs.update(context, change)
    var offset by remember { mutableLongStateOf(0L) }
    var widgets by remember { mutableIntStateOf(DateWidget.count(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        widgets = DateWidget.count(context)
        DateWidget.updateAll(context)
    }
    val date = LocalDate.now().plusDays(offset)

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        // The preview stays at the top while the settings scroll.
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                TilePreview(s, date, 150)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TilePreview(s, date, 64)
                    Text("actual size", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { offset-- }) { Text("◀") }
                Text(
                    if (offset == 0L) "Today" else date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy")),
                    Modifier.clickable { offset = 0 }.padding(horizontal = 8.dp),
                )
                TextButton(onClick = { offset++ }) { Text("▶") }
            }
        }
        HorizontalDivider()
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            if (widgets == 0) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Put Date Tile on your home screen", fontWeight = FontWeight.Bold)
                        Text("Tap below, or long-press your home screen → Widgets → Date Tile.")
                        Button(onClick = { pin(context) }) { Text("Add widget") }
                    }
                }
            } else {
                Text(
                    "$widgets widget${if (widgets == 1) "" else "s"} on your home screen – changes show straight away.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section("Layouts", "Starting points – your colours are kept", open = true) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Presets.all.forEach { p ->
                        val ps = p.apply(s.copy(lines = p.lines))
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { set { ps } }) {
                            TilePreview(ps, date, 64)
                            Text(p.name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Section("What shows", s.lines.joinToString(" · ") { it.kind.label }) {
                s.lines.forEachIndexed { i, line ->
                    LineEditor(
                        s, line, i, s.lines.size,
                        onChange = { new -> set { st -> st.copy(lines = st.lines.toMutableList().also { it[i] = new }) } },
                        onMove = { d ->
                            set { st ->
                                val l = st.lines.toMutableList()
                                val j = (i + d).coerceIn(0, l.lastIndex)
                                l.add(j, l.removeAt(i)); st.copy(lines = l)
                            }
                        },
                        onDelete = { set { st -> st.copy(lines = st.lines.toMutableList().also { it.removeAt(i) }) } },
                    )
                }
                OutlinedButton(onClick = { set { it.copy(lines = it.lines + Line(Kind.YEAR, 0, 0.12f)) } }) { Text("+ Add a line") }
                Text("Top to bottom, in this order. Sizes are a share of the tile's height; long text shrinks to fit.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Section("Position & text", "${s.align.label} · ${s.valign.label} · ${s.font.label}") {
                Label("Font")
                Chips(Font.entries, s.font, { it.label }) { v -> set { it.copy(font = v) } }
                SwitchRow("CAPITAL LETTERS", s.uppercase) { v -> set { it.copy(uppercase = v) } }
                SwitchRow("Shadow behind text", s.shadow) { v -> set { it.copy(shadow = v) } }
                Label("Across")
                Chips(Align.entries, s.align, { it.label }) { v -> set { it.copy(align = v) } }
                Label("Up and down")
                Chips(VAlign.entries, s.valign, { it.label }) { v -> set { it.copy(valign = v) } }
                SliderRow("Letter spacing", "%.2f".format(s.spacing), s.spacing, -0.1f..0.4f) { v -> set { it.copy(spacing = v) } }
                SliderRow("Space between lines", pct(s.lineGap), s.lineGap, -0.05f..0.15f) { v -> set { it.copy(lineGap = v) } }
                SliderRow("Space from the edge", pct(s.padding), s.padding, 0f..0.25f) { v -> set { it.copy(padding = v) } }
            }

            Section("Shape & background", "${s.shape.label}${if (s.header) " · header band" else ""}") {
                Chips(Shape.entries, s.shape, { it.label }) { v -> set { it.copy(shape = v) } }
                if (s.shape == Shape.ROUNDED || s.shape == Shape.NONE) {
                    SliderRow("Corner roundness", pct(s.corner), s.corner, 0f..0.5f) { v -> set { it.copy(corner = v) } }
                }
                if (s.shape != Shape.NONE) {
                    Label("Background colour")
                    Chips(listOf(Role.CUSTOM, Role.ACCENT), s.bgRole, { if (it == Role.CUSTOM) "Pick" else "Today's accent" }) { v -> set { it.copy(bgRole = v) } }
                    if (s.bgRole == Role.CUSTOM) ColorRow("Background", s.bgColor) { c -> set { it.copy(bgColor = c) } }
                    SliderRow("Background opacity", pct(s.bgAlpha), s.bgAlpha, 0f..1f) { v -> set { it.copy(bgAlpha = v) } }
                    SliderRow("Border", pct(s.border), s.border, 0f..0.08f) { v -> set { it.copy(border = v) } }
                    if (s.border > 0f) {
                        Chips(listOf(Role.ACCENT, Role.TEXT, Role.CUSTOM), s.borderRole, { it.label }) { v -> set { it.copy(borderRole = v) } }
                        if (s.borderRole == Role.CUSTOM) ColorRow("Border colour", s.borderColor) { c -> set { it.copy(borderColor = c) } }
                    }
                }
                SwitchRow("Header band (like a calendar page)", s.header) { v -> set { it.copy(header = v) } }
                if (s.header) {
                    SliderRow("Header height", pct(s.headerHeight), s.headerHeight, 0.15f..0.5f) { v -> set { it.copy(headerHeight = v) } }
                    Chips(listOf(Role.ACCENT, Role.CUSTOM), s.headerRole, { if (it == Role.CUSTOM) "Pick" else "Today's accent" }) { v -> set { it.copy(headerRole = v) } }
                    if (s.headerRole == Role.CUSTOM) ColorRow("Header colour", s.headerColor) { c -> set { it.copy(headerColor = c) } }
                    Text("Choose which lines go in the band under \"What shows\".",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Section("Colours that change", "Accent: ${s.accentMode.label}${if (s.weekendOn) " · weekends" else ""}") {
                ColorRow("Text colour", s.textColor) { c -> set { it.copy(textColor = c) } }
                Label("Accent colour")
                Chips(AccentMode.entries, s.accentMode, { it.label }) { v -> set { it.copy(accentMode = v) } }
                when (s.accentMode) {
                    AccentMode.FIXED -> ColorRow("Accent", s.accent) { c -> set { it.copy(accent = c) } }
                    AccentMode.WEEKDAY -> java.time.DayOfWeek.entries.forEachIndexed { i, d ->
                        ColorRow(d.getDisplayName(TextStyle.FULL, Locale.getDefault()), s.weekdayColors[i]) { c ->
                            set { it.copy(weekdayColors = it.weekdayColors.toMutableList().also { l -> l[i] = c }) }
                        }
                    }
                    AccentMode.MONTH -> java.time.Month.entries.forEachIndexed { i, m ->
                        ColorRow(m.getDisplayName(TextStyle.FULL, Locale.getDefault()), s.monthColors[i]) { c ->
                            set { it.copy(monthColors = it.monthColors.toMutableList().also { l -> l[i] = c }) }
                        }
                    }
                    AccentMode.WALLPAPER -> Text("Uses your wallpaper's colour palette (updates daily and when you open this app).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SwitchRow("Different accent on weekends", s.weekendOn) { v -> set { it.copy(weekendOn = v) } }
                if (s.weekendOn) ColorRow("Weekend accent", s.weekendAccent) { c -> set { it.copy(weekendAccent = c) } }
                Text("Anything set to \"Accent\" – lines, header, ring, border, background – follows these.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Section("Progress ring", s.ring.label) {
                Text("A bar round the edge that fills up through the week, month or year.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Chips(Ring.entries, s.ring, { it.label }) { v -> set { it.copy(ring = v) } }
                if (s.ring != Ring.NONE) SliderRow("Thickness", pct(s.ringWidth), s.ringWidth, 0.02f..0.1f) { v -> set { it.copy(ringWidth = v) } }
            }

            Section("Special days", if (s.specials.isEmpty()) "Birthdays, holidays…" else "${s.specials.size} saved") {
                SpecialDays(s) { new -> set { new } }
            }

            Section("When you tap it", s.tap.label) {
                Chips(Tap.entries, s.tap, { it.label }) { v -> set { it.copy(tap = v) } }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

private fun pct(v: Float) = "${(v * 100).roundToInt()} %"

private fun pin(context: Context) {
    val mgr = context.getSystemService(AppWidgetManager::class.java)
    if (mgr.isRequestPinAppWidgetSupported) {
        mgr.requestPinAppWidget(ComponentName(context, DateWidget::class.java), null, null)
    } else {
        android.widget.Toast.makeText(context, "Long-press your home screen → Widgets → Date Tile", android.widget.Toast.LENGTH_LONG).show()
    }
}

@Composable
fun TilePreview(s: Style, date: LocalDate, sizeDp: Int) {
    val context = LocalContext.current
    Box(
        Modifier.size(sizeDp.dp).background(
            // A hint of a home screen behind, so see-through tiles still show.
            UiColor(0x22888888), RoundedCornerShape((sizeDp / 6).dp),
        ),
    ) {
        Canvas(Modifier.size(sizeDp.dp)) {
            drawIntoCanvas { Renderer.draw(context, it.nativeCanvas, s, date, size.minDimension) }
        }
    }
}

@Composable
private fun Section(title: String, summary: String, open: Boolean = false, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(open) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (!expanded) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(if (expanded) "▲" else "▼", color = MaterialTheme.colorScheme.primary)
        }
        if (expanded) Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
        HorizontalDivider()
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onPick(o) }, label = { Text(label(o)) }) }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun SliderRow(title: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(title, Modifier.weight(1f))
            Text(valueText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun LineEditor(
    s: Style, line: Line, index: Int, count: Int,
    onChange: (Line) -> Unit, onMove: (Int) -> Unit, onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { open = !open }) {
                    Text(line.kind.label, fontWeight = FontWeight.Bold)
                    Text(
                        "“${Renderer.text(line, s, LocalDate.now()).ifEmpty { "(only on special days)" }}”" +
                            if (line.inHeader && s.header) " · in header" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = { onMove(-1) }, enabled = index > 0) { Text("↑") }
                TextButton(onClick = { onMove(1) }, enabled = index < count - 1) { Text("↓") }
                TextButton(onClick = { open = !open }) { Text(if (open) "Close" else "Edit") }
            }
            if (open) {
                Label("Shows")
                Chips(Kind.entries, line.kind, { it.label }) { onChange(line.copy(kind = it, format = 0)) }
                if (line.kind.formats.size > 1) {
                    Label("Style")
                    Chips(line.kind.formats.indices.toList(), line.format, { line.kind.formats[it] }) { onChange(line.copy(format = it)) }
                }
                if (line.kind == Kind.PATTERN) {
                    OutlinedTextField(
                        line.text, { onChange(line.copy(text = it)) }, Modifier.fillMaxWidth(),
                        label = { Text("Format, e.g. EEE d  ·  d MMM  ·  MM/dd") }, singleLine = true,
                    )
                    Text("d = day, dd = 09, EEE = Thu, EEEE = Thursday, MMM = Oct, MMMM = October, MM = 10, yy = 26, yyyy = 2026. Put words in 'quotes'.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (line.kind == Kind.TEXT) {
                    OutlinedTextField(line.text, { onChange(line.copy(text = it)) }, Modifier.fillMaxWidth(), label = { Text("Text") }, singleLine = true)
                }
                if (line.kind == Kind.SPECIAL) {
                    Text("Shows the name of today's special day, and nothing on other days.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SliderRow("Size", pct(line.size), line.size, 0.06f..0.8f) { onChange(line.copy(size = it)) }
                SliderRow("Thickness", "${line.weight}", line.weight.toFloat(), 100f..900f, steps = 7) {
                    onChange(line.copy(weight = (it / 100).roundToInt() * 100))
                }
                SwitchRow("Italic", line.italic) { onChange(line.copy(italic = it)) }
                Label("Colour")
                Chips(Role.entries, line.role, { it.label }) { onChange(line.copy(role = it)) }
                if (line.role == Role.CUSTOM) ColorRow("Line colour", line.color) { onChange(line.copy(color = it)) }
                if (s.header) SwitchRow("In the header band", line.inHeader) { onChange(line.copy(inHeader = it)) }
                TextButton(onClick = onDelete) { Text("Remove this line", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun SpecialDays(s: Style, onSet: (Style) -> Unit) {
    val context = LocalContext.current
    Text("On these days the accent colour changes, and a \"Special day name\" line shows the name.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    s.specials.sortedWith(compareBy({ it.month }, { it.day })).forEach { sp ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(20.dp).background(UiColor(sp.color), CircleShape))
            val name = java.time.Month.of(sp.month).getDisplayName(TextStyle.SHORT, Locale.getDefault())
            Text("$name ${sp.day}${if (sp.year != 0) ", ${sp.year}" else " (every year)"} · ${sp.label}", Modifier.weight(1f))
            TextButton(onClick = { onSet(s.copy(specials = s.specials - sp)) }) { Text("Remove") }
        }
    }
    var label by remember { mutableStateOf("") }
    var every by remember { mutableStateOf(true) }
    var color by remember { mutableIntStateOf(0xFFD6409F.toInt()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Add a special day", fontWeight = FontWeight.Bold)
            OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), label = { Text("Name (emoji welcome 🎂)") }, singleLine = true)
            SwitchRow("Every year", every) { every = it }
            ColorRow("Colour for that day", color) { color = it }
            Button(onClick = {
                val today = LocalDate.now()
                DatePickerDialog(context, { _, y, m, d ->
                    onSet(s.copy(specials = s.specials + Special(m + 1, d, label.ifBlank { "Special" }, color, if (every) 0 else y)))
                    label = ""
                }, today.year, today.monthValue - 1, today.dayOfMonth).show()
            }) { Text("Pick the date and add") }
        }
    }
}
