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

val MUSCLES = listOf(
    "abdominals", "abductors", "adductors", "biceps", "calves", "chest", "forearms", "glutes", "hamstrings",
    "lats", "lower back", "middle back", "neck", "quadriceps", "rotator cuff", "serratus", "grip", "shoulders", "traps", "triceps",
)
val EQUIPMENT = listOf("Barbell", "Dumbbell", "Cable", "Machine", "Kettlebell", "Bodyweight", "EZ Bar", "Bands", "Medicine Ball", "Exercise Ball", "Foam Roll", "Other")

// ---------------- Exercise list / picker ----------------

/** The searchable exercise list. With a [Route.Picker] it picks exercises; otherwise it's the library. */
@Composable
fun PickerScreen(req: Route.Picker?) {
    val all by Store.exercises.collectAsState()
    var query by remember { mutableStateOf("") }
    var equipment by remember { mutableStateOf<String?>(null) }
    var muscle by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf(0) }
    val selected = remember { mutableStateListOf<String>() }
    val sorted = remember(all) { all.values.filter { !it.hidden }.sortedBy { it.name.lowercase() } }
    val words = query.lowercase().split(" ").filter { it.isNotBlank() }
    val filtered = sorted.filter { e ->
        (equipment == null || e.equipment == equipment) &&
            (muscle == null || muscle in e.primary || muscle in e.secondary) &&
            words.all { w -> e.name.lowercase().contains(w) || e.primary.any { it.contains(w) } || e.equipment.lowercase().contains(w) }
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
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search exercise", color = Sub, fontSize = 17.sp)
                BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 17.sp), cursorBrush = SolidColor(Blue))
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

    Column(Modifier.fillMaxSize()) {
        TopBar("Exercise", action = if (e.custom) "Edit" else null) { Nav.go(Route.EditExercise(e.id)) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ExercisePicture(e, Modifier.fillMaxWidth().height(320.dp))
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
                    if (busy) return@GreyButton
                    busy = true
                    status = "Asking OpenAI for a picture – this takes up to a minute…"
                    scope.launch {
                        val r = Pictures.generate(e, Store.profile.value)
                        busy = false
                        status = if (r.ok) "Done! Saved as this exercise's picture." else r.message
                    }
                }
            }
            if (busy) CircularProgressIndicator(Modifier.padding(horizontal = 16.dp))
            status?.let { Text(it, color = Sub, modifier = Modifier.padding(horizontal = 16.dp)) }

            Text(e.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 4.dp))
            Text("Primary: " + e.primary.joinToString { titleCase(it) }, modifier = Modifier.padding(horizontal = 16.dp))
            if (e.secondary.isNotEmpty()) Text(
                "Secondary: " + e.secondary.sortedByDescending { e.secondaryWeight(it) }
                    .joinToString { "${titleCase(it)} (${(e.secondaryWeight(it) * 100).toInt()}%)" },
                color = Sub, modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text("${e.equipment} · ${e.type.label}", color = Sub, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

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

// ---------------- Create / edit your own exercise ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditExerciseScreen(id: String?) {
    val existing = id?.let { Store.exercise(it) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var equipment by remember { mutableStateOf(existing?.equipment ?: "Barbell") }
    var type by remember { mutableStateOf(existing?.type ?: ExType.WEIGHT) }
    val primary = remember { mutableStateListOf<String>().apply { addAll(existing?.primary ?: emptyList()) } }
    val secondary = remember { mutableStateListOf<String>().apply { addAll(existing?.secondary ?: emptyList()) } }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopBar(if (existing == null) "Create Exercise" else "Edit Exercise", action = "Save") {
            when {
                name.isBlank() -> error = "Give it a name."
                primary.isEmpty() -> error = "Pick at least one main muscle."
                else -> {
                    val e = Exercise(existing?.id ?: "custom_" + Store.newId(), name.trim(), equipment, type, primary.toList(), secondary.toList(), custom = true)
                    Store.saveCustomExercise(e)
                    Nav.back()
                    if (existing == null) Nav.go(Route.ExerciseDetail(e.id))
                }
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
            error?.let { Text(it, color = Red) }
            Text("Equipment", color = Sub)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EQUIPMENT.forEach { o -> FilterChip(equipment == o, { equipment = o }, { Text(o) }) }
            }
            Text("Logged as", color = Sub)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExType.entries.forEach { t -> FilterChip(type == t, { type = t }, { Text(t.label) }) }
            }
            Text("Main muscles (dark red)", color = Sub)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MUSCLES.forEach { m -> FilterChip(m in primary, { if (m in primary) primary.remove(m) else { primary.add(m); secondary.remove(m) } }, { Text(titleCase(m)) }) }
            }
            Text("Other muscles worked (light red)", color = Sub)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MUSCLES.forEach { m -> FilterChip(m in secondary, { if (m in secondary) secondary.remove(m) else { secondary.add(m); primary.remove(m) } }, { Text(titleCase(m)) }) }
            }
            BodyMapView(primary.associateWith { 1f } + secondary.associateWith { 0.4f }.filterKeys { it !in primary },
                Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black))
            Text("Add a picture from the exercise's page after saving.", color = Sub, fontSize = 13.sp)
            if (existing != null) GreyButton("Delete exercise", textColor = Red) { deleting = true }
        }
    }
    if (deleting && existing != null) ConfirmText(
        "Delete \"${existing.name}\"? Past workouts keep their numbers but will show it as missing.",
        { Store.deleteCustomExercise(existing.id); deleting = false; Nav.back(); Nav.back() }, { deleting = false }, "Delete",
    )
}
