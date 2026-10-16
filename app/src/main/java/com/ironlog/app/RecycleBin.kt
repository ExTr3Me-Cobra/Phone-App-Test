package com.ironlog.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Everything you delete waits here for 30 days before it's gone for good: routines, workouts (finished or discarded),
 * your own exercises, your edits to built-in exercises, exercise pictures and 1 Rep Max list entries.
 */
object RecycleBin {
    const val DAYS = 30
    private const val FILE = "recycle_bin.json"
    private const val DAY = 24L * 3600 * 1000

    /** One deleted thing. [data] is what's needed to put it back; [picture] is a saved picture file in the bin folder. */
    class Item(val id: String, val kind: String, val name: String, val deletedAt: Long, val data: JSONObject, val picture: String?) {
        val daysLeft get() = (DAYS - ((System.currentTimeMillis() - deletedAt) / DAY).toInt()).coerceAtLeast(0)
        fun json(): JSONObject = JSONObject().put("id", id).put("kind", kind).put("name", name).put("at", deletedAt)
            .put("data", data).apply { if (picture != null) put("pic", picture) }
    }

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> get() = _items
    private var loaded = false

    private fun dir() = File(Store.imagesDir().parentFile, "recycle_bin").apply { mkdirs() }

    private fun load() {
        if (loaded) return
        loaded = true
        _items.value = runCatching {
            val a = JSONArray(Store.readFile(FILE) ?: "[]")
            List(a.length()) { a.getJSONObject(it) }.map {
                Item(it.getString("id"), it.getString("kind"), it.getString("name"), it.getLong("at"), it.getJSONObject("data"),
                    if (it.has("pic")) it.getString("pic") else null)
            }
        }.getOrDefault(emptyList())
        purge()
    }

    private fun save(list: List<Item>) {
        _items.value = list.sortedByDescending { it.deletedAt }
        Store.writeFile(FILE, JSONArray(_items.value.map { it.json() }).toString())
    }

    /** Throws away anything that's been here 30 days or more. Runs when the app starts and when you open the bin. */
    fun purge() {
        load()
        val cutoff = System.currentTimeMillis() - DAYS * DAY
        val (old, keep) = _items.value.partition { it.deletedAt < cutoff }
        if (old.isEmpty()) return
        old.forEach { it.picture?.let { p -> File(dir(), p).delete() } }
        save(keep)
    }

    fun init() = load()

    /** Puts something in the bin; [pictureOf] moves that exercise's picture file in with it. */
    private fun add(kind: String, name: String, data: JSONObject, pictureOf: String? = null) {
        load()
        val id = Store.newId()
        val pic = pictureOf?.let { ex ->
            val f = Store.imageFile(ex)
            if (f.exists()) "$id.jpg".also { n -> if (!f.renameTo(File(dir(), n))) { f.copyTo(File(dir(), n), true); f.delete() } } else null
        }
        save(_items.value + Item(id, kind, name, System.currentTimeMillis(), data, pic))
    }

    fun routine(r: Routine) = add("routine", r.name, JSONObject().put("routine", Json.routine(r)))
    fun workout(w: Workout) = add("workout", w.name.ifBlank { "Workout" }, JSONObject().put("workout", Json.workout(w)))
    fun discarded(w: Workout) = add("discarded", w.name.ifBlank { "Workout" }, JSONObject().put("workout", Json.workout(w)))
    fun exercise(e: Exercise) = add("exercise", e.name, JSONObject().put("exercise", e.toJson()).put("own", Pictures.isOwn(e.id)), e.id)
    fun edits(e: Exercise) = add("edits", e.name, JSONObject().put("exercise", e.toJson()))
    fun picture(e: Exercise) = add("picture", e.name, JSONObject().put("exerciseId", e.id).put("own", Pictures.isOwn(e.id)), e.id)
    fun oneRm(e: Exercise) = add("onerm", e.name, JSONObject().put("exerciseId", e.id))

    /** What kind of thing it was, for the list. */
    fun label(kind: String) = when (kind) {
        "routine" -> "Routine"
        "workout" -> "Workout"
        "discarded" -> "Discarded workout in progress"
        "exercise" -> "Your exercise"
        "edits" -> "Your changes to an exercise"
        "picture" -> "Exercise picture"
        "onerm" -> "1 Rep Max list entry"
        else -> kind
    }

    fun icon(kind: String) = when (kind) {
        "routine" -> "📋"
        "workout", "discarded" -> "🏋"
        "exercise" -> "💪"
        "edits" -> "✏️"
        "picture" -> "🖼"
        "onerm" -> "🏆"
        else -> "🗑"
    }

    /** Puts it back. Returns a message if it couldn't. */
    fun restore(item: Item): String? {
        val d = item.data
        val msg: String? = runCatching {
            when (item.kind) {
                "routine" -> { Store.saveRoutine(Json.routine(d.getJSONObject("routine"))); null }
                "workout" -> { Store.saveWorkout(Json.workout(d.getJSONObject("workout"))); null }
                "discarded" -> {
                    // Comes back as a finished workout with the sets you'd ticked.
                    val w = Json.workout(d.getJSONObject("workout"))
                    val kept = w.copy(end = w.end ?: item.deletedAt,
                        items = w.items.map { it.copy(sets = it.sets.filter { s -> s.done }) }.filter { it.sets.isNotEmpty() })
                    if (kept.items.isEmpty()) "That workout had no completed sets, so there's nothing to save."
                    else { Store.saveWorkout(kept); null }
                }
                "exercise" -> {
                    val e = Exercise.fromJson(d.getJSONObject("exercise"), true)
                    Store.saveCustomExercise(e)
                    putPictureBack(item, e.id, d.optBoolean("own"))
                    null
                }
                "edits" -> {
                    val e = Exercise.fromJson(d.getJSONObject("exercise"), false)
                    if (Store.exercise(e.id) == null) "The exercise these changes belong to no longer exists."
                    else { Store.saveExercise(e); null }
                }
                "picture" -> {
                    val id = d.getString("exerciseId")
                    val e = Store.exercise(id) ?: return "The exercise this picture belongs to no longer exists."
                    // A picture it has now goes to the bin in its place.
                    if (Store.imageFile(id).exists()) picture(e)
                    putPictureBack(item, id, d.optBoolean("own"))
                    null
                }
                "onerm" -> { OneRm.add(d.getString("exerciseId")); null }
                else -> "Unknown item."
            }
        }.getOrElse { "Couldn't restore it." }
        if (msg == null) save(_items.value.filter { it.id != item.id })
        return msg
    }

    private fun putPictureBack(item: Item, exerciseId: String, own: Boolean) {
        val p = item.picture ?: return
        val f = File(dir(), p)
        if (!f.exists()) return
        val to = Store.imageFile(exerciseId)
        if (!f.renameTo(to)) { f.copyTo(to, true); f.delete() }
        Pictures.markOwn(exerciseId, own)
        Store.imageChanged()
    }

    /** Gone for good, straight away. */
    fun deleteForever(item: Item) {
        item.picture?.let { File(dir(), it).delete() }
        save(_items.value.filter { it.id != item.id })
    }

    fun empty() = _items.value.forEach { deleteForever(it) }
}

@Composable
fun RecycleBinScreen() {
    LaunchedEffect(Unit) { RecycleBin.purge() }
    val items by RecycleBin.items.collectAsState()
    var confirmEmpty by remember { mutableStateOf(false) }
    var forever by remember { mutableStateOf<RecycleBin.Item?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Recycle Bin", action = if (items.isNotEmpty()) "Empty" else null) { confirmEmpty = true }
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Anything you delete waits here for ${RecycleBin.DAYS} days, then it's deleted for good. Tap Restore to put it back.",
                    color = Sub, fontSize = 14.sp)
                message?.let { Text(it, color = Red, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
            if (items.isEmpty()) item { Text("The Recycle Bin is empty.", color = Sub, modifier = Modifier.padding(top = 20.dp)) }
            items(items, key = { it.id }) { it ->
                Row(
                    Modifier.animateItem().fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(RecycleBin.icon(it.kind), fontSize = 26.sp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(it.name, fontWeight = FontWeight.Medium, maxLines = 2)
                        Text(RecycleBin.label(it.kind), color = Sub, fontSize = 12.sp)
                        Text("Deleted ${Units.date(it.deletedAt)} · gone in ${it.daysLeft} day${if (it.daysLeft == 1) "" else "s"}",
                            color = if (it.daysLeft <= 3) Red else Sub, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BigButton("Restore", modifier = Modifier.padding(0.dp)) { message = RecycleBin.restore(it) }
                        Text("Delete now", color = Red, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp))
                            .pressClick(rememberPress()) { forever = it }.padding(4.dp))
                    }
                }
            }
        }
    }
    if (confirmEmpty) ConfirmText("Delete everything in the Recycle Bin for good? This can't be undone.",
        { confirmEmpty = false; RecycleBin.empty() }, { confirmEmpty = false }, "Empty")
    forever?.let { f ->
        ConfirmText("Delete \"${f.name}\" for good? This can't be undone.", { forever = null; RecycleBin.deleteForever(f) }, { forever = null }, "Delete")
    }
}

/** Settings: the way into the bin, with how many things are in it. */
@Composable
fun RecycleBinButton() {
    val items by RecycleBin.items.collectAsState()
    GreyButton("🗑  Recycle Bin" + if (items.isNotEmpty()) " (${items.size})" else "") { Nav.go(Route.Bin) }
}
