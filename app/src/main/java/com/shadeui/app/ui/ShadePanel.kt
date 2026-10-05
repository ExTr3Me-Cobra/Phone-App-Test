package com.shadeui.app.ui

import android.media.AudioManager
import android.os.BatteryManager
import android.telephony.TelephonyManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadeui.app.ShadeApp
import com.shadeui.app.data.AlertMode
import com.shadeui.app.data.LockVisibility
import com.shadeui.app.data.ShadeSettings
import com.shadeui.app.media.MediaState
import com.shadeui.app.notif.NotifItem
import com.shadeui.app.overlay.OverlayService
import com.shadeui.app.tiles.TileDef
import com.shadeui.app.tiles.Tiles
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ShadePanel(host: OverlayService) {
    val app = ShadeApp.instance
    val ui = host.ui
    val p = LocalPalette.current
    val s by app.settings.flow.collectAsState()
    val notifs by app.notifs.flow.collectAsState()
    val tileStates by app.tiles.flow.collectAsState()
    val media by app.media.flow.collectAsState()
    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            app.tiles.refresh()
        }
    }

    val progress = ui.progress
    run {
        Box(
            Modifier
                .fillMaxSize()
                .background(p.scrim.copy(alpha = s.dimPercent / 100f * progress))
                .pointerInput(Unit) { detectTapGestures { host.closeShade() } }
                // Swipe up anywhere on the empty part of the panel: it follows the finger up.
                .pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onDragEnd = { host.releasePanel(tracker.calculateVelocity().y) },
                        onDragCancel = { host.releasePanel(0f) },
                    ) { change, dy ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        host.dragPanelBy(dy)
                    }
                },
        ) {
            // Top part: down expands quick settings; up collapses them, or (collapsed) moves the
            // whole panel up with the finger.
            val dragModifier = Modifier.pointerInput(Unit) {
                var total = 0f
                var movingPanel = false
                val tracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = {
                        total = 0f
                        movingPanel = false
                        tracker.resetTracking()
                    },
                    onDragEnd = {
                        if (movingPanel) {
                            host.releasePanel(tracker.calculateVelocity().y)
                        } else {
                            val threshold = with(density) { 56.dp.toPx() }
                            when {
                                total > threshold -> ui.expanded = true
                                total < -threshold && ui.expanded -> ui.expanded = false
                            }
                        }
                    },
                    onDragCancel = { if (movingPanel) host.releasePanel(0f) },
                ) { change, dy ->
                    tracker.addPosition(change.uptimeMillis, change.position)
                    total += dy
                    if (!ui.expanded && (movingPanel || dy < 0f)) {
                        movingPanel = true
                        host.dragPanelBy(dy)
                    }
                }
            }
            // The notification list: pulling down at its top expands quick settings; pushing up
            // past its end (or when it can't scroll) moves the whole panel up.
            val listGestures = remember {
                object : NestedScrollConnection {
                    var pulled = 0f
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        // Panel partly pushed up: moving down first brings the panel back.
                        if (host.panelDragging && available.y > 0f) {
                            host.dragPanelBy(available.y)
                            return Offset(0f, available.y)
                        }
                        return Offset.Zero
                    }

                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        if (source != NestedScrollSource.UserInput) return Offset.Zero
                        if (available.y < 0f) {
                            host.dragPanelBy(available.y)
                            return Offset(0f, available.y)
                        }
                        if (available.y > 0f) {
                            pulled += available.y
                            if (pulled > with(density) { 90.dp.toPx() }) {
                                ui.expanded = true
                                pulled = 0f
                            }
                        } else {
                            pulled = 0f
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        pulled = 0f
                        if (host.panelDragging) {
                            host.releasePanel(available.y)
                            return available
                        }
                        return Velocity.Zero
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    // Slides down and fades in as it's pulled out.
                    .graphicsLayer {
                        translationY = -(1f - progress) * size.height * 0.35f
                        alpha = (progress * 1.4f).coerceIn(0f, 1f)
                    }
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding()
                    .padding(horizontal = 18.dp),
            ) {
                StatusRow(s)
                Column(dragModifier.animateContentSize()) {
                    Header(host, ui.expanded)
                    Spacer(Modifier.height(18.dp))
                    if (ui.expanded) {
                        ExpandedQs(host, s, tileStates, media)
                    } else {
                        CollapsedQs(host, s, tileStates)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                NotificationList(host, s, notifs, media, Modifier.weight(1f).nestedScroll(listGestures))
                BottomPill(ui.expanded) { ui.expanded = it }
            }

            ui.message?.let { msg ->
                Box(Modifier.fillMaxSize().padding(bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
                    Text(
                        msg, color = p.text, fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp).clip(RoundedCornerShape(20.dp))
                            .background(p.pill).padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(s: ShadeSettings) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val carrier = remember {
        runCatching { context.getSystemService(TelephonyManager::class.java)?.networkOperatorName }.getOrNull().orEmpty()
    }
    val battery by produceState(0) {
        while (true) {
            value = context.getSystemService(BatteryManager::class.java)
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
            delay(30_000)
        }
    }
    val tiles by ShadeApp.instance.tiles.flow.collectAsState()
    Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (s.showCarrier) Text(carrier, color = p.text, fontSize = 15.sp)
        Spacer(Modifier.weight(1f))
        if (tiles["bluetooth"] == true) Icon(Icons.Filled.Bluetooth, null, tint = p.text, modifier = Modifier.size(16.dp))
        if (tiles["wifi"] == true) Icon(Icons.Filled.Wifi, null, tint = p.text, modifier = Modifier.padding(start = 4.dp).size(16.dp))
        Text("$battery%", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun Header(host: OverlayService, expanded: Boolean) {
    val p = LocalPalette.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(5_000)
        }
    }
    val time = remember(now / 60_000) {
        SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(host)) "H:mm" else "h:mm", Locale.getDefault()).format(Date(now))
    }
    val date = remember(now / 60_000) { SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(now)) }
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(time, color = p.text, fontSize = 36.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Text(date, color = p.text, fontSize = 17.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.weight(1f))
        if (expanded) {
            HeaderIcon(Icons.Filled.Edit, "Edit tiles") { host.openTileEditor() }
            HeaderIcon(Icons.Filled.PowerSettingsNew, "Power") { host.powerMenu() }
        }
        HeaderIcon(Icons.Filled.Settings, "Settings") { host.openSystemSettings() }
    }
}

@Composable
private fun HeaderIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.size(46.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = p.text, modifier = Modifier.size(26.dp)) }
}

@Composable
private fun tileIcon(host: OverlayService, def: TileDef): ImageVector = when (def.id) {
    "sound" -> when (ShadeApp.instance.tiles.soundMode()) {
        AudioManager.RINGER_MODE_VIBRATE -> Icons.Filled.Vibration
        AudioManager.RINGER_MODE_SILENT -> Icons.AutoMirrored.Filled.VolumeOff
        else -> Icons.AutoMirrored.Filled.VolumeUp
    }
    else -> def.icon
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(host: OverlayService, def: TileDef, on: Boolean?, size: Dp, showLabel: Boolean) {
    val p = LocalPalette.current
    val active = on == true
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (active) p.tileOn else p.tileOff)
                .combinedClickable(
                    onClick = { host.toggleTile(def.id) },
                    onLongClick = { host.openTileSettings(def.id) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                tileIcon(host, def), def.label,
                tint = if (active) p.tileOnIcon else p.tileOffIcon,
                modifier = Modifier.size(size * 0.46f),
            )
        }
        if (showLabel) {
            Text(
                def.label, color = p.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.width(size + 12.dp).padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun CollapsedQs(host: OverlayService, s: ShadeSettings, states: Map<String, Boolean?>) {
    val defs = s.tiles.mapNotNull { Tiles.get(it) }.take(s.collapsedTileCount)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        defs.forEach { Tile(host, it, states[it.id], 54.dp, s.showTileLabels) }
    }
    if (s.showBrightnessCollapsed) {
        Spacer(Modifier.height(18.dp))
        BrightnessSlider(host, showMenu = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ExpandedQs(host: OverlayService, s: ShadeSettings, states: Map<String, Boolean?>, media: MediaState?) {
    val p = LocalPalette.current
    var allRows by remember { mutableStateOf(false) }
    val defs = s.tiles.mapNotNull { Tiles.get(it) }
    val shown = if (allRows) defs else defs.take(8)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(40.dp)).background(p.card)
            .animateContentSize().padding(top = 22.dp, bottom = 10.dp, start = 14.dp, end = 14.dp),
    ) {
        shown.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { Tile(host, it, states[it.id], 60.dp, s.showTileLabels) }
                repeat(4 - row.size) { Spacer(Modifier.size(60.dp)) }
            }
        }
        // Handle: shows the rest of the tiles, like Samsung's pager.
        Box(
            Modifier.fillMaxWidth().height(18.dp).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { allRows = !allRows },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(p.subText.copy(alpha = 0.6f)))
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(40.dp)).background(p.card).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrightnessSlider(host, showMenu = false, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(14.dp))
        val dark = states["dark_mode"] == true
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(if (dark) p.tileOn else p.tileOff)
                .clickable { host.toggleTile("dark_mode") },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.DarkMode, "Dark mode", tint = if (dark) p.tileOnIcon else p.tileOffIcon, modifier = Modifier.size(28.dp)) }
    }
    if (s.showMediaCard) {
        Spacer(Modifier.height(10.dp))
        MediaCard(host, media)
    }
}

@Composable
private fun BrightnessSlider(host: OverlayService, showMenu: Boolean, modifier: Modifier) {
    val p = LocalPalette.current
    var value by remember { mutableFloatStateOf(host.brightness() / 255f) }
    var menu by remember { mutableStateOf(false) }
    var warned by remember { mutableStateOf(false) }
    fun set(f: Float) {
        value = f.coerceIn(0.02f, 1f)
        if (!host.setBrightness((value * 255).toInt()) && !warned) {
            warned = true
            host.toast("Allow \"Modify system settings\" for Shade in its setup screen to use the slider.")
        }
    }
    BoxWithConstraints(
        modifier.height(52.dp).clip(RoundedCornerShape(26.dp)).background(p.sliderTrack)
            .pointerInput(Unit) {
                detectTapGestures { pos -> set(pos.x / size.width) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> set(change.position.x / size.width) }
            },
    ) {
        val fill = maxWidth * value
        Box(Modifier.fillMaxHeight().width(fill.coerceAtLeast(52.dp)).clip(RoundedCornerShape(26.dp)).background(p.sliderFill))
        Icon(
            Icons.Filled.LightMode, null, tint = p.sliderIcon,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 15.dp).size(22.dp),
        )
        if (showMenu) {
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MoreVert, "Brightness options", tint = p.text)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    var adaptive by remember { mutableStateOf(host.adaptiveBrightness()) }
                    DropdownMenuItem(
                        text = { Text("Adaptive brightness") },
                        trailingIcon = { Switch(checked = adaptive, onCheckedChange = null) },
                        onClick = {
                            host.setAdaptiveBrightness(!adaptive)
                            adaptive = host.adaptiveBrightness()
                        },
                    )
                    DropdownMenuItem(text = { Text("Display settings") }, onClick = {
                        menu = false
                        host.launch(android.content.Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS))
                    })
                }
            }
        }
    }
}

@Composable
private fun MediaCard(host: OverlayService, media: MediaState?) {
    val p = LocalPalette.current
    val app = ShadeApp.instance
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(40.dp)).background(p.card).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (media == null || media.title.isEmpty()) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable { app.media.playPause(host) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = p.text)
                Spacer(Modifier.width(10.dp))
                Text("Play last song", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            media.art?.let {
                Image(it, null, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(media.title, color = p.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(media.artist.ifEmpty { media.appName }, color = p.subText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MediaButton(Icons.Filled.SkipPrevious, "Previous") { app.media.previous() }
            MediaButton(if (media.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/pause") { app.media.playPause(host) }
            MediaButton(Icons.Filled.SkipNext, "Next") { app.media.next() }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "Media output", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(p.chip)
                .clickable { host.openMediaOutput(media?.pkg) }.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun MediaButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = LocalPalette.current.text)
    }
}

/** What the panel lists, with per-app rules and the lock screen applied. */
private sealed interface Row2 {
    data class Header(val text: String) : Row2
    data class Single(val item: NotifItem) : Row2
    data class Group(val items: List<NotifItem>) : Row2
}

@Composable
private fun NotificationList(
    host: OverlayService,
    s: ShadeSettings,
    notifs: List<NotifItem>,
    media: MediaState?,
    modifier: Modifier,
) {
    val p = LocalPalette.current
    val app = ShadeApp.instance
    val rules by app.rules.flow.collectAsState()
    val locked = host.ui.locked
    val groupKeysWithChildren = remember(notifs) {
        notifs.filter { !it.isGroupSummary }.map { it.groupKey }.toSet()
    }
    val visible = notifs.filter { n ->
        val rule = rules[n.pkg]
        rule?.alert != AlertMode.HIDDEN &&
            !(n.isGroupSummary && n.groupKey in groupKeysWithChildren) &&
            !(n.isMedia && s.showMediaCard && media != null) &&
            !(locked && rule?.lockScreen == LockVisibility.HIDE)
    }
    fun hide(n: NotifItem) = locked && (s.lockHideContent || rules[n.pkg]?.lockScreen == LockVisibility.HIDE_CONTENT)
    fun silent(n: NotifItem) = n.isSilent || rules[n.pkg]?.alert == AlertMode.SILENT

    val rows = buildList<Row2> {
        val sections = if (s.separateSilent) {
            listOf(null to visible.filterNot(::silent), "Silent notifications" to visible.filter(::silent))
        } else {
            listOf(null to visible)
        }
        for ((title, items) in sections) {
            if (items.isEmpty()) continue
            title?.let { add(Row2.Header(it)) }
            if (s.groupByApp) {
                items.groupBy { it.pkg }.values.forEach { g ->
                    add(if (g.size == 1) Row2.Single(g[0]) else Row2.Group(g))
                }
            } else {
                items.forEach { add(Row2.Single(it)) }
            }
        }
    }

    Box(modifier.fillMaxWidth()) {
        if (rows.isEmpty()) {
            Text(
                "No notifications", color = p.text, fontSize = 20.sp,
                modifier = Modifier.align(Alignment.Center).padding(bottom = 60.dp),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { r ->
                    when (r) {
                        is Row2.Header -> "h:" + r.text
                        is Row2.Single -> r.item.key
                        is Row2.Group -> "g:" + r.items.first().key
                    }
                }) { r ->
                    when (r) {
                        is Row2.Header -> Text(
                            r.text, color = p.subText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 10.dp, top = 8.dp),
                        )
                        is Row2.Single -> NotifCard(host, r.item, s, hide(r.item))
                        is Row2.Group -> GroupCard(host, r.items, s, ::hide)
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        PillButton("Notification settings") { host.openNotificationSettings() }
                        if (visible.any { it.clearable }) PillButton("Clear") { host.clearAll() }
                    }
                }
            }
        }
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(
        text, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(p.pill).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    )
}

/** "Quick settings | Notifications" switcher at the bottom, as on One UI. */
@Composable
private fun BottomPill(expanded: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.clip(RoundedCornerShape(26.dp)).background(p.pill).padding(4.dp)) {
            listOf(true to "Quick settings", false to "Notifications").forEach { (value, label) ->
                val selected = expanded == value
                Text(
                    label, color = if (selected) p.text else p.subText, fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.clip(RoundedCornerShape(22.dp))
                        .background(if (selected) p.pillSelected else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onChange(value) }.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}
