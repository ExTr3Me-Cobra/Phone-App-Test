package com.clockout.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.load(this)
        Reminder.ensureChannel(this)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)) {
                Surface(Modifier.fillMaxSize()) { Screen() }
            }
        }
    }
}

private val MEANINGS = mapOf(
    "BT" to "Start of day", "MV" to "Job change", "OL" to "Out to lunch", "IL" to "In from lunch",
    "ET" to "End of day", "OB" to "Out on break", "IB" to "In from break",
)

@Composable
private fun Screen() {
    val context = LocalContext.current
    val rings by Store.rings.collectAsState()
    val s by Store.settings.collectAsState()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }
    var reading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var rawText by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Ring?>(null) }
    var adding by remember { mutableStateOf(false) }

    fun readImage(uri: Uri) {
        reading = true
        message = null
        val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrElse {
            reading = false
            message = "Couldn't open that picture."
            return
        }
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
            .addOnSuccessListener { text ->
                reading = false
                rawText = text.text
                val found = RingReader.read(text)
                if (found.isEmpty()) {
                    message = "No clock rings found. Try again closer, straight on, with the whole table in the picture – or add them by hand."
                } else {
                    Store.setRings(context, found)
                    val unknown = found.count { it.code == "?" }
                    message = "Read ${found.size} ring${if (found.size == 1) "" else "s"}." +
                        (if (unknown > 0) " $unknown without a code – tap to fix." else "") + " Check they match your screen."
                }
            }
            .addOnFailureListener {
                reading = false
                message = "Couldn't read the picture (${it.message})."
            }
    }

    val photoFile = remember { File(context.cacheDir, "photos").apply { mkdirs() }.let { File(it, "rings.jpg") } }
    val photoUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.files", photoFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) readImage(photoUri) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) readImage(uri) }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(s.remind) {
        if (s.remind && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Clock Out", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        ResultCard(rings, s, now)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { camera.launch(photoUri) }, enabled = !reading, modifier = Modifier.weight(1f)) { Text("📷  Scan clock rings") }
            OutlinedButton(onClick = {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, enabled = !reading) { Text("From photos") }
        }
        if (reading) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Reading…")
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        // The rings, oldest first.
        Text("Today's rings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (rings.isEmpty()) {
            Text("Take a picture of your clock rings, or add them by hand.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        rings.forEachIndexed { i, r ->
            val next = rings.getOrNull(i + 1)?.time
            val off = Calc.isOff(r.code, s)
            Card(
                Modifier.fillMaxWidth().clickable { editing = r },
                colors = CardDefaults.cardColors(
                    containerColor = if (off) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.code, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.width(48.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Fmt.time(r.time), fontSize = 18.sp)
                        Text(
                            (MEANINGS[r.code.uppercase()] ?: if (r.code == "?") "Unknown code – tap to fix" else "Code ${r.code}") +
                                if (off) " · off the clock" else " · on the clock",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    val span = Duration.between(r.time, next ?: if (off) r.time else now)
                    if (next != null || !off) Text(
                        Fmt.dur(span) + if (next == null) " so far" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (off) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { adding = true }) { Text("+ Add a ring") }
            if (rings.isNotEmpty()) TextButton(onClick = { Store.setRings(context, emptyList()); message = null }) { Text("Clear all") }
        }

        HorizontalDivider()
        SettingsPanel(s)

        if (rawText.isNotBlank()) {
            var show by remember { mutableStateOf(false) }
            TextButton(onClick = { show = !show }) { Text(if (show) "Hide what the camera read" else "Show what the camera read") }
            if (show) Text(rawText, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(32.dp))
    }

    editing?.let { r ->
        RingDialog(
            title = "Edit ring", initial = r,
            onDismiss = { editing = null },
            onDelete = { Store.setRings(context, rings - r); editing = null },
            onSave = { new -> Store.setRings(context, rings - r + new); editing = null },
        )
    }
    if (adding) {
        val last = rings.lastOrNull()
        RingDialog(
            title = "Add a ring",
            initial = Ring(if (last == null) "BT" else if (Calc.isOff(last.code, s)) "IL" else "OL", now.withNano(0)),
            onDismiss = { adding = false },
            onDelete = null,
            onSave = { new -> Store.setRings(context, rings + new); adding = false },
        )
    }
}

@Composable
private fun ResultCard(rings: List<Ring>, s: Settings, now: LocalDateTime) {
    val r = Calc.compute(rings, s, now)
    val target = Duration.ofSeconds(s.targetSeconds)
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                r == null -> {
                    Text("Scan your clock rings to see when to clock out.", fontSize = 18.sp)
                }
                r.finished -> {
                    Text("Day finished", style = MaterialTheme.typography.titleMedium)
                    Text(Fmt.dur(r.worked), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Text("on the clock today (${if (r.worked >= target) "${Fmt.dur(r.worked - target)} over" else "${Fmt.dur(target - r.worked)} short"})")
                }
                r.onClock && r.clockOut != null -> {
                    val reached = r.remaining.isZero
                    Text(if (reached) "You reached ${Fmt.dur(target)} at" else "Clock out at", style = MaterialTheme.typography.titleMedium)
                    Text(Fmt.time(r.clockOut), fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (reached) "You're ${Fmt.dur(r.worked - target)} over – go home!"
                        else "${Fmt.dur(r.remaining)} to go · ${Fmt.dur(r.worked)} worked",
                        fontSize = 18.sp,
                    )
                }
                else -> {
                    // Off the clock (lunch): the clock-out time depends on when you come back.
                    Text("Off the clock", style = MaterialTheme.typography.titleMedium)
                    Text("${Fmt.dur(r.remaining)} left to work", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("${Fmt.dur(r.worked)} worked so far.")
                    Text("Clock back in now → clock out at ${Fmt.time(now + r.remaining)}", fontSize = 18.sp)
                }
            }
            if (r != null) {
                val f = (r.worked.seconds.toFloat() / target.seconds.coerceAtLeast(1)).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun SettingsPanel(s: Settings) {
    val context = LocalContext.current
    fun set(new: Settings) = Store.setSettings(context, new)
    Text("Settings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

    var targetText by remember { mutableStateOf(Fmt.dur(Duration.ofSeconds(s.targetSeconds)).removeSuffix(":00")) }
    OutlinedTextField(
        value = targetText,
        onValueChange = { t ->
            targetText = t
            val p = t.trim().split(":", ".")
            val h = p.getOrNull(0)?.toIntOrNull()
            val m = p.getOrNull(1)?.toIntOrNull() ?: 0
            val sec = p.getOrNull(2)?.toIntOrNull() ?: 0
            if (h != null && h in 0..23 && m in 0..59 && sec in 0..59) set(s.copy(targetSeconds = h * 3600L + m * 60L + sec))
        },
        label = { Text("Time on the clock (hours:minutes)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    var offText by remember { mutableStateOf(s.offCodes.sorted().joinToString(", ")) }
    OutlinedTextField(
        value = offText,
        onValueChange = { t ->
            offText = t
            set(s.copy(offCodes = t.split(",", " ").map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()))
        },
        label = { Text("Codes that take you OFF the clock") },
        supportingText = { Text("Time after these rings isn't counted (OL = out to lunch, ET = end). Every other code counts as working.") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Row(Modifier.fillMaxWidth().clickable { set(s.copy(remind = !s.remind)) }, verticalAlignment = Alignment.CenterVertically) {
        Text("Remind me when it's time", Modifier.weight(1f))
        Switch(s.remind, { set(s.copy(remind = it)) })
    }
    if (s.remind) {
        Text(if (s.remindBefore == 0) "Only at clock-out time" else "Also ${s.remindBefore} min before")
        Slider(
            value = s.remindBefore.toFloat(),
            onValueChange = { set(s.copy(remindBefore = it.roundToInt())) },
            valueRange = 0f..30f,
            steps = 29,
        )
    }
}

@Composable
private fun RingDialog(title: String, initial: Ring, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (Ring) -> Unit) {
    var code by remember { mutableStateOf(initial.code) }
    var time by remember { mutableStateOf(Fmt.time(initial.time)) }
    val parsed = Fmt.parseClock(time)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    code, { code = it.uppercase().take(4) }, label = { Text("Code (BT, MV, OL, IL, ET…)") }, singleLine = true,
                )
                OutlinedTextField(
                    time, { time = it }, label = { Text("Time, e.g. 2:41:06 PM") }, singleLine = true,
                    isError = parsed == null,
                    supportingText = { if (parsed == null) Text("Type it like 2:41:06 PM") },
                )
                Text(initial.time.toLocalDate().toString(), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { onSave(Ring(code.trim().ifEmpty { "?" }, initial.time.toLocalDate().atTime(it))) } },
                enabled = parsed != null,
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
