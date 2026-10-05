package com.shadeui.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadeui.app.data.ShadeSettings
import com.shadeui.app.notif.NotifItem
import com.shadeui.app.overlay.OverlayService

/** One notification card, One UI style. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotifCard(
    host: OverlayService,
    item: NotifItem,
    s: ShadeSettings,
    hideContent: Boolean,
    showHeader: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    var expanded by remember(item.key) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val replying = host.ui.replyKey == item.key
    val expandable = !hideContent && (item.bigText != null || item.messages.isNotEmpty() ||
        item.picture != null || item.actions.isNotEmpty() || item.text.length > 80)

    SwipeDismiss(enabled = item.clearable, onDismiss = { host.dismiss(item) }, modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(p.card)
                .combinedClickable(
                    onClick = { host.open(item) },
                    onLongClick = { menu = true },
                )
                .animateContentSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            if (showHeader) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppBadge(item, 20)
                    Spacer(Modifier.width(8.dp))
                    val meta = buildString {
                        append(item.appName)
                        if (!hideContent) item.subText?.let { append(" • ").append(it) }
                        if (s.showTimestamps && item.showTime) append(" • ").append(relativeTime(item.time))
                    }
                    Text(meta, color = p.subText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (expandable) {
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = p.subText,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .combinedClickable(onClick = { expanded = !expanded }),
                        )
                    }
                }
                Spacer(Modifier.size(6.dp))
            }
            if (hideContent) {
                Text("Content hidden", color = p.text, fontSize = 15.sp)
            } else {
                Row {
                    Column(Modifier.weight(1f)) {
                        if (item.title.isNotEmpty()) {
                            Text(item.title, color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = if (expanded) 3 else 1, overflow = TextOverflow.Ellipsis)
                        }
                        val body = if (expanded) item.bigText ?: item.text else item.text
                        if (expanded && item.messages.isNotEmpty()) {
                            item.messages.forEach { m ->
                                Text(
                                    (m.sender?.let { "$it: " } ?: "") + m.text,
                                    color = p.text.copy(alpha = 0.9f), fontSize = 14.sp,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        } else if (body.isNotEmpty()) {
                            Text(
                                body, color = p.text.copy(alpha = 0.9f), fontSize = 14.sp,
                                maxLines = if (expanded) 12 else s.maxLinesCollapsed,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    item.largeIcon?.let {
                        Spacer(Modifier.width(10.dp))
                        Image(it, null, Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                    }
                }
                if (expanded) {
                    item.picture?.let {
                        Image(
                            it, null,
                            Modifier.padding(top = 10.dp).fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                if (item.hasProgress) {
                    if (item.progressIndeterminate) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
                    } else {
                        LinearProgressIndicator(
                            progress = { item.progress.toFloat() / item.progressMax.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                    }
                }
                if ((expanded || item.actions.any { it.isReply }) && item.actions.isNotEmpty() && !replying) {
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.actions.take(3).forEach { a ->
                            TextButton(onClick = {
                                if (a.isReply) {
                                    host.ui.replyKey = item.key
                                } else {
                                    host.runAction(item, a)
                                }
                            }) {
                                Text(a.title, color = p.text, fontSize = 14.sp, maxLines = 1)
                            }
                        }
                    }
                }
                if (replying) ReplyField(host, item)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            listOf(15 to "Snooze 15 minutes", 60 to "Snooze 1 hour", 120 to "Snooze 2 hours").forEach { (m, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; host.snooze(item, m) })
            }
            DropdownMenuItem(text = { Text("Shade rules for ${item.appName}") }, onClick = { menu = false; host.openShadeRules(item.pkg) })
            DropdownMenuItem(text = { Text("Samsung notification settings") }, onClick = { menu = false; host.openAppNotificationSettings(item.pkg) })
        }
    }
}

@Composable
private fun ReplyField(host: OverlayService, item: NotifItem) {
    val p = LocalPalette.current
    val action = item.actions.firstOrNull { it.isReply } ?: return
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val send = {
        if (text.isNotBlank()) host.reply(item, action, text.trim())
    }
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Reply") },
            singleLine = false,
            maxLines = 4,
            shape = RoundedCornerShape(22.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = p.chip, unfocusedContainerColor = p.chip,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = p.text, unfocusedTextColor = p.text,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        IconButton(onClick = send) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = p.text)
        }
    }
}

/** Small round app badge: the notification's own icon on its accent colour, or the app icon. */
@Composable
fun AppBadge(item: NotifItem, sizeDp: Int) {
    val small = item.smallIcon
    if (small != null && item.accentColor != 0) {
        Box(
            Modifier.size(sizeDp.dp).clip(CircleShape).background(Color(item.accentColor)),
            contentAlignment = Alignment.Center,
        ) {
            Image(small, null, Modifier.size((sizeDp * 0.62f).dp), colorFilter = ColorFilter.tint(Color.White))
        }
    } else if (item.appIcon != null) {
        Image(item.appIcon, null, Modifier.size(sizeDp.dp).clip(CircleShape))
    } else if (small != null) {
        Image(small, null, Modifier.size(sizeDp.dp), colorFilter = ColorFilter.tint(LocalPalette.current.text))
    }
}

/** Several notifications from one app, stacked like One UI; tap the header to expand. */
@Composable
fun GroupCard(host: OverlayService, items: List<NotifItem>, s: ShadeSettings, hide: (NotifItem) -> Boolean) {
    val p = LocalPalette.current
    var open by remember(items.first().pkg) { mutableStateOf(false) }
    val first = items.first()
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        if (!open) {
            Box {
                // Stacked edge behind the top card hints at the group.
                Box(
                    Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp).fillMaxWidth().heightIn(min = 60.dp)
                        .clip(RoundedCornerShape(26.dp)).background(p.card.copy(alpha = 0.55f)),
                )
                Column(Modifier.padding(bottom = 8.dp)) {
                    NotifCard(host, first, s, hide(first))
                }
            }
            Text(
                "+${items.size - 1} more from ${first.appName}",
                color = p.subText, fontSize = 13.sp,
                modifier = Modifier
                    .padding(start = 18.dp, top = 2.dp, bottom = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickableCompat { open = true }
                    .padding(6.dp),
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppBadge(first, 22)
                Spacer(Modifier.width(8.dp))
                Text("${first.appName} (${items.size})", color = p.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { open = false }) { Text("Collapse", color = p.subText) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEach { NotifCard(host, it, s, hide(it), showHeader = true) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier =
    this.then(Modifier.combinedClickable(onClick = onClick))
