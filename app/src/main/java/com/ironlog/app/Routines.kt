package com.ironlog.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The routine being edited (kept here while you pick exercises). */
object Drafts {
    var routine by mutableStateOf<Routine?>(null)

    fun openEditor(id: String?) {
        routine = Store.routines.value.firstOrNull { it.id == id } ?: Routine(Store.newId(), "", emptyList())
        Nav.go(Route.RoutineEdit(id))
    }
}

val REST_CHOICES = listOf(0, 15, 30, 45, 60, 75, 90, 105, 120, 150, 180, 210, 240, 300, 360, 420, 480, 600)

// ---------------- Workout tab ----------------

@Composable
fun WorkoutTab() {
    val routines by Store.routines.collectAsState()
    val context = LocalContext.current
    var deleting by remember { mutableStateOf<Routine?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Workout", back = false)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            item {
                SectionTitle("Quick Start")
                GreyButton("+  Start Empty Workout", Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Workouts.start(context, null)
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Routines (${routines.size})", Modifier.weight(1f))
                    Text("+ New Routine", color = Blue, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { Drafts.openEditor(null) }.padding(16.dp))
                }
                if (routines.isEmpty()) Text(
                    "Build your first routine: pick exercises, sets, failure sets and rest timers, then start it any time.",
                    color = Sub, modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(routines, key = { it.id }) { r ->
                var menu by remember { mutableStateOf(false) }
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Card).clickable { Nav.go(Route.RoutineView(r.id)) }.padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.name.ifBlank { "Routine" }, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Box {
                            Text("•••", color = Sub, modifier = Modifier.clickable { menu = true }.padding(horizontal = 6.dp))
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text("Edit") }, { menu = false; Drafts.openEditor(r.id) })
                                DropdownMenuItem({ Text("Duplicate") }, {
                                    menu = false
                                    Store.saveRoutine(r.copy(id = Store.newId(), name = r.name + " (copy)"))
                                })
                                DropdownMenuItem({ Text("Delete", color = Red) }, { menu = false; deleting = r })
                            }
                        }
                    }
                    Text(
                        r.items.joinToString(", ") { Store.exercise(it.exerciseId)?.name ?: "?" },
                        color = Sub, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
                    )
                    Spacer(12)
                    BigButton("Start Routine") { Workouts.start(context, r) }
                }
            }
        }
    }
    deleting?.let { r ->
        ConfirmText("Delete \"${r.name}\"? Your logged workouts stay.", { Store.deleteRoutine(r.id); deleting = null }, { deleting = null }, "Delete")
    }
}

// ---------------- Routine (read-only, like the reference) ----------------

@Composable
fun RoutineViewScreen(id: String) {
    val routines by Store.routines.collectAsState()
    val r = routines.firstOrNull { it.id == id } ?: return Nav.back()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TopBar("Routine", action = "Edit") { Drafts.openEditor(r.id) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
            item {
                Text(r.name.ifBlank { "Routine" }, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                BigButton("Start Routine", modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Workouts.start(context, r) }
                Spacer(8)
            }
            items(r.items.size) { i ->
                val item = r.items[i]
                val ex = Store.exercise(item.exerciseId)
                ExerciseHeader(ex)
                RestLine(item.restSec, null)
                if (ex != null) {
                    TableHeader(columnsFor(ex.type, false))
                    item.sets.forEachIndexed { n, s -> PlanRowView(ex.type, n, s) }
                }
                Spacer(12)
            }
        }
    }
}

@Composable
fun ExerciseHeader(ex: Exercise?, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clickable { ex?.let { Nav.go(Route.ExerciseDetail(it.id)) } }) { ExerciseThumb(ex, 48.dp) }
        HSpacer(14)
        Column(Modifier.weight(1f).clickable { ex?.let { Nav.go(Route.ExerciseDetail(it.id)) } }) {
            Text(ex?.name ?: "Missing exercise", color = Blue, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            if (ex?.custom == true) { Spacer(4); Chip("Custom") }
        }
        trailing?.invoke()
    }
}

@Composable
fun RestLine(sec: Int, onClick: (() -> Unit)?) {
    Text(
        "⏱  Rest Timer: ${Units.restLabel(sec)}", color = Blue, fontSize = 15.sp,
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
fun SetLabel(n: Int, failure: Boolean, modifier: Modifier) =
    Text(if (failure) "F" else "${n + 1}", color = if (failure) Red else Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = modifier)

@Composable
private fun PlanRowView(t: ExType, n: Int, s: PlanSet) {
    val cols = columnsFor(t, false)
    Row(
        Modifier.fillMaxWidth().background(if (n % 2 == 1) Card else Bg).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetLabel(n, s.failure, Modifier.weight(cols[0].second))
        val values = when (t) {
            ExType.WEIGHT, ExType.BODYWEIGHT -> listOf(Units.weight(s.weight).ifEmpty { "-" }, s.reps?.toString() ?: if (s.failure) "max" else "-")
            ExType.TIMED -> listOf(Units.time(s.seconds).ifEmpty { "-" })
            ExType.CARDIO -> listOf(Units.distance(s.distance).ifEmpty { "-" }, Units.time(s.seconds).ifEmpty { "-" })
        }
        values.forEachIndexed { i, v -> Text(v, fontSize = 17.sp, modifier = Modifier.weight(cols[i + 1].second)) }
    }
}

// ---------------- Routine editor ----------------

@Composable
fun RoutineEditScreen(id: String?) {
    val draft = Drafts.routine ?: return Nav.back()
    var restFor by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    fun update(change: (Routine) -> Routine) { Drafts.routine = change(Drafts.routine ?: draft) }
    fun updateItem(i: Int, change: (RoutineItem) -> RoutineItem) =
        update { r -> r.copy(items = r.items.toMutableList().also { it[i] = change(it[i]) }) }

    Column(Modifier.fillMaxSize()) {
        TopBar(if (id == null) "New Routine" else "Edit Routine", action = "Save") {
            val r = Drafts.routine ?: return@TopBar
            when {
                r.name.isBlank() -> error = "Give the routine a name."
                r.items.isEmpty() -> error = "Add at least one exercise."
                else -> { Store.saveRoutine(r); Drafts.routine = null; Nav.back() }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp)) {
            item {
                OutlinedTextField(
                    draft.name, { n -> update { it.copy(name = n) } },
                    Modifier.fillMaxWidth().padding(16.dp), label = { Text("Routine name") }, singleLine = true,
                )
                error?.let { Text(it, color = Red, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            items(draft.items.size, key = { "${draft.items[it].exerciseId}#$it" }) { i ->
                val item = draft.items[i]
                val ex = Store.exercise(item.exerciseId)
                var menu by remember { mutableStateOf(false) }
                ExerciseHeader(ex) {
                    Box {
                        Text("•••", color = Sub, fontSize = 18.sp, modifier = Modifier.clickable { menu = true }.padding(8.dp))
                        DropdownMenu(menu, { menu = false }) {
                            if (i > 0) DropdownMenuItem({ Text("Move up") }, {
                                menu = false
                                update { r -> r.copy(items = r.items.toMutableList().also { l -> l.add(i - 1, l.removeAt(i)) }) }
                            })
                            if (i < draft.items.lastIndex) DropdownMenuItem({ Text("Move down") }, {
                                menu = false
                                update { r -> r.copy(items = r.items.toMutableList().also { l -> l.add(i + 1, l.removeAt(i)) }) }
                            })
                            DropdownMenuItem({ Text("Replace exercise") }, {
                                menu = false
                                Nav.go(Route.Picker("Replace Exercise", false) { ids ->
                                    ids.firstOrNull()?.let { newId -> updateItem(i) { it.copy(exerciseId = newId) } }
                                })
                            })
                            DropdownMenuItem({ Text("Remove", color = Red) }, {
                                menu = false
                                update { r -> r.copy(items = r.items.toMutableList().also { it.removeAt(i) }) }
                            })
                        }
                    }
                }
                RestLine(item.restSec) { restFor = i }
                if (ex != null) {
                    val cols = columnsFor(ex.type, false)
                    TableHeader(cols + ("" to 0.5f))
                    item.sets.forEachIndexed { n, s ->
                        Row(
                            Modifier.fillMaxWidth().background(if (n % 2 == 1) Card else Bg).padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SetLabel(n, s.failure, Modifier.weight(cols[0].second).clickable {
                                updateItem(i) { it.copy(sets = it.sets.toMutableList().also { l -> l[n] = s.copy(failure = !s.failure) }) }
                            }.padding(vertical = 6.dp))
                            fun setAt(change: (PlanSet) -> PlanSet) =
                                updateItem(i) { it.copy(sets = it.sets.toMutableList().also { l -> l[n] = change(l[n]) }) }
                            val k = "${draft.id}:$i:$n"
                            when (ex.type) {
                                ExType.WEIGHT, ExType.BODYWEIGHT -> {
                                    NumField(Units.weight(s.weight), "-", "$k:w", Modifier.weight(cols[1].second)) { v ->
                                        setAt { it.copy(weight = v.toDoubleOrNull()?.let(Units::fromShown)) }
                                    }
                                    NumField(s.reps?.toString() ?: "", if (s.failure) "max" else "-", "$k:r", Modifier.weight(cols[2].second), KeyboardType.Number) { v ->
                                        setAt { it.copy(reps = v.toIntOrNull()) }
                                    }
                                }
                                ExType.TIMED -> NumField(Units.time(s.seconds), "0:30", "$k:t", Modifier.weight(cols[1].second)) { v ->
                                    setAt { it.copy(seconds = Units.parseTime(v)) }
                                }
                                ExType.CARDIO -> {
                                    NumField(Units.distance(s.distance), "-", "$k:d", Modifier.weight(cols[1].second)) { v ->
                                        setAt { it.copy(distance = v.toDoubleOrNull()?.let(Units::distFromShown)) }
                                    }
                                    NumField(Units.time(s.seconds), "-", "$k:t", Modifier.weight(cols[2].second)) { v ->
                                        setAt { it.copy(seconds = Units.parseTime(v)) }
                                    }
                                }
                            }
                            Text("✕", color = Sub, modifier = Modifier.weight(0.5f).clickable {
                                updateItem(i) { it.copy(sets = it.sets.toMutableList().also { l -> l.removeAt(n) }) }
                            }.padding(6.dp))
                        }
                    }
                    Text("Tap a set number to mark it as a failure set (F).", color = Sub, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    GreyButton("+ Add Set", Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        updateItem(i) { it.copy(sets = it.sets + (it.sets.lastOrNull()?.copy() ?: PlanSet())) }
                    }
                }
                Spacer(14)
            }
            item {
                BigButton("+ Add Exercise", modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Nav.go(Route.Picker("Add Exercises", true) { ids ->
                        val rest = Store.profile.value.defaultRest
                        update { r -> r.copy(items = r.items + ids.map { RoutineItem(it, List(3) { PlanSet() }, rest) }) }
                    })
                }
            }
        }
    }
    restFor?.let { i ->
        RestPicker(draft.items.getOrNull(i)?.restSec ?: 90, { restFor = null }) { sec -> updateItem(i) { it.copy(restSec = sec) }; restFor = null }
    }
}

@Composable
fun RestPicker(current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rest Timer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                REST_CHOICES.forEach { sec ->
                    Text(
                        Units.restLabel(sec), fontSize = 18.sp,
                        color = if (sec == current) Blue else Color.White,
                        fontWeight = if (sec == current) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(sec) }.padding(vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Suppress("unused")
private val keepImports = listOf(Arrangement.Center, KeyboardOptions.Default)
