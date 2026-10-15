package com.ironlog.app

import android.content.Context
import org.json.JSONArray

/**
 * Strength standards from Strength Level (strengthlevel.com, built from lifts logged by its users),
 * one-rep max in lb (or reps for bodyweight moves) for Beginner / Novice / Intermediate / Advanced / Elite
 * = stronger than 5 / 20 / 50 / 80 / 95% of lifters, at each published body weight.
 * Kept in assets/standards.json, made by tools/gen_standards.py (too big to compile into code).
 */
object StandardsData {
    class Std(
        val id: String, val lift: String,
        val male: List<Pair<Double, DoubleArray>>?, val female: List<Pair<Double, DoubleArray>>?,
        val reps: Boolean, val perDumbbell: Boolean, val note: String?,
        /** Multiplies the levels: for an exercise ranked from a similar lift's standard. */
        val factor: Double = 1.0,
        /** Why this is only an estimate (null = data that fits closely). */
        val approx: String? = null,
        /** An AI estimate (a ratio to another lift's table): only ranked in "All on + AI" mode. */
        val ai: Boolean = false,
    )

    @Volatile var all: List<Std> = emptyList()
        private set

    /** Reads the tables once; call before anything uses [Standards]. */
    fun init(c: Context) {
        if (all.isNotEmpty()) return
        all = runCatching {
            val a = JSONArray(c.assets.open("standards.json").bufferedReader().readText())
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                fun rows(k: String) = o.optJSONArray(k)?.let { r ->
                    List(r.length()) { j ->
                        val row = r.getJSONArray(j)
                        row.getDouble(0) to DoubleArray(5) { row.getDouble(it + 1) }
                    }
                }
                fun str(k: String) = if (!o.has(k) || o.isNull(k)) null else o.getString(k)
                Std(o.getString("i"), o.getString("lift"), rows("m"), rows("f"), o.getBoolean("r"), o.getBoolean("d"),
                    str("note"), o.optDouble("k", 1.0), str("a"), o.optBoolean("ai", false))
            }
        }.getOrDefault(emptyList())
    }
}
