package com.ironlog.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Numbers worked out from logged sets. Weights are pounds. */
object Stats {
    /** Estimated one-rep max (Epley), from sets of up to 12 reps. */
    fun e1rm(w: Double?, r: Int?): Double {
        if (w == null || r == null || w <= 0 || r <= 0) return 0.0
        return if (r == 1) w else w * (1 + minOf(r, 12) / 30.0)
    }

    enum class Metric(val label: String, val unit: String) {
        E1RM("Est. 1-rep max", "w"),
        MAX_WEIGHT("Heaviest weight", "w"),
        VOLUME("Volume", "w"),
        BEST_REPS("Most reps", "reps"),
        TOTAL_REPS("Total reps", "reps"),
        TIME("Longest time", "s"),
        DISTANCE("Distance", "dist"),
    }

    fun metricsFor(t: ExType): List<Metric> = when (t) {
        ExType.WEIGHT -> listOf(Metric.E1RM, Metric.MAX_WEIGHT, Metric.VOLUME, Metric.BEST_REPS)
        ExType.BODYWEIGHT -> listOf(Metric.BEST_REPS, Metric.TOTAL_REPS, Metric.MAX_WEIGHT)
        ExType.TIMED -> listOf(Metric.TIME)
        ExType.CARDIO -> listOf(Metric.DISTANCE, Metric.TIME)
    }

    /** The value of [m] for one session's sets, or null if there's nothing to show. */
    fun value(m: Metric, sets: List<DoneSet>): Double? {
        val s = sets.filter { it.done }
        if (s.isEmpty()) return null
        val v = when (m) {
            Metric.E1RM -> s.maxOf { e1rm(it.weight, it.reps) }
            Metric.MAX_WEIGHT -> s.maxOf { it.weight ?: 0.0 }
            Metric.VOLUME -> s.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) }
            Metric.BEST_REPS -> s.maxOf { it.reps ?: 0 }.toDouble()
            Metric.TOTAL_REPS -> s.sumOf { it.reps ?: 0 }.toDouble()
            Metric.TIME -> s.maxOf { it.seconds ?: 0 }.toDouble()
            Metric.DISTANCE -> s.sumOf { it.distance ?: 0.0 }
        }
        return v.takeIf { it > 0 }
    }

    class Session(val workout: Workout, val sets: List<DoneSet>)

    /** Every finished workout that included [exId], oldest first. */
    fun sessions(exId: String, workouts: List<Workout>): List<Session> = workouts
        .filter { it.end != null }
        .sortedBy { it.start }
        .mapNotNull { w ->
            val sets = w.items.filter { it.exerciseId == exId }.flatMap { it.sets }.filter { it.done }
            if (sets.isEmpty()) null else Session(w, sets)
        }

    /** The sets from the last time this exercise was done. */
    fun previous(exId: String, workouts: List<Workout>, exclude: String? = null): List<DoneSet> =
        sessions(exId, workouts.filter { it.id != exclude }).lastOrNull()?.sets ?: emptyList()

    /** Things that went better than before, one line each. */
    fun improvements(w: Workout, history: List<Workout>): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val before = history.filter { it.id != w.id && it.start < w.start }
        for (item in w.items.distinctBy { it.exerciseId }) {
            val ex = Store.exercise(item.exerciseId) ?: continue
            val sets = w.items.filter { it.exerciseId == item.exerciseId }.flatMap { it.sets }.filter { it.done }
            if (sets.isEmpty()) continue
            val past = sessions(item.exerciseId, before)
            if (past.isEmpty()) {
                out += ex.name to "First time logged – this is your starting point."
                continue
            }
            val last = past.last().sets
            for (m in metricsFor(ex.type)) {
                val now = value(m, sets) ?: continue
                val best = past.mapNotNull { value(m, it.sets) }.maxOrNull()
                val prev = value(m, last)
                when {
                    best != null && now > best + 0.01 -> out += ex.name to "New best ${m.label.lowercase()}: ${Units.metric(m, now)} (was ${Units.metric(m, best)})"
                    prev != null && now > prev + 0.01 -> out += ex.name to "${m.label} up from ${Units.metric(m, prev)} to ${Units.metric(m, now)}"
                }
            }
        }
        return out
    }

    /** How hard each muscle worked in a workout: 0..1 (most-worked muscle = 1). */
    fun muscleLoad(w: Workout): Map<String, Float> {
        val load = HashMap<String, Float>()
        for (item in w.items) {
            val ex = Store.exercise(item.exerciseId) ?: continue
            val n = item.sets.count { it.done }
            if (n == 0) continue
            ex.primary.forEach { load[it] = (load[it] ?: 0f) + n }
            // Secondary muscles count by how hard they work in this exercise compared with the primary.
            ex.secondary.forEach { load[it] = (load[it] ?: 0f) + n * ex.secondaryWeight(it) }
        }
        val max = load.values.maxOrNull() ?: return emptyMap()
        return load.mapValues { it.value / max }
    }

    fun volume(w: Workout): Double = w.items.sumOf { i ->
        i.sets.filter { it.done }.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) }
    }
}

/** Showing numbers in the chosen units. Everything is stored in pounds and miles. */
object Units {
    private const val KG = 0.45359237
    private const val KM = 1.609344
    val kg get() = Store.profile.value.kg
    val w get() = if (kg) "kg" else "lbs"
    val dist get() = if (kg) "km" else "mi"

    fun toShown(lb: Double) = if (kg) lb * KG else lb
    fun fromShown(v: Double) = if (kg) v / KG else v
    fun distShown(mi: Double) = if (kg) mi * KM else mi
    fun distFromShown(v: Double) = if (kg) v / KM else v

    fun num(v: Double): String = if (v == Math.floor(v) && v < 1e7) v.toLong().toString() else "%.1f".format(v)
    fun weight(lb: Double?): String = if (lb == null) "" else num((toShown(lb) * 10).roundToInt() / 10.0)
    fun weightU(lb: Double) = "${weight(lb)} $w"
    fun distance(mi: Double?): String = if (mi == null) "" else "%.2f".format(distShown(mi)).trimEnd('0').trimEnd('.')
    fun time(sec: Int?): String {
        if (sec == null) return ""
        return if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)
    }

    /** "1:30" or "90" -> seconds. */
    fun parseTime(s: String): Int? {
        val p = s.trim().split(":").map { it.trim() }
        if (p.any { it.isEmpty() || it.toIntOrNull() == null }) return null
        return p.fold(0) { acc, x -> acc * 60 + x.toInt() }
    }

    fun metric(m: Stats.Metric, v: Double): String = when (m.unit) {
        "w" -> weightU(v)
        "reps" -> "${v.toInt()} reps"
        "s" -> time(v.toInt())
        else -> "${distance(v)} $dist"
    }

    fun set(t: ExType, s: DoneSet): String = when (t) {
        ExType.WEIGHT -> "${weight(s.weight ?: 0.0)} $w × ${s.reps ?: 0}"
        ExType.BODYWEIGHT -> (if ((s.weight ?: 0.0) > 0) "+${weight(s.weight)} $w × " else "") + "${s.reps ?: 0} reps"
        ExType.TIMED -> time(s.seconds ?: 0)
        ExType.CARDIO -> "${distance(s.distance ?: 0.0)} $dist · ${time(s.seconds ?: 0)}"
    }

    fun restLabel(sec: Int): String = when {
        sec <= 0 -> "Off"
        sec % 60 == 0 -> "${sec / 60}min 0s"
        sec > 60 -> "${sec / 60}min ${sec % 60}s"
        else -> "${sec}s"
    }

    private val dayFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())
    private val shortFmt = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    fun date(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dayFmt)
    fun shortDate(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(shortFmt)
    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) "%dh %dmin".format(s / 3600, s / 60 % 60) else "%dmin %ds".format(s / 60, s % 60)
    }
    fun clock(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }
}
