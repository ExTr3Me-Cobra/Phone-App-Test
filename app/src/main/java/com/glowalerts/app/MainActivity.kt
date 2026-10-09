package com.glowalerts.app

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        GlowListener.ensureChannels(this)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)) {
                Surface(Modifier.fillMaxSize()) { Screen() }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Screen() {
    val context = LocalContext.current
    Prefs.get(context)
    val s by Prefs.flow.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        tick++
        KeepAlive.start(context)
    }
    fun set(change: (Settings) -> Settings) = Prefs.update(context, change)
    // Which colour the picker is open for: 1, 2, or 0 = closed.
    var picking by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())) {
        Text("Glow Alerts", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(28.dp))
                .background(if (s.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .clickable { set { it.copy(enabled = !it.enabled) } }
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (s.enabled) "Glow Alerts is on" else "Glow Alerts is off", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(if (s.enabled) "Every new notification lights up the screen." else "No lighting.", style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = s.enabled, onCheckedChange = { v -> set { it.copy(enabled = v) } })
        }

        Section("Setup")
        Steps(tick, s.wakeScreen)

        Section("Preview")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniPreview(s)
            Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.effect.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Updates as you change settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { playFullScreen(context, s) }) { Text("Play full screen") }
                OutlinedButton(onClick = { sendTest(context, 0) }) { Text("Test notification") }
                OutlinedButton(onClick = { sendTest(context, 6000) }) { Text("Test in 6 s") }
            }
        }
        Note("For the lock screen / Always On Display test, tap \"Test in 6 s\" and lock the phone straight away.")

        Section("Effect  (${LightEffect.entries.size} presets)")
        LightGroup.entries.forEach { group ->
            Label(group.label)
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LightEffect.entries.filter { it.group == group }.forEach { e ->
                    FilterChip(selected = s.effect == e, onClick = { set { it.copy(effect = e) } }, label = { Text(e.label) })
                }
            }
        }

        if (s.effect.isCrack) {
            Section("Cracking")
            SwitchRow("All at once", "The whole crack appears in one hit instead of spreading", s.crackAllAtOnce) { v -> set { it.copy(crackAllAtOnce = v) } }
            Label(if (s.crackAllAtOnce) "Centred on" else "Spreads from")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CrackOrigin.entries.forEach { o ->
                    FilterChip(selected = s.crackOrigin == o, onClick = { set { it.copy(crackOrigin = o) } }, label = { Text(o.label) })
                }
            }
            SliderRow("Line thickness", "${s.crackLineDp.roundToInt()} dp", s.crackLineDp, 1f..16f) { v -> set { it.copy(crackLineDp = v.roundToInt().toFloat()) } }
            SliderRow("Detail", "${s.crackDetail} / 10", s.crackDetail.toFloat(), 1f..10f) { v -> set { it.copy(crackDetail = v.roundToInt()) } }
            SwitchRow("Flash on impact", "A burst of light where the crack hits", s.crackFlash) { v -> set { it.copy(crackFlash = v) } }
            SwitchRow("Also light the screen edge", "A glow around the edge while it's cracked", s.crackEdgeGlow) { v -> set { it.copy(crackEdgeGlow = v) } }
            Note("Each notification gets a freshly random crack. Speed sets how fast it spreads.")
        }

        Section("Colour")
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorMode.entries.forEach { m ->
                FilterChip(selected = s.colorMode == m, onClick = { set { it.copy(colorMode = m) } }, label = { Text(m.label) })
            }
        }
        when (s.colorMode) {
            ColorMode.APP -> {
                Note("Each notification uses its app's colour (or its icon's main colour). This one is used when an app has none:")
                ColorRow("Fallback colour", s.color1) { picking = 1 }
            }
            ColorMode.ONE -> ColorRow("Colour", s.color1) { picking = 1 }
            ColorMode.TWO -> {
                ColorRow("First colour", s.color1) { picking = 1 }
                ColorRow("Second colour", s.color2) { picking = 2 }
            }
            ColorMode.RAINBOW -> Note("Every effect turns through all the colours of the rainbow.")
        }

        Section("Look")
        SliderRow("Speed", "%.2fx".format(s.speed), s.speed, 0.25f..3f) { v -> set { it.copy(speed = (v * 20).roundToInt() / 20f) } }
        SliderRow("Line thickness (edge effects)", "${s.thicknessDp.roundToInt()} dp", s.thicknessDp, 1f..30f) { v -> set { it.copy(thicknessDp = v.roundToInt().toFloat()) } }
        SliderRow("Glow", "${(s.glow * 100).roundToInt()} %", s.glow, 0f..2f) { v -> set { it.copy(glow = (v * 20).roundToInt() / 20f) } }
        SliderRow("Brightness", "${s.brightness} %", s.brightness.toFloat(), 20f..100f) { v -> set { it.copy(brightness = v.roundToInt()) } }
        SliderRow("Plays for", "${s.seconds} s", s.seconds.toFloat(), 1f..20f) { v -> set { it.copy(seconds = v.roundToInt()) } }
        SwitchRow("Match my screen's corners", "Follows the exact curve of your screen's corners", s.matchCorners) { v -> set { it.copy(matchCorners = v) } }
        if (!s.matchCorners) {
            SliderRow("Corner curve", "${s.cornerDp.roundToInt()} dp", s.cornerDp, 0f..120f) { v -> set { it.copy(cornerDp = v.roundToInt().toFloat()) } }
        }

        Section("When")
        SwitchRow("While using the phone", "Lights up over whatever's on screen", s.onUnlocked) { v -> set { it.copy(onUnlocked = v) } }
        SwitchRow("On the lock screen", "Lights up while locked", s.onLocked) { v -> set { it.copy(onLocked = v) } }
        SwitchRow(
            "On the Always On Display",
            "Lights up on the Always On Display without waking the phone. Smoothness depends on the phone (the AOD refreshes slowly).",
            s.onAod,
        ) { v -> set { it.copy(onAod = v) } }
        SwitchRow(
            "Wake the screen",
            "When the screen is fully off (no Always On Display), turn it on so you see the lighting. Needs \"Full screen notifications\" in Setup.",
            s.wakeScreen,
        ) { v -> set { it.copy(wakeScreen = v) } }
        SwitchRow("Include silent notifications", "Also light up for notifications apps send quietly", s.includeSilent) { v -> set { it.copy(includeSilent = v) } }

        Section("Reliability")
        SwitchRow(
            "Always ready",
            "Keeps Glow Alerts running in the foreground (with a small silent notification) so Android never puts it to sleep " +
                "or delays it, and reconnects it if it's ever cut off. Uses more battery.",
            s.alwaysReady,
        ) { v ->
            set { it.copy(alwaysReady = v) }
            KeepAlive.start(context)
        }
        if (s.alwaysReady) {
            SwitchRow(
                "Keep the processor awake",
                "Never lets the phone's processor fully sleep, for the fastest possible reaction. Uses noticeably more battery.",
                s.keepAwake,
            ) { v ->
                set { it.copy(keepAwake = v) }
                KeepAlive.start(context)
            }
        }
        Note("Tip: also set Settings → Battery → Background usage limits so Glow Alerts is under \"Never auto sleeping apps\".")

        Section("Apps")
        AppList(s.excluded) { pkg, on -> set { it.copy(excluded = if (on) it.excluded - pkg else it.excluded + pkg) } }

        Section("Recent activity")
        if (Log.lines.isEmpty()) Note("Nothing yet. New notifications will be listed here with what Glow Alerts did.")
        Log.lines.take(15).forEach {
            Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp))
        }
        Spacer(Modifier.size(40.dp))
    }

    if (picking != 0) {
        ColorPickerDialog(
            title = if (picking == 1) "First colour" else "Second colour",
            initial = if (picking == 1) s.color1 else s.color2,
            recent = s.recentColors,
            onDismiss = { picking = 0 },
            onPick = { c ->
                val which = picking
                set { if (which == 1) it.copy(color1 = c) else it.copy(color2 = c) }
                Prefs.addRecentColor(context, c)
                picking = 0
            },
        )
    }
}

@Composable
private fun Steps(tick: Int, wake: Boolean) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val pkgUri = Uri.parse("package:${context.packageName}")
    val nm = context.getSystemService(NotificationManager::class.java)
    val listener = remember(tick) { GlowListener.isEnabled(context) }
    val lighting = remember(tick) { LightService.isEnabled(context) }
    val battery = remember(tick) { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }
    val notify = remember(tick) {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED && nm.areNotificationsEnabled()
    }
    val fsi = remember(tick) { Waker.fullScreenAllowed(context) }

    Step("1. Notification access", "Lets Glow Alerts see new notifications. Turn on \"Glow Alerts\".", listener) {
        open(context, Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
    Step(
        "2. Switch on \"Glow Alerts Lighting\"",
        "Settings → Accessibility → Installed apps → Glow Alerts Lighting → On. Needed to draw over the lock screen and Always On Display; it only draws the light and reads nothing on your screen.",
        lighting,
    ) { open(context, Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) }
    if (!listener || !lighting) {
        Note("Switch greyed out (\"Restricted setting\")? Open App info → ⋮ (top right) → Allow restricted settings, then try again.")
        OutlinedButton(
            onClick = { open(context, Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)) },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) { Text("Open App info") }
    }
    Step("3. Unrestricted battery", "So Samsung never puts Glow Alerts to sleep.", battery) {
        open(context, Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri))
    }
    Step("4. Allow notifications", "For the test button (and the screen wake).", notify) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            activity?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else {
            open(context, Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
        }
    }
    if (wake) {
        Step("5. Full screen notifications", "Needed for \"Wake the screen\" (how alarm apps turn the screen on).", fsi) {
            open(context, Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri))
        }
    }
}

@Composable
private fun Step(title: String, summary: String, done: Boolean, action: () -> Unit) {
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

/** A small phone-shaped preview that plays the effect in a loop. */
@Composable
private fun MiniPreview(s: Settings) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthDp = 130.dp
    val screenW = context.resources.displayMetrics.widthPixels.toFloat()
    val screenH = context.resources.displayMetrics.heightPixels.toFloat()
    val boxW = with(density) { widthDp.toPx() }
    val scale = boxW / screenW
    val heightDp = with(density) { (screenH * scale).toDp() }
    val full = LightService.spec(context, s, null, forever = true)
    val spec = full.copy(
        thicknessPx = full.thicknessPx * scale * 2f,
        cornerPx = full.cornerPx * scale,
        crack = full.crack.copy(lineWidthPx = full.crack.lineWidthPx * scale * 2f),
    )
    val cornerDp = with(density) { spec.cornerPx.toDp() }
    Box(
        Modifier.size(widthDp, heightDp).clip(RoundedCornerShape(cornerDp)).background(Color(0xFF0B0B0F))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(cornerDp)),
    ) {
        AndroidView(
            factory = { EdgeLightView(it, spec) },
            update = { view ->
                if (view.spec.effect != spec.effect || view.spec.crack != spec.crack) {
                    view.spec = spec
                    view.restart()
                } else {
                    view.spec = spec
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun AppList(excluded: Set<String>, onChange: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<Pair<String, String>>?>(null) {
        value = withContext(Dispatchers.IO) {
            context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName }.distinct().filter { it != context.packageName }
                .map { it to GlowListener.appName(context, it) }.sortedBy { it.second.lowercase() }
        }
    }
    var expanded by remember { mutableStateOf(false) }
    Note("Untick apps that should never light up.${if (excluded.isNotEmpty()) " (${excluded.size} switched off)" else ""}")
    OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(if (expanded) "Hide apps" else "Show apps")
    }
    if (!expanded) return
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
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
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

@Composable
private fun SliderRow(title: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = current, onValueChange = onChange, valueRange = range)
    }
}

private fun open(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
}

/** Plays the effect over the whole screen: through the service if it's on, else inside the app. */
private fun playFullScreen(context: Context, s: Settings) {
    val spec = LightService.spec(context, s, null)
    LightService.instance?.let {
        it.play(spec)
        return
    }
    val activity = context as? Activity ?: return
    val root = activity.window.decorView as? ViewGroup ?: return
    lateinit var view: EdgeLightView
    view = EdgeLightView(activity, spec) { root.removeView(view) }
    root.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
}

/**
 * Posts an ordinary notification, which Glow Alerts then lights up for like any other. A wake
 * lock keeps the phone awake during the wait so the test arrives on time after locking.
 */
private fun sendTest(context: Context, delayMs: Long) {
    val app = context.applicationContext
    GlowListener.ensureChannels(app)
    if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        (context as? Activity)?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        return
    }
    if (delayMs > 0) {
        app.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "glowalerts:test")
            .acquire(delayMs + 10_000)
        Log.add("Test: sending in ${delayMs / 1000} s…")
    }
    Handler(Looper.getMainLooper()).postDelayed({
        val n = Notification.Builder(app, GlowListener.TEST_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Test notification")
            .setContentText("If the screen lit up, Glow Alerts is working.")
            .setAutoCancel(true)
            .build()
        runCatching { app.getSystemService(NotificationManager::class.java).notify((System.currentTimeMillis() % 100000).toInt(), n) }
    }, delayMs)
}
