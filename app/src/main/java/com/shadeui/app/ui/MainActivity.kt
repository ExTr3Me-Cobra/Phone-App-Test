package com.shadeui.app.ui

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.shadeui.app.ShadeApp
import com.shadeui.app.data.AlertMode
import com.shadeui.app.data.AppRule
import com.shadeui.app.data.LightColorMode
import com.shadeui.app.data.LightStyle
import com.shadeui.app.data.LockStyle
import com.shadeui.app.data.LockVisibility
import com.shadeui.app.data.PopupStyle
import com.shadeui.app.data.PullArea
import com.shadeui.app.data.ShadeSettings
import com.shadeui.app.data.ThemeMode
import com.shadeui.app.data.Tri
import com.shadeui.app.notif.NotifItem
import com.shadeui.app.notif.NotifListener
import com.shadeui.app.overlay.AlertController
import com.shadeui.app.overlay.OverlayService
import com.shadeui.app.tiles.TileKind
import com.shadeui.app.tiles.Tiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shade's own app: setup checklist and every setting. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startApp = intent.getStringExtra(EXTRA_APP)
        val startScreen = intent.getStringExtra(EXTRA_SCREEN)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)) {
                SettingsApp(startApp, startScreen)
            }
        }
    }

    companion object {
        const val EXTRA_APP = "app"
        const val EXTRA_SCREEN = "screen"
    }
}

private sealed interface Page {
    data object Home : Page
    data object Panel : Page
    data object Popups : Page
    data object Lighting : Page
    data object Lock : Page
    data object Apps : Page
    data class App(val pkg: String) : Page
    data object TilesPage : Page
    data object Duplicates : Page
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsApp(startApp: String?, startScreen: String?) {
    var stack by remember {
        mutableStateOf(
            listOfNotNull<Page>(
                Page.Home,
                if (startScreen == "tiles") Page.TilesPage else null,
                startApp?.let { Page.App(it) },
            ),
        )
    }
    val page = stack.last()
    fun go(p: Page) { stack = stack + p }
    BackHandler(enabled = stack.size > 1) { stack = stack.dropLast(1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pageTitle(page)) },
                navigationIcon = {
                    if (stack.size > 1) {
                        IconButton(onClick = { stack = stack.dropLast(1) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when (page) {
                Page.Home -> HomePage(::go)
                Page.Panel -> PanelPage()
                Page.Popups -> PopupsPage()
                Page.Lighting -> LightingPage()
                Page.Lock -> LockPage()
                Page.Apps -> AppsPage { go(Page.App(it)) }
                is Page.App -> AppRulePage(page.pkg)
                Page.TilesPage -> TilesPage()
                Page.Duplicates -> DuplicatesPage()
            }
        }
    }
}

private fun pageTitle(p: Page) = when (p) {
    Page.Home -> "Shade"
    Page.Panel -> "Panel & quick settings"
    Page.Popups -> "Pop-ups"
    Page.Lighting -> "Edge lighting"
    Page.Lock -> "Lock screen"
    Page.Apps -> "Apps"
    is Page.App -> "App rules"
    Page.TilesPage -> "Edit tiles"
    Page.Duplicates -> "Turn off Samsung's duplicates"
}

private val store get() = ShadeApp.instance.settings

@Composable
private fun settings(): ShadeSettings {
    val s by store.flow.collectAsState()
    return s
}

// ---------------- Home / setup ----------------

@Composable
private fun HomePage(go: (Page) -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val checks = remember(tick) { setupChecks(context) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        val missing = checks.count { !it.done && it.required }
        BodyText(
            if (missing == 0) "✅ Shade is running. Swipe down from the top of the screen to open it."
            else "Finish the setup steps below ($missing left).",
        )
        SectionTitle("Setup")
        checks.forEach { c ->
            Row(
                Modifier.fillMaxWidth().clickable { c.open() }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (c.done) "✅" else "⬜", Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.title + if (!c.required) " (optional)" else "", style = MaterialTheme.typography.bodyLarge)
                    Text(c.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        NavRow("Turn off Samsung's duplicates", "So pop-ups and lock screen notifications don't show twice") { go(Page.Duplicates) }

        SectionTitle("Settings")
        NavRow("Panel & quick settings", "Look, tiles, brightness, gestures, notification list") { go(Page.Panel) }
        NavRow("Edit tiles", "Choose and order the quick setting buttons") { go(Page.TilesPage) }
        NavRow("Pop-ups", "Style, duration, when to show") { go(Page.Popups) }
        NavRow("Edge lighting", "Style, colours, waking the screen, quiet hours") { go(Page.Lighting) }
        NavRow("Lock screen", "Cards or icons, hiding content, position") { go(Page.Lock) }
        NavRow("Apps", "Per-app: silent, pop-up, lock screen, lighting, wake screen") { go(Page.Apps) }

        SectionTitle("Try it")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { OverlayService.instance?.openShade() }) { Text("Open panel") }
            OutlinedButton(onClick = { testAlert(context, delayMs = 0) }) { Text("Test pop-up") }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { testAlert(context, delayMs = 6000) }) { Text("Test with screen off (6 s)") }
        }
        BodyText("For the screen-off test, tap it and turn the screen off straight away.")
        Spacer(Modifier.size(32.dp))
    }
}

private class Check(val title: String, val summary: String, val done: Boolean, val required: Boolean, val open: () -> Unit)

private fun setupChecks(context: Context): List<Check> {
    fun start(intent: Intent) = runCatching { context.startActivity(intent) }
    val pkgUri = Uri.parse("package:${context.packageName}")
    return listOf(
        Check(
            "1. Notification access",
            "Lets Shade read and act on notifications. Turn on \"Shade\".",
            NotifListener.isEnabled(context), true,
        ) { start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        Check(
            "2. Accessibility service",
            "Draws the panel, pop-ups and lighting and keeps Shade running after restarts. Installed apps → Shade → on. If it's greyed out, use step 2b.",
            isAccessibilityOn(context), true,
        ) { start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        Check(
            "2b. Allow restricted settings",
            "Only if step 2 is blocked: tap ⋮ (top right) → Allow restricted settings, then redo step 2.",
            isAccessibilityOn(context), false,
        ) { start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)) },
        Check(
            "3. Modify system settings",
            "For the brightness slider and the auto-rotate tile.",
            Settings.System.canWrite(context), true,
        ) { start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkgUri)) },
        Check(
            "4. Do not disturb access",
            "For the Do not disturb and Mute tiles.",
            context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted, false,
        ) { start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
        Check(
            "5. Unrestricted battery",
            "So Samsung never puts Shade to sleep.",
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName), true,
        ) { start(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)) },
    )
}

private fun isAccessibilityOn(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    val me = ComponentName(context, OverlayService::class.java)
    return flat.split(":").any { ComponentName.unflattenFromString(it) == me }
}

/** Sends a fake notification through the real alert logic (pop-up, lighting, wake). */
private fun testAlert(context: Context, delayMs: Long) {
    Handler(Looper.getMainLooper()).postDelayed({
        val app = ShadeApp.instance
        val item = NotifItem(
            key = "shade-test-${System.currentTimeMillis()}", pkg = "com.shadeui.app.test", appName = "Shade",
            appIcon = runCatching { context.packageManager.getApplicationIcon(context.packageName).toBitmap(144, 144).asImageBitmap() }.getOrNull(),
            smallIcon = null, accentColor = 0, title = "Test notification",
            text = "This is how a new notification will look.", bigText = null, subText = null, messages = emptyList(),
            largeIcon = null, picture = null, progress = 0, progressMax = 0, progressIndeterminate = false,
            actions = emptyList(), contentIntent = null, autoCancel = true, time = System.currentTimeMillis(),
            showTime = true, ongoing = false, clearable = true, isGroupSummary = false, groupKey = "test",
            isMedia = false, importance = NotificationManager.IMPORTANCE_HIGH, onlyAlertOnce = false, matchesFilter = true,
        )
        if (OverlayService.instance == null) return@postDelayed
        // Unlocked tests always pop up, even with Shade's own app in front.
        val rule = app.rules.get(item.pkg)
        if (delayMs == 0L) {
            OverlayService.instance?.showPopup(item, rule)
            OverlayService.instance?.showLighting(item, rule)
        } else {
            AlertController.onPosted(context, item, isUpdate = false)
        }
    }, delayMs)
}

// ---------------- Samsung duplicates ----------------

@Composable
private fun DuplicatesPage() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val granted = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    Column(Modifier.verticalScroll(rememberScrollState())) {
        BodyText(
            "Samsung keeps showing its own pop-ups and lock screen notifications next to Shade's. " +
                "Apps can't hide those by themselves, so switch them off once, either automatically " +
                "(after a one-time command from a computer) or by hand.",
        )
        SectionTitle("Automatic (recommended)")
        if (granted) {
            val cr = context.contentResolver
            val headsUp = remember(tick) { Settings.Global.getInt(cr, "heads_up_notifications_enabled", 1) == 1 }
            val lockNotifs = remember(tick) { Settings.Secure.getInt(cr, "lock_screen_show_notifications", 1) == 1 }
            SwitchRow("Samsung pop-ups", if (headsUp) "On — showing twice" else "Off — only Shade's", headsUp) {
                Settings.Global.putInt(cr, "heads_up_notifications_enabled", if (it) 1 else 0); tick++
            }
            SwitchRow("Samsung lock screen notifications", if (lockNotifs) "On — showing twice" else "Off — only Shade's", lockNotifs) {
                Settings.Secure.putInt(cr, "lock_screen_show_notifications", if (it) 1 else 0); tick++
            }
        } else {
            BodyText(
                "Needs a one-time permission from a computer with Android platform tools (Android Studio " +
                    "includes them): turn on USB debugging in Developer options, connect the phone, and run:",
            )
            Text(
                "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS",
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            BodyText("Then come back to this page. The permission survives restarts.")
        }
        SectionTitle("By hand")
        BodyText(
            "• Lock screen: Settings → Lock screen and AOD → Notifications → off.\n" +
                "• Pop-ups: Settings → Notifications → Notification pop-up style, and turn off pop-ups for apps; or per app: " +
                "Settings → Notifications → App notifications → (app) → Notification categories → Pop-up off.\n" +
                "• Samsung's edge lighting: Settings → Notifications → Notification pop-up style → turn off the lighting effect.",
        )
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { runCatching { context.startActivity(Intent("android.settings.NOTIFICATION_SETTINGS")) } }) {
                Text("Notification settings")
            }
            OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) } }) {
                Text("Lock screen settings")
            }
        }
    }
}

// ---------------- Panel ----------------

@Composable
private fun PanelPage() {
    val s = settings()
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionTitle("Opening")
        ChoiceRow("Swipe-down area", "Where along the top edge a swipe opens Shade", s.pullArea, listOf(
            PullArea.FULL to "Whole top edge", PullArea.LEFT_HALF to "Left half", PullArea.RIGHT_HALF to "Right half",
        )) { v -> store.update { it.copy(pullArea = v) } }
        SwitchRow("Open in full-screen apps", "Off: in games and videos, Samsung's own panel opens instead", s.pullInFullscreen) { v -> store.update { it.copy(pullInFullscreen = v) } }
        SwitchRow("Vibrate on open and tile taps", null, s.haptics) { v -> store.update { it.copy(haptics = v) } }

        SectionTitle("Look")
        ChoiceRow("Theme", null, s.theme, listOf(ThemeMode.SYSTEM to "Follow system", ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light")) { v -> store.update { it.copy(theme = v) } }
        SliderRow("Background blur", s.blurRadius, 0..150) { v -> store.update { it.copy(blurRadius = v) } }
        SliderRow("Background dimming", s.dimPercent, 0..100, { "$it%" }) { v -> store.update { it.copy(dimPercent = v) } }
        SwitchRow("Show carrier name", null, s.showCarrier) { v -> store.update { it.copy(showCarrier = v) } }

        SectionTitle("Quick settings")
        SliderRow("Tiles in the first row", s.collapsedTileCount, 4..7) { v -> store.update { it.copy(collapsedTileCount = v) } }
        SwitchRow("Show tile names", null, s.showTileLabels) { v -> store.update { it.copy(showTileLabels = v) } }
        SwitchRow("Brightness slider on first pull", null, s.showBrightnessCollapsed) { v -> store.update { it.copy(showBrightnessCollapsed = v) } }
        SwitchRow("Media player card", null, s.showMediaCard) { v -> store.update { it.copy(showMediaCard = v) } }
        SwitchRow("Close panel after tapping a tile", null, s.closeAfterTile) { v -> store.update { it.copy(closeAfterTile = v) } }
        BodyText("Wi-Fi, Bluetooth, mobile data, airplane mode and similar tiles work by pressing Samsung's own tile for you behind Shade's panel. Keep those tiles in Samsung's quick panel. Long-press any tile to open its settings.")

        SectionTitle("Notification list")
        SwitchRow("Group notifications by app", null, s.groupByApp) { v -> store.update { it.copy(groupByApp = v) } }
        SwitchRow("Separate silent notifications", "Shown under a \"Silent notifications\" heading", s.separateSilent) { v -> store.update { it.copy(separateSilent = v) } }
        SwitchRow("Show how long ago", null, s.showTimestamps) { v -> store.update { it.copy(showTimestamps = v) } }
        SwitchRow("Close panel after opening a notification", null, s.closeAfterOpen) { v -> store.update { it.copy(closeAfterOpen = v) } }
        SliderRow("Lines of text before expanding", s.maxLinesCollapsed, 1..5) { v -> store.update { it.copy(maxLinesCollapsed = v) } }
        BodyText("Swipe a card sideways to dismiss it, tap the arrow to expand, long-press for snooze and per-app options.")
        Spacer(Modifier.size(32.dp))
    }
}

// ---------------- Pop-ups ----------------

@Composable
private fun PopupsPage() {
    val s = settings()
    Column(Modifier.verticalScroll(rememberScrollState())) {
        ChoiceRow("Pop-up style", null, s.popupStyle, listOf(PopupStyle.DETAILED to "Detailed (card with actions)", PopupStyle.BRIEF to "Brief (small bubble)")) { v -> store.update { it.copy(popupStyle = v) } }
        SliderRow("Show for", s.popupSeconds, 2..15, { "$it s" }) { v -> store.update { it.copy(popupSeconds = v) } }
        SwitchRow("Skip pop-ups from the app you're using", null, s.popupSkipForegroundApp) { v -> store.update { it.copy(popupSkipForegroundApp = v) } }
        SwitchRow("Pop up in full-screen apps", "Games, videos", s.popupInFullscreen) { v -> store.update { it.copy(popupInFullscreen = v) } }
        SwitchRow("Pop up on the lock screen", null, s.popupOnLockScreen) { v -> store.update { it.copy(popupOnLockScreen = v) } }
        BodyText("Swipe a pop-up up or sideways to hide it, down to open the panel, tap to open the notification. Which apps pop up is set per app in Apps.")
    }
}

// ---------------- Lighting ----------------

@Composable
private fun LightingPage() {
    val s = settings()
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionTitle("Defaults (each app can override)")
        SwitchRow("Light up on the lock screen", null, s.defaultLightLocked) { v -> store.update { it.copy(defaultLightLocked = v) } }
        SwitchRow("Wake the screen", "Turns the screen on when the phone is off, so the lighting can show", s.defaultWakeScreen) { v -> store.update { it.copy(defaultWakeScreen = v) } }
        SwitchRow("Light up while using the phone", null, s.defaultLightUnlocked) { v -> store.update { it.copy(defaultLightUnlocked = v) } }

        SectionTitle("Look")
        ChoiceRow("Style", null, s.lightStyle, listOf(
            LightStyle.GLOW to "Glow", LightStyle.LINE to "Basic line", LightStyle.GRADIENT to "Spinning gradient", LightStyle.PULSE to "Pulse",
        )) { v -> store.update { it.copy(lightStyle = v) } }
        ChoiceRow("Colour", null, s.lightColorMode, listOf(
            LightColorMode.APP_ICON to "App icon colour", LightColorMode.NOTIFICATION to "Notification's colour",
            LightColorMode.CUSTOM to "Custom colour", LightColorMode.RAINBOW to "Multicolour",
        )) { v -> store.update { it.copy(lightColorMode = v) } }
        if (s.lightColorMode == LightColorMode.CUSTOM) {
            ColorSwatches(s.lightCustomColor, allowAuto = false) { c -> c?.let { col -> store.update { it.copy(lightCustomColor = col) } } }
        }
        SliderRow("Thickness", s.lightThicknessDp, 2..20, { "$it dp" }) { v -> store.update { it.copy(lightThicknessDp = v) } }
        SliderRow("Transparency", s.lightOpacity, 20..100, { "${100 - it}%" }) { v -> store.update { it.copy(lightOpacity = v) } }
        SliderRow("Repeat", s.lightRepeats, 1..10, { "$it ×" }) { v -> store.update { it.copy(lightRepeats = v) } }

        SectionTitle("When the screen was off")
        SliderRow("Keep the screen on for", s.wakeSeconds, 3..20, { "$it s" }) { v -> store.update { it.copy(wakeSeconds = v) } }
        SwitchRow("Turn the screen off again", "If you don't touch the phone", s.screenOffAfterWake) { v -> store.update { it.copy(screenOffAfterWake = v) } }
        SwitchRow("Show the notification with the lighting", null, s.previewWithLighting) { v -> store.update { it.copy(previewWithLighting = v) } }

        SectionTitle("Quiet hours")
        SwitchRow("No lighting or waking at night", null, s.quietHoursEnabled) { v -> store.update { it.copy(quietHoursEnabled = v) } }
        if (s.quietHoursEnabled) {
            val hours = (0 until 24).map { it * 60 to hourLabel(it) }
            ChoiceRow("From", null, s.quietStartMinutes, hours) { v -> store.update { it.copy(quietStartMinutes = v) } }
            ChoiceRow("Until", null, s.quietEndMinutes, hours) { v -> store.update { it.copy(quietEndMinutes = v) } }
        }
        Spacer(Modifier.size(32.dp))
    }
}

private fun hourLabel(h: Int) = when {
    h == 0 -> "12 AM"
    h < 12 -> "$h AM"
    h == 12 -> "12 PM"
    else -> "${h - 12} PM"
}

// ---------------- Lock screen ----------------

@Composable
private fun LockPage() {
    val s = settings()
    Column(Modifier.verticalScroll(rememberScrollState())) {
        ChoiceRow("Show notifications as", null, s.lockStyle, listOf(LockStyle.CARDS to "Cards", LockStyle.ICONS to "Icons only", LockStyle.OFF to "Don't show")) { v -> store.update { it.copy(lockStyle = v) } }
        SwitchRow("Hide content", "Show only the app name until unlocked", s.lockHideContent) { v -> store.update { it.copy(lockHideContent = v) } }
        SliderRow("Cards shown", s.lockMaxCards, 1..8) { v -> store.update { it.copy(lockMaxCards = v) } }
        SliderRow("Position from the top", s.lockTopPercent, 10..75, { "$it%" }) { v -> store.update { it.copy(lockTopPercent = v) } }
        SliderRow("Card opacity", s.lockCardOpacity, 20..100, { "$it%" }) { v -> store.update { it.copy(lockCardOpacity = v) } }
        BodyText("The cards step aside as soon as you swipe anywhere else on the lock screen, so they never block unlocking. Tap a card to unlock and open it. Silent notifications aren't shown on the lock screen.")
    }
}

// ---------------- Apps ----------------

private data class AppEntry(val pkg: String, val name: String)

@Composable
private fun AppsPage(open: (String) -> Unit) {
    val context = LocalContext.current
    val rules by ShadeApp.instance.rules.flow.collectAsState()
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val launchable = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
            ).map { it.activityInfo.packageName }
            val notifying = ShadeApp.instance.notifs.value.map { it.pkg }
            (launchable + notifying).distinct().filter { it != context.packageName }.map {
                AppEntry(it, NotifItem.appInfo(context, it).first)
            }.sortedBy { it.name.lowercase() }
        }
    }
    Column {
        OutlinedTextField(
            query, { query = it }, label = { Text("Search apps") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        val list = apps
        if (list == null) {
            BodyText("Loading apps…")
        } else {
            LazyColumn {
                items(list.filter { query.isBlank() || it.name.contains(query, true) }, key = { it.pkg }) { a ->
                    val icon by produceState<ImageBitmap?>(null, a.pkg) { value = NotifItem.appInfo(context, a.pkg).second }
                    Row(
                        Modifier.fillMaxWidth().clickable { open(a.pkg) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        icon?.let { Image(it, null, Modifier.size(36.dp).clip(CircleShape)) } ?: Spacer(Modifier.size(36.dp))
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(a.name)
                            Text(ruleSummary(rules[a.pkg] ?: AppRule()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun ruleSummary(r: AppRule): String = when (r.alert) {
    AlertMode.FOLLOW_APP -> "Default"
    AlertMode.POPUP -> "Always pop up"
    AlertMode.SILENT -> "Silent"
    AlertMode.HIDDEN -> "Hidden"
} + (if (r.lightLocked == Tri.ON || r.wakeScreen == Tri.ON) " • lights up" else "") +
    (if (r.lockScreen != LockVisibility.SHOW) " • lock screen limited" else "")

@Composable
private fun AppRulePage(pkg: String) {
    val context = LocalContext.current
    val store = ShadeApp.instance.rules
    val rules by store.flow.collectAsState()
    val rule = rules[pkg] ?: AppRule()
    val (name, icon) = remember(pkg) { NotifItem.appInfo(context, pkg) }
    fun set(change: (AppRule) -> AppRule) = store.set(pkg, change(rule))
    val triOptions = listOf(Tri.DEFAULT to "Default", Tri.ON to "On", Tri.OFF to "Off")
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Image(it, null, Modifier.size(48.dp).clip(CircleShape)) }
            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 14.dp))
        }
        ChoiceRow("When a notification arrives", null, rule.alert, listOf(
            AlertMode.FOLLOW_APP to "Default (pop up if the app marks it urgent)",
            AlertMode.POPUP to "Always pop up",
            AlertMode.SILENT to "Silent (only in the panel, no pop-up or lighting)",
            AlertMode.HIDDEN to "Hide completely",
        )) { v -> set { it.copy(alert = v) } }
        ChoiceRow("Pop-up style", null, rule.popupStyle, listOf(
            null to "Default", PopupStyle.DETAILED to "Detailed", PopupStyle.BRIEF to "Brief",
        )) { v -> set { it.copy(popupStyle = v) } }
        ChoiceRow("On the lock screen", null, rule.lockScreen, listOf(
            LockVisibility.SHOW to "Show", LockVisibility.HIDE_CONTENT to "Hide content", LockVisibility.HIDE to "Don't show",
        )) { v -> set { it.copy(lockScreen = v) } }
        SectionTitle("Edge lighting")
        ChoiceRow("Light up on the lock screen", null, rule.lightLocked, triOptions) { v -> set { it.copy(lightLocked = v) } }
        ChoiceRow("Wake the screen", "When the screen is off", rule.wakeScreen, triOptions) { v -> set { it.copy(wakeScreen = v) } }
        ChoiceRow("Light up while using the phone", null, rule.lightUnlocked, triOptions) { v -> set { it.copy(lightUnlocked = v) } }
        Text("Lighting colour", modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        ColorSwatches(rule.lightColor, allowAuto = true) { c -> set { it.copy(lightColor = c) } }
        SectionTitle("Sound & vibration")
        BodyText("Sounds and vibration are still played by Android, so they're set in Samsung's settings for this app.")
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg))
                }
            }) { Text("Samsung settings for $name") }
        }
        if (!rule.isDefault) {
            Button(onClick = { store.set(pkg, AppRule()) }, modifier = Modifier.padding(16.dp)) { Text("Reset to defaults") }
        }
        Spacer(Modifier.size(32.dp))
    }
}

// ---------------- Tiles ----------------

@Composable
private fun TilesPage() {
    val s = settings()
    val active = s.tiles.mapNotNull { Tiles.get(it) }
    val available = Tiles.all.filter { it.id !in s.tiles }
    fun save(list: List<String>) = store.update { it.copy(tiles = list) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        BodyText("The first ${s.collapsedTileCount} show on the first pull; the rest in the expanded panel (8 at a time, tap the handle for more).")
        SectionTitle("In the panel")
        active.forEachIndexed { i, t ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(t.icon, null)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(t.label)
                    if (t.kind == TileKind.SAMSUNG) {
                        Text("Presses Samsung's tile", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(enabled = i > 0, onClick = {
                    val l = s.tiles.toMutableList(); java.util.Collections.swap(l, i, i - 1); save(l)
                }) { Icon(Icons.Filled.ArrowUpward, "Move up") }
                IconButton(enabled = i < active.size - 1, onClick = {
                    val l = s.tiles.toMutableList(); java.util.Collections.swap(l, i, i + 1); save(l)
                }) { Icon(Icons.Filled.ArrowDownward, "Move down") }
                IconButton(onClick = { save(s.tiles - t.id) }) { Icon(Icons.Filled.Close, "Remove") }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionTitle("Available")
        if (available.isEmpty()) BodyText("All tiles are in use.")
        available.forEach { t ->
            Row(Modifier.fillMaxWidth().clickable { save(s.tiles + t.id) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(t.icon, null)
                Text(t.label, Modifier.weight(1f).padding(start = 14.dp))
                Icon(Icons.Filled.Add, "Add")
            }
        }
        Spacer(Modifier.size(32.dp))
    }
}
