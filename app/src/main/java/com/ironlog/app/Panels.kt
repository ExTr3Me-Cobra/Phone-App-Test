package com.ironlog.app

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** One movable section of a screen. [label] is what you see while moving it; hidden panels keep their place in the order. */
class Panel(val key: String, val label: String, val visible: Boolean = true, val content: @Composable () -> Unit)

/** The panels in the order saved for [layout]; panels the saved order doesn't know yet slot in after their default neighbour. */
fun orderPanels(saved: List<String>, panels: List<Panel>): List<Panel> {
    val byKey = panels.associateBy { it.key }
    val out = saved.filter { it in byKey }.distinct().toMutableList()
    panels.forEachIndexed { i, pn ->
        if (pn.key !in out) {
            val prev = panels.subList(0, i).lastOrNull { it.key in out }?.key
            out.add(if (prev == null) 0 else out.indexOf(prev) + 1, pn.key)
        }
    }
    return out.map { byKey.getValue(it) }
}

/**
 * A scrolling column of panels you can rearrange: hold any panel, then drag. The order is saved under [layout],
 * so every screen that uses the same layout name shows the same order.
 */
@Composable
fun PanelColumn(
    layout: String,
    panels: List<Panel>,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    scroll: ScrollState = rememberScrollState(),
    top: @Composable ColumnScope.() -> Unit = {},
    bottom: @Composable ColumnScope.() -> Unit = {},
) {
    val p by Store.profile.collectAsState()
    val all = orderPanels(p.layouts[layout].orEmpty(), panels)
    val shown = all.filter { it.visible }
    val haptic = LocalHapticFeedback.current
    val rowH = with(LocalDensity.current) { 48.dp.toPx() }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragY by remember { mutableStateOf(0f) }   // finger, in the screen's coordinates
    var listTop by remember { mutableStateOf(0f) } // where the short list starts
    var outer by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val where = remember { mutableMapOf<String, LayoutCoordinates>() }
    fun dragTo() = ((dragY - listTop) / rowH).toInt().coerceIn(0, shown.lastIndex)
    val latest by rememberUpdatedState(all to shown)

    fun drop() {
        val from = dragFrom ?: return
        dragFrom = null
        val (full, vis) = latest
        val to = dragTo()
        if (to == from) return
        // Move it among the panels you can see; hidden ones keep their slots.
        val moved = vis.map { it.key }.toMutableList().also { it.add(to, it.removeAt(from)) }.iterator()
        val visKeys = vis.map { it.key }.toSet()
        val order = full.map { if (it.key in visKeys) moved.next() else it.key }
        val now = Store.profile.value
        Store.setProfile(now.copy(layouts = now.layouts + (layout to order)))
    }

    Box(modifier.onGloballyPositioned { outer = it }) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).then(contentModifier), verticalArrangement = Arrangement.spacedBy(spacing)) {
            top()
            shown.forEachIndexed { i, pn ->
                key(pn.key) {
                    Box(Modifier.fillMaxWidth().onGloballyPositioned { where[pn.key] = it }.holdToMove(
                        layout + "|" + shown.joinToString { it.key } + "@" + i,
                        onStart = { at ->
                            val o = outer ?: return@holdToMove
                            val c = where[pn.key] ?: return@holdToMove
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            dragY = o.localPositionOf(c, at).y
                            val room = (o.size.height - rowH * shown.size).coerceAtLeast(0f)
                            listTop = (dragY - rowH * i - rowH / 2).coerceIn(0f, room)
                            dragFrom = i
                        },
                        onMove = { dy -> dragY += dy },
                        onEnd = { ok -> if (ok) drop() else dragFrom = null },
                    )) { pn.content() }
                }
            }
            bottom()
        }

        // While you move a panel the screen shrinks to just the panel names.
        dragFrom?.let { from ->
            val to = dragTo()
            val order = shown.indices.toMutableList().also { it.add(to, it.removeAt(from)) }
            Box(Modifier.matchParentSize().background(Bg)) {
                order.forEachIndexed { pos, idx ->
                    val held = idx == from
                    val y = if (held) dragY - rowH / 2 else listTop + pos * rowH
                    Row(
                        Modifier.offset { IntOffset(0, y.toInt()) }.fillMaxWidth().height(48.dp)
                            .padding(horizontal = 12.dp, vertical = 3.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (held) Blue.copy(alpha = 0.35f) else Card).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("≡   " + shown[idx].label, fontWeight = if (held) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/**
 * Hold still on a panel, then drag. Watches the touch before the panel's own buttons, chart or scrolling do, so a
 * tap, a swipe or a chart drag works as usual, and once the hold is recognised nothing inside the panel reacts.
 */
private fun Modifier.holdToMove(key: Any, onStart: (Offset) -> Unit, onMove: (Float) -> Unit, onEnd: (Boolean) -> Unit) =
    pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var last = down.position
            // Lifting or moving before the hold time means it's a tap, scroll or swipe: leave it alone.
            val cancelled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    if (c == null || !c.pressed || c.isConsumed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                    last = c.position
                }
                true
            }
            if (cancelled != null) return@awaitEachGesture
            onStart(last)
            var done = false
            try {
                while (true) {
                    val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    val dy = c.position.y - c.previousPosition.y
                    c.consume()
                    if (!c.pressed) { done = true; onEnd(true); break }
                    onMove(dy)
                }
            } finally {
                if (!done) onEnd(false)
            }
        }
    }
