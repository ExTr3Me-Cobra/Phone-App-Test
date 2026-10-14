package com.ironlog.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen() {
    val workouts by Store.workouts.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar("Home", back = false)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { HomeRankCard() }
            item {
                val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
                val week = workouts.filter { it.start >= weekAgo }
                Row(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
                    StatBox("This week", "${week.size} workout${if (week.size == 1) "" else "s"}", Modifier.weight(1f))
                    StatBox("Time", Units.duration(week.sumOf { (it.end ?: it.start) - it.start }), Modifier.weight(1f))
                    StatBox("Volume", "${Units.weight(week.sumOf { Stats.volume(it) })} ${Units.w}", Modifier.weight(1f))
                }
                if (workouts.isEmpty()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Welcome to Loadout 💪", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(6)
                        Text("Build a routine on the Workout tab, start it, and log your sets. Your workouts show up here.", color = Sub)
                        Spacer(12)
                        BigButton("Go to Workout") { Nav.home(1) }
                    }
                } else SectionTitle("Workouts")
            }
            items(workouts, key = { it.id }) { w -> WorkoutCard(w) }
        }
    }
}

@Composable
private fun WorkoutCard(w: Workout) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card)
            .clickable { Nav.go(Route.WorkoutDetail(w.id)) }.padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(w.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(Units.date(w.start), color = Sub, fontSize = 13.sp)
            }
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black)) {
                BodyMapView(remember(w.id) { Stats.muscleLoad(w) }, Modifier.fillMaxSize())
            }
        }
        Spacer(8)
        Row {
            StatBox("Time", Units.duration((w.end ?: w.start) - w.start), Modifier.weight(1f))
            StatBox("Volume", "${Units.weight(Stats.volume(w))} ${Units.w}", Modifier.weight(1f))
            StatBox("Sets", "${w.doneSets}", Modifier.weight(1f))
        }
        Spacer(8)
        w.items.take(4).forEach { i ->
            Text("${i.sets.size} × ${Store.exercise(i.exerciseId)?.name ?: "?"}", color = Sub, fontSize = 14.sp, maxLines = 1)
        }
        if (w.items.size > 4) Text("+ ${w.items.size - 4} more", color = Sub, fontSize = 14.sp)
    }
}

@Composable
fun WorkoutDetailScreen(id: String) {
    val w = Store.workouts.collectAsState().value.firstOrNull { it.id == id } ?: return Nav.back()
    var menu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Box {
            TopBar("Workout", action = "•••") { menu = true }
            Box(Modifier.align(Alignment.TopEnd).padding(top = 40.dp)) {
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Save as routine") }, {
                        menu = false
                        val r = Routine(Store.newId(), w.name, w.items.map { i ->
                            RoutineItem(i.exerciseId, i.sets.map { PlanSet(it.reps, it.weight, it.seconds, it.distance, it.failure) }, i.restSec)
                        })
                        Store.saveRoutine(r)
                        Nav.go(Route.RoutineView(r.id))
                    })
                    DropdownMenuItem({ Text("Delete workout", color = Red) }, { menu = false; deleting = true })
                }
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(w.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(Units.date(w.start), color = Sub)
            Spacer(10)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
                StatBox("Duration", Units.duration((w.end ?: w.start) - w.start), Modifier.weight(1f))
                StatBox("Volume", "${Units.weight(Stats.volume(w))} ${Units.w}", Modifier.weight(1f))
                StatBox("Sets", "${w.doneSets}", Modifier.weight(1f))
            }
            Spacer(10)
            WorkoutHeatmap(w)
            Spacer(10)
            w.items.forEach { item ->
                val ex = Store.exercise(item.exerciseId)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp).clickable {
                    ex?.let { Nav.go(Route.ExerciseDetail(it.id)) }
                }) {
                    ExerciseThumb(ex, 44.dp)
                    HSpacer(12)
                    Text(starredName(ex), color = Blue, fontSize = 17.sp)
                }
                item.sets.forEachIndexed { n, s ->
                    Row(Modifier.fillMaxWidth().background(if (n % 2 == 1) Card else Bg).padding(horizontal = 8.dp, vertical = 8.dp)) {
                        SetLabel(n, s.failure, Modifier.height(22.dp).padding(end = 24.dp))
                        Text(ex?.let { Units.set(it.type, s) } ?: "")
                    }
                }
            }
            Spacer(30)
        }
    }
    if (deleting) ConfirmText("Delete this workout from your history?", {
        deleting = false; Store.deleteWorkout(w.id); Nav.back()
    }, { deleting = false }, "Delete")
}
