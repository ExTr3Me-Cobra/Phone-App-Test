package com.ironlog.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Imports a workout history CSV exported from Hevy (Settings → Export data). Workouts are added
 * (ones already here are skipped); exercises are matched to the library, anything without a match
 * becomes a custom exercise.
 */
object HevyImport {
    /** Hevy exercise -> Loadout exercise name. */
    private val MAP = mapOf(
        "Bench Press (Barbell)" to "Barbell Bench Press - Medium Grip", "Bicep Curl (Dumbbell)" to "Dumbbell Bicep Curl",
        "Hammer Curl (Dumbbell)" to "Hammer Curls", "Pull Up" to "Pullups", "Hip Thrust (Barbell)" to "Barbell Hip Thrust",
        "Leg Extension (Machine)" to "Leg Extensions", "Lying Leg Curl (Machine)" to "Lying Leg Curls",
        "Seated Leg Curl (Machine)" to "Seated Leg Curl", "Lateral Raise (Band)" to "Lateral Raise - With Bands",
        "Chest Fly (Band)" to "Cross Over - With Bands", "External Shoulder Rotation (Cable)" to "External Rotation with Cable",
        "Seated Clamshell" to "Clamshell", "Reverse Crunch" to "Reverse Crunch", "Triceps Rope Pushdown" to "Triceps Pushdown - Rope Attachment",
        "90-Degree External Rotation (Cable)" to "Cable External Rotation at 90°", "Triceps Pushdown" to "Triceps Pushdown",
        "Kneeling Hip Flexor Stretch" to "Kneeling Hip Flexor Stretch", "Plank" to "Plank", "Doorway Pec Stretch" to "Doorway Pec Stretch",
        "Seated Forward Fold" to "Seated Floor Hamstring Stretch", "Ab Wheel" to "Ab Wheel Rollout",
        "Seated Wrist Curl (Barbell)" to "Seated Palm-Up Barbell Wrist Curl", "Seated Wrist Extension (Barbell)" to "Seated Palms-Down Barbell Wrist Curl",
        "Triceps Extension (Dumbbell)" to "Standing Dumbbell Triceps Extension", "Chest Supported Reverse Fly (Dumbbell)" to "Reverse Flyes",
        "Reverse Curl (Dumbbell)" to "Standing Dumbbell Reverse Curl", "Lat Pulldown (Cable)" to "Medium-Grip Lat Pulldown",
        "Glute Bridge" to "Glute Bridge", "Seated Cable Row - Bar Wide Grip" to "Seated Cable Rows",
        "External Shoulder Rotation (Band)" to "External Rotation with Band", "Dead Hang" to "Dead Hang", "Full Can Raises" to "Full Can Scaption",
        "Seated Incline Hammer Curl (Dumbbell)" to "Incline Hammer Curls", "Standing Calf Raise (Dumbbell)" to "Standing Dumbbell Calf Raise",
        "Lateral Raise (Dumbbell)" to "Side Lateral Raise", "Cable Crunch" to "Cable Crunch", "Squat (Band)" to "Squats - With Bands",
        "Squat (Bodyweight)" to "Bodyweight Squat", "Chin Up" to "Chin-Up", "Low Row (Suspension)" to "Suspended Row", "Push Up" to "Pushups",
        "Push Up Plus" to "Push-Up Plus", "Preacher Curl (Dumbbell)" to "Two-Arm Dumbbell Preacher Curl", "Sit Up" to "Sit-Up",
        "Squat (Barbell)" to "Barbell Squat", "Deadlift (Barbell)" to "Barbell Deadlift", "Overhead Press (Barbell)" to "Standing Military Press",
        "Bent Over Row (Barbell)" to "Bent Over Barbell Row", "Incline Bench Press (Barbell)" to "Barbell Incline Bench Press - Medium Grip",
        "Bench Press (Dumbbell)" to "Dumbbell Bench Press", "Incline Bench Press (Dumbbell)" to "Incline Dumbbell Press",
        "Shoulder Press (Dumbbell)" to "Dumbbell Shoulder Press", "Romanian Deadlift (Barbell)" to "Romanian Deadlift",
        "Leg Press (Machine)" to "Leg Press", "Seated Cable Row - V Grip (Cable)" to "Seated Cable Rows", "Dumbbell Row" to "One-Arm Dumbbell Row",
        "Bicep Curl (Barbell)" to "Barbell Curl", "Skullcrusher (Barbell)" to "Lying Triceps Press", "Shrug (Dumbbell)" to "Dumbbell Shrug",
        "Shrug (Barbell)" to "Barbell Shrug", "Face Pull" to "Face Pull", "Lunge (Dumbbell)" to "Dumbbell Lunges", "Goblet Squat" to "Goblet Squat",
        "Standing Calf Raise (Machine)" to "Standing Calf Raises", "Seated Calf Raise" to "Seated Calf Raise", "Hanging Leg Raise" to "Hanging Leg Raise",
        "Chest Dip" to "Dips - Chest Version", "Triceps Dip" to "Dips - Triceps Version", "Front Squat" to "Front Barbell Squat",
    )

    /** Hevy exercises with no Loadout match: custom exercise (based on, equipment, logged as). */
    private val CUSTOM = mapOf(
        "Hammer Curl (Band)" to Triple("Hammer Curls", "Bands", ExType.WEIGHT),
        "Kneeling Pulldown (band)" to Triple("Medium-Grip Lat Pulldown", "Bands", ExType.WEIGHT),
        "Supine Barbell Serratus Punch" to Triple("Serratus Punch (Dumbbell, Lying)", "Barbell", ExType.WEIGHT),
        "Triceps Extension (Suspension)" to Triple("Body Tricep Press", "Other", ExType.BODYWEIGHT),
    )

    fun looksLikeHevy(text: String): Boolean {
        val head = text.trimStart('﻿').lineSequence().firstOrNull() ?: return false
        return "exercise_title" in head && "start_time" in head
    }

    // ---- Your choices for exercises without a direct match, remembered for the next import ----

    /** Hevy exercise -> Loadout exercise id, or "" for a new custom exercise. */
    fun savedChoices(): Map<String, String> = runCatching {
        val o = JSONObject(Store.readFile("hevy_map.json") ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    private fun saveChoices(m: Map<String, String>) =
        Store.writeFile("hevy_map.json", JSONObject((savedChoices() + m) as Map<*, *>).toString())

    private fun library() = Store.exercises.value.values.filter { !it.hidden }.associateBy { it.name.lowercase() }

    /** A direct match in the library, if there is one. */
    private fun directId(hevy: String, byName: Map<String, Exercise>): String? =
        MAP[hevy]?.let { byName[it.lowercase()]?.id } ?: byName[hevy.lowercase()]?.id

    /** One Hevy exercise that needs a choice. [suggested]: a Loadout exercise id, or "" for a custom one. */
    class Unmatched(val hevy: String, val sets: Int, val suggested: String)

    class Plan(val text: String, val unmatched: List<Unmatched>)

    /** The file waiting on the matching screen. */
    var pending: Plan? = null

    /** Choices on the matching screen (kept here so they survive opening the exercise picker). */
    val choices = androidx.compose.runtime.mutableStateMapOf<String, String>()

    /** Reads the file and lists the exercises with no direct match. */
    fun prepare(text: String): Plan {
        val rows = parseCsv(text.trimStart('﻿'))
        if (rows.size < 2) error("Empty file")
        val cEx = rows[0].indexOf("exercise_title").also { if (it < 0) error("Missing column exercise_title") }
        val byName = library()
        val saved = savedChoices()
        val counts = rows.drop(1).mapNotNull { it.getOrNull(cEx)?.takeIf { n -> n.isNotBlank() } }.groupingBy { it }.eachCount()
        val unmatched = counts.keys.filter { directId(it, byName) == null }.sorted().map { n ->
            val savedId = saved[n]?.takeIf { it == "" || Store.exercise(it) != null }
            Unmatched(n, counts.getValue(n), savedId ?: "")
        }
        choices.clear()
        unmatched.forEach { choices[it.hevy] = it.suggested }
        return Plan(text, unmatched)
    }

    /** Imports with your [choices] (Hevy name -> exercise id, "" = custom). Returns how many workouts were added. */
    fun import(text: String, choices: Map<String, String> = emptyMap()): Int {
        if (choices.isNotEmpty()) saveChoices(choices)
        val rows = parseCsv(text.trimStart('﻿'))
        if (rows.size < 2) error("Empty file")
        val head = rows[0]
        fun col(name: String) = head.indexOf(name).also { if (it < 0) error("Missing column $name") }
        val cTitle = col("title"); val cStart = col("start_time"); val cEnd = col("end_time"); val cEx = col("exercise_title")
        val cNotes = head.indexOf("exercise_notes"); val cType = head.indexOf("set_type")
        val cW = head.indexOf("weight_lbs").takeIf { it >= 0 }; val cKg = head.indexOf("weight_kg").takeIf { it >= 0 }
        val cR = head.indexOf("reps"); val cD = head.indexOf("distance_miles").takeIf { it >= 0 }
        val cKm = head.indexOf("distance_km").takeIf { it >= 0 }; val cT = head.indexOf("duration_seconds")

        val byName = library()
        val custom = HashMap<String, Exercise>()
        fun exerciseFor(hevy: String, row: List<String>): String {
            directId(hevy, byName)?.let { return it }
            choices[hevy]?.takeIf { it.isNotEmpty() && Store.exercise(it) != null }?.let { return it }
            val id = "custom_hevy_" + hevy.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
            Store.exercise(id)?.let { return it.id }
            custom[id]?.let { return id }
            val c = CUSTOM[hevy]
            val base = c?.first?.let { byName[it.lowercase()] }
            fun has(i: Int?) = i != null && i >= 0 && row.getOrNull(i).orEmpty().isNotBlank()
            val type = c?.third ?: when {
                has(cW) || has(cKg) -> ExType.WEIGHT
                has(cT) && !has(cR) -> ExType.TIMED
                has(cD) || has(cKm) -> ExType.CARDIO
                else -> ExType.BODYWEIGHT
            }
            custom[id] = Exercise(
                id, hevy.replace("(band)", "(Band)"), c?.second ?: "Other", type,
                base?.primary ?: emptyList(), base?.secondary ?: emptyList(), base?.category ?: "strength",
                emptyList(), custom = true, // no directions or picture until you add them secondaryWeights = base?.secondaryWeights ?: emptyList(),
                detail = base?.detail ?: emptyMap(),
            )
            return id
        }

        class W(val id: String, val name: String, val start: String, val end: String?, val items: MutableList<JSONObject> = mutableListOf())
        val workouts = LinkedHashMap<String, W>()
        for (r in rows.drop(1)) {
            if (r.size <= cEx || r[cEx].isBlank()) continue
            val start = localTime(r[cStart]) ?: continue
            val w = workouts.getOrPut(r[cTitle] + "|" + r[cStart]) {
                W("hevy_" + start.filter { it.isDigit() }, r[cTitle].ifBlank { "Workout" }, start, localTime(r.getOrNull(cEnd).orEmpty()))
            }
            val ex = exerciseFor(r[cEx], r)
            if (w.items.isEmpty() || w.items.last().getString("ex") != ex) {
                w.items += JSONObject().put("ex", ex).put("rest", 90).put("note", if (cNotes >= 0) r.getOrNull(cNotes).orEmpty() else "")
                    .put("sets", JSONArray())
            }
            fun num(i: Int?) = if (i == null || i < 0) null else r.getOrNull(i)?.trim()?.toDoubleOrNull()
            val set = JSONObject().put("f", cType >= 0 && r.getOrNull(cType) == "failure").put("x", true)
            (num(cW) ?: num(cKg)?.let { it / 0.45359237 })?.let { set.put("w", it) }
            num(cR)?.let { set.put("r", it.toInt()) }
            num(cT)?.let { set.put("t", it.toInt()) }
            (num(cD) ?: num(cKm)?.let { it / 1.609344 })?.let { set.put("d", it) }
            w.items.last().getJSONArray("sets").put(set)
        }
        val json = JSONObject().put("app", "Loadout").put("merge", true)
            .put("custom", JSONArray(custom.values.map { it.toJson() }))
            .put("workouts", JSONArray(workouts.values.map { w ->
                JSONObject().put("id", w.id).put("name", w.name).put("start", w.start).apply { w.end?.let { put("end", it) } }
                    .put("items", JSONArray(w.items))
            }))
        Store.restore(json.toString())
        return Store.lastImported ?: 0
    }

    private val FORMATS = listOf("d MMM yyyy, HH:mm", "d MMM yyyy HH:mm", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm")

    /** Hevy's "12 Oct 2026, 20:12" -> "2026-10-12T20:12" (read later in the phone's time zone). */
    private fun localTime(s: String): String? {
        val t = s.trim().ifEmpty { return null }
        for (f in FORMATS) runCatching {
            val d = java.time.LocalDateTime.parse(t, java.time.format.DateTimeFormatter.ofPattern(f, java.util.Locale.ENGLISH))
            return d.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
        }
        return null
    }

    /** CSV with quoted fields (commas, quotes and line breaks inside quotes). */
    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val f = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { f.append('"'); i++ } else quoted = false
                } else f.append(ch)
            } else when (ch) {
                '"' -> quoted = true
                ',' -> { row += f.toString(); f.clear() }
                '\r' -> {}
                '\n' -> { row += f.toString(); f.clear(); rows += row; row = mutableListOf() }
                else -> f.append(ch)
            }
            i++
        }
        if (f.isNotEmpty() || row.isNotEmpty()) { row += f.toString(); rows += row }
        return rows
    }
}
