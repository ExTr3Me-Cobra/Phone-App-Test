package com.ironlog.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

val MUSCLES = Muscles.groups.keys.sorted()
val EQUIPMENT = listOf("Barbell", "Dumbbell", "Cable", "Machine", "Kettlebell", "Bodyweight", "EZ Bar", "Bands", "Medicine Ball", "Exercise Ball", "Foam Roll", "Other")

// ---------------- Exercise list / picker ----------------

/** The searchable exercise list. With a [Route.Picker] it picks exercises; otherwise it's the library. */
@Composable
fun PickerScreen(req: Route.Picker?) {
    val all by Store.exercises.collectAsState()
    // Saveable, so the search is still there when you come back from an exercise.
    var query by rememberSaveable { mutableStateOf("") }
    var equipment by rememberSaveable { mutableStateOf<String?>(null) }
    var muscle by rememberSaveable { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf(0) }
    val selected = rememberSaveable(saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })) {
        mutableStateListOf<String>()
    }
    val sorted = remember(all) { all.values.filter { !it.hidden }.sortedBy { it.name.lowercase() } }
    val words = query.lowercase().split(" ").filter { it.isNotBlank() }
    val filtered = sorted.filter { e ->
        (equipment == null || e.equipment == equipment) &&
            (muscle == null || muscle in e.primary || muscle in e.secondary) &&
            words.all { w ->
                e.name.lowercase().contains(w) || e.primary.any { it.contains(w) } || e.equipment.lowercase().contains(w) ||
                    e.muscles.any { it.value >= 0.7f && it.key.lowercase().contains(w) }
            }
    }
    val browsing = words.isEmpty() && equipment == null && muscle == null
    val recent = if (browsing) Store.recentExerciseIds().mapNotNull { all[it] } else emptyList()

    fun tap(e: Exercise) {
        when {
            req == null -> Nav.go(Route.ExerciseDetail(e.id))
            req.multi -> if (e.id in selected) selected.remove(e.id) else selected.add(e.id)
            else -> { Nav.back(); req.onPick(listOf(e.id)) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(req?.title ?: "Exercises", action = "Create") { Nav.go(Route.EditExercise(null)) }
        Row(
            Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔍", fontSize = 16.sp)
            HSpacer(10)
            Box(Modifier.weight(1f).typing()) {
                if (query.isEmpty()) Text("Search exercise", color = Sub, fontSize = 17.sp)
                BasicTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 17.sp), cursorBrush = SolidColor(Blue))
            }
            if (query.isNotEmpty()) Text("✕", color = Sub, modifier = Modifier.clickable { query = "" }.padding(start = 8.dp))
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GreyButton(equipment ?: "All Equipment", Modifier.weight(1f), if (equipment != null) Blue else Color.White) { dialog = 1 }
            GreyButton(muscle?.let(::titleCase) ?: "All Muscles", Modifier.weight(1f), if (muscle != null) Blue else Color.White) { dialog = 2 }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (recent.isNotEmpty()) {
                item { SectionTitle("Recent Exercises", Modifier.padding(top = 8.dp)) }
                items(recent, key = { "r_" + it.id }) { e -> ExerciseRow(e, e.id in selected, req != null) { tap(e) } }
            }
            item { SectionTitle(if (browsing) "All Exercises (${filtered.size})" else "${filtered.size} found", Modifier.padding(top = 8.dp)) }
            items(filtered, key = { it.id }) { e -> ExerciseRow(e, e.id in selected, req != null) { tap(e) } }
        }
        if (req != null && req.multi && selected.isNotEmpty()) {
            BigButton(
                "Add ${selected.size} exercise${if (selected.size == 1) "" else "s"}",
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            ) {
                val ids = selected.toList()
                Nav.back()
                req.onPick(ids)
            }
        }
    }
    if (dialog != 0) {
        val opts = if (dialog == 1) EQUIPMENT else MUSCLES
        AlertDialog(
            onDismissRequest = { dialog = 0 },
            title = { Text(if (dialog == 1) "Equipment" else "Muscle") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(if (dialog == 1) "All Equipment" else "All Muscles", fontSize = 18.sp, modifier = Modifier.fillMaxWidth().clickable {
                        if (dialog == 1) equipment = null else muscle = null; dialog = 0
                    }.padding(vertical = 10.dp))
                    opts.forEach { o ->
                        Text(titleCase(o), fontSize = 18.sp, modifier = Modifier.fillMaxWidth().clickable {
                            if (dialog == 1) equipment = o else muscle = o; dialog = 0
                        }.padding(vertical = 10.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = 0 }) { Text("Close") } },
        )
    }
}

@Composable
fun ExerciseRow(e: Exercise, selected: Boolean, picking: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).background(if (selected) Blue.copy(alpha = 0.12f) else Bg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(80.dp).background(if (selected) Blue else Color.Transparent))
        Row(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ExerciseThumb(e, 60.dp)
            HSpacer(16)
            Column(Modifier.weight(1f)) {
                Text(e.name, fontSize = 17.sp, maxLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.mainMuscle, color = Sub, fontSize = 15.sp)
                    if (e.custom) { HSpacer(8); Chip("Custom") }
                }
            }
            Text("›", color = Sub, fontSize = 26.sp, modifier = Modifier.clickable { Nav.go(Route.ExerciseDetail(e.id)) }.padding(horizontal = 8.dp))
        }
    }
    HorizontalDivider(color = Card, modifier = Modifier.padding(start = 92.dp))
}

// ---------------- Exercise page ----------------

@Composable
fun ExerciseDetailScreen(id: String) {
    val all by Store.exercises.collectAsState()
    val e = all[id] ?: return Nav.back()
    val workouts by Store.workouts.collectAsState()
    val profile by Store.profile.collectAsState()
    val version by Store.imageVersion.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var askAi by remember { mutableStateOf(false) }
    val hasImage = remember(version) { Store.hasImage(e.id) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) runCatching { Pictures.import(context, e.id, uri) }.onFailure { status = "Couldn't use that picture." }
    }
    val camFile = remember { File(context.cacheDir, "cam").apply { mkdirs() }.let { File(it, "shot.jpg") } }
    val camUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.files", camFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok || camFile.sizeOk()) runCatching { Pictures.import(context, e.id, camUri) }.onFailure { status = "Couldn't use that picture." }
    }

    val sessions = remember(workouts, id) { Stats.sessions(id, workouts) }
    val metrics = Stats.metricsFor(e.type)
    var metric by remember(id) { mutableStateOf(metrics.first()) }

    var topMenu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Box {
            TopBar("Exercise", action = "⋮") { topMenu = true }
            Box(Modifier.align(Alignment.TopEnd).padding(top = 48.dp)) {
                DropdownMenu(topMenu, { topMenu = false }) {
                    DropdownMenuItem({ Text("Edit name, muscles & directions") }, { topMenu = false; Nav.go(Route.EditExercise(e.id)) })
                    if (e.edited) DropdownMenuItem({ Text("Reset to original") }, { topMenu = false; Store.resetExercise(e.id) })
                    if (e.custom) DropdownMenuItem({ Text("Delete exercise", color = Red) }, { topMenu = false; deleting = true })
                }
            }
        }
        if (deleting) ConfirmText("Delete \"${e.name}\"? Past workouts keep their numbers.", {
            deleting = false; Store.deleteCustomExercise(e.id); Nav.back()
        }, { deleting = false }, "Delete")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ExercisePicture(e, Modifier.fillMaxWidth())
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    GreyButton("📷 Your picture") { menu = true }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Choose from gallery") }, {
                            menu = false; gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        DropdownMenuItem({ Text("Take a photo") }, { menu = false; camFile.delete(); camera.launch(camUri) })
                        if (hasImage) DropdownMenuItem({ Text("Remove picture", color = Red) }, { menu = false; Pictures.remove(e.id) })
                    }
                }
                GreyButton(if (busy) "Generating…" else "✨ AI picture", Modifier.weight(1f), Blue) {
                    if (!busy) askAi = true
                }
            }
            if (askAi) {
                val p = Store.profile.value
                AlertDialog(
                    onDismissRequest = { askAi = false },
                    title = { Text("Make an AI picture?") },
                    text = {
                        Text(
                            if (p.apiKey.isBlank()) "First add your OpenAI API key in Profile → Settings."
                            else "One picture of \"${e.name}\" at ${p.imageQuality} quality costs about $" +
                                "%.3f".format(Pictures.price(p.imageQuality)) + ", taken from your OpenAI API credit." +
                                if (hasImage) "\n\nIt replaces this exercise's current picture." else "",
                        )
                    },
                    confirmButton = {
                        if (p.apiKey.isNotBlank()) TextButton(onClick = {
                            askAi = false
                            busy = true
                            status = "Asking OpenAI for a picture – this takes up to a minute…"
                            scope.launch {
                                val r = Pictures.generate(e, Store.profile.value)
                                busy = false
                                status = if (r.ok) "Done! Saved as this exercise's picture." else r.message
                            }
                        }) { Text("Yes, make it") }
                    },
                    dismissButton = { TextButton(onClick = { askAi = false }) { Text("Cancel") } },
                )
            }
            if (busy) CircularProgressIndicator(Modifier.padding(horizontal = 16.dp))
            status?.let { Text(it, color = Sub, modifier = Modifier.padding(horizontal = 16.dp)) }

            Text(e.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 4.dp))
            Text("Primary: " + e.primary.joinToString { titleCase(it) }, modifier = Modifier.padding(horizontal = 16.dp))
            Text("${e.equipment} · ${e.type.label}", color = Sub, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            MusclesCard(e)

            // Ranking
            if (Standards.covers(e.id)) {
                val best = sessions.maxOfOrNull { Standards.bestValue(e.id, it.sets) } ?: 0.0
                RankCard(e, best, profile)
            }

            // Records
            if (sessions.isNotEmpty()) {
                SectionTitle("Personal Records")
                metrics.forEach { m ->
                    val best = sessions.mapNotNull { Stats.value(m, it.sets) }.maxOrNull()
                    if (best != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(m.label, Modifier.weight(1f))
                        Text(Units.metric(m, best), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Progress chart
            SectionTitle("Progress")
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                metrics.forEach { m -> FilterChip(metric == m, { metric = m }, { Text(m.label, fontSize = 12.sp) }) }
            }
            val pts = sessions.mapNotNull { s -> Stats.value(metric, s.sets)?.let { s.workout.start to it } }
            LineChart(pts) { Units.metric(metric, it) }

            // History
            if (sessions.isNotEmpty()) {
                SectionTitle("History")
                sessions.reversed().take(30).forEach { s ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(10.dp)).background(Card).padding(12.dp)) {
                        Text(Units.date(s.workout.start), fontWeight = FontWeight.Medium)
                        s.sets.forEachIndexed { i, set ->
                            Text("${if (set.failure) "F" else "${i + 1}"}   ${Units.set(e.type, set)}", color = if (set.failure) Red else Sub)
                        }
                    }
                }
            }

            if (e.instructions.isNotEmpty()) {
                SectionTitle("How to do it")
                e.instructions.forEachIndexed { i, s -> Text("${i + 1}. $s", modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) }
            }
            Spacer(40)
        }
    }
}

/** Every muscle the exercise works, with how hard compared with the main muscle, and the research behind it. */
@Composable
fun MusclesCard(e: Exercise) {
    var sources by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp, 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(14.dp)) {
        Text("Muscles worked", fontWeight = FontWeight.Bold)
        e.muscles.entries.sortedByDescending { it.value }.forEach { (m, v) ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Muscles.label(m), Modifier.weight(1f), fontSize = 14.sp)
                Box(Modifier.width(70.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Card2)) {
                    Box(Modifier.fillMaxWidth(v.coerceIn(0f, 1f)).fillMaxHeight().background(Color(BodyMap.heat(v))))
                }
                Text("${(v * 100).roundToInt()}%", Modifier.width(48.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End, fontSize = 14.sp)
            }
        }
        Text(
            if (e.edited || e.custom) "Your numbers." else "Estimates from EMG and training studies of this kind of movement, compared with the main muscle (100%).",
            color = Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
        )
        if (e.refs.isNotEmpty() && !e.edited && !e.custom) {
            Text(if (sources) "Hide sources" else "Sources (${e.refs.size})", color = Blue, fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp).clickable { sources = !sources })
            if (sources) e.refs.forEach { r ->
                Text("• " + (Muscles.refs[r] ?: r), color = Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun RankCard(e: Exercise, best: Double, profile: Profile) {
    Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
        Text("How you compare", fontWeight = FontWeight.Bold)
        val rank = Standards.rank(e.id, best, profile)
        when {
            best <= 0 -> Text("Log this exercise to see where you rank.", color = Sub)
            rank == null -> Text("Add your body weight in Profile to see where you rank.", color = Sub)
            else -> {
                Text("Stronger than about ${rank.percentile}% of ${rank.against}", fontSize = 17.sp)
                Text(
                    "${rank.level} · based on your best " + if (Standards.isReps(e.id)) "${best.toInt()} reps" else "est. 1-rep max of ${Units.weightU(best)}",
                    color = Sub, fontSize = 14.sp,
                )
                Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Card2)) {
                    Box(Modifier.fillMaxWidth(rank.percentile / 100f).fillMaxHeight().background(Blue))
                }
            }
        }
    }
}

// ---------------- Create / edit an exercise ----------------

private class MuscleRow(val muscle: String, main: Boolean, pct: Int) {
    var main by mutableStateOf(main)
    var pct by mutableStateOf(pct)
}

/** Edit any exercise (built-in ones are saved as your version and can be reset), or create one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditExerciseScreen(id: String?) {
    val existing = id?.let { Store.exercise(it) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var equipment by remember { mutableStateOf(existing?.equipment ?: "Barbell") }
    var type by remember { mutableStateOf(existing?.type ?: ExType.WEIGHT) }
    var directions by remember { mutableStateOf(existing?.instructions?.joinToString("\n") ?: "") }
    val rows = remember {
        mutableStateListOf<MuscleRow>().apply {
            existing?.muscles?.entries?.sortedByDescending { it.value }?.forEach { (m, v) ->
                val main = v >= 0.999f && Muscles.group(m) in existing.primary
                add(MuscleRow(m, main, (v * 100).roundToInt().coerceIn(5, 100)))
            }
        }
    }
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val levels = rows.associate { it.muscle to if (it.main) 1f else it.pct / 100f }

    Column(Modifier.fillMaxSize()) {
        TopBar(if (existing == null) "Create Exercise" else "Edit Exercise", action = "Save") {
            when {
                name.isBlank() -> error = "Give it a name."
                rows.none { it.main } -> error = "Mark at least one muscle as Main."
                else -> {
                    // Groups (for the list filters and the line under the name) follow from the muscles.
                    val primary = rows.filter { it.main }.map { Muscles.group(it.muscle) }.distinct()
                    val sec = levels.entries.filter { Muscles.group(it.key) !in primary }
                        .groupBy { Muscles.group(it.key) }.mapValues { (_, v) -> v.maxOf { it.value } }
                        .entries.sortedByDescending { it.value }
                    val e = (existing ?: Exercise("custom_" + Store.newId(), "", equipment, type, emptyList(), emptyList(), custom = true)).copy(
                        name = name.trim(), equipment = equipment, type = type,
                        primary = primary,
                        secondary = sec.map { it.key },
                        secondaryWeights = sec.map { it.value },
                        detail = levels,
                        instructions = directions.lines().map { it.trim() }.filter { it.isNotEmpty() },
                    )
                    Store.saveExercise(e)
                    Nav.back()
                    if (existing == null) Nav.go(Route.ExerciseDetail(e.id))
                }
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().typing(), label = { Text("Name") }, singleLine = true)
            error?.let { Text(it, color = Red) }

            // Live preview: changes as you move the sliders.
            BodyMapView(levels, Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black))

            Text("Muscles worked", fontWeight = FontWeight.Bold)
            Text("Main muscles count 100%. For the others, set how hard they work compared with the main muscle. " +
                "The main muscles' groups show under the exercise name and in the list filters.",
                color = Sub, fontSize = 13.sp)
            rows.forEach { r ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Muscles.label(r.muscle), Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        FilterChip(r.main, { r.main = !r.main; if (!r.main && r.pct >= 100) r.pct = 50 }, { Text("Main") })
                        HSpacer(8)
                        Text(if (r.main) "100%" else "${r.pct}%", modifier = Modifier.width(48.dp))
                        Text("✕", color = Sub, modifier = Modifier.clickable { rows.remove(r) }.padding(6.dp))
                    }
                    if (!r.main) androidx.compose.material3.Slider(
                        value = r.pct.toFloat(), onValueChange = { r.pct = (it / 5).roundToInt() * 5 },
                        valueRange = 5f..100f, steps = 18,
                    )
                }
            }
            GreyButton("+ Add a muscle", textColor = Blue) { adding = true }

            Text("Directions (one step per line)", fontWeight = FontWeight.Bold)
            OutlinedTextField(directions, { directions = it }, Modifier.fillMaxWidth().typing().heightIn(min = 120.dp))

            Text("Equipment", color = Sub)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EQUIPMENT.forEach { o -> FilterChip(equipment == o, { equipment = o }, { Text(o) }) }
            }
            Text("Logged as", color = Sub)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExType.entries.forEach { t -> FilterChip(type == t, { type = t }, { Text(t.label) }) }
            }
            if (existing?.edited == true) GreyButton("Reset to original", textColor = Red) { Store.resetExercise(existing.id); Nav.back() }
            Spacer(30)
        }
    }
    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add a muscle") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Muscles.groups.forEach { (g, ms) ->
                        val options = ms.filter { m -> rows.none { it.muscle == m } }
                        if (options.isEmpty()) return@forEach
                        Text(titleCase(g), color = Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                        options.forEach { m ->
                            Text(Muscles.label(m), fontSize = 17.sp, modifier = Modifier.fillMaxWidth().clickable {
                                rows.add(MuscleRow(m, rows.none { it.main }, if (rows.none { it.main }) 100 else 30)); adding = false
                            }.padding(vertical = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { adding = false }) { Text("Close") } },
        )
    }
}
