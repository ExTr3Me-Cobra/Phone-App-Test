package com.ironlog.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Before a Hevy import: pick a Loadout exercise (or a new custom one) for each Hevy exercise with no
 * direct match, and tick which Hevy workouts should also become routines.
 */
@Composable
fun HevyMatchScreen() {
    val plan = HevyImport.pending ?: run { Nav.back(); return }
    val choices = HevyImport.choices
    val picks = HevyImport.routinePicks
    var asking by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) } // still open after reading an exercise
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }
    var warn by remember { mutableStateOf<String?>(null) }
    var editAsk by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) } // custom exercise id
    val scope = rememberCoroutineScope()
    val haveRoutines = Store.routines.collectAsState().value.map { it.id }.toSet()

    Column(Modifier.fillMaxSize()) {
        val left = plan.unmatched.count { it.hevy !in choices }
        TopBar("Import from Hevy", action = if (busy) null else "Import") {
            if (left > 0) { warn = "Match every exercise first – $left still to go."; return@TopBar }
            busy = true
            scope.launch {
                done = withContext(Dispatchers.IO) {
                    runCatching {
                        val n = HevyImport.import(plan.text, choices.toMap(), picks.filterValues { it }.keys)
                        val r = Store.lastRoutines
                        "Imported $n workout${if (n == 1) "" else "s"} from Hevy (any already here were skipped)." +
                            if (r > 0) " Made $r routine${if (r == 1) "" else "s"} – find them on the Workout tab." else ""
                    }.getOrElse { "Import failed: ${it.message}" }
                }
                HevyImport.pending = null
                busy = false
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            if (plan.unmatched.isNotEmpty()) {
                item {
                    Text("Match exercises", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Only match an exercise to the exact same Loadout exercise – same movement, equipment and grip. " +
                            "A close-but-different match gives you wrong ranks, because your Hevy numbers get compared " +
                            "against the wrong lift. If there's no exact match, add it as a custom exercise.",
                        fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        "Tap each Hevy exercise and choose the Loadout exercise its sets go to, or add it as a new custom exercise " +
                            "(no picture or directions until you add them). When picking, tap ⓘ Info to read how an exercise is done.",
                        color = Sub, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                    val skipped = plan.unmatched.count { choices[it.hevy] == HevyImport.SKIP }
                    Text(
                        (if (left == 0) "✓ All ${plan.unmatched.size} done" else "${plan.unmatched.size - left} of ${plan.unmatched.size} done") +
                            if (skipped > 0) " · $skipped not imported" else "",
                        color = if (left == 0) androidx.compose.ui.graphics.Color(0xFF34C759) else Blue,
                        fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                items(plan.unmatched, key = { "u_" + it.hevy }) { u ->
                    val choice = choices[u.hevy]
                    val included = choice != HevyImport.SKIP
                    val target = choice?.takeIf { it.isNotEmpty() }?.let { Store.exercise(it) }
                    val guess = u.guess?.let { Store.exercise(it) }
                    Row(
                        Modifier.padding(vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card)
                            .clickable(enabled = included) { asking = u.hevy }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(u.hevy, fontWeight = FontWeight.Medium)
                            Text("${u.sets} set${if (u.sets == 1) "" else "s"} from Hevy", color = Sub, fontSize = 12.sp)
                            when {
                                !included -> Text("Won't be imported", color = Sub, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                                target != null -> Text("→ " + target.name + if (target.custom) " (custom)" else "", color = Blue, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                                choice == "" -> Text("→ New custom exercise", color = Blue, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                                else -> Text("Not matched yet" + (guess?.let { " · maybe ${it.name}?" } ?: ""),
                                    color = androidx.compose.ui.graphics.Color(0xFFFF9F0A), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        if (target != null && included) ExerciseThumb(target, 44.dp)
                        androidx.compose.material3.Checkbox(included, { on -> if (on) choices.remove(u.hevy) else choices[u.hevy] = HevyImport.SKIP })
                    }
                }
                item { Spacer(16) }
            } else item {
                Text("✓  Every exercise in this file matches a Loadout exercise.", color = Sub, fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp))
            }

            if (plan.routines.isNotEmpty()) {
                item {
                    Text("Create routines (optional)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Hevy's export doesn't include routines, so Loadout can rebuild them from your workout names. Each ticked one becomes a " +
                            "routine with the exercises, sets, reps and weights from the most recent workout with that name. " +
                            "Names you did at least twice are ticked to start with.",
                        color = Sub, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                        Text("Tick all", color = Blue, fontSize = 14.sp, modifier = Modifier.clickable {
                            plan.routines.forEach { if (HevyImport.routineId(it.name) !in haveRoutines) picks[it.name] = true }
                        }.padding(vertical = 6.dp))
                        Text("Untick all", color = Blue, fontSize = 14.sp, modifier = Modifier.clickable {
                            plan.routines.forEach { picks[it.name] = false }
                        }.padding(vertical = 6.dp))
                    }
                }
                items(plan.routines, key = { "r_" + it.name }) { r ->
                    val exists = HevyImport.routineId(r.name) in haveRoutines
                    val on = picks[r.name] == true && !exists
                    Row(
                        Modifier.padding(vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card)
                            .clickable(enabled = !exists) { picks[r.name] = !on }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, fontWeight = FontWeight.Medium)
                            Text(
                                if (exists) "Already in your routines" else
                                    "Done ${r.times} time${if (r.times == 1) "" else "s"} · ${r.exercises} exercise${if (r.exercises == 1) "" else "s"} · last ${r.last.take(10)}",
                                color = Sub, fontSize = 12.sp,
                            )
                        }
                        androidx.compose.material3.Checkbox(on, { picks[r.name] = it }, enabled = !exists)
                    }
                }
            }
            item { Spacer(40) }
        }
    }
    asking?.let { hevy ->
        val guess = plan.unmatched.firstOrNull { it.hevy == hevy }?.guess?.let { Store.exercise(it) }
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(hevy) },
            text = {
                Column {
                    Text("Where should these sets go?", modifier = Modifier.padding(bottom = 8.dp))
                    if (guess != null) {
                        TextButton(onClick = { choices[hevy] = guess.id; asking = null }) { Text("Use \"${guess.name}\"") }
                        TextButton(onClick = { Nav.go(Route.ExerciseDetail(guess.id)) }) { Text("ⓘ Read about \"${guess.name}\"") }
                    }
                    TextButton(onClick = {
                        asking = null
                        Nav.go(Route.Picker("Match \"$hevy\"", false) { ids -> ids.firstOrNull()?.let { choices[hevy] = it } })
                    }) { Text("Pick a Loadout exercise") }
                    TextButton(onClick = {
                        choices[hevy] = HevyImport.createCustom(hevy)
                        editAsk = choices[hevy]
                        asking = null
                    }) { Text("New custom exercise") }
                }
            },
            confirmButton = { TextButton(onClick = { asking = null }) { Text("Cancel") } },
        )
    }
    editAsk?.let { id ->
        val ex = Store.exercise(id)
        AlertDialog(
            onDismissRequest = { editAsk = null },
            title = { Text("Edit \"${ex?.name ?: "exercise"}\" now?") },
            text = { Text("It starts with no picture, directions or muscles, so it won't show on your heatmaps until you add them. " +
                "Recommended: set it up now. You can also do it later from its page in your exercises.") },
            confirmButton = { TextButton(onClick = { editAsk = null; Nav.go(Route.EditExercise(id)) }) { Text("Edit now (recommended)") } },
            dismissButton = { TextButton(onClick = { editAsk = null }) { Text("Later") } },
        )
    }
    warn?.let { msg ->
        AlertDialog(
            onDismissRequest = { warn = null },
            title = { Text("Not finished") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { warn = null }) { Text("OK") } },
        )
    }
    done?.let { msg ->
        AlertDialog(
            onDismissRequest = { done = null; Nav.back() },
            title = { Text("Import finished") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { done = null; Nav.back() }) { Text("OK") } },
        )
    }
}
