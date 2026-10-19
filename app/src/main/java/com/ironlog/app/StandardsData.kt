package com.ironlog.app

import android.content.Context
import org.json.JSONArray

/**
 * Strength standards for people who lift: one-rep max in lb (or reps for bodyweight moves) for
 * Beginner / Novice / Intermediate / Advanced / Elite = stronger than 5 / 20 / 50 / 80 / 95% of lifters,
 * every 10 lb of body weight. Built from published research and open data by tools/standards/build_standards.js
 * (sources listed there and in How ranks work) and kept in assets/standards.json.
 */
object StandardsData {
    class Std(
        val id: String, val lift: String,
        val male: List<Pair<Double, DoubleArray>>?, val female: List<Pair<Double, DoubleArray>>?,
        val reps: Boolean, val perDumbbell: Boolean, val note: String?,
        /** Multiplies the levels (1 for every current standard). */
        val factor: Double = 1.0,
        /** Why men's levels are only an estimate (null = published research on this exercise). */
        val approx: String? = null,
        /** Men's levels are a rough estimate from AI-generated databases (purple ★): only ranked in "All on + AI" mode. */
        val ai: Boolean = false,
        /** One table for everyone of a sex: body weight and age aren't used (grip dynamometer). */
        val flat: Boolean = false,
        /** One leg of a two-leg body-weight move: ranked on the two-leg table after converting your load. */
        val oneLeg: Boolean = false,
        /** The standard's full name (equipment and style), e.g. "Barbell Wrist Curl". */
        val liftName: String = lift,
        /** [approx], [ai] and [note] for women, which can differ from men's (e.g. research on one sex only). */
        val approxF: String? = approx,
        val aiF: Boolean = ai,
        val noteF: String? = note,
    ) {
        fun approxFor(male: Boolean) = if (male) approx else approxF
        fun aiFor(male: Boolean) = if (male) ai else aiF
        fun noteFor(male: Boolean) = if (male) note else noteF
    }

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
                val approx = str("a"); val ai = o.optBoolean("ai", false); val note = str("note")
                Std(o.getString("i"), o.getString("lift"), rows("m"), rows("f"), o.getBoolean("r"), o.getBoolean("d"),
                    note, o.optDouble("k", 1.0), approx, ai, o.optBoolean("u", false), o.optBoolean("ul", false), str("ln") ?: o.getString("lift"),
                    if (o.has("af")) str("af") else approx, o.optBoolean("aif", ai), if (o.has("nf")) str("nf") else note)
            }
        }.getOrDefault(emptyList())
    }
}
