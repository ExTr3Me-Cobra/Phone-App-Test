package com.clockout.app

import android.content.Context
import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** One clock ring: its two-letter code (BT, MV, OL, IL, ET…) and when it happened. */
data class Ring(val code: String, val time: LocalDateTime)

/** Reads clock rings out of the text the camera saw. */
object RingReader {
    private val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

    // 09-OCT-26 02.41.06 PM  (dots or colons, a little forgiving about spaces)
    private val DATETIME = Regex(
        """(\d{1,2})\s*-\s*([A-Za-z0-9]{3})\s*-\s*(\d{2,4})\s+(\d{1,2})\s*[.:,]\s*(\d{2})\s*[.:,]\s*(\d{2})\s*([AaPp])\s*\.?\s*[Mm]""",
    )

    // Military + decimal: 09-OCT-26 14.68  (14 hours + 0.68 of an hour). The date is optional.
    private val DECIMAL_DATED = Regex(
        """(\d{1,2})\s*-\s*([A-Za-z0-9]{3})\s*-\s*(\d{2,4})\s+(\d{1,2})\s*[.,]\s*(\d{1,4})(?![\d:.]|\s*[AaPp]\.?\s*[Mm])""",
    )
    private val DECIMAL_ALONE = Regex("""^(\d{1,2})\s*[.,]\s*(\d{1,4})$""")

    // 013 IL
    private val CODE = Regex("""\b[0-9Oo]{3}\s+([A-Z]{2})\b""")

    // Lines from the details panel underneath ("Code: 013 IL Op Code …", "time: 09-OCT-26 …"),
    // which can belong to other people: ignored.
    private val PANEL = Regex("""(?i)(op\s*code|code\s*:|time\s*:|ime\s*:|employee|oyee)""")

    private class Found(val y: Float, val h: Float, val value: Any)

    /** Month letters that the camera sometimes reads as digits. */
    private fun month(raw: String): Int? {
        val m = raw.uppercase().replace('0', 'O').replace('1', 'I').replace('5', 'S')
        return MONTHS.indexOf(m).takeIf { it >= 0 }?.plus(1)
    }

    private fun parseTime(m: MatchResult): LocalDateTime? = runCatching {
        val (d, mon, y, hh, mm, ss, ap) = m.destructured
        var hour = hh.toInt() % 12
        if (ap.uppercase() == "P") hour += 12
        val year = y.toInt().let { if (it < 100) 2000 + it else it }
        LocalDateTime.of(year, month(mon) ?: return null, d.toInt(), hour, mm.toInt(), ss.toInt())
    }.getOrNull()

    /** Hours with a decimal fraction ("14", "68") as a time of day. */
    fun decimalTime(hours: String, fraction: String): LocalTime? {
        val h = hours.toIntOrNull() ?: return null
        if (h > 24) return null
        val secs = (("0.$fraction").toDouble() * 3600).roundToInt()
        val total = h * 3600 + secs
        if (total >= 24 * 3600) return null
        return LocalTime.ofSecondOfDay(total.toLong())
    }

    private fun parseDecimal(m: MatchResult): LocalDateTime? = runCatching {
        val (d, mon, y, hh, frac) = m.destructured
        val year = y.toInt().let { if (it < 100) 2000 + it else it }
        LocalDate.of(year, month(mon) ?: return null, d.toInt()).atTime(decimalTime(hh, frac) ?: return null)
    }.getOrNull()

    /** Times on one line of text, in the chosen format. Undated decimal times get [day]. */
    private fun timesIn(line: String, decimal: Boolean, day: LocalDate): List<LocalDateTime> {
        if (!decimal) return DATETIME.findAll(line).mapNotNull { parseTime(it) }.toList()
        val dated = DECIMAL_DATED.findAll(line).mapNotNull { parseDecimal(it) }.toList()
        if (dated.isNotEmpty()) return dated
        val rest = CODE.replace(line, "").trim()
        val m = DECIMAL_ALONE.find(rest) ?: return emptyList()
        return listOfNotNull(decimalTime(m.groupValues[1], m.groupValues[2])?.let { day.atTime(it) })
    }

    fun read(text: Text, decimal: Boolean): List<Ring> {
        val day = LocalDate.now()
        val times = mutableListOf<Found>()
        val codes = mutableListOf<Found>()
        val paired = mutableListOf<Ring>()
        for (block in text.textBlocks) for (line in block.lines) {
            val t = line.text
            if (PANEL.containsMatchIn(t)) continue
            val box: Rect = line.boundingBox ?: Rect()
            val y = box.exactCenterY()
            val h = box.height().toFloat().coerceAtLeast(1f)
            val lineTimes = timesIn(t, decimal, day)
            val lineCodes = CODE.findAll(t).map { it.groupValues[1] }.toList()
            // A whole row read as one line: "013 IL 09-OCT-26 02.41.06 PM".
            if (lineTimes.size == 1 && lineCodes.size == 1) {
                paired += Ring(lineCodes[0], lineTimes[0])
                continue
            }
            lineTimes.forEach { times += Found(y, h, it) }
            lineCodes.forEach { codes += Found(y, h, it) }
        }
        // Columns read separately: match each time with the code on the same row.
        val used = HashSet<Found>()
        for (t in times.sortedBy { it.y }) {
            val best = codes.filter { it !in used }.minByOrNull { abs(it.y - t.y) }
            val code = if (best != null && abs(best.y - t.y) < t.h * 1.5f) {
                used += best
                best.value as String
            } else {
                "?"
            }
            paired += Ring(code, t.value as LocalDateTime)
        }
        return paired.distinctBy { it.time }.sortedBy { it.time }
    }
}

/** Where the day stands. */
data class Result(
    /** Time on the clock up to now (or up to the last ring when off the clock). */
    val worked: Duration,
    /** Still to work to reach the target (zero when reached). */
    val remaining: Duration,
    /** When to clock out, if on the clock now. */
    val clockOut: LocalDateTime?,
    val onClock: Boolean,
    /** The day is finished (last ring is an end-of-day code). */
    val finished: Boolean,
)

object Calc {
    fun isOff(code: String, s: Settings) = code.uppercase() in s.offCodes

    fun compute(rings: List<Ring>, s: Settings, now: LocalDateTime): Result? {
        if (rings.isEmpty()) return null
        val sorted = rings.sortedBy { it.time }
        var worked = Duration.ZERO
        for (i in 0 until sorted.lastIndex) {
            if (!isOff(sorted[i].code, s)) worked += Duration.between(sorted[i].time, sorted[i + 1].time)
        }
        val last = sorted.last()
        val target = Duration.ofSeconds(s.targetSeconds)
        val leftAtLast = target - worked
        return if (!isOff(last.code, s)) {
            val clockOut = last.time + leftAtLast
            val soFar = worked + Duration.between(last.time, now).coerceAtLeast(Duration.ZERO)
            Result(soFar, (target - soFar).coerceAtLeast(Duration.ZERO), clockOut, onClock = true, finished = false)
        } else {
            Result(worked, leftAtLast.coerceAtLeast(Duration.ZERO), null, onClock = false, finished = last.code.uppercase() == "ET")
        }
    }
}

data class Settings(
    val targetSeconds: Long = 8 * 3600L,
    /** Codes that take you off the clock (lunch out, end of day…). */
    val offCodes: Set<String> = setOf("OL", "ET"),
    val remind: Boolean = true,
    val remindBefore: Int = 5,
    /** The clock shows military time with decimal hours (14.68) instead of 2:40:48 PM. */
    val decimal: Boolean = false,
)

/** Saved rings and settings. */
object Store {
    private const val FILE = "clock_out"
    private val ringState = MutableStateFlow<List<Ring>>(emptyList())
    private val settingsState = MutableStateFlow(Settings())
    private var loaded = false
    val rings: StateFlow<List<Ring>> get() = ringState
    val settings: StateFlow<Settings> get() = settingsState

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        runCatching {
            val a = JSONArray(p.getString("rings", "[]"))
            ringState.value = List(a.length()) {
                val o = a.getJSONObject(it)
                Ring(o.getString("code"), LocalDateTime.parse(o.getString("time")))
            }
        }
        settingsState.value = Settings(
            targetSeconds = p.getLong("target", 8 * 3600L),
            offCodes = p.getString("off", "OL,ET")!!.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet(),
            remind = p.getBoolean("remind", true),
            remindBefore = p.getInt("remindBefore", 5),
            decimal = p.getBoolean("decimal", false),
        )
    }

    fun setRings(c: Context, rings: List<Ring>) {
        ringState.value = rings.sortedBy { it.time }
        val a = JSONArray(ringState.value.map { JSONObject().put("code", it.code).put("time", it.time.toString()) })
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("rings", a.toString()).apply()
        Reminder.update(c)
    }

    fun setSettings(c: Context, s: Settings) {
        settingsState.value = s
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong("target", s.targetSeconds).putString("off", s.offCodes.joinToString(","))
            .putBoolean("remind", s.remind).putInt("remindBefore", s.remindBefore)
            .putBoolean("decimal", s.decimal).apply()
        Reminder.update(c)
    }
}

object Fmt {

    private val clock = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.US)
    fun time(t: LocalDateTime): String = t.format(clock)
    fun time(t: LocalTime): String = t.format(clock)
    fun dur(d: Duration): String {
        val s = d.seconds.coerceAtLeast(0)
        return "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
    }

    /** Military time with decimal hours: 2:40:48 PM -> "14.68". */
    fun decimal(t: LocalTime): String {
        var h = t.hour
        var hundredths = ((t.minute * 60 + t.second) / 36.0).roundToInt()
        if (hundredths == 100) { h += 1; hundredths = 0 }
        return "%02d.%02d".format(h, hundredths)
    }

    /** A time the way the clock shows it, with the other style alongside in decimal mode. */
    fun show(t: LocalDateTime, s: Settings): String =
        if (s.decimal) "${decimal(t.toLocalTime())}  (${time(t)})" else time(t)

    /** A typed time in either style ("14.68", "2:41:06 PM", "14:41"). */
    fun parseAny(raw: String, s: Settings): LocalTime? {
        if (s.decimal) {
            Regex("""^\s*(\d{1,2})\s*[.,]\s*(\d{1,4})\s*$""").find(raw)?.let {
                return RingReader.decimalTime(it.groupValues[1], it.groupValues[2])
            }
        }
        return parseClock(raw)
    }

    /** "2:41:06 PM", "2.41.06 pm", "14:41:06"… Null if it can't be read. */
    fun parseClock(raw: String): LocalTime? {
        val m = Regex("""^\s*(\d{1,2})\s*[.:]\s*(\d{2})(?:\s*[.:]\s*(\d{2}))?\s*([AaPp])?\.?\s*[Mm]?\.?\s*$""").find(raw) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        val sec = m.groupValues[3].ifEmpty { "0" }.toInt()
        val ap = m.groupValues[4].uppercase()
        if (ap.isNotEmpty()) {
            if (h !in 1..12) return null
            h = h % 12 + if (ap == "P") 12 else 0
        }
        if (h > 23 || min > 59 || sec > 59) return null
        return LocalTime.of(h, min, sec)
    }

    fun epochMillis(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun today(): LocalDate = LocalDate.now()
}
