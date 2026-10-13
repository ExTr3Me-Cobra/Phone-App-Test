package com.ironlog.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen() {
    val p by Store.profile.collectAsState()
    val workouts by Store.workouts.collectAsState()
    fun set(np: Profile) = Store.setProfile(np)
    Column(Modifier.fillMaxSize()) {
        TopBar("Profile", back = false, action = "Settings") { Nav.go(Route.Settings) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("About you", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Used to rank your lifts against other people your size.", color = Sub, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                var bw by remember { mutableStateOf(p.bodyweight?.let { Units.weight(it) } ?: "") }
                OutlinedTextField(bw, { t -> bw = t; set(p.copy(bodyweight = t.toDoubleOrNull()?.let(Units::fromShown))) },
                    Modifier.weight(1f), label = { Text("Body weight (${Units.w})") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                var age by remember { mutableStateOf(p.age?.toString() ?: "") }
                OutlinedTextField(age, { t -> age = t; set(p.copy(age = t.toIntOrNull())) },
                    Modifier.weight(1f), label = { Text("Age") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                var ft by remember { mutableStateOf(p.heightIn?.let { (it / 12).toString() } ?: "") }
                var inch by remember { mutableStateOf(p.heightIn?.let { (it % 12).toString() } ?: "") }
                fun saveH() { val f = ft.toIntOrNull(); val i = inch.toIntOrNull() ?: 0; set(p.copy(heightIn = f?.let { it * 12 + i })) }
                OutlinedTextField(ft, { ft = it; saveH() }, Modifier.weight(1f), label = { Text("Height ft") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(inch, { inch = it; saveH() }, Modifier.weight(1f), label = { Text("in") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(p.male, { set(p.copy(male = true)) }, { Text("Male") })
                FilterChip(!p.male, { set(p.copy(male = false)) }, { Text("Female") })
            }
            Text("Strength tables use body weight, sex and age; height isn't part of them, so it's kept for your records.",
                color = Sub, fontSize = 12.sp)

            Spacer(6)
            GreyButton("📚  Exercises (${Store.exercises.value.size})") { Nav.go(Route.Library) }

            Text("All time", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
                StatBox("Workouts", "${workouts.size}", Modifier.weight(1f))
                StatBox("Time", Units.duration(workouts.sumOf { (it.end ?: it.start) - it.start }), Modifier.weight(1f))
                StatBox("Sets", "${workouts.sumOf { it.doneSets }}", Modifier.weight(1f))
            }
            val monthAgo = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
            val recent = workouts.filter { it.start >= monthAgo }
            if (recent.isNotEmpty()) {
                val merged = Workout("m", null, "", 0, 0, recent.flatMap { it.items })
                Text("Last 30 days", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                WorkoutHeatmap(merged)
            }
            Spacer(30)
        }
    }
}

@Composable
fun SettingsScreen() {
    val p by Store.profile.collectAsState()
    val routines by Store.routines.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun set(np: Profile) = Store.setProfile(np)
    var message by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) message = runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { it.write(Store.backup().toByteArray()) }
            "Backup saved."
        }.getOrElse { "Couldn't save the backup." }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) message = runCatching {
            Store.restore(context.contentResolver.openInputStream(uri)!!.bufferedReader().readText())
            "Backup restored."
        }.getOrElse { "That file isn't an Iron Log backup." }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Settings")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Units", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!p.kg, { set(p.copy(kg = false)) }, { Text("Pounds (lbs) · miles") })
                FilterChip(p.kg, { set(p.copy(kg = true)) }, { Text("Kilograms · km") })
            }
            Text("Default rest timer for new exercises", fontWeight = FontWeight.Bold)
            var pick by remember { mutableStateOf(false) }
            GreyButton(Units.restLabel(p.defaultRest)) { pick = true }
            if (pick) RestPicker(p.defaultRest, { pick = false }) { set(p.copy(defaultRest = it)); pick = false }

            Spacer(8)
            Text("AI exercise pictures (OpenAI)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Paste an API key from platform.openai.com (API keys). Each picture is made once and saved on your phone; " +
                    "OpenAI charges a few cents per picture (low quality is cheapest).",
                color = Sub, fontSize = 13.sp,
            )
            var key by remember { mutableStateOf(p.apiKey) }
            OutlinedTextField(key, { key = it.trim(); set(p.copy(apiKey = key)) }, Modifier.fillMaxWidth(),
                label = { Text("OpenAI API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            var model by remember { mutableStateOf(p.imageModel) }
            OutlinedTextField(model, { model = it.trim(); set(p.copy(imageModel = model.ifBlank { "gpt-image-1" })) }, Modifier.fillMaxWidth(),
                label = { Text("Image model") }, singleLine = true)
            Text("Quality", color = Sub)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("low", "medium", "high").forEach { q -> FilterChip(p.imageQuality == q, { set(p.copy(imageQuality = q)) }, { Text(titleCase(q)) }) }
            }
            val missing = routines.flatMap { r -> r.items.map { it.exerciseId } }.distinct().filter { !Store.hasImage(it) }
            GreyButton(
                if (progress != null) "Making pictures ${progress!!.first}/${progress!!.second}…"
                else "✨ Make pictures for my routines' exercises (${missing.size})",
                textColor = Blue,
            ) {
                if (progress != null || missing.isEmpty()) return@GreyButton
                scope.launch {
                    var done = 0
                    progress = 0 to missing.size
                    for (id in missing) {
                        val e = Store.exercise(id) ?: continue
                        val err = Pictures.generate(e, Store.profile.value)
                        if (err != null) { message = err; break }
                        done++
                        progress = done to missing.size
                    }
                    if (done == missing.size) message = "All $done pictures made."
                    progress = null
                }
            }
            progress?.let { (d, t) -> LinearProgressIndicator(progress = { d / t.toFloat().coerceAtLeast(1f) }, modifier = Modifier.fillMaxWidth()) }
            Text("You can also make or add a picture on any exercise's page.", color = Sub, fontSize = 13.sp)

            Spacer(8)
            Text("Backup", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Saves your routines, workouts, your own exercises and profile to a file (pictures aren't included).", color = Sub, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GreyButton("Save backup", Modifier.weight(1f)) { export.launch("iron-log-backup.json") }
                GreyButton("Restore", Modifier.weight(1f)) { import.launch(arrayOf("application/json", "*/*")) }
            }
            message?.let { Text(it, color = Blue) }
            Spacer(30)
        }
    }
}

