package com.ironlog.app

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
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

/** A simple progress line chart. Tap a point to see its value. */
@Composable
fun LineChart(points: List<Pair<Long, Double>>, label: (Double) -> String) {
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (points.isEmpty()) {
            Text("No data yet – log this exercise to see your progress.", color = Sub)
            return@Column
        }
        val sel = points.getOrNull(selected) ?: points.last()
        Text(label(sel.second), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(Units.date(sel.first), color = Sub, fontSize = 13.sp)
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
