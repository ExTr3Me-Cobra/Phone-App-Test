package com.datetile.app

import android.content.Context
import android.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** One piece of information on the tile. */
enum class Kind(val label: String, val formats: List<String>) {
    WEEKDAY("Day of the week", listOf("Thursday", "Thu", "Th", "T")),
    DATE("Date", listOf("9", "09", "9th")),
    MONTH("Month", listOf("October", "Oct", "10", "O")),
    YEAR("Year", listOf("2026", "'26")),
    WEEK("Week number", listOf("W41", "Week 41", "41")),
    DAY_OF_YEAR("Day of the year", listOf("282", "Day 282", "282/365")),
    DAYS_LEFT("Days left in year", listOf("83 left", "83")),
    SPECIAL("Special day name", listOf("Name")),
    PATTERN("Custom format", listOf("Pattern")),
    TEXT("Your own text", listOf("Text")),
}

/** What colour a line (or the background) uses. */
enum class Role(val label: String) { TEXT("Text"), ACCENT("Accent"), ON_ACCENT("On accent"), CUSTOM("Custom") }

enum class Shape(val label: String) { ROUNDED("Rounded"), SQUARE("Square"), CIRCLE("Circle"), SQUIRCLE("Squircle"), NONE("No background") }

enum class Font(val label: String, val family: String) {
    SANS("Sans", "sans-serif"),
    CONDENSED("Condensed", "sans-serif-condensed"),
    SERIF("Serif", "serif"),
    MONO("Mono", "monospace"),
    CASUAL("Casual", "casual"),
    CURSIVE("Cursive", "cursive"),
    SMALLCAPS("Small caps", "sans-serif-smallcaps"),
}

enum class AccentMode(val label: String) { FIXED("One colour"), WEEKDAY("Each weekday"), MONTH("Each month"), WALLPAPER("Wallpaper") }

enum class Ring(val label: String) { NONE("None"), WEEK("Week"), MONTH("Month"), YEAR("Year") }

enum class Align(val label: String) { LEFT("Left"), CENTER("Centre"), RIGHT("Right") }

enum class VAlign(val label: String) { TOP("Top"), CENTER("Middle"), BOTTOM("Bottom") }

enum class Tap(val label: String) {
    CALENDAR("Google Calendar"),
    PHONE_CALENDAR("Phone's calendar app"),
    APP("Date Tile settings"),
    NOTHING("Nothing"),
}

/** Colour of the words in the header band. */
enum class HeaderText(val label: String) { AUTO("Automatic"), CUSTOM("Pick"), ACCENT("Today's accent"), LINES("Each line's own") }

data class Line(
    val kind: Kind = Kind.DATE,
    val format: Int = 0,
    /** Height of the text as a share of the tile. */
    val size: Float = 0.3f,
    val weight: Int = 400,
    val italic: Boolean = false,
    val role: Role = Role.TEXT,
    val color: Int = Color.WHITE,
    /** Drawn in the header band (when the band is on). */
    val inHeader: Boolean = false,
    /** For Custom format: e.g. "EEE d". For Your own text: the text. */
    val text: String = "",
)

data class Special(
    val month: Int,
    val day: Int,
    val label: String,
    val color: Int,
    /** 0 = every year. */
    val year: Int = 0,
)

data class Style(
    val lines: List<Line> = Presets.all.first().lines,
    val shape: Shape = Shape.ROUNDED,
    val corner: Float = 0.24f,
    val bgRole: Role = Role.CUSTOM,
    val bgColor: Int = 0xFF1C1B1F.toInt(),
    val bgAlpha: Float = 1f,
    val header: Boolean = true,
    val headerHeight: Float = 0.3f,
    val headerRole: Role = Role.ACCENT,
    val headerColor: Int = 0xFFE5484D.toInt(),
    val border: Float = 0f,
    val borderRole: Role = Role.ACCENT,
    val borderColor: Int = Color.WHITE,
    val textColor: Int = Color.WHITE,
    val accent: Int = 0xFFE5484D.toInt(),
    val accentMode: AccentMode = AccentMode.FIXED,
    val weekdayColors: List<Int> = DEFAULT_WEEKDAY,
    val monthColors: List<Int> = DEFAULT_MONTH,
    val weekendOn: Boolean = false,
    val weekendAccent: Int = 0xFF3E9BFF.toInt(),
    val font: Font = Font.SANS,
    val uppercase: Boolean = true,
    val spacing: Float = 0f,
    val lineGap: Float = 0.02f,
    val padding: Float = 0.08f,
    val align: Align = Align.CENTER,
    val valign: VAlign = VAlign.CENTER,
    val shadow: Boolean = false,
    val ring: Ring = Ring.NONE,
    val ringWidth: Float = 0.05f,
    val specials: List<Special> = emptyList(),
    val tap: Tap = Tap.CALENDAR,
    val headerText: HeaderText = HeaderText.AUTO,
    val headerTextColor: Int = Color.WHITE,
    /** How much of the widget's space the tile fills. */
    val tileScale: Float = 1f,
    /** Makes every line bigger or smaller together. */
    val textScale: Float = 1f,
    val shadowSize: Float = 0.03f,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("lines", JSONArray(lines.map { l ->
            JSONObject().put("kind", l.kind.name).put("format", l.format).put("size", l.size.toDouble())
                .put("weight", l.weight).put("italic", l.italic).put("role", l.role.name).put("color", l.color)
                .put("inHeader", l.inHeader).put("text", l.text)
        }))
        put("shape", shape.name); put("corner", corner.toDouble())
        put("bgRole", bgRole.name); put("bgColor", bgColor); put("bgAlpha", bgAlpha.toDouble())
        put("header", header); put("headerHeight", headerHeight.toDouble())
        put("headerRole", headerRole.name); put("headerColor", headerColor)
        put("border", border.toDouble()); put("borderRole", borderRole.name); put("borderColor", borderColor)
        put("textColor", textColor); put("accent", accent); put("accentMode", accentMode.name)
        put("weekdayColors", JSONArray(weekdayColors)); put("monthColors", JSONArray(monthColors))
        put("weekendOn", weekendOn); put("weekendAccent", weekendAccent)
        put("font", font.name); put("uppercase", uppercase); put("spacing", spacing.toDouble())
        put("lineGap", lineGap.toDouble()); put("padding", padding.toDouble())
        put("align", align.name); put("valign", valign.name); put("shadow", shadow)
        put("ring", ring.name); put("ringWidth", ringWidth.toDouble())
        put("specials", JSONArray(specials.map {
            JSONObject().put("month", it.month).put("day", it.day).put("label", it.label).put("color", it.color).put("year", it.year)
        }))
        put("tap", tap.name)
        put("headerText", headerText.name); put("headerTextColor", headerTextColor)
        put("tileScale", tileScale.toDouble()); put("textScale", textScale.toDouble()); put("shadowSize", shadowSize.toDouble())
    }

    companion object {
        val DEFAULT_WEEKDAY = listOf(
            0xFF3E9BFF, 0xFF30A46C, 0xFFF5A524, 0xFF8E4EC6, 0xFFE5484D, 0xFFD6409F, 0xFF12A594,
        ).map { it.toInt() } // Monday … Sunday
        val DEFAULT_MONTH = listOf(
            0xFF3E9BFF, 0xFFD6409F, 0xFF30A46C, 0xFF8BC34A, 0xFFF5A524, 0xFFFFC53D,
            0xFFE5484D, 0xFFFF7A45, 0xFFB0763B, 0xFFF76B15, 0xFF8E4EC6, 0xFF12A594,
        ).map { it.toInt() }

        private inline fun <reified E : Enum<E>> JSONObject.enum(key: String, def: E): E =
            runCatching { enumValueOf<E>(getString(key)) }.getOrDefault(def)

        private fun JSONObject.f(key: String, def: Float) = optDouble(key, def.toDouble()).toFloat()

        private fun JSONArray?.ints(def: List<Int>): List<Int> =
            if (this == null || length() != def.size) def else List(length()) { getInt(it) }

        fun fromJson(o: JSONObject): Style {
            val d = Style()
            val lines = o.optJSONArray("lines")?.let { a ->
                List(a.length()) { i ->
                    val l = a.getJSONObject(i)
                    Line(
                        kind = l.enum("kind", Kind.DATE), format = l.optInt("format", 0), size = l.f("size", 0.3f),
                        weight = l.optInt("weight", 400), italic = l.optBoolean("italic"), role = l.enum("role", Role.TEXT),
                        color = l.optInt("color", Color.WHITE), inHeader = l.optBoolean("inHeader"), text = l.optString("text"),
                    )
                }
            } ?: d.lines
            val specials = o.optJSONArray("specials")?.let { a ->
                List(a.length()) { i ->
                    val s = a.getJSONObject(i)
                    Special(s.getInt("month"), s.getInt("day"), s.optString("label"), s.optInt("color"), s.optInt("year"))
                }
            } ?: emptyList()
            return Style(
                lines = lines,
                shape = o.enum("shape", d.shape), corner = o.f("corner", d.corner),
                bgRole = o.enum("bgRole", d.bgRole), bgColor = o.optInt("bgColor", d.bgColor), bgAlpha = o.f("bgAlpha", d.bgAlpha),
                header = o.optBoolean("header", d.header), headerHeight = o.f("headerHeight", d.headerHeight),
                headerRole = o.enum("headerRole", d.headerRole), headerColor = o.optInt("headerColor", d.headerColor),
                border = o.f("border", d.border), borderRole = o.enum("borderRole", d.borderRole), borderColor = o.optInt("borderColor", d.borderColor),
                textColor = o.optInt("textColor", d.textColor), accent = o.optInt("accent", d.accent),
                accentMode = o.enum("accentMode", d.accentMode),
                weekdayColors = o.optJSONArray("weekdayColors").ints(d.weekdayColors),
                monthColors = o.optJSONArray("monthColors").ints(d.monthColors),
                weekendOn = o.optBoolean("weekendOn", d.weekendOn), weekendAccent = o.optInt("weekendAccent", d.weekendAccent),
                font = o.enum("font", d.font), uppercase = o.optBoolean("uppercase", d.uppercase), spacing = o.f("spacing", d.spacing),
                lineGap = o.f("lineGap", d.lineGap), padding = o.f("padding", d.padding),
                align = o.enum("align", d.align), valign = o.enum("valign", d.valign), shadow = o.optBoolean("shadow", d.shadow),
                ring = o.enum("ring", d.ring), ringWidth = o.f("ringWidth", d.ringWidth),
                specials = specials, tap = o.enum("tap", d.tap),
                headerText = o.enum("headerText", d.headerText), headerTextColor = o.optInt("headerTextColor", d.headerTextColor),
                tileScale = o.f("tileScale", d.tileScale), textScale = o.f("textScale", d.textScale), shadowSize = o.f("shadowSize", d.shadowSize),
            )
        }
    }
}

/** Saved settings, shared by the app and the widget. */
object Prefs {
    private const val FILE = "date_tile"
    private const val KEY = "style"
    private val state = MutableStateFlow(Style())
    private var loaded = false
    val flow: StateFlow<Style> get() = state

    fun get(context: Context): Style {
        if (!loaded) {
            loaded = true
            val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
            if (raw != null) runCatching { state.value = Style.fromJson(JSONObject(raw)) }
        }
        return state.value
    }

    fun set(context: Context, style: Style) {
        get(context)
        state.value = style
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, style.toJson().toString()).apply()
        DateWidget.updateAll(context)
    }

    fun update(context: Context, change: (Style) -> Style) = set(context, change(get(context)))
}
