package com.ironlog.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val PER_PAGE = 10

/** Every workout, newest first, 10 to a page, with sort and filter at the top. */
@Composable
fun AllWorkoutsScreen() {
    val workouts by Store.workouts.collectAsState()
    val routines by Store.routines.collectAsState()
    var sort by rememberSaveable { mutableStateOf("new") }
    var tests by rememberSaveable { mutableStateOf(true) }
    var fromRoutines by rememberSaveable { mutableStateOf(true) }
    var freestyle by rememberSaveable { mutableStateOf(true) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }

    // A workout counts as a routine if it was started from one, or (imported ones) it has a routine's name.
    val names = routines.map { it.name.trim().lowercase() }.toSet()
    val shown = remember(workouts, names, sort, tests, fromRoutines, freestyle) {
        workouts.filter { w ->
            when {
                w.test -> tests
                w.routineId != null || w.name.trim().lowercase() in names -> fromRoutines
                else -> freestyle
            }
        }.let { l ->
            when (sort) {
                "old" -> l.sortedBy { it.start }
                "most" -> l.sortedByDescending { Stats.volume(it) }
                "least" -> l.sortedBy { Stats.volume(it) }
                else -> l.sortedByDescending { it.start }
            }
        }
    }
    val pages = maxOf(1, (shown.size + PER_PAGE - 1) / PER_PAGE)
    val at = page.coerceIn(0, pages - 1)
    val listState = rememberLazyListState()
    LaunchedEffect(at, sort, tests, fromRoutines, freestyle) { listState.scrollToItem(0) }
    val sorts = listOf("new" to "Newest", "old" to "Oldest", "most" to "Most volume", "least" to "Least volume")
    val off = listOf(tests, fromRoutines, freestyle).count { !it }

    Column(Modifier.fillMaxSize()) {
        TopBar("All Workouts")
        Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                GreyButton("Sort: ${sorts.first { it.first == sort }.second} ▾") { sortMenu = true }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    sorts.forEach { (k, label) ->
                        DropdownMenuItem({ Text((if (k == sort) "✓  " else "     ") + label) }, { sort = k; page = 0; sortMenu = false })
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                GreyButton("Filter" + (if (off > 0) " (${3 - off} of 3)" else "") + " ▾") { filterMenu = true }
                DropdownMenu(filterMenu, { filterMenu = false }) {
                    // Ticking a box keeps the menu open so you can change several.
                    @Composable fun Tick(label: String, on: Boolean, set: (Boolean) -> Unit) = DropdownMenuItem(
                        { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(on, { set(it); page = 0 }); Text(label) } },
                        { set(!on); page = 0 },
                    )
                    Tick("1 Rep Max tests", tests) { tests = it }
                    Tick("Routines", fromRoutines) { fromRoutines = it }
                    Tick("Freestyle", freestyle) { freestyle = it }
                }
            }
        }
        Text("${shown.size} workout${if (shown.size == 1) "" else "s"}", color = Sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
            if (shown.isEmpty()) item {
                Text(if (workouts.isEmpty()) "No workouts yet." else "No workouts match the filter.", color = Sub, modifier = Modifier.padding(16.dp))
            }
            items(shown.drop(at * PER_PAGE).take(PER_PAGE), key = { it.id }) { w -> WorkoutCard(w) }
            if (pages > 1) item {
                Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PageButton("‹ Prev", at > 0) { page = at - 1 }
                    Text("Page ${at + 1} of $pages", Modifier.weight(1f), color = Sub, fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    PageButton("Next ›", at < pages - 1) { page = at + 1 }
                }
            }
        }
    }
}

@Composable
private fun PageButton(text: String, enabled: Boolean, onClick: () -> Unit) = Text(
    text, color = if (enabled) Color.White else Sub.copy(alpha = 0.4f), fontWeight = FontWeight.Medium,
    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Card).clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 10.dp),
)
