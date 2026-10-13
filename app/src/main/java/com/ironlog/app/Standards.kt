package com.ironlog.app

import kotlin.math.pow

/**
 * Rough strength standards, from published tables of what lifters typically manage, for a
 * 180 lb man aged 20-35: Beginner, Novice, Intermediate, Advanced, Elite (about the 5th, 20th,
 * 50th, 80th and 95th percentile of people who lift). Scaled for body weight, sex and age.
 * Dumbbell numbers are per dumbbell. These are estimates.
 */
object Standards {
    private class Std(val levels: DoubleArray, val upper: Boolean, val reps: Boolean = false)

    private fun s(vararg v: Double, upper: Boolean) = Std(v, upper)
    private fun r(vararg v: Double) = Std(v, upper = true, reps = true)

    private val table: Map<String, Std> = buildMap {
        fun put(ids: List<String>, std: Std) = ids.forEach { put(it, std) }
        put(listOf("Barbell_Bench_Press_-_Medium_Grip", "Bench_Press_-_Powerlifting"), s(107.0, 151.0, 205.0, 268.0, 336.0, upper = true))
        put(listOf("Barbell_Squat", "Barbell_Full_Squat"), s(136.0, 190.0, 256.0, 331.0, 413.0, upper = false))
        put(listOf("Barbell_Deadlift"), s(162.0, 224.0, 300.0, 386.0, 479.0, upper = false))
        put(listOf("Standing_Military_Press", "Barbell_Shoulder_Press", "Seated_Barbell_Military_Press"), s(70.0, 98.0, 133.0, 173.0, 217.0, upper = true))
        put(listOf("Bent_Over_Barbell_Row"), s(92.0, 130.0, 180.0, 237.0, 300.0, upper = true))
        put(listOf("Dumbbell_Bench_Press"), s(38.0, 58.0, 84.0, 115.0, 150.0, upper = true))
        put(listOf("Dumbbell_Bicep_Curl", "Dumbbell_Alternate_Bicep_Curl"), s(17.0, 29.0, 46.0, 67.0, 90.0, upper = true))
        put(listOf("Barbell_Curl"), s(49.0, 74.0, 105.0, 142.0, 182.0, upper = true))
        put(listOf("Wide-Grip_Lat_Pulldown", "Close-Grip_Front_Lat_Pulldown", "Full_Range-Of-Motion_Lat_Pulldown"), s(90.0, 126.0, 171.0, 224.0, 283.0, upper = true))
        put(listOf("Leg_Press"), s(164.0, 261.0, 391.0, 546.0, 720.0, upper = false))
        put(listOf("Barbell_Hip_Thrust"), s(109.0, 190.0, 302.0, 439.0, 594.0, upper = false))
        put(listOf("Front_Barbell_Squat"), s(107.0, 153.0, 210.0, 276.0, 349.0, upper = false))
        put(listOf("Barbell_Incline_Bench_Press_-_Medium_Grip"), s(89.0, 127.0, 176.0, 233.0, 295.0, upper = true))
        put(listOf("Dumbbell_Shoulder_Press"), s(30.0, 45.0, 65.0, 89.0, 116.0, upper = true))
        put(listOf("One-Arm_Dumbbell_Row"), s(39.0, 62.0, 92.0, 128.0, 168.0, upper = true))
        put(listOf("Leg_Extensions"), s(77.0, 121.0, 178.0, 246.0, 322.0, upper = false))
        put(listOf("Lying_Leg_Curls", "Seated_Leg_Curl"), s(62.0, 94.0, 135.0, 184.0, 238.0, upper = false))
        put(listOf("Triceps_Pushdown", "Triceps_Pushdown_-_Rope_Attachment", "Triceps_Pushdown_-_V-Bar_Attachment"), s(46.0, 72.0, 107.0, 149.0, 196.0, upper = true))
        put(listOf("Seated_Cable_Rows"), s(84.0, 124.0, 175.0, 236.0, 304.0, upper = true))
        put(listOf("Barbell_Shrug"), s(129.0, 198.0, 288.0, 394.0, 512.0, upper = true))
        put(listOf("Dumbbell_Shrug"), s(50.0, 80.0, 115.0, 155.0, 200.0, upper = true))
        put(listOf("Romanian_Deadlift"), s(117.0, 179.0, 258.0, 350.0, 452.0, upper = false))
        put(listOf("Sumo_Deadlift"), s(167.0, 234.0, 316.0, 410.0, 512.0, upper = false))
        put(listOf("Side_Lateral_Raise"), s(12.0, 22.0, 35.0, 51.0, 70.0, upper = true))
        put(listOf("Preacher_Curl"), s(47.0, 72.0, 104.0, 142.0, 184.0, upper = true))
        put(listOf("Hammer_Curls"), s(20.0, 32.0, 48.0, 68.0, 91.0, upper = true))
        put(listOf("EZ-Bar_Skullcrusher"), s(46.0, 72.0, 106.0, 147.0, 193.0, upper = true))
        put(listOf("Close-Grip_Barbell_Bench_Press"), s(103.0, 147.0, 202.0, 265.0, 334.0, upper = true))
        put(listOf("Goblet_Squat"), s(40.0, 65.0, 97.0, 137.0, 181.0, upper = false))
        put(listOf("Standing_Calf_Raises"), s(107.0, 187.0, 295.0, 428.0, 580.0, upper = false))
        put(listOf("Dumbbell_Flyes"), s(18.0, 32.0, 50.0, 73.0, 98.0, upper = true))
        put(listOf("Dumbbell_Lunges"), s(25.0, 45.0, 73.0, 108.0, 149.0, upper = false))
        put(listOf("Barbell_Lunge"), s(75.0, 125.0, 190.0, 265.0, 350.0, upper = false))
        put(listOf("Pullups", "V-Bar_Pullup"), r(0.5, 3.0, 10.0, 18.0, 26.0))
        put(listOf("Chin-Up"), r(1.0, 4.0, 11.0, 19.0, 27.0))
        put(listOf("Pushups"), r(4.0, 16.0, 32.0, 51.0, 72.0))
        put(listOf("Dips_-_Triceps_Version", "Dips_-_Chest_Version"), r(1.0, 6.0, 16.0, 28.0, 40.0))
    }

    private val PCT = doubleArrayOf(5.0, 20.0, 50.0, 80.0, 95.0)
    private val NAMES = listOf("Beginner", "Novice", "Intermediate", "Advanced", "Elite")

    fun covers(id: String) = table.containsKey(id)
    fun isReps(id: String) = table[id]?.reps == true

    class Rank(val percentile: Int, val level: String, val against: String)

    private fun ageFactor(age: Int?): Double = when {
        age == null -> 1.0
        age < 18 -> 0.88
        age <= 35 -> 1.0
        age <= 40 -> 0.97
        age <= 50 -> 0.92
        age <= 60 -> 0.84
        age <= 70 -> 0.74
        else -> 0.64
    }

    /**
     * Where [value] (an estimated 1-rep max in lb, or a rep count for bodyweight moves) puts you.
     * Null if this exercise has no standard or your body weight isn't set.
     */
    fun rank(id: String, value: Double, p: Profile): Rank? {
        val std = table[id] ?: return null
        if (value <= 0) return null
        val f = ageFactor(p.age)
        val levels = if (std.reps) {
            val sex = if (p.male) 1.0 else if (id == "Pushups") 0.55 else 0.4
            std.levels.map { it * sex * f }
        } else {
            val bw = p.bodyweight ?: return null
            val sex = if (p.male) 1.0 else if (std.upper) 0.58 else 0.82
            std.levels.map { it * (bw / 180.0).pow(0.67) * sex * f }
        }
        val pct = when {
            value <= levels[0] -> (PCT[0] * value / levels[0]).coerceAtLeast(1.0)
            value >= levels[4] -> (95 + 4 * ((value - levels[4]) / (levels[4] * 0.25)).coerceAtMost(1.0))
            else -> {
                val i = (0 until 4).first { value < levels[it + 1] }
                PCT[i] + (PCT[i + 1] - PCT[i]) * (value - levels[i]) / (levels[i + 1] - levels[i])
            }
        }
        val level = NAMES[(levels.indexOfLast { value >= it }).coerceAtLeast(0)]
        val who = (if (p.male) "men" else "women") +
            (p.bodyweight?.let { " around ${Units.weightU(it)}" } ?: "") +
            (p.age?.let { ", age ${it}" } ?: "")
        return Rank(pct.toInt().coerceIn(1, 99), level, who)
    }

    /** The best rankable number in a set of sets: est. 1RM, or best reps. */
    fun bestValue(id: String, sets: List<DoneSet>): Double {
        val done = sets.filter { it.done }
        return if (isReps(id)) (done.maxOfOrNull { it.reps ?: 0 } ?: 0).toDouble()
        else done.maxOfOrNull { Stats.e1rm(it.weight, it.reps) } ?: 0.0
    }
}
