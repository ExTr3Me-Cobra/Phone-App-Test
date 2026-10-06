package com.popdown.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PopListener.ensureChannels(this)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)) {
                Surface(Modifier.fillMaxSize()) { Screen() }
            }
        }
    }
}

@Composable
private fun Screen() {
    val context = LocalContext.current
    Prefs.get(context)
    val s by Prefs.flow.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    fun set(change: (PopSettings) -> PopSettings) = Prefs.update(context, change)

    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())) {
        Text("Pop Down", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))

        // Master switch
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(28.dp))
                .background(if (s.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .clickable { set { it.copy(enabled = !it.enabled) } }
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (s.enabled) "Pop Down is on" else "Pop Down is off", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (s.enabled) "Every new notification pops down from the top." else "Notifications behave as normal.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(checked = s.enabled, onCheckedChange = { v -> set { it.copy(enabled = v) } })
        }

        val headsUpOff = remember(tick) {
            runCatching { Settings.Global.getInt(context.contentResolver, "heads_up_notifications_enabled", 1) == 0 }.getOrDefault(false)
        }
        if (headsUpOff) {
            Note(
                "⚠️ Pop-ups are switched off for the whole phone (the Shade app's \"Turn off Samsung's duplicates\" does this). " +
                    "Open Shade and switch it off, or turn \"Samsung pop-ups\" back on there.",
            )
        }

        Section("Setup")
        Steps(tick)

        Section("Options")
        SwitchRow("Wake the screen", "When the screen is off, turn it on for each new notification", s.wakeScreen) { v -> set { it.copy(wakeScreen = v) } }
        SwitchRow(
            "Skip ones that already pop down",
            "Avoids two pop-ups for apps (like messaging) that already pop down. Turn off to give every notification the same pop-down.",
            s.skipIfAlreadyPops,
        ) { v -> set { it.copy(skipIfAlreadyPops = v) } }
        SwitchRow("Include silent notifications", "Also pop down notifications that apps send quietly", s.includeSilent) { v -> set { it.copy(includeSilent = v) } }
        var keep by remember(s.keepSeconds) { mutableStateOf(s.keepSeconds.toFloat()) }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Remove the pop-down copy after ${keep.roundToInt()} s", style = MaterialTheme.typography.bodyLarge)
            Text(
                "The original notification always stays. The copy just needs to live long enough to pop down.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(value = keep, onValueChange = { keep = it }, onValueChangeFinished = { set { it.copy(keepSeconds = keep.roundToInt()) } }, valueRange = 3f..30f)
        }

        Section("Samsung's lighting effect")
        Note(
            "One UI's lighting effect plays for pop-down notifications from the apps it's allowed for. Since every pop-down now " +
                "comes through Pop Down, allow just this one app:\n" +
                "1. Settings → Notifications → Notification pop-up style.\n" +
                "2. Choose Brief or Detailed, and turn on the lighting effect (\"Edge lighting\" / \"Lighting style\").\n" +
                "3. In its app list (\"Apps to show as brief pop-ups\" / \"Choose apps\"), turn on Pop Down.\n" +
                "4. For lighting with the screen off, also turn on \"Show even when screen is off\" if your phone shows it.",
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { open(context, Intent("android.settings.NOTIFICATION_SETTINGS")) }) { Text("Notification settings") }
            OutlinedButton(onClick = {
                open(
                    context,
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, PopListener.CHANNEL),
                )
            }) { Text("Pop-down channel") }
        }

        Section("Try it")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { sendTest(context, 0) }) { Text("Test now") }
            OutlinedButton(onClick = { sendTest(context, 6000) }) { Text("Test in 6 s") }
        }
        Note("For the screen-off test, tap \"Test in 6 s\" and lock the phone straight away.")

        Section("Apps")
        AppList(s.excluded) { pkg, on -> set { it.copy(excluded = if (on) it.excluded - pkg else it.excluded + pkg) } }

        Section("Recent activity")
        if (Log.lines.isEmpty()) Note("Nothing yet. New notifications will be listed here with what Pop Down did.")
        Log.lines.take(15).forEach {
            Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp))
        }
        Spacer(Modifier.size(40.dp))
    }
}

@Composable
private fun Steps(tick: Int) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val pkgUri = Uri.parse("package:${context.packageName}")
    val nm = context.getSystemService(NotificationManager::class.java)
    val notifyGranted = remember(tick) {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED && nm.areNotificationsEnabled()
    }
    val steps = remember(tick) {
        listOf(
            Triple("1. Notification access", "Lets Pop Down see new notifications. Turn on \"Pop Down\".", PopListener.isEnabled(context)) to {
                open(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            Triple("2. Allow notifications", "Pop Down shows the pop-downs as its own notifications.", notifyGranted) to {
                if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    activity?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                } else {
                    open(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            },
            Triple(
                "3. Full screen notifications",
                "How alarm apps turn the screen on. Needed for waking the screen.",
                Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent(),
            ) to {
                if (Build.VERSION.SDK_INT >= 34) open(context, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri))
            },
            Triple(
                "4. Unrestricted battery",
                "So Samsung never puts Pop Down to sleep.",
                context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
            ) to {
                open(context, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri))
            },
        )
    }
    steps.forEach { (info, action) ->
        val (title, summary, done) = info
        Row(
            Modifier.fillMaxWidth().clickable { action() }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (done) "✅" else "⬜", Modifier.padding(end = 12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AppList(excluded: Set<String>, onChange: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<Pair<String, String>>?>(null) {
        value = withContext(Dispatchers.IO) {
            context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName }.distinct().filter { it != context.packageName }
                .map { it to PopListener.appName(context, it) }.sortedBy { it.second.lowercase() }
        }
    }
    Note("Untick apps that should never pop down.")
    val list = apps
    if (list == null) {
        Note("Loading apps…")
        return
    }
    list.forEach { (pkg, name) ->
        val on = pkg !in excluded
        Row(
            Modifier.fillMaxWidth().clickable { onChange(pkg, !on) }.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = on, onCheckedChange = { onChange(pkg, it) })
            Text(name)
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 4.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun open(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
}

/** Posts an ordinary (non-pop-up) notification, which Pop Down then pops down like any other. */
private fun sendTest(context: Context, delayMs: Long) {
    PopListener.ensureChannels(context)
    Handler(Looper.getMainLooper()).postDelayed({
        val n = Notification.Builder(context, PopListener.TEST_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Test notification")
            .setContentText("If this popped down from the top, Pop Down is working.")
            .setAutoCancel(true)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify((System.currentTimeMillis() % 100000).toInt(), n) }
    }, delayMs)
}
