package com.ironlog.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import com.ironlog.app.Json.objs

/** Everything the app remembers, saved as small JSON files. */
object Store {
    private lateinit var dir: File
    private var loaded = false
    private var assets: android.content.res.AssetManager? = null
    /** Pictures built into the app (assets/pictures/<name>.jpg). */
    private var bundled: Set<String> = emptySet()

    private val _exercises = MutableStateFlow<Map<String, Exercise>>(emptyMap())
    private val _routines = MutableStateFlow<List<Routine>>(emptyList())
    private val _workouts = MutableStateFlow<List<Workout>>(emptyList())
    private val _active = MutableStateFlow<Workout?>(null)
    private val _profile = MutableStateFlow(Profile())
    private val _images = MutableStateFlow(0)

    val exercises: StateFlow<Map<String, Exercise>> get() = _exercises
    val routines: StateFlow<List<Routine>> get() = _routines
    /** Finished workouts, newest first. */
    val workouts: StateFlow<List<Workout>> get() = _workouts
    val active: StateFlow<Workout?> get() = _active
    val profile: StateFlow<Profile> get() = _profile
    /** Changes whenever an exercise picture is added or removed. */
    val imageVersion: StateFlow<Int> get() = _images

    fun newId(): String = UUID.randomUUID().toString().take(12)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        dir = c.filesDir
        assets = c.assets
        bundled = runCatching { c.assets.list("pictures")?.toSet() }.getOrNull() ?: emptySet()
        val builtIn = runCatching {
            JSONArray(c.assets.open("exercises.json").bufferedReader().readText()).objs().map { Exercise.fromJson(it, false) }
        }.getOrDefault(emptyList())
        val custom = read("custom.json")?.let { JSONArray(it).objs().map { o -> Exercise.fromJson(o, true) } } ?: emptyList()
        original = builtIn.associateBy { it.id }
        overrides = read("overrides.json")?.let { JSONArray(it).objs().map { o -> Exercise.fromJson(o, false).copy(edited = true) } }
            ?.associateBy { it.id } ?: emptyMap()
        _exercises.value = (builtIn.map { overrides[it.id] ?: it } + custom).associateBy { it.id }
        _routines.value = read("routines.json")?.let { JSONArray(it).objs().map(Json::routine) } ?: emptyList()
        _workouts.value = read("workouts.json")?.let { JSONArray(it).objs().map(Json::workout) }?.sortedByDescending { it.start } ?: emptyList()
        _active.value = read("active.json")?.let { runCatching { Json.workout(JSONObject(it)) }.getOrNull() }
        _profile.value = read("profile.json")?.let { runCatching { Json.profile(JSONObject(it)) }.getOrNull() } ?: Profile()
    }

    private fun read(name: String): String? = runCatching { File(dir, name).takeIf { it.exists() }?.readText() }.getOrNull()
    private fun write(name: String, text: String) {
        runCatching {
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(text)
            tmp.renameTo(File(dir, name))
        }
    }

    fun exercise(id: String): Exercise? = _exercises.value[id]

    /** Built-in exercises as they came, and your changes to them. */
    private var original: Map<String, Exercise> = emptyMap()
    private var overrides: Map<String, Exercise> = emptyMap()

    /** Saves a changed exercise (your own, or your version of a built-in one). */
    fun saveExercise(e: Exercise) {
        if (e.custom) return saveCustomExercise(e)
        val changed = e.copy(edited = true)
        overrides = overrides + (e.id to changed)
        write("overrides.json", JSONArray(overrides.values.map { it.toJson() }).toString())
        _exercises.value = _exercises.value + (e.id to changed)
    }

    /** Puts a built-in exercise back the way it came. */
    fun resetExercise(id: String) {
        val o = original[id] ?: return
        overrides = overrides - id
        write("overrides.json", JSONArray(overrides.values.map { it.toJson() }).toString())
        _exercises.value = _exercises.value + (id to o)
    }

    // ----- exercises -----
    fun saveCustomExercise(e: Exercise) {
        _exercises.value = _exercises.value + (e.id to e.copy(custom = true))
        write("custom.json", JSONArray(_exercises.value.values.filter { it.custom }.map { it.toJson() }).toString())
    }

    fun deleteCustomExercise(id: String) {
        _exercises.value = _exercises.value - id
        write("custom.json", JSONArray(_exercises.value.values.filter { it.custom }.map { it.toJson() }).toString())
        imageFile(id).delete()
        _images.value++
    }

    // ----- pictures -----
    fun imageName(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".jpg"
    fun imagesDir() = File(dir, "images").apply { mkdirs() }
    fun imageFile(id: String) = File(imagesDir(), imageName(id))
    /** A picture built into the app for this exercise, if any. */
    fun bundledImage(id: String): java.io.InputStream? =
        if (imageName(id) in bundled) runCatching { assets?.open("pictures/" + imageName(id)) }.getOrNull() else null
    fun hasImage(id: String) = imageFile(id).exists() || imageName(id) in bundled
    fun imageChanged() { _images.value++ }

    // ----- routines -----
    fun saveRoutine(r: Routine) {
        val list = _routines.value.toMutableList()
        val i = list.indexOfFirst { it.id == r.id }
        if (i >= 0) list[i] = r else list += r
        setRoutines(list)
    }

    fun deleteRoutine(id: String) = setRoutines(_routines.value.filter { it.id != id })

    fun setRoutines(list: List<Routine>) {
        _routines.value = list
        write("routines.json", JSONArray(list.map(Json::routine)).toString())
    }

    // ----- workouts -----
    fun setActive(w: Workout?) {
        _active.value = w
        if (w == null) File(dir, "active.json").delete() else write("active.json", Json.workout(w).toString())
    }

    fun saveWorkout(w: Workout) {
        val list = (_workouts.value.filter { it.id != w.id } + w).sortedByDescending { it.start }
        setWorkouts(list)
    }

    fun deleteWorkout(id: String) = setWorkouts(_workouts.value.filter { it.id != id })

    private fun setWorkouts(list: List<Workout>) {
        _workouts.value = list
        write("workouts.json", JSONArray(list.map(Json::workout)).toString())
    }

    fun workout(id: String): Workout? = _workouts.value.firstOrNull { it.id == id }

    // ----- profile -----
    fun setProfile(p: Profile) {
        _profile.value = p
        write("profile.json", Json.profile(p).toString())
    }

    /** Exercises used most recently first. */
    fun recentExerciseIds(limit: Int = 12): List<String> =
        _workouts.value.flatMap { w -> w.items.map { it.exerciseId } }.distinct().take(limit)

    // ----- backup -----
    fun backup(): String = JSONObject()
        .put("app", "Loadout").put("version", 1)
        .put("routines", JSONArray(_routines.value.map(Json::routine)))
        .put("workouts", JSONArray(_workouts.value.map(Json::workout)))
        .put("custom", JSONArray(_exercises.value.values.filter { it.custom }.map { it.toJson() }))
        .put("profile", Json.profile(_profile.value.copy(apiKey = "")))
        .toString()

    /**
     * Full backup as a zip: backup.json (routines, workouts, your exercises, profile without the API key)
     * plus every picture you added or made (pictures/<exercise>.jpg). Built-in pictures aren't needed.
     * Returns how many pictures went in.
     */
    fun writeBackup(out: java.io.OutputStream): Int {
        // Skip copies of built-in pictures (e.g. from an imported picture pack): the app already has those.
        fun sameAsBuiltIn(f: File) = f.name in bundled && runCatching {
            assets?.open("pictures/" + f.name)?.use { it.readBytes() }?.contentEquals(f.readBytes()) == true
        }.getOrDefault(false)
        val pics = imagesDir().listFiles { f -> f.name.endsWith(".jpg") }?.filterNot(::sameAsBuiltIn)?.sortedBy { it.name } ?: emptyList()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("backup.json"))
            zip.write(backup().toByteArray())
            zip.closeEntry()
            for (f in pics) {
                zip.putNextEntry(java.util.zip.ZipEntry("pictures/" + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return pics.size
    }

    /** Restores a backup zip (or an older .json backup). Returns how many pictures were restored. */
    fun readBackup(input: java.io.InputStream): Int {
        val bytes = input.readBytes()
        if (bytes.size < 2 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) {
            restore(String(bytes)); return 0
        }
        var json: String? = null
        var pics = 0
        java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = File(e.name).name
                when {
                    e.isDirectory -> {}
                    e.name == "backup.json" -> json = zip.readBytes().toString(Charsets.UTF_8)
                    // Only plain picture names: never writes outside the pictures folder.
                    e.name.startsWith("pictures/") && name.matches(Regex("[A-Za-z0-9_-]+\\.jpg")) -> {
                        File(imagesDir(), name).outputStream().use { zip.copyTo(it) }
                        pics++
                    }
                }
                zip.closeEntry()
            }
        }
        restore(json ?: error("No backup.json in the zip"))
        imageChanged()
        return pics
    }

    fun restore(text: String) {
        val o = JSONObject(text)
        val key = _profile.value.apiKey
        o.optJSONArray("custom")?.objs()?.forEach { saveCustomExercise(Exercise.fromJson(it, true)) }
        o.optJSONArray("routines")?.let { setRoutines(it.objs().map(Json::routine)) }
        o.optJSONArray("workouts")?.let { setWorkouts(it.objs().map(Json::workout).sortedByDescending { w -> w.start }) }
        o.optJSONObject("profile")?.let { setProfile(Json.profile(it).copy(apiKey = key)) }
    }
}
