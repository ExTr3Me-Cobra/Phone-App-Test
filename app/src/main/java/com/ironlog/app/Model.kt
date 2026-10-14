package com.ironlog.app

import org.json.JSONArray
import org.json.JSONObject

/** How an exercise is logged. */
enum class ExType(val label: String) {
    WEIGHT("Weight & reps"),
    BODYWEIGHT("Bodyweight (reps only)"),
    TIMED("Timed hold"),
    CARDIO("Cardio (distance & time)"),
}

data class Exercise(
    val id: String,
    val name: String,
    val equipment: String,
    val type: ExType,
    val primary: List<String>,
    val secondary: List<String>,
    val category: String = "strength",
    val instructions: List<String> = emptyList(),
    val custom: Boolean = false,
    /** Kept for old workouts but not offered in the list. */
    val hidden: Boolean = false,
    /** How hard each secondary muscle works compared with the primary (0..1). */
    val secondaryWeights: List<Float> = emptyList(),
    /** A built-in exercise you've changed. */
    val edited: Boolean = false,
    /** Individual muscles (e.g. "biceps (long head)") -> how hard each works, main = 1. */
    val detail: Map<String, Float> = emptyMap(),
    /** Studies the percentages are based on (keys of [Muscles.refs]). */
    val refs: List<String> = emptyList(),
) {
    /** Individual muscles worked, 0..1. Older saved exercises only have groups, so those are split the usual way. */
    val muscles: Map<String, Float> by lazy {
        Muscles.migrate(detail).ifEmpty { Muscles.expand(secondary.associateWith { secondaryWeight(it) } + primary.associateWith { 1f }) }
    }

    /** Secondary muscle -> weight (0.3 when not known). */
    fun secondaryWeight(m: String): Float {
        val i = secondary.indexOf(m)
        return secondaryWeights.getOrNull(i) ?: 0.3f
    }

    val mainMuscle: String get() = primary.firstOrNull()?.let(::titleCase) ?: "Other"

    fun toJson(): JSONObject = JSONObject().put("i", id).put("n", name).put("e", equipment).put("t", type.name)
        .put("p", JSONArray(primary)).put("s", JSONArray(secondary)).put("c", category).put("h", JSONArray(instructions))
        .put("w", JSONArray(secondary.map { secondaryWeight(it).toDouble() })).put("x", if (hidden) 1 else 0)
        .put("m", JSONObject().apply { detail.forEach { (k, v) -> put(k, v.toDouble()) } }).put("r", JSONArray(refs))

    companion object {
        fun fromJson(o: JSONObject, custom: Boolean) = Exercise(
            id = o.getString("i"), name = o.getString("n"), equipment = o.optString("e", "Other"),
            type = runCatching { ExType.valueOf(o.getString("t")) }.getOrDefault(ExType.WEIGHT),
            primary = o.optJSONArray("p").strings(), secondary = o.optJSONArray("s").strings(),
            category = o.optString("c", "strength"), instructions = o.optJSONArray("h").strings(), custom = custom,
            hidden = o.optInt("x", 0) == 1,
            secondaryWeights = o.optJSONArray("w")?.let { a -> List(a.length()) { a.getDouble(it).toFloat() } } ?: emptyList(),
            detail = o.optJSONObject("m")?.let { m -> m.keys().asSequence().associateWith { m.getDouble(it).toFloat() } } ?: emptyMap(),
            refs = o.optJSONArray("r").strings(),
        )
    }
}

fun titleCase(s: String) = s.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { getString(it) }

/** A planned set in a routine. Weight is in pounds. */
data class PlanSet(
    val reps: Int? = null,
    val weight: Double? = null,
    val seconds: Int? = null,
    val distance: Double? = null,
    val failure: Boolean = false,
)

data class RoutineItem(val exerciseId: String, val sets: List<PlanSet>, val restSec: Int = 90, val note: String = "")

data class Routine(val id: String, val name: String, val items: List<RoutineItem>)

/** A set in a workout. Weight in pounds (added weight for bodyweight exercises), distance in miles. */
data class DoneSet(
    val weight: Double? = null,
    val reps: Int? = null,
    val seconds: Int? = null,
    val distance: Double? = null,
    val failure: Boolean = false,
    val done: Boolean = false,
)

data class WorkoutItem(val exerciseId: String, val sets: List<DoneSet>, val restSec: Int = 90, val note: String = "")

data class Workout(
    val id: String,
    val routineId: String?,
    val name: String,
    /** When the timer started (0 = not started yet). */
    val start: Long,
    val end: Long? = null,
    val items: List<WorkoutItem>,
    /** Body weight at the time (for rankings). */
    val bodyweight: Double? = null,
) {
    val doneSets get() = items.sumOf { i -> i.sets.count { it.done } }
}

data class Profile(
    val bodyweight: Double? = null,
    val heightIn: Int? = null,
    val age: Int? = null,
    val male: Boolean = true,
    val kg: Boolean = false,
    val apiKey: String = "",
    val imageModel: String = "gpt-image-1",
    val imageQuality: String = "medium",
    val defaultRest: Int = 90,
    /** Rest-over sound: "" = phone's default alarm sound, "none" = silent, else a sound's address. */
    val restSound: String = "",
    val restVibrate: Boolean = true,
)

// ---------- JSON ----------

private fun JSONObject.optDoubleOrNull(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
private fun JSONObject.optIntOrNull(k: String): Int? = if (has(k) && !isNull(k)) getInt(k) else null
private fun JSONObject.putOpt2(k: String, v: Any?): JSONObject = if (v == null) this else put(k, v)

object Json {
    fun planSet(s: PlanSet) = JSONObject().putOpt2("r", s.reps).putOpt2("w", s.weight).putOpt2("t", s.seconds)
        .putOpt2("d", s.distance).put("f", s.failure)
    fun planSet(o: JSONObject) = PlanSet(o.optIntOrNull("r"), o.optDoubleOrNull("w"), o.optIntOrNull("t"), o.optDoubleOrNull("d"), o.optBoolean("f"))

    fun routine(r: Routine) = JSONObject().put("id", r.id).put("name", r.name).put("items", JSONArray(r.items.map { i ->
        JSONObject().put("ex", i.exerciseId).put("rest", i.restSec).put("note", i.note).put("sets", JSONArray(i.sets.map(::planSet)))
    }))
    fun routine(o: JSONObject) = Routine(o.getString("id"), o.getString("name"), o.getJSONArray("items").objs().map { i ->
        RoutineItem(i.getString("ex"), i.getJSONArray("sets").objs().map(::planSet), i.optInt("rest", 90), i.optString("note"))
    })

    fun doneSet(s: DoneSet) = JSONObject().putOpt2("w", s.weight).putOpt2("r", s.reps).putOpt2("t", s.seconds)
        .putOpt2("d", s.distance).put("f", s.failure).put("x", s.done)
    fun doneSet(o: JSONObject) = DoneSet(o.optDoubleOrNull("w"), o.optIntOrNull("r"), o.optIntOrNull("t"), o.optDoubleOrNull("d"), o.optBoolean("f"), o.optBoolean("x"))

    fun workout(w: Workout) = JSONObject().put("id", w.id).putOpt2("routine", w.routineId).put("name", w.name)
        .put("start", w.start).putOpt2("end", w.end).putOpt2("bw", w.bodyweight)
        .put("items", JSONArray(w.items.map { i ->
            JSONObject().put("ex", i.exerciseId).put("rest", i.restSec).put("note", i.note).put("sets", JSONArray(i.sets.map(::doneSet)))
        }))
    fun workout(o: JSONObject) = Workout(
        o.getString("id"), if (o.has("routine")) o.getString("routine") else null, o.getString("name"),
        o.getLong("start"), if (o.has("end")) o.getLong("end") else null,
        o.getJSONArray("items").objs().map { i ->
            WorkoutItem(i.getString("ex"), i.getJSONArray("sets").objs().map(::doneSet), i.optInt("rest", 90), i.optString("note"))
        },
        o.optDoubleOrNull("bw"),
    )

    fun profile(p: Profile) = JSONObject().putOpt2("bw", p.bodyweight).putOpt2("h", p.heightIn).putOpt2("age", p.age)
        .put("male", p.male).put("kg", p.kg).put("key", p.apiKey).put("model", p.imageModel)
        .put("quality", p.imageQuality).put("rest", p.defaultRest)
        .put("restSound", p.restSound).put("restVibrate", p.restVibrate)
    fun profile(o: JSONObject) = Profile(
        o.optDoubleOrNull("bw"), o.optIntOrNull("h"), o.optIntOrNull("age"), o.optBoolean("male", true),
        o.optBoolean("kg"), o.optString("key"), o.optString("model", "gpt-image-1").ifBlank { "gpt-image-1" },
        o.optString("quality", "medium"), o.optInt("rest", 90),
        o.optString("restSound", ""), o.optBoolean("restVibrate", true),
    )

    fun JSONArray.objs(): List<JSONObject> = List(length()) { getJSONObject(it) }
}
