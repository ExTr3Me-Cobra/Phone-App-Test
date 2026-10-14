package com.ironlog.app

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.withContext

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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
                    Modifier.weight(1f).typing(), label = { Text("Body weight (${Units.w})") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                var age by remember { mutableStateOf(p.age?.toString() ?: "") }
                OutlinedTextField(age, { t -> age = t; set(p.copy(age = t.toIntOrNull())) },
                    Modifier.weight(1f).typing(), label = { Text("Age") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                var ft by remember { mutableStateOf(p.heightIn?.let { (it / 12).toString() } ?: "") }
                var inch by remember { mutableStateOf(p.heightIn?.let { (it % 12).toString() } ?: "") }
                fun saveH() { val f = ft.toIntOrNull(); val i = inch.toIntOrNull() ?: 0; set(p.copy(heightIn = f?.let { it * 12 + i })) }
                OutlinedTextField(ft, { ft = it; saveH() }, Modifier.weight(1f).typing(), label = { Text("Height ft") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(inch, { inch = it; saveH() }, Modifier.weight(1f).typing(), label = { Text("in") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(p.male, { set(p.copy(male = true)) }, { Text("Male") })
                FilterChip(!p.male, { set(p.copy(male = false)) }, { Text("Female") })
            }
            Text("Strength tables use body weight, sex and age; height isn't part of them, so it's kept for your records.",
                color = Sub, fontSize = 12.sp)

            Spacer(6)
            GreyButton("📚  Exercises (${Store.exercises.value.values.count { !it.hidden }})") { Nav.go(Route.Library) }

            Text("All time", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
                StatBox("Workouts", "${workouts.size}", Modifier.weight(1f))
                StatBox("Time", Units.duration(workouts.sumOf { (it.end ?: it.start) - it.start }), Modifier.weight(1f))
                StatBox("Sets", "${workouts.sumOf { it.doneSets }}", Modifier.weight(1f))
            }
            Spacer(30)
        }
    }
}

@Composable
fun SettingsScreen() {
    val p by Store.profile.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun set(np: Profile) = Store.setProfile(np)
    var message by remember { mutableStateOf<String?>(null) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            message = "Saving backup…"
            scope.launch {
                message = withContext(Dispatchers.IO) {
                    runCatching {
                        val n = context.contentResolver.openOutputStream(uri)!!.use { Store.writeBackup(it) }
                        "Backup saved (with $n of your picture${if (n == 1) "" else "s"})."
                    }.getOrElse { "Couldn't save the backup." }
                }
            }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            message = "Restoring…"
            scope.launch {
                message = withContext(Dispatchers.IO) {
                    runCatching {
                        val n = context.contentResolver.openInputStream(uri)!!.use { Store.readBackup(it) }
                        Store.lastImported?.let { "Imported $it workout${if (it == 1) "" else "s"} (any already here were skipped)." }
                            ?: ("Backup restored" + if (n > 0) " with $n picture${if (n == 1) "" else "s"}." else ".")
                    }.getOrElse { "That file isn't a Loadout backup." }
                }
            }
        }
    }

    var packMsg by remember { mutableStateOf<String?>(null) }
    val exportPics = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            packMsg = "Exporting…"
            scope.launch {
                packMsg = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        val (pics, parts) = Pictures.exportPack(context, uri)
                        if (pics == 0) "No pictures to export yet." else "Exported $pics pictures in $parts zip file${if (parts == 1) "" else "s"}."
                    }.getOrElse { "Export failed: ${it.message}" }
                }
            }
        }
    }
    val importPics = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            packMsg = "Importing…"
            scope.launch {
                packMsg = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { "Imported ${Pictures.importPack(context, uris)} pictures." }.getOrElse { "Import failed: ${it.message}" }
                }
            }
        }
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

            Text("Rest timer alert", fontWeight = FontWeight.Bold)
            val soundName = remember(p.restSound) {
                when (p.restSound) {
                    "none" -> "Silent"
                    "" -> "Phone's default alarm sound"
                    else -> runCatching {
                        android.media.RingtoneManager.getRingtone(context, android.net.Uri.parse(p.restSound))?.getTitle(context)
                    }.getOrNull() ?: "Chosen sound"
                }
            }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
                if (r.resultCode == android.app.Activity.RESULT_OK) {
                    @Suppress("DEPRECATION")
                    val uri = r.data?.getParcelableExtra<android.net.Uri>(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                    set(Store.profile.value.copy(restSound = uri?.toString() ?: "none"))
                }
            }
            GreyButton("🔔  Sound: $soundName") {
                val i = android.content.Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_ALL)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Rest timer sound")
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                if (p.restSound.isNotBlank() && p.restSound != "none") {
                    i.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, android.net.Uri.parse(p.restSound))
                }
                runCatching { picker.launch(i) }
            }
            Row(Modifier.fillMaxWidth().clickable { set(p.copy(restVibrate = !p.restVibrate)) }, verticalAlignment = Alignment.CenterVertically) {
                Text("Vibrate", Modifier.weight(1f))
                androidx.compose.material3.Switch(p.restVibrate, { set(p.copy(restVibrate = it)) })
            }
            GreyButton("Test the alert", textColor = Blue) { Rest.alert(context) }
            Text("Plays at alarm volume, so you hear it even when your phone is on vibrate.", color = Sub, fontSize = 12.sp)

            Spacer(8)
            Text("Ranks", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            listOf(
                Triple("off", "Off", "No ranks, badges, stars or rank-up pop-ups anywhere."),
                Triple("accurate", "Accurate only", "Only exercises where the published data closely fits your exact body weight, age and sex are ranked."),
                Triple("all", "All on", "Every ★ exercise is ranked. Where the data doesn't fit you exactly, it's an estimate – tap \"Who you're compared with\" on the exercise to see why."),
            ).forEach { (key, label, desc) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (p.rankMode == key) Blue.copy(alpha = 0.15f) else Card)
                        .clickable { set(p.copy(rankMode = key)) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.RadioButton(p.rankMode == key, { set(p.copy(rankMode = key)) })
                    Column(Modifier.weight(1f)) {
                        Text(label, fontWeight = FontWeight.Medium)
                        Text(desc, color = Sub, fontSize = 13.sp)
                    }
                }
            }

            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = StarGold)) { append("★") }
                    append(" ranked from data that fits your body weight, age and sex.   ")
                    withStyle(SpanStyle(color = StarEstimate)) { append("★") }
                    append(" ranked as an estimate (All on only).")
                },
                color = Sub, fontSize = 13.sp,
            )

            Spacer(8)
            Text("Backup", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Saves your routines, workouts, your own exercises, profile and every picture you've added to one .zip file. Restore it on any phone to get everything back.", color = Sub, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GreyButton("Save backup", Modifier.weight(1f)) { export.launch("loadout-backup.zip") }
                GreyButton("Restore", Modifier.weight(1f)) { import.launch(arrayOf("application/zip", "application/json", "*/*")) }
            }
            message?.let { Text(it, color = Blue) }
            val build = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode }.getOrNull()
            }
            // Tap the build number 10 times quickly to unlock the developer settings.
            var taps by remember { mutableStateOf(0) }
            var lastTap by remember { mutableStateOf(0L) }
            Text("Loadout · build ${build ?: "?"}", color = Sub, fontSize = 13.sp,
                modifier = Modifier.padding(top = 16.dp).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {
                    val now = System.currentTimeMillis()
                    taps = if (now - lastTap < 700) taps + 1 else 1
                    lastTap = now
                    if (p.devUnlocked) {
                        if (taps == 1) android.widget.Toast.makeText(context, "Developer settings are already on", android.widget.Toast.LENGTH_SHORT).show()
                    } else if (taps >= 10) {
                        taps = 0
                        set(p.copy(devUnlocked = true))
                        android.widget.Toast.makeText(context, "Developer settings unlocked", android.widget.Toast.LENGTH_SHORT).show()
                    } else if (taps >= 6) {
                        android.widget.Toast.makeText(context, "${10 - taps} more taps", android.widget.Toast.LENGTH_SHORT).show()
                    }
                })
            if (p.devUnlocked) {
                Spacer(8)
                Text("Developer settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Red)
                Row(Modifier.fillMaxWidth().clickable { set(p.copy(devAi = !p.devAi)) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("AI picture tools")
                        Text("OpenAI picture making (per exercise, per routine, every exercise) and picture-pack export/import.",
                            color = Sub, fontSize = 13.sp)
                    }
                    androidx.compose.material3.Switch(p.devAi, { set(p.copy(devAi = it)) })
                }
                if (p.devAi) {
            Spacer(8)
            Text("AI exercise pictures (OpenAI)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Paste an API key from platform.openai.com (API keys). Each picture is made once and saved on your phone; " +
                    "OpenAI charges a few cents per picture (low quality is cheapest).",
                color = Sub, fontSize = 13.sp,
            )
            var key by remember { mutableStateOf(p.apiKey) }
            OutlinedTextField(key, { key = it.trim(); set(p.copy(apiKey = key)) }, Modifier.fillMaxWidth().typing(),
                label = { Text("OpenAI API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            var model by remember { mutableStateOf(p.imageModel) }
            OutlinedTextField(model, { model = it.trim(); set(p.copy(imageModel = model.ifBlank { "gpt-image-1" })) }, Modifier.fillMaxWidth().typing(),
                label = { Text("Image model") }, singleLine = true)
            Text("Quality", color = Sub)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("low", "medium", "high").forEach { q -> FilterChip(p.imageQuality == q, { set(p.copy(imageQuality = q)) }, { Text(titleCase(q)) }) }
            }
            var allDialog by remember { mutableStateOf(false) }
            val shown = Store.exercises.value.values.filter { !it.hidden }
            GreyButton("✨ Make pictures for every exercise (${shown.size})", textColor = Blue) { allDialog = true }
            if (allDialog) GenDialog("AI pictures: every exercise", shown.sortedBy { it.name }.map { it.id }) { allDialog = false }
            GenPanel()
            Text("For one routine, use the button on that routine (or its ••• menu on the Workout tab).", color = Sub, fontSize = 13.sp)
            Text("You can also make or add a picture on any exercise's page.", color = Sub, fontSize = 13.sp)

            Spacer(8)
            Text("Picture pack", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Export saves all your exercise pictures as zip files (split into parts under 20 MB) in a folder you pick – " +
                    "share them, or upload them to the app's GitHub page to have them built into the app. " +
                    "Import adds pictures from those zip files (pick all the parts at once).",
                color = Sub, fontSize = 13.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GreyButton("Export pictures", Modifier.weight(1f)) { exportPics.launch(null) }
                GreyButton("Import pictures", Modifier.weight(1f)) { importPics.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
            }
            packMsg?.let { Text(it, color = Blue) }

                }
                GreyButton("Hide developer settings", textColor = Red) { set(p.copy(devUnlocked = false, devAi = false)) }
            }
            Spacer(30)
        }
    }
}

