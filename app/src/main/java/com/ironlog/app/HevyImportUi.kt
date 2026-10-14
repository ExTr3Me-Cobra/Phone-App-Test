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

/** Before a Hevy import: pick a Loadout exercise (or a new custom one) for each Hevy exercise with no direct match. */
@Composable
fun HevyMatchScreen() {
    val plan = HevyImport.pending ?: run { Nav.back(); return }
    val choices = HevyImport.choices
    var asking by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        TopBar("Match exercises", action = if (busy) null else "Import") {
            busy = true
            scope.launch {
                done = withContext(Dispatchers.IO) {
                    runCatching {
                        val n = HevyImport.import(plan.text, choices.toMap())
                        "Imported $n workout${if (n == 1) "" else "s"} from Hevy (any already here were skipped)."
                    }.getOrElse { "Import failed: ${it.message}" }
                }
                HevyImport.pending = null
                busy = false
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            item {
                Text(
                    "These Hevy exercises have no direct match in Loadout. Tap one to choose which Loadout exercise its sets go to, " +
                        "or keep it as a new custom exercise. Your choices are remembered for next time.",
                    color = Sub, fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            items(plan.unmatched, key = { it.hevy }) { u ->
                val target = choices[u.hevy].orEmpty().takeIf { it.isNotEmpty() }?.let { Store.exercise(it) }
                Row(
                    Modifier.padding(vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card)
                        .clickable { asking = u.hevy }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(u.hevy, fontWeight = FontWeight.Medium)
                        Text("${u.sets} set${if (u.sets == 1) "" else "s"} from Hevy", color = Sub, fontSize = 12.sp)
                        Text(
                            "→ " + (target?.name ?: "New custom exercise \"${u.hevy}\""),
                            color = if (target != null) Blue else Sub, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (target != null) ExerciseThumb(target, 44.dp)
                    Text("›", color = Sub, fontSize = 24.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    asking?.let { hevy ->
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(hevy) },
            text = { Text("Where should these sets go?") },
            confirmButton = {
                TextButton(onClick = {
                    asking = null
                    Nav.go(Route.Picker("Match \"$hevy\"", false) { ids -> ids.firstOrNull()?.let { choices[hevy] = it } })
                }) { Text("Pick a Loadout exercise") }
            },
            dismissButton = { TextButton(onClick = { choices[hevy] = ""; asking = null }) { Text("New custom exercise") } },
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
