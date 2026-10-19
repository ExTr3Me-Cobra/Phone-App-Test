package com.ironlog.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One movable section of a screen. [label] names it; hidden panels keep their place in the order. */
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

/** Everything about the panel being moved. Plain fields are refreshed every frame; the states drive drawing. */
private class PanelDrag {
    var held by mutableStateOf<String?>(null)
    var settling by mutableStateOf(false)
    var fingerY by mutableFloatStateOf(0f)    // the finger, in screen coordinates
    var startFinger = 0f
    var startScroll by mutableIntStateOf(0)
    var frozenTarget = -1
    val settle = Animatable(0f)
    // Refreshed every composition.
    var keys: List<String> = emptyList()
    var all: List<String> = emptyList()
    var spacing = 0f
    val tops = HashMap<String, Float>()
    val heights = HashMap<String, Float>()
    val nodes = HashMap<String, LayoutCoordinates>()
    var outer: LayoutCoordinates? = null

    fun from() = held?.let { keys.indexOf(it) } ?: -1
    fun h(k: String) = heights[k] ?: 0f

    /** How far the held panel has moved from its place: with the finger, plus however far the page has scrolled. */
    fun dy(scroll: Int): Float = if (settling) settle.value else (fingerY - startFinger) + (scroll - startScroll)

    /** The slot the held panel would drop into: past the middle of a neighbour, it takes that neighbour's place. */
    fun target(scroll: Int): Int {
        val f = from()
        if (f < 0) return -1
        if (settling) return frozenTarget
        val c = (tops[keys[f]] ?: 0f) + h(keys[f]) / 2 + dy(scroll)
        var t = f
        for (j in f + 1 until keys.size) if (c > (tops[keys[j]] ?: 0f) + h(keys[j]) / 2) t = j
        for (j in f - 1 downTo 0) if (c < (tops[keys[j]] ?: 0f) + h(keys[j]) / 2) t = j
        return t
    }

    /** How far panel [j] slides aside to make room. */
    fun shift(j: Int, t: Int): Float {
        val f = from()
        if (f < 0 || j == f) return 0f
        val room = h(keys[f]) + spacing
        return when {
            j in (f + 1)..t -> -room
            j in t until f -> room
            else -> 0f
        }
    }

    /** Where the held panel lands for slot [t], measured from where it started. */
    fun landing(t: Int): Float {
        val f = from()
        return when {
            t > f -> ((f + 1)..t).sumOf { (h(keys[it]) + spacing).toDouble() }.toFloat()
            t < f -> -(t until f).sumOf { (h(keys[it]) + spacing).toDouble() }.toFloat()
            else -> 0f
        }
    }
}

/**
 * A scrolling column of panels you can rearrange: hold any panel to pick it up, drag it (the page scrolls when you
 * reach the top or bottom edge) and let go to drop it in place. The order is saved under [layout], so every screen
 * that uses the same layout name shows the same order.
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
    val saved = p.layouts[layout].orEmpty()
    // The order you just dropped, shown straight away while it's being saved.
    var dropped by remember(layout) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(saved) { dropped = null }
    val all = orderPanels(dropped ?: saved, panels)
    val shown = all.filter { it.visible }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val st = remember { PanelDrag() }
    val keys = shown.map { it.key }
    SideEffect {
        st.keys = keys
        st.all = all.map { it.key }
        st.spacing = with(density) { spacing.toPx() }
    }
    st.keys = keys
    val target by remember { derivedStateOf { st.target(scroll.value) } }

    // A light tick each time the panel moves into a new slot.
    var lastTarget by remember { mutableIntStateOf(-1) }
    LaunchedEffect(target) {
        if (st.held != null && !st.settling && lastTarget >= 0 && target != lastTarget)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        lastTarget = target
    }

    // Near the top or bottom edge the page scrolls, faster the closer you get.
    LaunchedEffect(st.held, st.settling) {
        if (st.held == null || st.settling) return@LaunchedEffect
        val zone = with(density) { 110.dp.toPx() }
        val fastest = with(density) { 9.dp.toPx() }
        while (isActive) {
            withFrameNanos { }
            val o = st.outer ?: continue
            val y = st.fingerY - o.positionInRoot().y
            val h = o.size.height.toFloat()
            val depth = when {
                y < zone -> -((zone - y) / zone)
                y > h - zone -> (y - (h - zone)) / zone
                else -> 0f
            }.coerceIn(-1f, 1f)
            if (depth != 0f) scroll.scrollBy(fastest * depth * kotlin.math.abs(depth))
        }
    }

    fun drop() {
        if (st.held == null || st.settling) return
        val f = st.from()
        val t = st.target(scroll.value)
        val now = st.dy(scroll.value)
        st.frozenTarget = t
        st.settling = true
        scope.launch {
            // Glide into the gap, then make the new order real in the same frame the panels stop moving.
            st.settle.snapTo(now)
            st.settle.animateTo(st.landing(t), spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
            if (t != f && t >= 0) {
                val moved = st.keys.toMutableList().also { it.add(t, it.removeAt(f)) }.iterator()
                val vis = st.keys.toSet()
                val order = st.all.map { if (it in vis) moved.next() else it }
                dropped = order
                val cur = Store.profile.value
                Store.setProfile(cur.copy(layouts = cur.layouts + (layout to order)))
            }
            st.held = null
            st.settling = false
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Box(modifier.onGloballyPositioned { st.outer = it }) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).then(contentModifier), verticalArrangement = Arrangement.spacedBy(spacing)) {
            top()
            shown.forEachIndexed { i, pn ->
                key(pn.key) {
                    val isHeld = st.held == pn.key
                    val dragging = st.held != null
                    val slide by animateFloatAsState(
                        if (dragging && !isHeld) st.shift(i, target) else 0f,
                        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow), label = "slide",
                    )
                    val lift by animateFloatAsState(if (isHeld && !st.settling) 1f else 0f, tween(if (isHeld && !st.settling) 160 else 220), label = "lift")
                    val fade by animateFloatAsState(if (dragging && !isHeld) 0.55f else 1f, tween(200), label = "fade")
                    val cardShape = RoundedCornerShape(14.dp)
                    Box(
                        Modifier.fillMaxWidth()
                            .onGloballyPositioned { c ->
                                st.nodes[pn.key] = c
                                st.tops[pn.key] = c.positionInParent().y
                                st.heights[pn.key] = c.size.height.toFloat()
                            }
                            .holdToMove(
                                layout + "|" + pn.key,
                                onStart = { at ->
                                    if (st.held != null || st.settling) return@holdToMove false
                                    val node = st.nodes[pn.key] ?: return@holdToMove false
                                    st.fingerY = node.localToRoot(at).y
                                    st.startFinger = st.fingerY
                                    st.startScroll = scroll.value
                                    st.held = pn.key
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    true
                                },
                                onMove = { at -> st.nodes[pn.key]?.let { st.fingerY = it.localToRoot(at).y } },
                                onEnd = { drop() },
                            )
                            .zIndex(if (isHeld) 1f else 0f)
                            .graphicsLayer {
                                // Read here (not while composing), so following the finger only redraws.
                                translationY = when {
                                    st.held == pn.key -> st.dy(scroll.value)
                                    st.held == null -> 0f
                                    else -> slide
                                }
                                val s = 1f + 0.035f * lift
                                scaleX = s; scaleY = s
                                shadowElevation = 24.dp.toPx() * lift
                                shape = cardShape
                                clip = false
                                alpha = fade
                            }
                            .background(Color(0xFF17171B).copy(alpha = 0.96f * lift), cardShape)
                            .border(1.5.dp, Blue.copy(alpha = 0.7f * lift), cardShape),
                    ) { pn.content() }
                }
            }
            bottom()
        }
    }
}

/**
 * Hold still on a panel (twice the usual long-press time), then drag. Watches the touch before the panel's own buttons, chart or scrolling do, so a
 * tap, a swipe or a chart drag works as usual, and once the hold is recognised nothing inside the panel reacts.
 * [onStart] returns false to ignore the hold.
 */
private fun Modifier.holdToMove(key: Any, onStart: (Offset) -> Boolean, onMove: (Offset) -> Unit, onEnd: () -> Unit) =
    pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var last = down.position
            // Lifting or moving before the hold time means it's a tap, scroll or swipe: leave it alone.
            val cancelled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis * 2) { // twice the usual hold, so it only starts on purpose
                while (true) {
                    val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    if (c == null || !c.pressed || c.isConsumed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                    last = c.position
                }
                true
            }
            if (cancelled != null) return@awaitEachGesture
            if (!onStart(last)) return@awaitEachGesture
            try {
                while (true) {
                    val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    c.consume()
                    if (!c.pressed) break
                    onMove(c.position)
                }
            } finally {
                onEnd()
            }
        }
    }
