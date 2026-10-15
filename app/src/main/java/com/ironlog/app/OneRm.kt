package com.ironlog.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * 1 Rep Max mode: test your true max on a ranked exercise. The result is saved as a one-exercise
 * workout marked as a test, and sets your rank for that exercise (later workouts only count if they beat it).
 */
object OneRm {
    class Notes(val prep: List<String>, val form: List<String>, val safety: List<String>)

    private var notes: Map<String, Notes> = emptyMap()
    fun init(c: Context) {
        notes = runCatching {
            val o = JSONObject(c.assets.open("onerm_notes.json").bufferedReader().readText())
            fun JSONArray.strings() = List(length()) { getString(it) }
            o.keys().asSequence().associateWith { k ->
                val n = o.getJSONObject(k)
                Notes(n.optJSONArray("prep")?.strings().orEmpty(), n.optJSONArray("form")?.strings().orEmpty(),
                    n.optJSONArray("safety")?.strings().orEmpty())
            }
        }.getOrDefault(emptyMap())
    }
    fun notes(id: String) = notes[id]

    /** Exercises a max test makes sense for: ranked for you (★) and scored on a max weight, max reps or a dynamometer squeeze. */
    fun canTest(id: String, p: Profile): Boolean {
        if (!Standards.covers(id, p)) return false
        val t = Store.exercise(id)?.type ?: return false
        return t == ExType.WEIGHT || t == ExType.BODYWEIGHT
    }

    // Exercises you added to the list before doing them.
    fun added(): List<String> = runCatching {
        JSONArray(Store.readFile("onerm_list.json") ?: "[]").let { a -> List(a.length()) { a.getString(it) } }
    }.getOrDefault(emptyList())
    fun add(id: String) { if (id !in added()) Store.writeFile("onerm_list.json", JSONArray(added() + id).toString()) }
    fun remove(id: String) = Store.writeFile("onerm_list.json", JSONArray(added() - id).toString())

    // The test in progress (kept while you look at other screens).
    var exerciseId: String? = null
    var startedAt = 0L
    val rows = mutableStateListOf<DoneSet>()

    /** Rounds to a loadable weight: 5 lb or 2.5 kg. */
    fun loadable(lb: Double): Double {
        val step = if (Units.kg) 2.5 else 5.0
        return Units.fromShown((Units.toShown(lb) / step).roundToInt() * step)
    }

    /** Your best so far for the warm-up plan: your rank's number, or your best from any workout. */
    fun best(id: String, workouts: List<Workout>, p: Profile): Double? =
        Ranks.all(workouts, p)[id]?.value ?: workouts.filter { it.end != null }.maxOfOrNull { w ->
            Standards.bestValue(id, w.items.filter { it.exerciseId == id }.flatMap { it.sets })
        }?.takeIf { it > 0 }

    /** Warm-up and attempt sets as (share of your best, reps, label). */
    val PLAN = listOf(
        Triple(0.40, 5, "warm-up"), Triple(0.55, 3, "warm-up"), Triple(0.70, 2, "warm-up"),
        Triple(0.80, 1, "warm-up"), Triple(0.87, 1, "warm-up"),
        Triple(0.95, 1, "attempt 1"), Triple(1.00, 1, "attempt 2"),
    )

    fun begin(id: String, best: Double?) {
        if (exerciseId == id && rows.isNotEmpty()) return
        exerciseId = id
        startedAt = System.currentTimeMillis()
        rows.clear()
        if (Standards.isFlat(id)) {
            repeat(6) { rows += DoneSet(reps = 1) } // 3 squeezes with each hand
        } else if (Standards.isReps(id)) {
            rows += DoneSet(reps = best?.let { (it * 0.35).roundToInt().coerceAtLeast(1) })
            rows += DoneSet()
        } else if (best != null) {
            PLAN.forEach { (f, r, _) -> rows += DoneSet(weight = loadable(best * f), reps = r) }
        } else rows += DoneSet(reps = 1)
    }

    fun clear() { exerciseId = null; rows.clear() }

    /** The test so far as a workout, so ticking a set shows rank-ups and rank-downs exactly like a workout does. */
    fun liveWorkout(id: String) = Workout(
        id = "onerm-$startedAt", routineId = null, name = "1RM test", start = startedAt, end = null,
        items = listOf(WorkoutItem(id, rows.toList(), 180)), bodyweight = Store.profile.value.bodyweight, test = true,
    )
}

/** The 1 Rep Max mode button on the Workout tab: darker and more serious than the others. */
@Composable
fun OneRmButton(modifier: Modifier = Modifier) {
    val gold = Color(0xFFC9A94E)
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF2A0E0E), Color(0xFF1A1A1A))))
            .border(1.5.dp, gold, RoundedCornerShape(12.dp))
            .clickable { Nav.go(Route.OneRmList) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🏆", fontSize = 26.sp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("1 REP MAX MODE", color = gold, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
            Text("Test your true max for your most accurate rank", color = Sub, fontSize = 13.sp)
        }
        Text("›", color = gold, fontSize = 26.sp)
    }
}

@Composable
fun OneRmListScreen() {
    val p by Store.profile.collectAsState()
    val workouts by Store.workouts.collectAsState()
    var refresh by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val ranks = remember(workouts, p) { Ranks.all(workouts, p) }
    val list = remember(workouts, p, refresh) {
        val counts = HashMap<String, Int>()
        workouts.filter { it.end != null }.forEach { w -> w.items.map { it.exerciseId }.distinct().forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        val ids = (counts.keys + OneRm.added()).filter { OneRm.canTest(it, p) && Store.exercise(it) != null }.distinct()
        ids.sortedWith(compareByDescending<String> { counts[it] ?: 0 }.thenBy { Store.exercise(it)?.name }).map { it to (counts[it] ?: 0) }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("1 Rep Max")
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            item {
                Text("Pick an exercise to test your true one-rep max (or max reps for bodyweight moves). " +
                    "Your result sets your rank for it – later workouts only change it if they beat it.", color = Sub, fontSize = 14.sp)
                if (Standards.mode(p) == Standards.Mode.OFF) Text("Ranks are turned off in Settings, so there's nothing to rank.",
                    color = Red, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                Spacer(10)
            }
            items(list, key = { it.first }) { (id, n) ->
                val e = Store.exercise(id)!!
                val r = ranks[id]
                Row(
                    Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card)
                        .clickable { Nav.go(Route.OneRmTest(id)) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExerciseThumb(e, 48.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(starredName(e), fontWeight = FontWeight.Medium)
                        Text((if (n == 0) "Not done yet" else "Done $n time${if (n == 1) "" else "s"}") +
                            (r?.testedAt?.let { " · tested" } ?: ""), color = Sub, fontSize = 12.sp)
                    }
                    if (r != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RankBadge(r.tier, 34.dp)
                        Text(r.tier.label, fontSize = 11.sp, color = Color(r.tier.color))
                    }
                    if (n == 0) Text("✕", color = Sub, modifier = Modifier.clickable { OneRm.remove(id); refresh++ }.padding(8.dp))
                }
            }
            item {
                if (list.isEmpty()) Text("No ranked exercises done yet. Add one below, or log a workout first.", color = Sub,
                    modifier = Modifier.padding(vertical = 8.dp))
                GreyButton("+ Add an exercise", Modifier.fillMaxWidth().padding(top = 8.dp), Blue) {
                    Nav.go(Route.Picker("Add to 1 Rep Max", false, only = { OneRm.canTest(it.id, Store.profile.value) }) { ids ->
                        val id = ids.firstOrNull() ?: return@Picker
                        if (OneRm.canTest(id, Store.profile.value)) { OneRm.add(id); refresh++ }
                        else message = "${Store.exercise(id)?.name} can't be ranked for you, so there's nothing to test it against. Pick one with a ★."
                    })
                }
                message?.let { Text(it, color = Red, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }
}

@Composable
fun OneRmTestScreen(id: String) {
    val p by Store.profile.collectAsState()
    val workouts by Store.workouts.collectAsState()
    val e = Store.exercise(id) ?: run { Nav.back(); return }
    val context = LocalContext.current
    val reps = Standards.isReps(id)
    val squeeze = Standards.isFlat(id)
    val best = remember(id) { OneRm.best(id, workouts, p) }
    remember(id) { OneRm.begin(id, best); 0 }
    val r = remember(workouts, p) { Ranks.all(workouts, p)[id] }
    val notes = OneRm.notes(id)
    var confirmLeave by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    val gold = Color(0xFFC9A94E)
    fun fmt(v: Double) = if (reps) "${v.roundToInt()} reps" else Units.weightU(v)

    androidx.activity.compose.BackHandler { confirmLeave = true }
    Column(Modifier.fillMaxSize()) {
        TopBar("1RM: ${e.name}", action = "Finish") { finishTest(context, id)?.let { error = it } }
        Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseThumb(e, 64.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(starredName(e), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(if (r != null) "Now: ${r.tier.label} (${topLabel(r.percentile)}) · ${fmt(r.value)}" else "Not ranked yet", color = Sub, fontSize = 13.sp)
                    Text(e.weightHint(), color = Sub, fontSize = 12.sp)
                }
            }

            // Warm-up plan
            Section("Warm-up plan", Blue) {
                Bullet("5–10 minutes of easy cardio (bike, rower, brisk walk) until you're warm and lightly sweating.")
                notes?.prep?.forEach { Bullet(it) }
                if (squeeze) {
                    Bullet("Open and close your hands 10–15 times and shake them out.")
                    Bullet("One practice squeeze with each hand at about half effort.")
                    Bullet("Then 3 all-out squeezes with each hand, alternating hands, resting about a minute before using the same hand again.")
                } else if (reps) {
                    Bullet(best?.let { "One easy set of about ${(it * 0.35).roundToInt().coerceAtLeast(1)} reps (a third of your best), then rest 3–5 minutes." }
                        ?: "One easy set of a few reps, well short of failure, then rest 3–5 minutes.")
                    Bullet("Then one all-out set: strict reps only, stop at the first rep you can't do with good form.")
                } else if (best != null) {
                    Text("Based on your current best of about ${fmt(best)}:", color = Sub, fontSize = 13.sp)
                    if (e.equipment == "Barbell") Bullet("Empty bar × 8–10")
                    OneRm.PLAN.forEach { (f, n, label) ->
                        Bullet("${Units.weightU(OneRm.loadable(best * f))} × $n  ($label, ${(f * 100).roundToInt()}%)")
                    }
                    Bullet("Attempt 3 (optional): about ${Units.weightU(OneRm.loadable(best * 1.04))} – only if attempt 2 moved fast and clean.")
                    Bullet("Rest 1–2 minutes between early warm-ups and 3–5 minutes before each heavy single.")
                } else {
                    Bullet("No previous numbers yet: work up in small jumps – 5 reps light, 3 reps moderate, then singles – stopping when the bar slows a lot.")
                    Bullet("Rest 3–5 minutes before each heavy single.")
                }
            }
            if (squeeze) Section("Form", Green) {
                Bullet("Sit upright with your feet flat, upper arm against your side and elbow bent to 90°; wrist straight.")
                Bullet("Set the handle so the middle of your fingers wraps it comfortably.")
                Bullet("Squeeze as hard as you can for about 3 seconds – steady, no jerking or swinging the arm.")
                Bullet("Read the peak value and enter it. Your single best squeeze from either hand counts.")
            }
            notes?.form?.takeIf { it.isNotEmpty() }?.let { f -> Section("Form", Green) { f.forEach { Bullet(it) } } }
            Section("Safety", Red) {
                notes?.safety?.forEach { Bullet(it) }
                Bullet("Stop straight away for sharp or joint pain, dizziness, or if your form breaks down – a missed or ugly rep doesn't count.")
                Bullet("Don't test when you're injured, ill, or very tired. Leave a few days between max tests on the same lift.")
            }

            // Attempts
            Text(if (squeeze) "Your squeezes" else if (reps) "Your sets" else "Your sets & attempts", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(if (squeeze) "Enter each reading and tick it. Your best squeeze counts." else if (reps) "Tick a set when it's done. Your best set counts." else
                "Tick each set when it's done. Your heaviest completed single counts; if you don't do a single, your best set is converted to a 1-rep max estimate.",
                color = Sub, fontSize = 13.sp)
            Row {
                Text("SET", Modifier.weight(0.6f), color = Sub, fontSize = 12.sp)
                if (!reps) Text(if (squeeze) "SQUEEZE (${Units.w})" else Units.w.uppercase(), Modifier.weight(1.4f), color = Sub, fontSize = 12.sp)
                Text(if (squeeze) "HAND" else "REPS", Modifier.weight(1.2f), color = Sub, fontSize = 12.sp)
                Text("", Modifier.weight(1.2f))
            }
            OneRm.rows.forEachIndexed { i, s ->
                val k = "$id:$i:$refresh"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", Modifier.weight(0.6f), color = Sub)
                    if (!reps) NumField(Units.weight(s.weight), "0", "$k:w", Modifier.weight(1.4f).padding(end = 6.dp), outlined = true) { v ->
                        OneRm.rows[i] = OneRm.rows[i].copy(weight = v.toDoubleOrNull()?.let(Units::fromShown))
                    }
                    if (squeeze) Text(if (i % 2 == 0) "Right" else "Left", Modifier.weight(1.2f), color = Sub)
                    else NumField(s.reps?.toString() ?: "", "0", "$k:r", Modifier.weight(1.2f).padding(end = 6.dp), KeyboardType.Number, outlined = true) { v ->
                        OneRm.rows[i] = OneRm.rows[i].copy(reps = v.toIntOrNull())
                    }
                    Row(Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (s.done) Green else Card2)
                                .clickable {
                                    OneRm.rows[i] = OneRm.rows[i].copy(done = !s.done)
                                    val live = OneRm.liveWorkout(id)
                                    if (!s.done) Ranks.onSetDone(live, id) else Ranks.onSetUndone(context, live, id)
                                }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("✓", fontWeight = FontWeight.Bold) }
                        Text("✕", color = Sub, modifier = Modifier.clickable { OneRm.rows.removeAt(i); refresh++ }.padding(8.dp))
                    }
                }
            }
            GreyButton("+ Add set", textColor = Blue) {
                val last = OneRm.rows.lastOrNull()
                OneRm.rows += DoneSet(weight = last?.weight, reps = if (reps) null else 1)
                refresh++
            }
            error?.let { Text(it, color = Red) }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A0E0E)).border(1.5.dp, gold, RoundedCornerShape(12.dp))
                    .clickable { finishTest(context, id)?.let { error = it } }.padding(16.dp),
                horizontalArrangement = Arrangement.Center,
            ) { Text("FINISH TEST", color = gold, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, letterSpacing = 1.5.sp) }
            Spacer(300)
        }
    }
    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text("Leave this test?") },
        text = { Text("Your sets here won't be saved.") },
        confirmButton = { TextButton(onClick = { confirmLeave = false; OneRm.clear(); Nav.back() }) { Text("Leave", color = Red) } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Keep testing") } },
    )
}

@Composable
private fun Section(title: String, color: Color, content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(Modifier.size(width = 4.dp, height = 18.dp).background(color))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
    }
    content()
}

@Composable
private fun Bullet(text: String) = Row {
    Text("•", color = Sub, modifier = Modifier.width(14.dp))
    Text(text, fontSize = 14.sp, lineHeight = 19.sp)
}

/** Saves the test as a one-exercise workout, shows your rank (changed or not), then the chart. Returns an error message, or null. */
private fun finishTest(c: Context, id: String): String? {
    val done = OneRm.rows.filter { it.done }
    val e = Store.exercise(id) ?: return "Exercise not found."
    if (done.isEmpty()) return "Tick at least one completed set first."
    val value = Standards.testValue(id, done)
    if (value <= 0) return if (Standards.isFlat(id)) "Enter your squeeze reading." else if (Standards.isReps(id)) "Enter the reps you did." else "Enter the weight and reps you lifted."
    val p = Store.profile.value
    val w = Workout(
        id = Store.newId(), routineId = null, name = "1RM test: ${e.name}", start = OneRm.startedAt, end = System.currentTimeMillis(),
        items = listOf(WorkoutItem(id, done, 180)), bodyweight = p.bodyweight, test = true,
    )
    Store.saveWorkout(w)
    // Rank changes already popped up as each set was ticked; finishing just saves and shows the chart.
    OneRm.clear()
    Nav.back()
    Nav.go(Route.RankDetail(id))
    return null
}
