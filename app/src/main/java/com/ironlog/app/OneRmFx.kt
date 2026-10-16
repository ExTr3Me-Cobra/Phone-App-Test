package com.ironlog.app

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** Developer: which animation 1 Rep Max mode uses for each moment, so you can try them and keep your favourites. */
object OneRmFx {
    private const val FILE = "onerm_fx.json"

    val ENTRY = listOf("Slam (drop from too big)", "Drop from above (bounce)", "Rise from below", "Zoom punch", "Shutter (opens from the middle)",
        "Fade through black", "Whip in from the side", "None")
    val ARRIVAL = listOf("Jolt + red flash", "Earthquake", "Heartbeat (double red pulse)", "Gold shockwave", "Red vignette breathe",
        "Lightning flash", "None", "Fire ring", "Ember shower", "Inferno (fire ring + embers + jolt)")
    val TICK = listOf("Stamp + rattle", "Pop + gold burst", "Flip", "Slam + red row flash", "Green glow", "Hard shake", "None",
        "Fire ring burst", "Fire ring + stamp")
    val SHINE = listOf("Gold glint + ember glow", "Ember glow only", "Fast glint", "None", "Flame flicker")

    var entry by mutableIntStateOf(0)
    var arrival by mutableIntStateOf(0)
    var tick by mutableIntStateOf(0)
    var shine by mutableIntStateOf(0)
    /** Test: every exercise in the 1 Rep Max list rattles like an earthquake when it opens. */
    var listQuake by mutableStateOf(false)
    /** Set by the developer button on the Workout tab: Settings scrolls straight to these pickers. */
    var jump by mutableStateOf(false)
    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        runCatching {
            val o = JSONObject(Store.readFile(FILE) ?: "{}")
            entry = o.optInt("entry").coerceIn(ENTRY.indices)
            arrival = o.optInt("arrival").coerceIn(ARRIVAL.indices)
            tick = o.optInt("tick").coerceIn(TICK.indices)
            shine = o.optInt("shine").coerceIn(SHINE.indices)
            listQuake = o.optBoolean("quake")
        }
    }

    fun save() = Store.writeFile(FILE, JSONObject().put("entry", entry).put("arrival", arrival).put("tick", tick).put("shine", shine).put("quake", listQuake).toString())

    /** Going into a 1 Rep Max screen. */
    fun enter(): ContentTransform = when (entry) {
        1 -> slideInVertically(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) { -it } togetherWith
            fadeOut(tween(200))
        2 -> (slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200))) togetherWith
            (scaleOut(tween(380), targetScale = 0.92f) + fadeOut(tween(300)))
        3 -> (scaleIn(spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), initialScale = 0.55f) + fadeIn(tween(120))) togetherWith
            fadeOut(tween(150))
        4 -> (expandVertically(tween(360, easing = FastOutSlowInEasing), expandFrom = Alignment.CenterVertically) + fadeIn(tween(200))) togetherWith
            (shrinkVertically(tween(300), shrinkTowards = Alignment.CenterVertically) + fadeOut(tween(250)))
        5 -> fadeIn(tween(260, delayMillis = 240)) togetherWith fadeOut(tween(200))
        6 -> (slideInHorizontally(tween(220, easing = FastOutLinearInEasing)) { it } + scaleIn(tween(260), initialScale = 0.95f)) togetherWith
            (slideOutHorizontally(tween(220, easing = FastOutLinearInEasing)) { -it / 2 } + fadeOut(tween(200)))
        7 -> fadeIn(tween(0)) togetherWith fadeOut(tween(0))
        else -> (scaleIn(tween(320, easing = EaseOutBack), initialScale = 1.14f) + fadeIn(tween(160))) togetherWith
            (scaleOut(tween(260), targetScale = 0.9f) + fadeOut(tween(220)))
    }
}

/** Developer settings: a picker for each 1 Rep Max animation. */
@Composable
fun OneRmFxSettings() {
    OneRmFx.load()
    val req = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    androidx.compose.runtime.LaunchedEffect(OneRmFx.jump) {
        if (OneRmFx.jump) { kotlinx.coroutines.delay(400); runCatching { req.bringIntoView() }; OneRmFx.jump = false }
    }
    Column(Modifier.bringIntoViewRequester(req), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.Text("1 Rep Max animations", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        androidx.compose.material3.Text("Try each one, then open 1 Rep Max mode to see it.", color = Sub, fontSize = 13.sp)
        FxPicker("Opening 1 Rep Max", OneRmFx.ENTRY, OneRmFx.entry) { OneRmFx.entry = it; OneRmFx.save() }
        FxPicker("When a test opens", OneRmFx.ARRIVAL, OneRmFx.arrival) { OneRmFx.arrival = it; OneRmFx.save() }
        FxPicker("Ticking a set", OneRmFx.TICK, OneRmFx.tick) { OneRmFx.tick = it; OneRmFx.save() }
        FxPicker("Gold buttons", OneRmFx.SHINE, OneRmFx.shine) { OneRmFx.shine = it; OneRmFx.save() }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                androidx.compose.material3.Text("Earthquake on open (test)", fontSize = 14.sp)
                androidx.compose.material3.Text("Every exercise in the 1 Rep Max list rattles violently when it opens.", color = Sub, fontSize = 12.sp)
            }
            androidx.compose.material3.Switch(OneRmFx.listQuake, { OneRmFx.listQuake = it; OneRmFx.save() })
        }
        GreyButton("Open 1 Rep Max to try them", textColor = Blue) { Nav.go(Route.OneRmList) }
    }
}

@Composable
private fun FxPicker(label: String, options: List<String>, chosen: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Text(label, Modifier.weight(0.8f), fontSize = 14.sp)
        Box(Modifier.weight(1.2f)) {
            GreyButton(options[chosen] + " ▾") { open = true }
            DropdownMenu(open, { open = false }) {
                options.forEachIndexed { i, o ->
                    DropdownMenuItem({ androidx.compose.material3.Text((if (i == chosen) "✓  " else "     ") + o) }, { onPick(i); open = false })
                }
            }
        }
    }
}

// ---------------- Fire, in the 1 Rep Max red and gold ----------------

private val FireGold = androidx.compose.ui.graphics.Color(0xFFFFD36A)
private val FireOrange = androidx.compose.ui.graphics.Color(0xFFFF6A00)
private val FireRed = androidx.compose.ui.graphics.Color(0xFFB30000)

/** A repeatable 0–1 "random" number, so flames flicker the same way each frame step. */
private fun rnd(i: Int, salt: Float = 0f): Float {
    val v = kotlin.math.sin(i * 12.9898f + salt * 78.233f) * 43758.547f
    return v - kotlin.math.floor(v)
}

/**
 * A ring of fire at [radius] around [center]: tongues of flame (gold at the root, orange, fading to red) flickering
 * outward, with a hot glow under them. [p] is how far through the effect we are (0–1); it fades as p reaches 1.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.fireRing(center: androidx.compose.ui.geometry.Offset, radius: Float, p: Float, flame: Float) {
    val a = (1f - p).coerceIn(0f, 1f)
    if (a <= 0f || radius <= 0f) return
    val outer = radius + flame * 1.6f
    // The glow.
    drawCircle(
        androidx.compose.ui.graphics.Brush.radialGradient(
            0f to androidx.compose.ui.graphics.Color.Transparent,
            ((radius - flame * 0.4f) / outer).coerceIn(0f, 1f) to androidx.compose.ui.graphics.Color.Transparent,
            (radius / outer).coerceIn(0f, 1f) to FireOrange.copy(alpha = 0.45f * a),
            1f to androidx.compose.ui.graphics.Color.Transparent,
            center = center, radius = outer,
        ),
        radius = outer, center = center,
    )
    // The flames: each one flickers frame to frame.
    val n = 96
    val tick = (p * 30f).toInt()
    for (i in 0 until n) {
        val ang = (i * 2.0 * Math.PI / n + p * 0.8).toFloat()
        val h = flame * (0.35f + 0.65f * rnd(i, tick.toFloat()))
        val dx = kotlin.math.cos(ang); val dy = kotlin.math.sin(ang)
        val start = androidx.compose.ui.geometry.Offset(center.x + dx * radius, center.y + dy * radius)
        val end = androidx.compose.ui.geometry.Offset(center.x + dx * (radius + h), center.y + dy * (radius + h))
        drawLine(
            androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(FireGold.copy(alpha = a), FireOrange.copy(alpha = 0.9f * a), FireRed.copy(alpha = 0f)), start, end,
            ),
            start, end, strokeWidth = (flame * 0.16f).coerceAtLeast(2f), cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
    // A bright core line.
    drawCircle(FireGold.copy(alpha = 0.8f * a), radius = radius, center = center,
        style = androidx.compose.ui.graphics.drawscope.Stroke((flame * 0.12f).coerceAtLeast(2f)))
}

/** Glowing embers drifting up the screen and burning out. [p] runs 0–1. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.embers(p: Float, count: Int = 80) {
    if (p <= 0f || p >= 1f) return
    for (i in 0 until count) {
        val speed = 0.7f + rnd(i, 2f) * 0.9f
        val x = rnd(i, 1f) * size.width + kotlin.math.sin(p * 9f + i) * 14f * density
        val y = size.height * (1.05f + rnd(i, 3f) * 0.25f) - p * speed * size.height * 1.15f
        val life = (1f - p) * (0.55f + 0.45f * rnd(i, (p * 25f).toInt().toFloat()))
        val c = if (rnd(i, 4f) < 0.5f) FireGold else if (rnd(i, 5f) < 0.6f) FireOrange else FireRed
        val r = (1.5f + rnd(i, 6f) * 3.5f) * density
        drawCircle(c.copy(alpha = life * 0.35f), radius = r * 2.6f, center = androidx.compose.ui.geometry.Offset(x, y))
        drawCircle(c.copy(alpha = life), radius = r, center = androidx.compose.ui.geometry.Offset(x, y))
    }
}
