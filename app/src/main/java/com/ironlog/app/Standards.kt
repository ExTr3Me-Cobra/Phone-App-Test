package com.ironlog.app

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln

/**
 * Where a lift puts you among people who lift, from Strength Level's standards (see [StandardsData]):
 * adjusted for your body weight (between and beyond the published rows), sex and age.
 * Height isn't used: no reliable published standards include it, and body weight already
 * accounts for most of the difference in size.
 */
object Standards {
    private val table: Map<String, StandardsData.Std> by lazy { StandardsData.all.associateBy { it.id } }

    fun covers(id: String) = table.containsKey(id)
    fun isReps(id: String) = table[id]?.reps == true
    fun info(id: String) = table[id]

    /** How well the published data matches you for one exercise. */
    class Fit(val rows: List<Pair<Double, DoubleArray>>, val estimatedFromMen: Boolean, val problems: List<String>, val compared: String) {
        val exact get() = problems.isEmpty()
    }

    /** Women's levels as a share of men's (at 140 vs 180 lb), averaged over push-ups and dips: used when one sex has no data. */
    private val WOMEN_REPS = doubleArrayOf(0.3, 0.23, 0.46, 0.55, 0.62)

    /** The rows to use for you, and anything that makes your percentile less exact. Null without a standard or body weight. */
    fun fit(id: String, p: Profile): Fit? {
        val s = table[id] ?: return null
        val bw = p.bodyweight ?: return null
        val own = if (p.male) s.male else s.female
        // Your own sex's table first; the other sex's (scaled) only when there's none at all for yours.
        val rows = own ?: if (p.male) {
            s.female?.map { (w, lv) -> w * 180 / 140 to DoubleArray(5) { lv[it] / WOMEN_REPS[it] } }
        } else {
            s.male?.map { (w, lv) -> w * 140 / 180 to DoubleArray(5) { lv[it] * WOMEN_REPS[it] } }
        } ?: return null
        val problems = mutableListOf<String>()
        val lo = rows.first().first; val hi = rows.last().first
        if (bw < lo || bw > hi) problems += "Your body weight (${Units.weightU(bw)}) is outside the published rows " +
            "(${Units.weightU(lo)}–${Units.weightU(hi)}), so the standard is stretched beyond the data."
        else {
            // Inside the range: the two rows either side must be close enough to blend reliably.
            val below = rows.last { it.first <= bw }.first; val above = rows.first { it.first >= bw }.first
            if (above - below > 50) problems += "The nearest published rows (${Units.weightU(below)} and ${Units.weightU(above)}) " +
                "are far apart, so your exact body weight is blended across a wide gap."
        }
        when {
            p.age == null -> problems += "Your age isn't set, so no age adjustment is made."
            p.age < 25 || p.age > 40 -> problems += "The adjustment for age ${p.age} is estimated: Strength Level's age data is from the bench press and is applied to every lift."
        }
        s.approx?.let { problems += it }
        if (own == null) problems += if (p.male) "No published data for men on this exercise: estimated from women's data, scaled by the men-to-women ratio on push-ups and dips."
            else "No published data for women on this exercise: estimated from men's data, scaled by the women-to-men ratio on push-ups and dips."
        val sex = if (p.male) "men" else "women"
        val near = rows.map { it.first }.let { ws ->
            val below = ws.lastOrNull { it <= bw }; val above = ws.firstOrNull { it >= bw }
            when {
                below == null || above == null -> "nearest row ${Units.weightU(if (below == null) ws.first() else ws.last())}, extended"
                below == above -> "the ${Units.weightU(below)} row"
                else -> "blended from the ${Units.weightU(below)} and ${Units.weightU(above)} rows"
            }
        }
        val age = when {
            p.age == null -> "age not set (no adjustment)"
            p.age in 25..40 -> "age ${p.age} (no adjustment needed)"
            else -> "age ${p.age} (standards × ${"%.2f".format(ageFactor(p.age))})"
        }
        val compared = "Compared with $sex who lift, at your body weight of ${Units.weightU(bw)} ($near), $age. " +
            "Source: Strength Level \"${s.lift}\"" + (if (own == null) (if (p.male) ", women's table scaled for men." else ", men's table scaled for women.") else ".") + (s.note?.let { " $it" } ?: "")
        return Fit(rows, own == null, problems, compared)
    }

    enum class Mode { OFF, ACCURATE, ALL }
    fun mode(p: Profile) = when (p.rankMode) { "off" -> Mode.OFF; "all" -> Mode.ALL; else -> Mode.ACCURATE }

    /** Does this exercise get a rank for you in your current mode? */
    fun covers(id: String, p: Profile): Boolean {
        val m = mode(p)
        if (m == Mode.OFF) return false
        val f = fit(id, p) ?: return false
        return m == Mode.ALL || f.exact
    }

    enum class Star { NONE, EXACT, ESTIMATE }

    /**
     * The ★ after an exercise's name: gold when it's ranked from data that fits you closely,
     * silver-blue when it's only ranked as an estimate (All on mode), none when it isn't ranked.
     * Before your body weight is set, every rankable exercise shows gold.
     */
    fun star(id: String, p: Profile): Star {
        val m = mode(p)
        if (m == Mode.OFF || !covers(id)) return Star.NONE
        if (p.bodyweight == null) return Star.EXACT
        val f = fit(id, p) ?: return Star.NONE
        return when {
            f.exact -> Star.EXACT
            m == Mode.ALL -> Star.ESTIMATE
            else -> Star.NONE
        }
    }

    private val PCT = doubleArrayOf(5.0, 20.0, 50.0, 80.0, 95.0)

    /**
     * Age adjustment: Strength Level's by-age tables (bench press, intermediate), as a share of
     * the 25–40 peak. Applied to every lift.
     */
    private val AGE = listOf(15 to 0.85, 20 to 0.98, 25 to 1.0, 40 to 1.0, 45 to 0.95, 50 to 0.89, 55 to 0.82,
        60 to 0.75, 65 to 0.67, 70 to 0.61, 75 to 0.55, 80 to 0.49, 85 to 0.44, 90 to 0.39)

    fun ageFactor(age: Int?): Double {
        if (age == null) return 1.0
        if (age <= AGE.first().first) return AGE.first().second
        if (age >= AGE.last().first) return AGE.last().second
        val i = AGE.indexOfLast { it.first <= age }
        val (a0, f0) = AGE[i]; val (a1, f1) = AGE[i + 1]
        return f0 + (f1 - f0) * (age - a0) / (a1 - a0).toDouble()
    }

    /** The five level values for a body weight: log-log interpolation between rows, extended past the ends. */
    private fun levelsAt(rows: List<Pair<Double, DoubleArray>>, bw: Double): DoubleArray = DoubleArray(5) { k ->
        fun v(i: Int) = maxOf(rows[i].second[k], 0.5) // "< 1 rep" cells count as half a rep
        val i = when {
            bw <= rows.first().first -> 0
            bw >= rows.last().first -> rows.size - 2
            else -> rows.indexOfLast { it.first <= bw }.coerceAtMost(rows.size - 2)
        }
        val x0 = ln(rows[i].first); val x1 = ln(rows[i + 1].first)
        val slope = ((ln(v(i + 1)) - ln(v(i))) / (x1 - x0)).coerceIn(-2.0, 1.5)
        exp(ln(v(i)) + slope * (ln(bw) - x0))
    }

    /**
     * Percentile among lifters (0–99.9) for [value] (est. 1-rep max in lb, or reps).
     * Null if there's no standard for you or your body weight isn't set.
     */
    fun percentile(id: String, value: Double, p: Profile): Double? {
        if (value <= 0) return null
        val rows = fit(id, p)?.rows ?: return null
        val bw = p.bodyweight ?: return null
        val f = ageFactor(p.age)
        val lv = levelsAt(rows, bw).map { it * f * (table[id]?.factor ?: 1.0) }
        return when {
            value <= lv[0] -> PCT[0] * value / lv[0]
            value >= lv[4] -> 95 + 4.9 * (1 - exp(-(value - lv[4]) / (lv[4] - lv[3]).coerceAtLeast(1.0)))
            else -> {
                val i = (0 until 4).first { value < lv[it + 1] }
                PCT[i] + (PCT[i + 1] - PCT[i]) * (value - lv[i]) / (lv[i + 1] - lv[i])
            }
        }.coerceIn(0.0, 99.9)
    }

    /** The rankable number in a set of sets: best est. 1-rep max, or most reps. */
    fun bestValue(id: String, sets: List<DoneSet>): Double {
        val done = sets.filter { it.done }
        return if (isReps(id)) (done.maxOfOrNull { it.reps ?: 0 } ?: 0).toDouble()
        else done.maxOfOrNull { Stats.e1rm(it.weight, it.reps) } ?: 0.0
    }

    fun who(p: Profile) = (if (p.male) "men" else "women") +
        (p.bodyweight?.let { " around ${Units.weightU(it)}" } ?: "") + (p.age?.let { ", age $it" } ?: "")
}

/** The 20 ranks: Wood 1 (everyone) … Diamond 3 (top 15%), Olympian (top 10%), Legend (top 5%). */
enum class Tier(val label: String, val badge: String, val color: Long) {
    WOOD_1("Wood 1", "wood_1", 0xFFD9B48A), WOOD_2("Wood 2", "wood_2", 0xFFC1925F), WOOD_3("Wood 3", "wood_3", 0xFFA06A3A),
    BRONZE_1("Bronze 1", "bronze_1", 0xFFF3A774), BRONZE_2("Bronze 2", "bronze_2", 0xFFE0803F), BRONZE_3("Bronze 3", "bronze_3", 0xFFC0601F),
    SILVER_1("Silver 1", "silver_1", 0xFFDCE3EC), SILVER_2("Silver 2", "silver_2", 0xFFB6C2D1), SILVER_3("Silver 3", "silver_3", 0xFF8E9DB2),
    GOLD_1("Gold 1", "gold_1", 0xFFFFE27D), GOLD_2("Gold 2", "gold_2", 0xFFF7C743), GOLD_3("Gold 3", "gold_3", 0xFFDDA21A),
    PLATINUM_1("Platinum 1", "platinum_1", 0xFF97F2EA), PLATINUM_2("Platinum 2", "platinum_2", 0xFF52D8D0), PLATINUM_3("Platinum 3", "platinum_3", 0xFF1FB4AE),
    DIAMOND_1("Diamond 1", "diamond_1", 0xFF94C8FF), DIAMOND_2("Diamond 2", "diamond_2", 0xFF529DFF), DIAMOND_3("Diamond 3", "diamond_3", 0xFF2371E0),
    OLYMPIAN("Olympian", "olympian", 0xFFFF6FB5), LEGEND("Legend", "legend", 0xFFA64DFF);

    /** "Top 45%": the share of lifters at this rank or above. */
    val topPct get() = 100 - ordinal * 5

    companion object {
        fun of(percentile: Double): Tier = entries[floor(percentile / 5).toInt().coerceIn(0, entries.size - 1)]
    }
}

/** "top 47%" for a percentile (stronger than 53% of lifters). */
fun topLabel(percentile: Double) = "top ${(100 - percentile).toInt().coerceIn(1, 100)}%"

/** Your ranks from your logged workouts. */
object Ranks {
    class Ranked(val exercise: Exercise, val value: Double, val percentile: Double) {
        val tier get() = Tier.of(percentile)
    }

    /** Each ranked exercise from the last finished session you did it in (skipping it changes nothing). */
    fun all(workouts: List<Workout>, p: Profile): Map<String, Ranked> {
        val out = HashMap<String, Ranked>()
        for (w in workouts.filter { it.end != null }.sortedByDescending { it.start }) {
            for (id in w.items.map { it.exerciseId }.distinct()) {
                if (id in out || !Standards.covers(id, p)) continue
                val ex = Store.exercise(id) ?: continue
                val v = Standards.bestValue(id, w.items.filter { it.exerciseId == id }.flatMap { it.sets })
                if (v <= 0) continue
                val pct = Standards.percentile(id, v, p) ?: continue
                out[id] = Ranked(ex, v, pct)
            }
        }
        return out
    }

    /** Overall: the average percentile of your ranked exercises. */
    fun overall(ranks: Collection<Double>): Double? = if (ranks.isEmpty()) null else ranks.average()

    /** The main muscles an exercise ranks: its primary group's muscles that work at 75% or more. */
    fun rankedMuscles(e: Exercise): List<String> =
        e.muscles.filter { (m, v) -> v >= 0.75f && Muscles.group(m) in e.primary }.keys.toList()

    /** Each main muscle -> your best ranked exercise for it. */
    fun muscles(ranks: Map<String, Ranked>): Map<String, Ranked> {
        val out = HashMap<String, Ranked>()
        for (r in ranks.values) for (m in rankedMuscles(r.exercise)) {
            if ((out[m]?.percentile ?: -1.0) < r.percentile) out[m] = r
        }
        return out
    }

    /** Each muscle group -> your best ranked exercise where it's the main muscle. */
    fun groups(ranks: Map<String, Ranked>): Map<String, Ranked> {
        val out = HashMap<String, Ranked>()
        for (r in ranks.values) for (g in r.exercise.primary) {
            if ((out[g]?.percentile ?: -1.0) < r.percentile) out[g] = r
        }
        return out
    }

    // ---- Rank-up celebrations during a workout ----

    class Celebration(val title: String, val subtitle: String, val tier: Tier, val percentile: Double)

    val celebrations = androidx.compose.runtime.mutableStateListOf<Celebration>()
    private var forWorkout: String? = null
    private val shownTier = HashMap<String, Int>()
    private var shownOverall = -1

    /** Call after a set is ticked off: queues a celebration if it raised a rank. */
    fun onSetDone(w: Workout, exerciseId: String) {
        if (forWorkout != w.id) { forWorkout = w.id; shownTier.clear(); shownOverall = -1 }
        val p = Store.profile.value
        if (!Standards.covers(exerciseId, p)) return
        val ex = Store.exercise(exerciseId) ?: return
        val before = all(Store.workouts.value.filter { it.id != w.id }, p)
        val sets = w.items.filter { it.exerciseId == exerciseId }.flatMap { it.sets }
        val pct = Standards.percentile(exerciseId, Standards.bestValue(exerciseId, sets), p) ?: return
        val tier = Tier.of(pct).ordinal
        val was = maxOf(before[exerciseId]?.tier?.ordinal ?: -1, shownTier[exerciseId] ?: -1)
        if (tier > was) {
            shownTier[exerciseId] = tier
            celebrations += Celebration(ex.name, if (was < 0) "Ranked!" else "Rank up!", Tier.of(pct), pct)
        }
        // Overall: this session's numbers only count when they're better than before (drops apply when you finish).
        val now = HashMap(before.mapValues { it.value.percentile })
        for (id in w.items.map { it.exerciseId }.distinct()) {
            if (!Standards.covers(id, p)) continue
            val s = w.items.filter { it.exerciseId == id }.flatMap { it.sets }
            val v = Standards.percentile(id, Standards.bestValue(id, s), p) ?: continue
            now[id] = maxOf(now[id] ?: 0.0, v)
        }
        val o = overall(now.values) ?: return
        val oldO = overall(before.values.map { it.percentile })
        val oTier = Tier.of(o).ordinal
        if (oldO == null) { shownOverall = maxOf(shownOverall, oTier); return }
        val oWas = maxOf(Tier.of(oldO).ordinal, shownOverall)
        if (oTier > oWas) {
            shownOverall = oTier
            celebrations += Celebration("Overall rank", "Your overall rank went up!", Tier.of(o), o)
        }
    }
}
