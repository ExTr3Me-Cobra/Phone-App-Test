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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** Developer: which animation 1 Rep Max mode uses for each moment, so you can try them and keep your favourites. */
object OneRmFx {
    private const val FILE = "onerm_fx.json"

    val ENTRY = listOf("Slam (drop from too big)", "Drop from above (bounce)", "Rise from below", "Zoom punch", "Shutter (opens from the middle)",
        "Fade through black", "Whip in from the side", "None")
    val ARRIVAL = listOf("Jolt + red flash", "Earthquake", "Heartbeat (double red pulse)", "Gold shockwave", "Red vignette breathe",
        "Lightning flash", "None")
    val TICK = listOf("Stamp + rattle", "Pop + gold burst", "Flip", "Slam + red row flash", "Green glow", "Hard shake", "None")
    val SHINE = listOf("Gold glint + ember glow", "Ember glow only", "Fast glint", "None")

    var entry by mutableIntStateOf(0)
    var arrival by mutableIntStateOf(0)
    var tick by mutableIntStateOf(0)
    var shine by mutableIntStateOf(0)
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
        }
    }

    fun save() = Store.writeFile(FILE, JSONObject().put("entry", entry).put("arrival", arrival).put("tick", tick).put("shine", shine).toString())

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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.Text("1 Rep Max animations", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        androidx.compose.material3.Text("Try each one, then open 1 Rep Max mode to see it.", color = Sub, fontSize = 13.sp)
        FxPicker("Opening 1 Rep Max", OneRmFx.ENTRY, OneRmFx.entry) { OneRmFx.entry = it; OneRmFx.save() }
        FxPicker("When a test opens", OneRmFx.ARRIVAL, OneRmFx.arrival) { OneRmFx.arrival = it; OneRmFx.save() }
        FxPicker("Ticking a set", OneRmFx.TICK, OneRmFx.tick) { OneRmFx.tick = it; OneRmFx.save() }
        FxPicker("Gold buttons", OneRmFx.SHINE, OneRmFx.shine) { OneRmFx.shine = it; OneRmFx.save() }
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
