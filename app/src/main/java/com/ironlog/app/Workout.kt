package com.ironlog.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Starting, changing and finishing the workout in progress. */
object Workouts {
    /** Bumped when numbers are filled in for you, so the boxes show them. */
    var refresh by androidx.compose.runtime.mutableIntStateOf(0)
    fun start(c: Context, r: Routine?) {
        val current = Store.active.value
        if (current != null) {
            // Already going: just go back to it.
            Nav.go(Route.Active)
            return
        }
        val w = Workout(
            id = Store.newId(),
            routineId = r?.id,
            name = r?.name ?: "Workout",
            start = System.currentTimeMillis(),
            items = r?.items?.map { i ->
                WorkoutItem(i.exerciseId, i.sets.map { p -> DoneSet(p.weight, p.reps, p.seconds, p.distance, p.failure, false) }, i.restSec)
            } ?: emptyList(),
            bodyweight = Store.profile.value.bodyweight,
        )
        Store.setActive(w)
        Rest.stop(c)
        Nav.go(Route.Active)
    }

    fun update(change: (Workout) -> Workout) {
        val w = Store.active.value ?: return
        Store.setActive(change(w))
    }

    fun updateItem(i: Int, change: (WorkoutItem) -> WorkoutItem) = update { w ->
        w.copy(items = w.items.toMutableList().also { if (i in it.indices) it[i] = change(it[i]) })
    }

    fun updateSet(i: Int, n: Int, change: (DoneSet) -> DoneSet) = updateItem(i) { item ->
        item.copy(sets = item.sets.toMutableList().also { if (n in it.indices) it[n] = change(it[n]) })
    }

    /** Ends the workout: keeps finished sets, saves it, and returns its id (null if nothing was done). */
    fun finish(c: Context): String? {
        val w = Store.active.value ?: return null
        Rest.stop(c)
        val kept = w.copy(
            end = System.currentTimeMillis(),
            items = w.items.map { it.copy(sets = it.sets.filter { s -> s.done }) }.filter { it.sets.isNotEmpty() },
        )
        Store.setActive(null)
        if (kept.items.isEmpty()) return null
        Store.saveWorkout(kept)
        return kept.id
    }

    fun discard(c: Context) {
        Rest.stop(c)
        Store.setActive(null)
    }
}

// ---------------- The workout in progress ----------------

@Composable
fun ActiveWorkoutScreen() {
    val w = Store.active.collectAsState().value
    val context = LocalContext.current
    if (w == null) { LaunchedEffect(Unit) { Nav.back() }; return }
    val workouts by Store.workouts.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    var restFor by remember { mutableStateOf<Int?>(null) }
    var confirmEnd by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // Header: name, timer, volume, sets.
        Column(Modifier.fillMaxWidth().background(Card).statusBarsPadding().padding(16.dp, 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⌄", fontSize = 26.sp, modifier = Modifier.clickable { Nav.back() }.padding(end = 12.dp))
                Text(w.name, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("End", color = Blue, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Blue.copy(alpha = 0.15f)).clickable { confirmEnd = true }
                        .padding(horizontal = 14.dp, vertical = 6.dp))
            }
            Spacer(8)
            Row {
                StatBox("Duration", Units.clock(now - w.start), Modifier.weight(1f))
                StatBox("Volume", "${Units.weight(Stats.volume(w))} ${Units.w}", Modifier.weight(1f))
                StatBox("Sets", "${w.doneSets}", Modifier.weight(1f))
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(w.items.size) { i ->
                val item = w.items[i]
                val ex = Store.exercise(item.exerciseId)
                var menu by remember { mutableStateOf(false) }
                ExerciseHeader(ex) {
                    Box {
                        Text("•••", color = Sub, fontSize = 18.sp, modifier = Modifier.clickable { menu = true }.padding(8.dp))
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Replace exercise") }, {
                                menu = false
                                Nav.go(Route.Picker("Replace Exercise", false) { ids ->
                                    ids.firstOrNull()?.let { id -> Workouts.updateItem(i) { it.copy(exerciseId = id) } }
                                })
                            })
                            DropdownMenuItem({ Text("Remove exercise", color = Red) }, {
                                menu = false
                                Workouts.update { ww -> ww.copy(items = ww.items.toMutableList().also { it.removeAt(i) }) }
                            })
                        }
                    }
                }
                RestLine(item.restSec) { restFor = i }
                if (ex != null) {
                    val prev = remember(workouts, ex.id) { Stats.previous(ex.id, workouts) }
                    val cols = columnsFor(ex.type, true)
                    TableHeader(cols + ("✓" to 0.8f))
                    item.sets.forEachIndexed { n, s ->
                        ActiveSetRow(ex, i, n, s, prev.getOrNull(n) ?: prev.lastOrNull(), cols, item.restSec, w.id)
                    }
                    Text("Tap a set number to mark it F (to failure). Long-press ✓ to delete a set.", color = Sub, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    GreyButton("+ Add Set", Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Workouts.updateItem(i) { it.copy(sets = it.sets + (it.sets.lastOrNull()?.copy(done = false) ?: DoneSet())) }
                    }
                }
                Spacer(16)
            }
            item {
                BigButton("+ Add Exercise", modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Nav.go(Route.Picker("Add Exercises", true) { ids ->
                        val rest = Store.profile.value.defaultRest
                        Workouts.update { ww -> ww.copy(items = ww.items + ids.map { WorkoutItem(it, List(3) { DoneSet() }, rest) }) }
                    })
                }
                BigButton("End Routine", Green, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { confirmEnd = true }
                GreyButton("Discard Workout", Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), Red) { confirmDiscard = true }
            }
        }
        RestBar()
    }
    restFor?.let { i ->
        RestPicker(w.items.getOrNull(i)?.restSec ?: 90, { restFor = null }) { sec -> Workouts.updateItem(i) { it.copy(restSec = sec) }; restFor = null }
    }
    if (confirmEnd) {
        val unfinished = w.items.sumOf { it.sets.count { s -> !s.done } }
        ConfirmText(
            if (unfinished > 0) "End the workout? $unfinished set${if (unfinished == 1) "" else "s"} not ticked off won't be saved." else "End the workout and save it?",
            {
                confirmEnd = false
                val id = Workouts.finish(context)
                Nav.home(0)
                if (id != null) Nav.go(Route.Summary(id))
            },
            { confirmEnd = false }, "End Routine",
        )
    }
    if (confirmDiscard) ConfirmText(
        "Throw away this workout? Nothing from it will be saved.",
        { confirmDiscard = false; Workouts.discard(context); Nav.home(1) }, { confirmDiscard = false }, "Discard",
    )
}

@Composable
private fun ActiveSetRow(ex: Exercise, i: Int, n: Int, s: DoneSet, prev: DoneSet?, cols: List<Pair<String, Float>>, rest: Int, wid: String) {
    val context = LocalContext.current
    val bg = when {
        s.done -> Green.copy(alpha = 0.18f)
        n % 2 == 1 -> Card
        else -> Bg
    }
    val k = "$wid:$i:${ex.id}:$n"
    Row(Modifier.fillMaxWidth().background(bg).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        SetLabel(n, s.failure, Modifier.weight(cols[0].second).clickable { Workouts.updateSet(i, n) { it.copy(failure = !it.failure) } }.padding(vertical = 6.dp))
        // Previous: tap to copy it in.
        Text(
            prev?.let { Units.set(ex.type, it) } ?: "-", color = Sub, fontSize = 14.sp, maxLines = 1,
            modifier = Modifier.weight(cols[1].second).clickable(enabled = prev != null) {
                prev?.let { p ->
                    Workouts.updateSet(i, n) { it.copy(weight = p.weight, reps = p.reps, seconds = p.seconds, distance = p.distance) }
                    Workouts.refresh++
                }
            },
        )
        val fieldBg = if (s.done) Color.Transparent else Card2
        // Refreshes the boxes when numbers are filled in for you (not while you type).
        val vk = "$k:${Workouts.refresh}:${s.done}"
        when (ex.type) {
            ExType.WEIGHT, ExType.BODYWEIGHT -> {
                NumField(Units.weight(s.weight).takeIf { s.weight != null } ?: "", prev?.weight?.let { Units.weight(it) } ?: "0", "$vk:w",
                    Modifier.weight(cols[2].second).padding(end = 6.dp), background = fieldBg) { v ->
                    Workouts.updateSet(i, n) { it.copy(weight = v.toDoubleOrNull()?.let(Units::fromShown)) }
                }
                NumField(s.reps?.toString() ?: "", prev?.reps?.toString() ?: if (s.failure) "max" else "0", "$vk:r",
                    Modifier.weight(cols[3].second).padding(end = 6.dp), KeyboardType.Number, fieldBg) { v ->
                    Workouts.updateSet(i, n) { it.copy(reps = v.toIntOrNull()) }
                }
            }
            ExType.TIMED -> {
                Row(Modifier.weight(cols[2].second), verticalAlignment = Alignment.CenterVertically) {
                    NumField(Units.time(s.seconds), prev?.seconds?.let { Units.time(it) } ?: "0:30", "$vk:t",
                        Modifier.weight(1f).padding(end = 6.dp), background = fieldBg) { v ->
                        Workouts.updateSet(i, n) { it.copy(seconds = Units.parseTime(v)) }
                    }
                    Stopwatch { sec -> Workouts.updateSet(i, n) { it.copy(seconds = sec) } }
                }
            }
            ExType.CARDIO -> {
                NumField(Units.distance(s.distance), prev?.distance?.let { Units.distance(it) } ?: "0", "$vk:d",
                    Modifier.weight(cols[2].second).padding(end = 6.dp), background = fieldBg) { v ->
                    Workouts.updateSet(i, n) { it.copy(distance = v.toDoubleOrNull()?.let(Units::distFromShown)) }
                }
                NumField(Units.time(s.seconds), prev?.seconds?.let { Units.time(it) } ?: "0:00", "$vk:t",
                    Modifier.weight(cols[3].second).padding(end = 6.dp), background = fieldBg) { v ->
                    Workouts.updateSet(i, n) { it.copy(seconds = Units.parseTime(v)) }
                }
            }
        }
        var del by remember { mutableStateOf(false) }
        Box(
            Modifier.weight(0.8f).clip(RoundedCornerShape(6.dp)).background(if (s.done) Green else Card2)
                .combinedClickableCompat(
                    onClick = {
                        if (!s.done) {
                            // Empty boxes take the greyed-out numbers (last time's).
                            Workouts.updateSet(i, n) {
                                it.copy(
                                    weight = it.weight ?: prev?.weight,
                                    reps = it.reps ?: prev?.reps,
                                    seconds = it.seconds ?: prev?.seconds,
                                    distance = it.distance ?: prev?.distance,
                                    done = true,
                                )
                            }
                            if (rest > 0) Rest.start(context, rest)
                        } else {
                            Workouts.updateSet(i, n) { it.copy(done = false) }
                        }
                    },
                    onLongClick = { del = true },
                )
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) { Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
        if (del) ConfirmText("Delete set ${n + 1}?", {
            del = false
            Workouts.updateItem(i) { it.copy(sets = it.sets.toMutableList().also { l -> l.removeAt(n) }) }
        }, { del = false }, "Delete")
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: () -> Unit) =
    this.combinedClickable(onClick = onClick, onLongClick = onLongClick)

/** A start/stop button that counts seconds for timed holds. */
@Composable
private fun Stopwatch(onStop: (Int) -> Unit) {
    var startedAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(startedAt) { while (startedAt > 0) { now = System.currentTimeMillis(); delay(250) } }
    val running = startedAt > 0
    Text(
        if (running) Units.time(((now - startedAt) / 1000).toInt()) else "▶",
        color = if (running) Green else Blue, fontWeight = FontWeight.Bold,
        modifier = Modifier.clickable {
            if (running) { onStop(((System.currentTimeMillis() - startedAt) / 1000).toInt()); startedAt = 0 }
            else startedAt = System.currentTimeMillis().also { now = it }
        }.padding(horizontal = 6.dp),
    )
}

/** The rest countdown along the bottom while a rest is running. */
@Composable
private fun RestBar() {
    val context = LocalContext.current
    val endsAt by Rest.endsAt.collectAsState()
    val total by Rest.total.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) { while (endsAt > 0) { now = System.currentTimeMillis(); delay(200) } }
    if (endsAt <= 0) return
    val left = ((endsAt - now) / 1000).coerceAtLeast(0)
    if (endsAt - now <= 0) { LaunchedEffect(endsAt) { Rest.finished() }; return }
    Column(Modifier.fillMaxWidth().background(Card2).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            Box(Modifier.fillMaxWidth(((endsAt - now) / (total * 1000f)).coerceIn(0f, 1f)).fillMaxHeight().background(Blue))
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            GreyButton("-15", Modifier.weight(1f)) { Rest.add(context, -15) }
            Text(Units.time(left.toInt()), fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1.4f))
            GreyButton("+15", Modifier.weight(1f)) { Rest.add(context, 15) }
            HSpacer(8)
            BigButton("Skip", modifier = Modifier.weight(1f)) { Rest.stop(context) }
        }
    }
}

// ---------------- After the workout ----------------

private val CHEERS = listOf("Great job! 💪", "Workout complete! 🔥", "Nice work! 🏆", "Crushed it! 💥", "Another one done! ✅")

@Composable
fun SummaryScreen(id: String) {
    val w = Store.workouts.collectAsState().value.firstOrNull { it.id == id } ?: return Nav.back()
    val history by Store.workouts.collectAsState()
    val profile by Store.profile.collectAsState()
    val cheer = remember(id) { CHEERS.random() }
    val improvements = remember(id, history) { Stats.improvements(w, history) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Summary", back = false, action = "Done") { Nav.home(0) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(cheer, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("${w.name} · ${Units.date(w.start)}", color = Sub)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
                StatBox("Duration", Units.duration((w.end ?: w.start) - w.start), Modifier.weight(1f))
                StatBox("Volume", "${Units.weight(Stats.volume(w))} ${Units.w}", Modifier.weight(1f))
                StatBox("Sets", "${w.doneSets}", Modifier.weight(1f))
            }
            WorkoutHeatmap(w)
            Text("What improved", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (improvements.isEmpty()) Text("No new records this time – consistency is what builds them. Keep going!", color = Sub)
            improvements.groupBy { it.first }.forEach { (name, lines) ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card).padding(12.dp)) {
                    Text(name, color = Blue, fontWeight = FontWeight.Medium)
                    lines.forEach { Text("🏅 " + it.second) }
                }
            }
            val ranked = w.items.distinctBy { it.exerciseId }.mapNotNull { Store.exercise(it.exerciseId) }.filter { Standards.covers(it.id) }
            if (ranked.isNotEmpty()) {
                Text("How you compare", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (profile.bodyweight == null) {
                    Text("Add your body weight (and age) in Profile to see how you rank against other lifters.", color = Sub)
                    GreyButton("Open Profile") { Nav.home(2) }
                }
                ranked.forEach { e ->
                    val sets = w.items.filter { it.exerciseId == e.id }.flatMap { it.sets }
                    val best = Standards.bestValue(e.id, sets)
                    val rank = Standards.rank(e.id, best, profile.copy(bodyweight = w.bodyweight ?: profile.bodyweight))
                    if (rank != null) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card).padding(12.dp)) {
                        Text(e.name, color = Blue, fontWeight = FontWeight.Medium)
                        Text("Stronger than about ${rank.percentile}% of ${rank.against} · ${rank.level}")
                        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Card2)) {
                            Box(Modifier.fillMaxWidth(rank.percentile / 100f).fillMaxHeight().background(Blue))
                        }
                    }
                }
                Text("Rankings are estimates from published strength standards.", color = Sub, fontSize = 12.sp)
            }
            BigButton("Done") { Nav.home(0) }
            Spacer(24)
        }
    }
}

@Composable
fun WorkoutHeatmap(w: Workout) {
    val load = remember(w.id, w.end) { Stats.muscleLoad(w) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(12.dp)) {
        Text("Muscles worked", color = Color.Black, fontWeight = FontWeight.Bold)
        BodyMapView(load, Modifier.fillMaxWidth().height(300.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Text("Less", color = Color.DarkGray, fontSize = 12.sp)
            listOf(0.05f, 0.35f, 0.65f, 1f).forEach { v ->
                Box(Modifier.padding(horizontal = 3.dp).height(10.dp).fillMaxWidth(0.08f).background(Color(BodyMap.heat(v))))
            }
            Text("More", color = Color.DarkGray, fontSize = 12.sp)
        }
    }
}
