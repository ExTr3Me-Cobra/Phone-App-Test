package com.ironlog.app

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** A progress line chart for a chosen stretch of time (default: the last 3 months). Tap a point to see its value. */
@Composable
fun LineChart(all: List<Pair<Long, Double>>, label: (Double) -> String) {
    // How far back to show: a number (1–99) and a unit, each on its own scroll wheel.
    var count by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(3) }
    var unit by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(1) } // 0 weeks, 1 months, 2 years
    val since = remember(count, unit) {
        val today = java.time.LocalDate.now()
        when (unit) { 0 -> today.minusWeeks(count.toLong()); 2 -> today.minusYears(count.toLong()); else -> today.minusMonths(count.toLong()) }
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    val points = remember(all, since) { all.filter { it.first >= since } }
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (all.isEmpty()) {
            Text("No data yet – log this exercise to see your progress.", color = Sub)
            return@Column
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val sel = points.getOrNull(selected) ?: points.lastOrNull()
                if (sel != null) {
                    Text(label(sel.second), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(Units.date(sel.first), color = Sub, fontSize = 13.sp)
                } else Text("Nothing logged in this time.", color = Sub, fontSize = 14.sp)
            }
            Text("Last", color = Sub, fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
            Wheel((1..99).map { "$it" }, count - 1, Modifier.width(52.dp)) { count = it + 1 }
            Wheel(listOf("Week", "Month", "Year").map { if (count == 1) it else it + "s" }, unit, Modifier.width(84.dp)) { unit = it }
        }
        if (points.isEmpty()) return@Column
        val minV = points.minOf { it.second }
        val maxV = points.maxOf { it.second }
        val span = (maxV - minV).takeIf { it > 0 } ?: maxOf(1.0, maxV * 0.2)
        val lo = minV - span * 0.15
        val hi = maxV + span * 0.15
        Canvas(
            Modifier.fillMaxWidth().height(200.dp).padding(top = 10.dp).pointerInput(points) {
                detectTapGestures { pos ->
                    val left = 44.dp.toPx(); val w = size.width - left - 8.dp.toPx()
                    val xs = points.indices.map { i -> left + if (points.size == 1) w / 2 else w * i / (points.size - 1) }
                    selected = xs.indices.minByOrNull { abs(xs[it] - pos.x) } ?: selected
                }
            },
        ) {
            val left = 44.dp.toPx(); val bottom = size.height - 20.dp.toPx(); val top = 8.dp.toPx()
            val w = size.width - left - 8.dp.toPx()
            fun x(i: Int) = left + if (points.size == 1) w / 2 else w * i / (points.size - 1)
            fun y(v: Double) = (bottom - (v - lo) / (hi - lo) * (bottom - top)).toFloat()
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Sub.toArgb(); textSize = 11.sp.toPx() }
            for (g in 0..3) {
                val v = lo + (hi - lo) * g / 3
                drawLine(Card2, Offset(left, y(v)), Offset(size.width, y(v)), 1f)
                drawIntoCanvas { it.nativeCanvas.drawText(Units.num(Math.round(v * 10) / 10.0), 0f, y(v) + 4.dp.toPx(), text) }
            }
            drawIntoCanvas {
                it.nativeCanvas.drawText(Units.shortDate(points.first().first), left, size.height - 2.dp.toPx(), text)
                if (points.size > 1) {
                    val s = Units.shortDate(points.last().first)
                    it.nativeCanvas.drawText(s, size.width - text.measureText(s), size.height - 2.dp.toPx(), text)
                }
            }
            val path = Path()
            points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(i), y(p.second)) else path.lineTo(x(i), y(p.second)) }
            drawPath(path, Blue, style = Stroke(3.dp.toPx()))
            points.forEachIndexed { i, p ->
                drawCircle(if (i == selected) androidx.compose.ui.graphics.Color.White else Blue, if (i == selected) 6.dp.toPx() else 4.dp.toPx(), Offset(x(i), y(p.second)))
            }
        }
    }
}

/**
 * A scroll wheel: drag up or down and it snaps to the item in the middle (highlighted), like a phone's time picker.
 */
@Composable
fun Wheel(items: List<String>, selected: Int, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    val rowH = 30.dp
    val state = androidx.compose.foundation.lazy.rememberLazyListState(selected)
    val fling = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(state)
    val itemPx = with(androidx.compose.ui.platform.LocalDensity.current) { rowH.toPx() }
    val centre by remember {
        androidx.compose.runtime.derivedStateOf {
            (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0).coerceIn(0, items.lastIndex)
        }
    }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.compose.runtime.LaunchedEffect(centre) {
        if (centre != selected) { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); onPick(centre) }
    }
    Box(modifier.height(rowH * 3).clip(RoundedCornerShape(10.dp)).background(Card), contentAlignment = Alignment.Center) {
        // The band marking the chosen row.
        Box(Modifier.fillMaxWidth().height(rowH).background(Blue.copy(alpha = 0.18f)))
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize(), state = state, flingBehavior = fling,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = rowH),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(items.size) { i ->
                val on = i == centre
                Box(Modifier.height(rowH).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(items[i], fontSize = if (on) 16.sp else 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) androidx.compose.ui.graphics.Color.White else Sub, maxLines = 1)
                }
            }
        }
    }
}
