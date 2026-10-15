package com.ironlog.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One exercise's rank: a chart of where your number sits among comparable lifters, and exactly who they are. */
@Composable
fun RankDetailScreen(id: String) {
    val p by Store.profile.collectAsState()
    val workouts by Store.workouts.collectAsState()
    val e = Store.exercise(id) ?: run { Nav.back(); return }
    val info = Standards.info(id)
    val fit = Standards.fit(id, p)
    val lv = Standards.levels(id, p)
    val r = remember(workouts, p, id) { Ranks.all(workouts, p)[id] }
    val reps = info?.reps == true
    val flat = info?.flat == true
    fun fmt(v: Double) = if (reps) "${v.toInt()} reps" else Units.weightU(Math.round(v).toDouble())

    Column(Modifier.fillMaxSize()) {
        TopBar(e.name)
        if (info == null || fit == null || lv == null) {
            Text("No rank data for this exercise with your current profile.", color = Sub, modifier = Modifier.padding(16.dp))
            return
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Your rank
            Text(starredName(e), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (r != null) Row(verticalAlignment = Alignment.CenterVertically) {
                RankBadge(r.tier, 64.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(r.tier.label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(r.tier.color))
                    Text("(${topLabel(r.percentile)})")
                    Text((if (reps) "Your best: " else if (flat) "Your best squeeze: " else "Your est. 1-rep max: ") + fmt(r.value), color = Sub, fontSize = 14.sp)
                }
            } else Text("Log this exercise to see where you sit.", color = Sub)

            // Chart: % of lifters you're stronger than, against the number you lift.
            val mine = r?.value
            val xMax = maxOf(lv[4] * 1.2, (mine ?: 0.0) * 1.1)
            val curve = remember(lv.toList(), xMax) { (0..80).map { i -> val x = xMax * i / 80; x to (Standards.percentile(id, x, p) ?: 0.0) } }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(12.dp)) {
                Text("Where you sit", fontWeight = FontWeight.Bold)
                Text(if (reps) "Across: reps.  Up: % of ${Standards.groupLabel(p)} you beat." else if (flat) "Across: best squeeze.  Up: % of ${if (p.male) "men" else "women"} aged 20–39 you beat." else "Across: est. 1-rep max.  Up: % of ${Standards.groupLabel(p)} you beat.",
                    color = Sub, fontSize = 12.sp)
                val line = Color.White
                val myColor = r?.let { Color(it.tier.color) } ?: Blue
                Canvas(Modifier.fillMaxWidth().height(230.dp).padding(top = 8.dp)) {
                    val left = 34.dp.toPx(); val bottom = size.height - 22.dp.toPx(); val top = 6.dp.toPx(); val right = size.width - 6.dp.toPx()
                    fun px(x: Double) = (left + (right - left) * (x / xMax)).toFloat()
                    fun py(pct: Double) = (bottom - (bottom - top) * (pct / 100)).toFloat()
                    // Rank bands: each 5% in its rank colour.
                    Tier.entries.forEach { t ->
                        drawRect(Color(t.color).copy(alpha = 0.13f), Offset(left, py(t.ordinal * 5.0 + 5)),
                            androidx.compose.ui.geometry.Size(right - left, py(t.ordinal * 5.0) - py(t.ordinal * 5.0 + 5)))
                    }
                    val text = android.graphics.Paint().apply { color = Sub.toArgb(); textSize = 10.sp.toPx(); isAntiAlias = true }
                    listOf(0, 25, 50, 75, 100).forEach { y ->
                        drawLine(Color.White.copy(alpha = 0.08f), Offset(left, py(y.toDouble())), Offset(right, py(y.toDouble())))
                        drawContext.canvas.nativeCanvas.drawText("$y%", 0f, py(y.toDouble()) + 4.dp.toPx(), text)
                    }
                    // The curve.
                    val path = Path()
                    curve.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(px(x), py(y)) else path.lineTo(px(x), py(y)) }
                    drawPath(path, line, style = Stroke(2.dp.toPx()))
                    // The five published levels.
                    lv.forEachIndexed { k, v ->
                        drawCircle(line, 4.dp.toPx(), Offset(px(v), py(Standards.percentile(id, v, p) ?: Standards.LEVEL_PCT[k])))
                        drawContext.canvas.nativeCanvas.drawText(fmt(v).substringBefore(" "), px(v) - 10.dp.toPx(), size.height - 4.dp.toPx(), text)
                    }
                    // You.
                    if (mine != null && r != null) {
                        drawLine(myColor, Offset(px(mine), top), Offset(px(mine), bottom), 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                        drawCircle(myColor, 7.dp.toPx(), Offset(px(mine), py(r.percentile)))
                        drawCircle(Color.Black, 3.dp.toPx(), Offset(px(mine), py(r.percentile)))
                    }
                }
                if (mine != null) Text("● You: ${fmt(mine)} – stronger than ${r!!.percentile.toInt()}%", color = myColor, fontSize = 13.sp)
            }

            // The numbers behind it.
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("The levels for you", fontWeight = FontWeight.Bold)
                Standards.LEVEL_NAMES.forEachIndexed { k, name ->
                    val reached = mine != null && mine >= lv[k]
                    Row {
                        Text((if (reached) "✓ " else "   ") + name, Modifier.weight(1f), color = if (reached) Color.White else Sub)
                        Text("beats ${(Standards.percentile(id, lv[k], p) ?: Standards.LEVEL_PCT[k]).toInt()}%", Modifier.width(90.dp), color = Sub, fontSize = 13.sp)
                        Text(fmt(lv[k]), Modifier.width(80.dp), fontWeight = FontWeight.Medium)
                    }
                }
                if (r != null && r.tier != Tier.entries.last()) {
                    val next = Tier.entries[r.tier.ordinal + 1]
                    Standards.valueFor(id, next.ordinal * 5.0, p)?.let {
                        Text("Next rank (${next.label}): ${fmt(it)}" + if (!reps) (if (flat) "" else " est. 1-rep max") else "", color = Color(next.color),
                            fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            // Exactly who you're compared with.
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Who you're compared with", fontWeight = FontWeight.Bold)
                @Composable fun Line(k: String, v: String) = Row {
                    Text(k, Modifier.width(110.dp), color = Sub, fontSize = 14.sp)
                    Text(v, Modifier.weight(1f), fontSize = 14.sp)
                }
                if (flat) {
                    Line("People", (if (p.male) "Men" else "Women") + " aged 20–39, everyone (not just lifters)")
                    Line("Body weight", "not used")
                    Line("Age", "not used")
                    Line("Data", "International grip-strength norms, 2.4 million adults (Tomkinson et al. 2025)")
                } else {
                Line("People", (if (p.male) "Men" else "Women") + (if (p.compareAll) " – everyone, lifters and non-lifters" else " who lift") + if (fit.estimatedFromMen) " (estimated from the other sex's data)" else "")
                Line("Body weight", p.bodyweight?.let { Units.weightU(it) } ?: "not set")
                Line("Age", p.age?.let { a -> "$a" + if (a in 25..40) " (no adjustment)" else " (levels × ${"%.2f".format(Standards.ageFactor(a))})" } ?: "not set")
                Line("Data", if (info.ai) "AI estimate: ${"%.0f".format(info.factor * 100)}% of the Strength Level \"${info.lift}\" standard"
                    else "Strength Level \"${info.lift}\"" + if (info.factor != 1.0) " × ${"%.2f".format(info.factor)} for this variation" else "")
                if (info.perDumbbell) Line("Weight", "per dumbbell")
                }
                Text(fit.compared, color = Sub, fontSize = 12.sp)
                if (!fit.exact) {
                    Text("Why it's an estimate:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    fit.problems.forEach { Text("• $it", color = Sub, fontSize = 12.sp) }
                }
                Text(r?.testedAt?.let {
                    "Your rank is set by your 1 Rep Max test on " + java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(it)) +
                        ". Workouts since then only count if they beat it."
                } ?: "Your rank uses your best in the 30 days up to the last time you did this exercise.", color = Sub, fontSize = 12.sp)
            }
            Spacer(30)
        }
    }
}
