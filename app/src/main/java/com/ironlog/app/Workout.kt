package com.ironlog.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.layout.size
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
    /** [exercises]: for a freestyle workout, the exercises you picked (3 empty sets each). */
    fun start(c: Context, r: Routine?, exercises: List<String> = emptyList()) {
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
                WorkoutItem(i.exerciseId, i.sets.map { p -> DoneSet(p.weight, p.reps, p.seconds, p.distance, p.failure, false, p.warmup) }, i.restSec, i.note)
            } ?: exercises.map { WorkoutItem(it, List(3) { DoneSet() }, Store.profile.value.defaultRest) },
            bodyweight = Store.profile.value.bodyweight,
        )
        Store.setActive(w)
        Rest.stop(c)
        WorkoutService.start(c)
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

    fun saveNoteToRoutine(routineId: String?, exerciseId: String, note: String) {
        val r = Store.routines.value.firstOrNull { it.id == routineId } ?: return
        if (r.items.none { it.exerciseId == exerciseId }) return
        Store.saveRoutine(r.copy(items = r.items.map { if (it.exerciseId == exerciseId) it.copy(note = note) else it }))
    }

    /** Ends the workout: keeps finished sets, saves it, and returns its id (null if nothing was done). */
    fun finish(c: Context): String? {
        val w = Store.active.value ?: return null
        Rest.stop(c)
        WorkoutService.stop(c)
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
        WorkoutService.stop(c)
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
        // Top bar: timer, rest timer button, Finish.
        var bigMap by remember { mutableStateOf(false) }
        var manualRest by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().background(Card).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⌄", fontSize = 28.sp, modifier = Modifier.clickable { Nav.back() }.padding(horizontal = 8.dp))
            Text(Units.clock(now - w.start), color = Blue, fontSize = 24.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("⏱", fontSize = 26.sp, modifier = Modifier.clickable { manualRest = true }.padding(horizontal = 10.dp))
            Text("Discard", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Red).clickable { confirmDiscard = true }
                    .padding(horizontal = 14.dp, vertical = 9.dp))
            HSpacer(8)
            Text("Finish", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Blue).clickable { confirmEnd = true }
                    .padding(horizontal = 18.dp, vertical = 9.dp))
        }
        // Stats row with the live muscle map (tap it to enlarge).
        val doneSets = w.items.flatMap { it.sets }.filter { it.done }
        val load = remember(w.items) { Stats.muscleLoad(w) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1.2f)) { Text("Duration", color = Sub, fontSize = 13.sp); Text(Units.clock(now - w.start), color = Blue, fontSize = 17.sp) }
            StatBox("Volume", "${Units.weight(Stats.volume(w))} ${Units.w}", Modifier.weight(1.3f))
            StatBox("Sets", "${doneSets.size}", Modifier.weight(0.8f))
            StatBox("Reps", "${doneSets.sumOf { it.reps ?: 0 }}", Modifier.weight(0.8f))
            Box(Modifier.size(width = 64.dp, height = 56.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black).clickable { bigMap = !bigMap }) {
                BodyMapView(load, Modifier.fillMaxSize())
            }
        }
        if (bigMap) Box(Modifier.fillMaxWidth().height(260.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black).clickable { bigMap = false }) {
            BodyMapView(load, Modifier.fillMaxSize())
            if (load.isEmpty()) Text("Tick off a set to see the muscles you've worked", color = Sub, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp))
        }
        androidx.compose.material3.HorizontalDivider(color = Card2)
        if (manualRest) RestPicker(0, { manualRest = false }) { sec -> manualRest = false; if (sec > 0) Rest.start(context, sec) else Rest.stop(context) }
        // Lots of room at the bottom so the last sets can scroll up above the keyboard.
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 400.dp)) {
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
                // Notes (kept with the routine, so they're there next time).
                var note by remember(w.id, i) { mutableStateOf(item.note) }
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).typing()) {
                    if (note.isEmpty()) Text("Add notes here...", color = Sub, fontSize = 17.sp)
                    androidx.compose.foundation.text.BasicTextField(
                        note, { t ->
                            note = t
                            Workouts.updateItem(i) { it.copy(note = t) }
                            Workouts.saveNoteToRoutine(w.routineId, item.exerciseId, t)
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFFB0B0B5), fontSize = 17.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Blue),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                RestLine(item.restSec) { restFor = i }
                if (ex != null) {
                    val prev = remember(workouts, ex.id) { Stats.previous(ex.id, workouts) }
                    val cols = columnsFor(ex.type, true)
                    // 👑 on the ticked set that set a new best est. 1-rep max (moves to a later set that beats it).
                    val bestBefore = remember(workouts, ex.id, w.id) {
                        workouts.filter { it.id != w.id && it.end != null }.flatMap { o -> o.items.filter { it.exerciseId == ex.id }.flatMap { it.sets } }
                            .filter { it.done && !it.warmup }.maxOfOrNull { Stats.e1rm(it.weight, it.reps) } ?: 0.0
                    }
                    val crown = if (ex.type != ExType.WEIGHT) -1 else {
                        var best = bestBefore; var at = -1
                        item.sets.forEachIndexed { n, s ->
                            val v = if (s.done && !s.warmup) Stats.e1rm(s.weight, s.reps) else 0.0
                            if (v > best) { best = v; at = n }
                        }
                        at
                    }
                    TableHeader(cols + ("✓" to 0.8f))
                    item.sets.forEachIndexed { n, s ->
                        ActiveSetRow(ex, i, n, s, prev.getOrNull(n) ?: prev.lastOrNull(), cols, item.restSec, w.id, crown = n == crown)
                    }
                    Text("Tap a set number to mark it W (warm-up – never counts for ranks) or F (to failure). Long-press ✓ to delete a set.", color = Sub, fontSize = 12.sp,
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
                BigButton("Finish Workout", Green, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { confirmEnd = true }
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
private fun ActiveSetRow(ex: Exercise, i: Int, n: Int, s: DoneSet, prev: DoneSet?, cols: List<Pair<String, Float>>, rest: Int, wid: String, crown: Boolean = false) {
    val context = LocalContext.current
    val bg = if (s.done) Green.copy(alpha = 0.15f) else Bg
    val k = "$wid:$i:${ex.id}:$n"
    var typeMenu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().background(bg).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(cols[0].second)) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(Card2)
                    .clickable { typeMenu = true },
                contentAlignment = Alignment.Center,
            ) {
                val item = Store.active.value?.items?.getOrNull(i)
                SetLabel(setNumber(item?.sets?.map { it.warmup } ?: emptyList(), n).coerceAtLeast(0), s.failure, Modifier, s.warmup)
            }
            if (crown) Text("👑", fontSize = 15.sp, modifier = Modifier.align(Alignment.TopStart).offset(x = 30.dp, y = (-6).dp))
            SetTypeMenu(typeMenu, { typeMenu = false }, s.warmup, s.failure) { w, f ->
                Workouts.updateSet(i, n) { it.copy(warmup = w, failure = f) }
                // A set that's already ticked changes what counts for ranks.
                Store.active.value?.let { if (s.done) { if (w) Ranks.onSetUndone(context, it, ex.id) else Ranks.onSetDone(it, ex.id) } }
            }
        }
        // Previous: tap to copy it in.
        Text(
            prev?.let { Units.set(ex.type, it) } ?: "-", color = Sub, fontSize = 16.sp, maxLines = 1,
            modifier = Modifier.weight(cols[1].second).clickable(enabled = prev != null) {
                prev?.let { p ->
                    Workouts.updateSet(i, n) { it.copy(weight = p.weight, reps = p.reps, seconds = p.seconds, distance = p.distance) }
                    Workouts.refresh++
                }
            },
        )
        // Refreshes the boxes when numbers are filled in for you (not while you type).
        val vk = "$k:${Workouts.refresh}:${s.done}"
        when (ex.type) {
            ExType.BODYWEIGHT -> NumField(s.reps?.toString() ?: "", prev?.reps?.toString() ?: if (s.failure) "max" else "0", "$vk:r",
                Modifier.weight(cols[2].second).padding(end = 6.dp), KeyboardType.Number, outlined = true) { v ->
                Workouts.updateSet(i, n) { it.copy(reps = v.toIntOrNull(), weight = null) }
            }
            ExType.WEIGHT -> {
                NumField(Units.weight(s.weight).takeIf { s.weight != null } ?: "", prev?.weight?.let { Units.weight(it) } ?: "0", "$vk:w",
                    Modifier.weight(cols[2].second).padding(end = 6.dp), outlined = true) { v ->
                    Workouts.updateSet(i, n) { it.copy(weight = v.toDoubleOrNull()?.let(Units::fromShown)) }
                }
                NumField(s.reps?.toString() ?: "", prev?.reps?.toString() ?: if (s.failure) "max" else "0", "$vk:r",
                    Modifier.weight(cols[3].second).padding(end = 6.dp), KeyboardType.Number, outlined = true) { v ->
                    Workouts.updateSet(i, n) { it.copy(reps = v.toIntOrNull()) }
                }
            }
            ExType.TIMED -> {
                Row(Modifier.weight(cols[2].second), verticalAlignment = Alignment.CenterVertically) {
                    NumField(Units.time(s.seconds), prev?.seconds?.let { Units.time(it) } ?: "0:30", "$vk:t",
                        Modifier.weight(1f).padding(end = 6.dp), outlined = true) { v ->
                        Workouts.updateSet(i, n) { it.copy(seconds = Units.parseTime(v)) }
                    }
                    Stopwatch { sec -> Workouts.updateSet(i, n) { it.copy(seconds = sec) } }
                }
            }
            ExType.WEIGHT_TIME -> {
                NumField(Units.weight(s.weight).takeIf { s.weight != null } ?: "", prev?.weight?.let { Units.weight(it) } ?: "0", "$vk:w",
                    Modifier.weight(cols[2].second).padding(end = 6.dp), outlined = true) { v ->
                    Workouts.updateSet(i, n) { it.copy(weight = v.toDoubleOrNull()?.let(Units::fromShown)) }
                }
                Row(Modifier.weight(cols[3].second), verticalAlignment = Alignment.CenterVertically) {
                    NumField(Units.time(s.seconds), prev?.seconds?.let { Units.time(it) } ?: "0:30", "$vk:t",
                        Modifier.weight(1f).padding(end = 6.dp), outlined = true) { v ->
                        Workouts.updateSet(i, n) { it.copy(seconds = Units.parseTime(v)) }
                    }
                    Stopwatch { sec -> Workouts.updateSet(i, n) { it.copy(seconds = sec) } }
                }
            }
            ExType.CARDIO -> {
                NumField(Units.distance(s.distance), prev?.distance?.let { Units.distance(it) } ?: "0", "$vk:d",
                    Modifier.weight(cols[2].second).padding(end = 6.dp), outlined = true) { v ->
                    Workouts.updateSet(i, n) { it.copy(distance = v.toDoubleOrNull()?.let(Units::distFromShown)) }
                }
                NumField(Units.time(s.seconds), prev?.seconds?.let { Units.time(it) } ?: "0:00", "$vk:t",
                    Modifier.weight(cols[3].second).padding(end = 6.dp), outlined = true) { v ->
                    Workouts.updateSet(i, n) { it.copy(seconds = Units.parseTime(v)) }
                }
            }
        }
        var del by remember { mutableStateOf(false) }
        Box(
            Modifier.weight(0.8f).clip(RoundedCornerShape(10.dp)).background(if (s.done) Green else Card2)
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
                            Store.active.value?.let { Ranks.onSetDone(it, ex.id) }
                            if (rest > 0) Rest.start(context, rest) else Rest.stop(context)
                        } else {
                            Workouts.updateSet(i, n) { it.copy(done = false) }
                            Store.active.value?.let { Ranks.onSetUndone(context, it, ex.id) }
                        }
                    },
                    onLongClick = { del = true },
                )
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
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
            val ranked = w.items.distinctBy { it.exerciseId }.mapNotNull { Store.exercise(it.exerciseId) }.filter { Standards.covers(it.id, profile) }
            if (ranked.isNotEmpty()) {
                Text("Your ranks", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (profile.bodyweight == null) {
                    Text("Add your body weight (and age) in Settings to see how you rank against other lifters.", color = Sub)
                    GreyButton("Open Profile") { Nav.home(2) }
                }
                ranked.forEach { e ->
                    val sets = w.items.filter { it.exerciseId == e.id }.flatMap { it.sets }
                    val pct = Standards.percentile(e.id, Standards.bestValue(e.id, sets), profile)
                    if (pct != null) Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RankBadge(Tier.of(pct), 48.dp)
                        HSpacer(12)
                        Column {
                            Text(starredName(e), color = Blue, fontWeight = FontWeight.Medium)
                            Text(rankText(pct), color = Color(Tier.of(pct).color))
                        }
                    }
                }
                Text("Ranks compare you with people who lift, using Strength Level's standards for your body weight, sex and age.",
                    color = Sub, fontSize = 12.sp)
            }
            BigButton("Done") { Nav.home(0) }
            Spacer(24)
        }
    }
}

@Composable
fun WorkoutHeatmap(w: Workout) = Heatmap(remember(w.id, w.end, w.items) { Stats.muscleLoad(w) }, "Muscles worked")

/** Front and back body picture shaded by how hard each muscle worked, with a key. */
@Composable
fun Heatmap(load: Map<String, Float>, title: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black).padding(12.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold)
        BodyMapView(load, Modifier.fillMaxWidth().height(340.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text("Not worked", color = Sub, fontSize = 12.sp)
            HSpacer(6)
            (0..8).forEach { i ->
                Box(Modifier.height(12.dp).fillMaxWidth(0.035f).background(Color(BodyMap.heat(i / 8f))))
            }
            HSpacer(6)
            Text("Hardest", color = Sub, fontSize = 12.sp)
        }
    }
}
