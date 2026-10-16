package com.ironlog.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
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
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Developer: which animation 1 Rep Max mode uses for each moment, so you can try them and keep your favourites. */
object OneRmFx {
    private const val FILE = "onerm_fx.json"

    /** Plain screen transitions (used on their own for opening a test). */
    val TRANS = listOf("Normal slide", "Slam (drop from too big)", "Drop from above (bounce)", "Drop hard (no bounce)", "Rise from below",
        "Zoom punch", "Shutter (opens from the middle)", "Fade through black", "Whip in from the side", "None")

    /** Opening 1 Rep Max: a transition, and for the fire ones an effect timed to when the screen lands. */
    private class Entry(val name: String, val trans: Int, val effect: Int = -1, val at: Long = 0, val quake: Boolean = false)
    private val entries = listOf(
        Entry("Slam (drop from too big)", 1), Entry("Drop from above (bounce)", 2), Entry("Rise from below", 4), Entry("Zoom punch", 5),
        Entry("Shutter (opens from the middle)", 6), Entry("Fade through black", 7), Entry("Whip in from the side", 8), Entry("None", 9),
        Entry("Drop from above + fire landing + embers + earthquake", 3, 13, 300, true), Entry("Drop from above + flame wall", 3, 11, 300),
        Entry("Rise from below + fire from below", 4, 10, 0), Entry("Zoom punch + fire ring", 5, 7, 100),
        Entry("Fade through black + ember shower", 7, 8, 250), Entry("Ignite (inferno, then fade in)", 7, 9, 120),
        Entry("Slam + fire ring", 1, 7, 280), Entry("Drop from above (bounce) + fire landing + embers + earthquake", 2, 13, 260, true),
        Entry("Whip in + fire from below", 8, 10, 180),
    )
    val ENTRY = entries.map { it.name }

    /** Opening a test: the plain transitions, then fire landings (the effect plays as the test screen lands). */
    private val tests = TRANS.indices.map { Entry(TRANS[it], it) } + listOf(
        Entry("Drop from above + fire landing + embers", 3, 13, 300), Entry("Drop from above (bounce) + fire landing + embers", 2, 13, 260),
        Entry("Drop from above + flame wall", 3, 11, 300), Entry("Zoom punch + fire ring", 5, 7, 100), Entry("Slam + fire ring", 1, 7, 280),
        Entry("Rise from below + fire from below", 4, 10, 0), Entry("Fade through black + ember shower", 7, 8, 250),
    )
    val TEST = tests.map { it.name }
    fun testEffect(i: Int = testTrans): Pair<Int, Long>? = tests.getOrNull(i)?.takeIf { it.effect >= 0 }?.let { it.effect to it.at }

    /** Leaving a 1 Rep Max screen (back button or the arrow): no shaking or fire. */
    val LEAVE = listOf("Thrown upward", "Normal slide back", "Dropped out the bottom", "Fade out", "Shrink away")

    /** Effects over the whole screen (opening a test, and the fire landings). */
    val ARRIVAL = listOf("Jolt + red flash", "Earthquake", "Heartbeat (double red pulse)", "Gold shockwave", "Red vignette breathe",
        "Lightning flash", "None", "Fire ring", "Ember shower", "Inferno (fire ring + embers + jolt)",
        "Fire from below (bottom + up the sides)", "Flame wall rising", "Fire ring + earthquake", "Fire from below + embers")
    val TICK = listOf("Stamp + rattle", "Pop + gold burst", "Flip", "Slam + red row flash", "Green glow", "Hard shake", "None",
        "Fire ring burst", "Fire ring + stamp", "Ember pop", "Big flame burst", "Flip + fire ring",
        "Flame pillar (fire shoots up)", "Fire swirl", "Ember fountain", "Scorch (row burns + fire ring)",
        "Molten cracks", "Meteor smash (cracks + fire ring + sparks)", "White-hot shockwave + cracks", "Lava splash",
        "Ground pound (screen shakes + cracks)", "Anvil strike (spark fan + flash)", "Volcano (cracks + lava + pillar)")
    val SHINE = listOf("Gold glint + ember glow", "Ember glow only", "Fast glint", "None", "Flame flicker", "Fire glint (orange)", "Lava pulse",
        "Fire inside (flames rising from the bottom)", "Burning border (flames all round)", "Ember drift", "Inferno button (flames + embers + glint)")

    var entry by mutableIntStateOf(0)
    var testTrans by mutableIntStateOf(0)
    var arrival by mutableIntStateOf(0)
    var tick by mutableIntStateOf(0)
    var shine by mutableIntStateOf(0)
    var leave by mutableIntStateOf(0)
    /** Test: the cards and sets on a test screen rattle like an earthquake when it opens. */
    var testQuake by mutableStateOf(false)
    /** Test: every exercise in the 1 Rep Max list rattles like an earthquake when it opens. */
    var listQuake by mutableStateOf(false)
    /** Set by the developer button on the Workout tab: Settings scrolls straight to these pickers. */
    var jump by mutableStateOf(false)
    /** Set when you go into 1 Rep Max / into a test (not when you come back to them), so their effects play once. */
    var enteringList = false
    var enteringTest = false
    private var loaded = false

    /** The animations are fixed now (picked while testing): these are the ones the app uses. */
    fun load() {
        if (loaded) return
        loaded = true
        entry = ENTRY.indexOf("Drop from above + fire landing + embers + earthquake").coerceAtLeast(0)
        leave = LEAVE.indexOf("Dropped out the bottom").coerceAtLeast(0)
        tick = TICK.indexOf("White-hot shockwave + cracks").coerceAtLeast(0)
        shine = SHINE.indexOf("Gold glint + ember glow").coerceAtLeast(0)
        listQuake = false
        testQuake = false
    }

    /**
     * The 1 Rep Max set tick's vibration: the same two quick thuds (70 ms apart) as a normal set, at full strength.
     */
    fun slamBuzz(c: android.content.Context) {
        runCatching {
            val v = c.getSystemService(android.os.VibratorManager::class.java).defaultVibrator
            v.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 30, 40, 30), intArrayOf(0, 255, 0, 255), -1))
        }
    }

    fun save() = Store.writeFile(FILE, JSONObject().put("entry", entry).put("testTrans", testTrans).put("arrival", arrival)
        .put("tick", tick).put("shine", shine).put("quake", listQuake).put("testQuake", testQuake).put("leave", leave).toString())

    /** Going into 1 Rep Max. */
    fun enter(i: Int = entry): ContentTransform = trans(entries.getOrElse(i) { entries[0] }.trans)
    /** The effect (and when) that goes with an opening, if any. */
    /** When (ms) the list should shake, if the opening includes an earthquake. */
    fun entryQuake(i: Int = entry): Long? = entries.getOrNull(i)?.takeIf { it.quake }?.at
    fun entryEffect(i: Int = entry): Pair<Int, Long>? = entries.getOrNull(i)?.takeIf { it.effect >= 0 }?.let { it.effect to it.at }
    /** Going from the 1 Rep Max list into a test. */
    fun openTest(i: Int = testTrans): ContentTransform = trans(tests.getOrElse(i) { tests[0] }.trans)

    /** Leaving: the old screen goes on top of the one you're going back to. */
    fun leaving(i: Int = leave): ContentTransform = when (i) {
        1 -> (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 8 } + fadeIn(tween(240))) togetherWith
            (slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(200)))
        2 -> fadeIn(tween(80)) togetherWith slideOutVertically(tween(160, easing = FastOutLinearInEasing)) { it }
        3 -> fadeIn(tween(220)) togetherWith fadeOut(tween(220))
        4 -> fadeIn(tween(200)) togetherWith (scaleOut(tween(260), targetScale = 0.6f) + fadeOut(tween(240)))
        // Thrown upward: flung off the top, speeding up as it goes.
        else -> fadeIn(tween(60)) togetherWith (slideOutVertically(tween(170, easing = FastOutLinearInEasing)) { -it } + scaleOut(tween(170), targetScale = 0.92f))
    }

    fun trans(i: Int): ContentTransform = when (i) {
        1 -> (scaleIn(tween(320, easing = EaseOutBack), initialScale = 1.14f) + fadeIn(tween(160))) togetherWith
            (scaleOut(tween(260), targetScale = 0.9f) + fadeOut(tween(220)))
        2 -> slideInVertically(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) { -it } togetherWith fadeOut(tween(200))
        3 -> slideInVertically(tween(300, easing = FastOutLinearInEasing)) { -it } togetherWith fadeOut(tween(250))
        4 -> (slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200))) togetherWith
            (scaleOut(tween(380), targetScale = 0.92f) + fadeOut(tween(300)))
        5 -> (scaleIn(spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), initialScale = 0.55f) + fadeIn(tween(120))) togetherWith
            fadeOut(tween(150))
        6 -> (expandVertically(tween(360, easing = FastOutSlowInEasing), expandFrom = Alignment.CenterVertically) + fadeIn(tween(200))) togetherWith
            (shrinkVertically(tween(300), shrinkTowards = Alignment.CenterVertically) + fadeOut(tween(250)))
        7 -> fadeIn(tween(260, delayMillis = 240)) togetherWith fadeOut(tween(200))
        8 -> (slideInHorizontally(tween(220, easing = FastOutLinearInEasing)) { it } + scaleIn(tween(260), initialScale = 0.95f)) togetherWith
            (slideOutHorizontally(tween(220, easing = FastOutLinearInEasing)) { -it / 2 } + fadeOut(tween(200)))
        9 -> EnterTransition.None togetherWith ExitTransition.None
        else -> (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(220))) togetherWith
            (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 8 } + fadeOut(tween(260)))
    }
}

// ---------------- Whole-screen effects ----------------

/** Everything a whole-screen effect moves or draws. Put [fxShake] on the screen and [FxOverlay] on top of it. */
class FxState {
    val jolt = Animatable(0f)
    val sway = Animatable(0f)
    val flash = Animatable(0f)
    val white = Animatable(0f)
    val ring = Animatable(0f)
    val fire = Animatable(0f)
    val ember = Animatable(0f)
    val edges = Animatable(0f)
    val wall = Animatable(0f)

    private suspend fun quake(ms: Int, big: Float) = coroutineScope {
        launch { jolt.animateTo(0f, keyframes { durationMillis = ms; big at ms / 14; -big * 0.9f at ms / 6; big * 0.7f at ms * 2 / 7; -big * 0.55f at ms * 2 / 5; big * 0.4f at ms * 5 / 9; -big * 0.2f at ms * 5 / 7; big * 0.1f at ms * 6 / 7 }) }
        sway.animateTo(0f, keyframes { durationMillis = ms; -big at ms / 12; big at ms / 5; -big * 0.8f at ms / 3; big * 0.55f at ms * 3 / 7; -big * 0.35f at ms * 5 / 8; big * 0.15f at ms * 4 / 5 })
    }
    private suspend fun bump(px: Float) = jolt.animateTo(0f, keyframes { durationMillis = 380; px at 40; -px * 0.8f at 100; px * 0.5f at 170; -px * 0.3f at 240; px * 0.15f at 310 })

    /** Plays effect [kind] (an index into [OneRmFx.ARRIVAL]) after [wait] ms. */
    suspend fun play(kind: Int, haptic: HapticFeedback?, wait: Long, density: Float) = coroutineScope {
        delay(wait)
        fun buzz() = haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
        val d = density
        when (kind) {
            1 -> { buzz(); launch { flash.snapTo(0.5f); flash.animateTo(0f, tween(900)) }; quake(700, 20f * d / 2.6f) }
            2 -> repeat(2) { buzz(); flash.snapTo(0.85f); flash.animateTo(0f, tween(320)); delay(90) }
            3 -> { buzz(); launch { flash.snapTo(0.35f); flash.animateTo(0f, tween(500)) }; ring.snapTo(0f); ring.animateTo(1f, tween(750, easing = FastOutSlowInEasing)) }
            4 -> { flash.animateTo(0.9f, tween(450)); flash.animateTo(0f, tween(1000)) }
            5 -> {
                buzz(); white.snapTo(0.85f)
                launch { jolt.animateTo(0f, keyframes { durationMillis = 220; 8f at 40; -5f at 110; 2f at 170 }) }
                white.animateTo(0f, tween(260)); delay(60); white.snapTo(0.4f); white.animateTo(0f, tween(180))
            }
            6 -> {}
            7 -> { buzz(); launch { flash.snapTo(0.4f); flash.animateTo(0f, tween(600)) }; fire.snapTo(0f); fire.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
            8 -> { ember.snapTo(0f); ember.animateTo(1f, tween(1800)) }
            9 -> {
                buzz()
                launch { flash.snapTo(0.7f); flash.animateTo(0f, tween(900)) }
                launch { ember.snapTo(0f); ember.animateTo(1f, tween(2000)) }
                launch { bump(16f) }
                fire.snapTo(0f); fire.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
            }
            10 -> {
                buzz()
                launch { bump(14f * d / 2.6f) }
                launch { flash.snapTo(0.35f); flash.animateTo(0f, tween(700)) }
                edges.snapTo(0f); edges.animateTo(1f, tween(1400))
            }
            11 -> { buzz(); launch { flash.snapTo(0.3f); flash.animateTo(0f, tween(900)) }; wall.snapTo(0f); wall.animateTo(1f, tween(1300, easing = FastOutSlowInEasing)) }
            12 -> {
                buzz()
                launch { quake(800, 22f * d / 2.6f) }
                launch { flash.snapTo(0.5f); flash.animateTo(0f, tween(800)) }
                fire.snapTo(0f); fire.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
            }
            13 -> {
                buzz()
                launch { bump(12f * d / 2.6f) }
                launch { ember.snapTo(0f); ember.animateTo(1f, tween(2000)) }
                edges.snapTo(0f); edges.animateTo(1f, tween(1400))
            }
            else -> { buzz(); launch { flash.snapTo(0.75f); flash.animateTo(0f, tween(650)) }; bump(14f) }
        }
    }
}

fun Modifier.fxShake(s: FxState) = graphicsLayer { translationY = s.jolt.value; translationX = s.sway.value }

/** Draws the effects; never blocks a tap. */
@Composable
fun FxOverlay(s: FxState, modifier: Modifier = Modifier.fillMaxSize()) = Box(modifier.drawWithContent {
    val c = Offset(size.width / 2, size.height / 2)
    val big = maxOf(size.width, size.height)
    val a = s.flash.value
    if (a > 0f) drawRect(Brush.radialGradient(
        listOf(Color.Transparent, Color(0xFF8B0000).copy(alpha = a * 0.5f), Color(0xFF3A0000).copy(alpha = a)), center = c, radius = big * 0.75f,
    ))
    val r = s.ring.value
    if (r > 0f && r < 1f) drawCircle(Color(0xFFC9A94E).copy(alpha = 1f - r), radius = r * big * 0.8f, center = c,
        style = Stroke((28f * (1f - r) + 2f) * density))
    val f = s.fire.value
    if (f > 0f && f < 1f) fireRing(c, f * big * 0.75f, f, (big / 12f) * (1f - f * 0.4f))
    fireEdges(s.edges.value)
    flameWall(s.wall.value)
    embers(s.ember.value)
    if (s.white.value > 0f) drawRect(Color.White.copy(alpha = s.white.value))
})

// ---------------- Ticking a set ----------------

/** The ✓'s effect when a set is ticked. Put [tickRow] on the row and [tickBox] on the ✓. */
class TickFx {
    val stamp = Animatable(1f)
    val shake = Animatable(0f)
    val spin = Animatable(0f)
    val burst = Animatable(0f)
    val rowFlash = Animatable(0f)
    /** Where the ✓ is on screen, for [TickOverlay]. */
    var bounds: androidx.compose.ui.geometry.Rect? = null
    /** Orange instead of red for the row flash (scorch). */
    var scorch = false

    private suspend fun rattle(ms: Int, px: Float) = shake.animateTo(0f, keyframes { durationMillis = ms; px at ms / 10; -px * 0.9f at ms / 4; px * 0.6f at ms * 9 / 20; -px * 0.35f at ms * 2 / 3; px * 0.12f at ms * 5 / 6 })
    private suspend fun stampDown(from: Float = 1.55f) { stamp.snapTo(from); stamp.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 700f)) }
    private suspend fun burstOut(ms: Int) { burst.snapTo(0f); burst.animateTo(1f, tween(ms, easing = FastOutSlowInEasing)) }

    /** Second layer for the heavy hits: 0–1 for cracks/flash, set alongside [burst]. */
    val crack = Animatable(0f)

    /** [screen] lets the heaviest hits shake the whole screen. */
    suspend fun play(kind: Int, haptic: HapticFeedback?, screen: FxState? = null) = coroutineScope {
        if (kind != 6) haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
        suspend fun heavy() { // a second thud just after the first, so it feels like a slam
            delay(70); haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        fun cracks(ms: Int) = launch { crack.snapTo(0f); crack.animateTo(1f, tween(ms, easing = androidx.compose.animation.core.LinearEasing)) }
        fun jolt(px: Float) = screen?.let { s -> launch { s.jolt.animateTo(0f, keyframes { durationMillis = 360; px at 30; -px * 0.7f at 90; px * 0.45f at 160; -px * 0.25f at 230; px * 0.1f at 300 }) } }
        when (kind) {
            16 -> { launch { heavy() }; launch { stampDown(1.9f) }; launch { rattle(320, 12f) }; jolt(10f); cracks(650).join() }
            17 -> {
                launch { heavy() }; launch { stamp.snapTo(2.6f); stamp.animateTo(1f, tween(130, easing = FastOutLinearInEasing)) }
                delay(110); jolt(16f); launch { rattle(380, 16f) }; cracks(700); burstOut(800)
            }
            18 -> { launch { heavy() }; launch { stampDown(1.7f) }; jolt(12f); launch { rowFlash.snapTo(0.5f); rowFlash.animateTo(0f, tween(500)) }; cracks(650); burstOut(650) }
            19 -> { launch { heavy() }; launch { stampDown(1.6f) }; launch { rattle(300, 10f) }; burstOut(1100) }
            20 -> {
                launch { heavy() }; launch { stamp.snapTo(0.6f); stamp.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 500f)) }
                jolt(24f); launch { rattle(500, 22f) }; cracks(600).join()
            }
            21 -> { launch { heavy() }; launch { stampDown(1.5f) }; launch { rowFlash.snapTo(0.4f); rowFlash.animateTo(0f, tween(350)) }; burstOut(900) }
            22 -> {
                launch { heavy() }; launch { stampDown(2.0f) }; jolt(18f); launch { rattle(420, 16f) }
                cracks(750); burstOut(1300)
            }
            1 -> { launch { stamp.snapTo(1.3f); stamp.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f)) }; burstOut(480) }
            2 -> { spin.snapTo(0f); spin.animateTo(360f, tween(450, easing = FastOutSlowInEasing)); spin.snapTo(0f) }
            3 -> { stamp.snapTo(2.2f); stamp.animateTo(1f, tween(140, easing = FastOutLinearInEasing)); rowFlash.snapTo(0.55f); rowFlash.animateTo(0f, tween(450)) }
            4 -> { burst.snapTo(0f); burst.animateTo(1f, tween(650)) }
            5 -> rattle(450, 18f)
            6 -> {}
            7 -> burstOut(700)
            8 -> { launch { stampDown() }; launch { rattle(300, 9f) }; burstOut(700) }
            9 -> { launch { stamp.snapTo(1.35f); stamp.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f)) }; burstOut(800) }
            10 -> { launch { stampDown(1.8f) }; launch { rowFlash.snapTo(0.35f); rowFlash.animateTo(0f, tween(600)) }; burstOut(900) }
            11 -> { launch { spin.snapTo(0f); spin.animateTo(360f, tween(450, easing = FastOutSlowInEasing)); spin.snapTo(0f) }; burstOut(750) }
            12 -> { launch { stamp.snapTo(1.25f); stamp.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f)) }; burstOut(900) }
            13 -> burstOut(1000)
            14 -> { launch { stamp.snapTo(1.3f); stamp.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f)) }; burstOut(1100) }
            15 -> {
                scorch = true
                launch { stampDown(1.6f) }
                launch { rowFlash.snapTo(0.6f); rowFlash.animateTo(0f, tween(900)) }
                burstOut(800)
            }
            else -> { launch { stampDown() }; rattle(300, 9f) }
        }
    }
}

fun Modifier.tickRow(t: TickFx) = graphicsLayer { translationX = t.shake.value }.drawBehind {
    if (t.rowFlash.value > 0f) drawRoundRect((if (t.scorch) Color(0xFFFF6A00) else Color(0xFFFF453A)).copy(alpha = t.rowFlash.value), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
}

/** The ✓ itself: it slams, bounces or flips. Its fire, cracks and sparks are drawn by [TickOverlay], on top of the screen. */
fun Modifier.tickBox(t: TickFx) = graphicsLayer {
    scaleX = t.stamp.value; scaleY = t.stamp.value; rotationY = t.spin.value; cameraDistance = 12f * density
}.onGloballyPositioned { t.bounds = it.boundsInRoot() }

/** Everything a ticked set throws out, drawn over the whole screen but never over the ✓ button. */
@Composable
fun TickOverlay(ticks: Collection<TickFx>, kind: Int, modifier: Modifier = Modifier.fillMaxSize()) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.onGloballyPositioned { origin = it.boundsInRoot().topLeft }.drawWithContent {
        for (t in ticks) {
            val r = t.bounds ?: continue
            val box = r.translate(-origin)
            val b = t.burst.value
            val k = t.crack.value
            if ((b <= 0f || b >= 1f) && (k <= 0f || k >= 1f)) continue
            val hole = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(box, androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())))
            }
            clipPath(hole, androidx.compose.ui.graphics.ClipOp.Difference) {
                tickEffect(kind, box.center, box.minDimension / 2, b, k)
            }
        }
    })
}

private fun DrawScope.tickEffect(kind: Int, c: Offset, half: Float, b: Float, k: Float) {
    if (k > 0f && k < 1f && kind in setOf(16, 17, 18, 20, 22)) moltenCracks(c, half, k, if (kind == 20) 1.4f else 1f)
    if (b <= 0f || b >= 1f) return
    when (kind) {
        4 -> drawCircle(Color(0xFF34C759).copy(alpha = 0.55f * (1f - b)), radius = half * (1f + b * 2.2f), center = c)
        7, 8, 11 -> fireRing(c, half * (1f + b * 2.4f), b, 18.dp.toPx() * (1f - b * 0.3f))
        9 -> sparks(c, half, b)
        10 -> { fireRing(c, half * (1f + b * 4f), b, 30.dp.toPx() * (1f - b * 0.3f)); sparks(c, half, b) }
        12 -> flamePillar(c, half, b)
        13 -> fireRing(c, half * (1.25f + b * 0.9f), b * 0.999f, 16.dp.toPx() * (1f - b * 0.4f), spin = b * 6f)
        14 -> fountain(c, half, b)
        15 -> fireRing(c, half * (1f + b * 2.6f), b, 22.dp.toPx() * (1f - b * 0.3f))
        17 -> { fireRing(c, half * (1f + b * 3.2f), b, 26.dp.toPx() * (1f - b * 0.3f)); sparks(c, half, b) }
        18 -> {
            drawCircle(Color.White.copy(alpha = (1f - b) * 0.8f), radius = half * (1f + b * 5f), center = c, style = Stroke(10.dp.toPx() * (1f - b)))
            drawCircle(FireGold.copy(alpha = (1f - b) * 0.5f), radius = half * (1f + b * 3f), center = c)
            sparkBurst(c, half, b)
        }
        19, 22 -> lava(c, half, b)
        21 -> anvil(c, half, b)
        16, 20 -> {}
        else -> drawCircle(Color(0xFFC9A94E).copy(alpha = 1f - b), radius = half * (1f + b * 1.8f), center = c, style = Stroke(3.dp.toPx() * (1.5f - b)))
    }
    if (kind == 22) flamePillar(c, half, b)
}

// ---------------- Fire, in the 1 Rep Max red and gold ----------------

private val FireGold = Color(0xFFFFD36A)
private val FireOrange = Color(0xFFFF6A00)
private val FireRed = Color(0xFFB30000)

/** A repeatable 0–1 "random" number, so flames flicker the same way each frame step. */
private fun rnd(i: Int, salt: Float = 0f): Float {
    val v = kotlin.math.sin(i * 12.9898f + salt * 78.233f) * 43758.547f
    return v - kotlin.math.floor(v)
}

/** One tongue of flame from [from] towards [to]: gold at the root, orange, fading to red at the tip. */
private fun DrawScope.flame(from: Offset, to: Offset, width: Float, a: Float) = drawLine(
    Brush.linearGradient(listOf(FireGold.copy(alpha = a), FireOrange.copy(alpha = 0.9f * a), FireRed.copy(alpha = 0f)), from, to),
    from, to, strokeWidth = width.coerceAtLeast(2f), cap = androidx.compose.ui.graphics.StrokeCap.Round,
)

/** A ring of fire at [radius] around [center], flickering outward over a hot glow; fades as [p] reaches 1. */
fun DrawScope.fireRing(center: Offset, radius: Float, p: Float, flame: Float, spin: Float = 0f) {
    val a = (1f - p).coerceIn(0f, 1f)
    if (a <= 0f || radius <= 0f) return
    val outer = radius + flame * 1.6f
    drawCircle(
        Brush.radialGradient(
            0f to Color.Transparent,
            ((radius - flame * 0.4f) / outer).coerceIn(0f, 0.98f) to Color.Transparent,
            (radius / outer).coerceIn(0f, 0.99f) to FireOrange.copy(alpha = 0.45f * a),
            1f to Color.Transparent,
            center = center, radius = outer,
        ),
        radius = outer, center = center,
    )
    val n = 96
    val step = (p * 30f).toInt().toFloat()
    for (i in 0 until n) {
        val ang = (i * 2.0 * Math.PI / n + p * 0.8 + spin).toFloat()
        val h = flame * (0.35f + 0.65f * rnd(i, step))
        val dx = kotlin.math.cos(ang); val dy = kotlin.math.sin(ang)
        flame(Offset(center.x + dx * radius, center.y + dy * radius), Offset(center.x + dx * (radius + h), center.y + dy * (radius + h)), flame * 0.16f, a)
    }
    drawCircle(FireGold.copy(alpha = 0.8f * a), radius = radius, center = center, style = Stroke((flame * 0.12f).coerceAtLeast(2f)))
}

/** Fire blowing out along the bottom edge and climbing up both sides. [p] runs 0–1. */
fun DrawScope.fireEdges(p: Float) {
    if (p <= 0f || p >= 1f) return
    val e = if (p < 0.2f) p / 0.2f else (1f - p) / 0.8f
    val step = (p * 30f).toInt().toFloat()
    val w = size.width; val h = size.height
    val maxH = h * 0.16f
    // Glow along the bottom and sides.
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, FireOrange.copy(alpha = 0.45f * e)), startY = h - maxH * 2.2f, endY = h),
        topLeft = Offset(0f, h - maxH * 2.2f), size = androidx.compose.ui.geometry.Size(w, maxH * 2.2f))
    // Bottom: a row of flames.
    val gap = 7.dp.toPx()
    var x = 0f; var i = 0
    while (x <= w) {
        val fh = maxH * e * (0.35f + 0.65f * rnd(i, step))
        flame(Offset(x, h), Offset(x + (rnd(i, step + 1f) - 0.5f) * gap * 2f, h - fh), gap * 1.5f, e)
        x += gap; i++
    }
    // Sides: the flames climb as p grows, shorter higher up.
    val climb = h * (p * 1.7f).coerceAtMost(1f) * 0.85f
    val sideW = w * 0.13f
    var y = h; var j = 0
    while (y >= h - climb) {
        val up = (h - y) / climb.coerceAtLeast(1f)
        val fw = sideW * e * (0.35f + 0.65f * rnd(j, step + 2f)) * (1f - up * 0.6f)
        flame(Offset(0f, y), Offset(fw, y - gap * 0.8f), gap * 1.4f, e * (1f - up * 0.4f))
        flame(Offset(w, y), Offset(w - fw * (0.8f + 0.4f * rnd(j, step + 3f)), y - gap * 0.8f), gap * 1.4f, e * (1f - up * 0.4f))
        y -= gap; j++
    }
}

/** A wall of flame sweeping up the screen from the bottom. [p] runs 0–1. */
fun DrawScope.flameWall(p: Float) {
    if (p <= 0f || p >= 1f) return
    val a = if (p < 0.15f) p / 0.15f else (1f - p) / 0.85f
    val line = size.height * (1.05f - p * 1.15f)
    drawRect(Brush.verticalGradient(listOf(FireOrange.copy(alpha = 0.35f * a), FireRed.copy(alpha = 0.15f * a)), startY = line, endY = size.height),
        topLeft = Offset(0f, line), size = androidx.compose.ui.geometry.Size(size.width, (size.height - line).coerceAtLeast(0f)))
    val gap = 8.dp.toPx(); val maxH = size.height * 0.14f
    val step = (p * 30f).toInt().toFloat()
    var x = 0f; var i = 0
    while (x <= size.width) {
        val fh = maxH * (0.35f + 0.65f * rnd(i, step))
        flame(Offset(x, line), Offset(x + (rnd(i, step + 1f) - 0.5f) * gap * 2f, line - fh), gap * 1.6f, a)
        x += gap; i++
    }
}

/** Glowing embers drifting up the screen and burning out. [p] runs 0–1. */
fun DrawScope.embers(p: Float, count: Int = 80) {
    if (p <= 0f || p >= 1f) return
    for (i in 0 until count) {
        val speed = 0.7f + rnd(i, 2f) * 0.9f
        val x = rnd(i, 1f) * size.width + kotlin.math.sin(p * 9f + i) * 14f * density
        val y = size.height * (1.05f + rnd(i, 3f) * 0.25f) - p * speed * size.height * 1.15f
        val life = (1f - p) * (0.55f + 0.45f * rnd(i, (p * 25f).toInt().toFloat()))
        val col = if (rnd(i, 4f) < 0.5f) FireGold else if (rnd(i, 5f) < 0.6f) FireOrange else FireRed
        val r = (1.5f + rnd(i, 6f) * 3.5f) * density
        drawCircle(col.copy(alpha = life * 0.35f), radius = r * 2.6f, center = Offset(x, y))
        drawCircle(col.copy(alpha = life), radius = r, center = Offset(x, y))
    }
}

/** Fire shooting straight up from a ✓ and dying down. */
private fun DrawScope.flamePillar(c: Offset, half: Float, b: Float) {
    val a = if (b < 0.2f) b / 0.2f else (1f - b) / 0.8f
    val tall = half * 7f * (if (b < 0.3f) b / 0.3f else 1f)
    val step = (b * 30f).toInt().toFloat()
    for (i in -4..4) {
        val x = c.x + i * half * 0.22f
        val h = tall * (0.45f + 0.55f * rnd(i + 20, step)) * (1f - kotlin.math.abs(i) * 0.09f)
        flame(Offset(x, c.y), Offset(x + (rnd(i + 40, step) - 0.5f) * half * 0.5f, c.y - h), half * 0.45f, a)
    }
}

/** Sparks thrown up out of a ✓, arcing and falling back. */
private fun DrawScope.fountain(c: Offset, half: Float, b: Float) {
    for (i in 0 until 26) {
        val vx = (rnd(i, 11f) - 0.5f) * half * 5f
        val vy = -(1.5f + rnd(i, 12f) * 2.5f) * half * 2.2f
        val g = half * 9f
        val pos = Offset(c.x + vx * b, c.y + vy * b + g * b * b)
        val col = if (rnd(i, 13f) < 0.5f) FireGold else if (rnd(i, 14f) < 0.6f) FireOrange else FireRed
        drawCircle(col.copy(alpha = (1f - b) * 0.35f), radius = 5.dp.toPx(), center = pos)
        drawCircle(col.copy(alpha = 1f - b), radius = 2.dp.toPx(), center = pos)
    }
}

/**
 * Real fire on a gold button, drawn under its text. [t] loops 0–1. 7 flames rising from the bottom,
 * 8 flames all round the edge, 9 embers drifting up through it, 10 flames + embers.
 */
fun DrawScope.buttonFire(style: Int, t: Float) {
    if (style !in 7..10) return
    val step = (t * 24f).toInt().toFloat()
    val gap = 6.dp.toPx()
    val w = size.width; val h = size.height
    if (style == 7 || style == 10) {
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, FireOrange.copy(alpha = 0.35f)), startY = h * 0.3f, endY = h))
        var x = 0f; var i = 0
        while (x <= w) {
            val fh = h * 0.6f * (0.35f + 0.65f * rnd(i, step))
            flame(Offset(x, h), Offset(x + (rnd(i, step + 1f) - 0.5f) * gap, h - fh), gap * 1.4f, 0.85f)
            x += gap; i++
        }
    }
    if (style == 8) {
        var x = 0f; var i = 0
        while (x <= w) {
            flame(Offset(x, h), Offset(x, h - h * 0.35f * (0.3f + 0.7f * rnd(i, step))), gap * 1.2f, 0.85f)
            flame(Offset(x, 0f), Offset(x, h * 0.25f * (0.3f + 0.7f * rnd(i + 500, step))), gap * 1.0f, 0.6f)
            x += gap; i++
        }
        var y = 0f; var j = 0
        while (y <= h) {
            flame(Offset(0f, y), Offset(w * 0.06f * (0.3f + 0.7f * rnd(j + 900, step)), y - gap * 0.5f), gap * 1.1f, 0.75f)
            flame(Offset(w, y), Offset(w - w * 0.06f * (0.3f + 0.7f * rnd(j + 950, step)), y - gap * 0.5f), gap * 1.1f, 0.75f)
            y += gap; j++
        }
    }
    if (style == 9 || style == 10) {
        for (i in 0 until 24) {
            val life = (t + rnd(i, 21f)) % 1f
            val pos = Offset(rnd(i, 22f) * w + kotlin.math.sin(life * 8f + i) * 6.dp.toPx(), h * (1.05f - life * 1.1f))
            val col = if (rnd(i, 23f) < 0.5f) FireGold else FireOrange
            val a = (1f - life) * (0.6f + 0.4f * rnd(i, step))
            drawCircle(col.copy(alpha = a * 0.35f), radius = 5.dp.toPx(), center = pos)
            drawCircle(col.copy(alpha = a), radius = 1.8.dp.toPx(), center = pos)
        }
    }
}

/** A list or screen part rattling for the earthquake tests: [q] runs 0–1, [i] staggers each part. */
fun Modifier.quakeRattle(q: () -> Float, i: Int) = graphicsLayer {
    val v = q()
    if (v < 1f) {
        val amp = (1f - v) * (1f - v)
        translationX = kotlin.math.sin(v * 70f + i * 1.9f) * 26f * density * amp
        translationY = kotlin.math.cos(v * 55f + i * 2.7f) * 9f * density * amp
        rotationZ = kotlin.math.sin(v * 60f + i) * 3.5f * amp
    }
}

/**
 * Molten cracks splitting outward from a ✓: jagged lines that tear out fast, glow white-gold to orange, then cool to
 * dark red and fade. [k] runs 0–1; [reach] stretches them.
 */
fun DrawScope.moltenCracks(c: Offset, half: Float, k: Float, reach: Float = 1f) {
    val grow = (k / 0.16f).coerceAtMost(1f)
    val cool = ((k - 0.25f) / 0.75f).coerceIn(0f, 1f)
    val a = 1f - cool
    val hot = androidx.compose.ui.graphics.lerp(FireGold, FireOrange, (cool * 2f).coerceAtMost(1f))
    val core = androidx.compose.ui.graphics.lerp(Color(0xFFFFF4D0), FireRed, cool)
    val total = half * 7f * reach
    for (branch in 0 until 9) {
        var ang = (branch * 2.0 * Math.PI / 9 + (rnd(branch, 41f) - 0.5f) * 0.5f).toFloat()
        var p = c
        var used = 0f
        val len = total * (0.55f + 0.45f * rnd(branch, 42f))
        val show = len * grow
        for (seg in 0 until 7) {
            if (used >= show) break
            val segLen = (len / 7f) * (0.7f + 0.6f * rnd(branch * 10 + seg, 43f))
            val l = minOf(segLen, show - used)
            ang += (rnd(branch * 10 + seg, 44f) - 0.5f) * 0.9f
            val q = Offset(p.x + kotlin.math.cos(ang) * l, p.y + kotlin.math.sin(ang) * l)
            val w = (1f - used / len) // thinner further out
            drawLine(FireOrange.copy(alpha = 0.35f * a), p, q, strokeWidth = (9.dp.toPx() * w).coerceAtLeast(2f), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(hot.copy(alpha = a), p, q, strokeWidth = (4.dp.toPx() * w).coerceAtLeast(1.5f), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(core.copy(alpha = a), p, q, strokeWidth = (1.6.dp.toPx() * w).coerceAtLeast(1f), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            // A small side crack now and then.
            if (rnd(branch * 10 + seg, 45f) < 0.35f) {
                val sa = ang + (if (rnd(branch * 10 + seg, 46f) < 0.5f) 0.9f else -0.9f)
                val sl = l * 0.6f
                val r2 = Offset(q.x + kotlin.math.cos(sa) * sl, q.y + kotlin.math.sin(sa) * sl)
                drawLine(hot.copy(alpha = a * 0.8f), q, r2, strokeWidth = (2.5.dp.toPx() * w).coerceAtLeast(1f), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
            p = q; used += l
        }
    }
    // The impact glow at the centre.
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF4D0).copy(alpha = a), FireOrange.copy(alpha = 0.5f * a), Color.Transparent), center = c, radius = half * 2.4f),
        radius = half * 2.4f, center = c)
}

/** Blobs of molten metal flung out of the ✓, arcing down with glowing streaks. */
private fun DrawScope.lava(c: Offset, half: Float, b: Float) {
    for (i in 0 until 20) {
        val ang = (i * 2.0 * Math.PI / 20 + rnd(i, 51f) * 0.6).toFloat()
        val speed = half * (3f + rnd(i, 52f) * 4f)
        val g = half * 10f
        fun at(t: Float) = Offset(c.x + kotlin.math.cos(ang) * speed * t, c.y + kotlin.math.sin(ang) * speed * t - half * 3f * t + g * t * t)
        val pos = at(b); val prev = at((b - 0.06f).coerceAtLeast(0f))
        val a = 1f - b
        val col = androidx.compose.ui.graphics.lerp(FireGold, FireRed, b)
        drawLine(FireOrange.copy(alpha = a * 0.6f), prev, pos, strokeWidth = 4.dp.toPx() * (1f - b * 0.5f), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(col.copy(alpha = a), radius = (2.5f + rnd(i, 53f) * 3f).dp.toPx() * (1f - b * 0.4f), center = pos)
    }
    drawCircle(FireOrange.copy(alpha = (1f - b) * 0.6f), radius = half * (1f + b * 1.5f), center = c)
}

/** An anvil strike: a fan of white-hot sparks thrown up and out, with a flash. */
private fun DrawScope.anvil(c: Offset, half: Float, b: Float) {
    if (b < 0.25f) drawCircle(Color.White.copy(alpha = (1f - b / 0.25f) * 0.9f), radius = half * (1.2f + b * 6f), center = c)
    for (i in 0 until 34) {
        val ang = (-Math.PI / 2 + (rnd(i, 61f) - 0.5f) * 2.4).toFloat()
        val speed = half * (4f + rnd(i, 62f) * 6f)
        val g = half * 8f
        fun at(t: Float) = Offset(c.x + kotlin.math.cos(ang) * speed * t, c.y + kotlin.math.sin(ang) * speed * t + g * t * t)
        val pos = at(b); val prev = at((b - 0.05f).coerceAtLeast(0f))
        val col = androidx.compose.ui.graphics.lerp(Color(0xFFFFF4D0), FireOrange, b)
        drawLine(col.copy(alpha = 1f - b), prev, pos, strokeWidth = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

/** An explosion of sparks: white-hot streaks flung out in every direction, cooling to gold and orange as they fly and drop. */
private fun DrawScope.sparkBurst(c: Offset, half: Float, b: Float) {
    for (i in 0 until 40) {
        val ang = (i * 2.0 * Math.PI / 40 + (rnd(i, 71f) - 0.5f) * 0.35).toFloat()
        val speed = half * (5f + rnd(i, 72f) * 8f)
        val g = half * 6f
        // Fast out, slowing down (ease-out), with a little gravity.
        val e = 1f - (1f - b) * (1f - b)
        fun at(t: Float): Offset {
            val et = 1f - (1f - t) * (1f - t)
            return Offset(c.x + kotlin.math.cos(ang) * speed * et, c.y + kotlin.math.sin(ang) * speed * et + g * t * t)
        }
        val pos = at(b)
        val tail = at((b - 0.12f).coerceAtLeast(0f))
        val life = (1f - b) * (0.6f + 0.4f * rnd(i, 73f))
        val col = androidx.compose.ui.graphics.lerp(Color(0xFFFFF4D0), if (rnd(i, 74f) < 0.5f) FireGold else FireOrange, (e * 1.4f).coerceAtMost(1f))
        drawLine(col.copy(alpha = life * 0.45f), tail, pos, strokeWidth = 4.5.dp.toPx() * (1f - b * 0.6f), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(col.copy(alpha = life), tail, pos, strokeWidth = 1.8.dp.toPx() * (1f - b * 0.5f), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(Color(0xFFFFF4D0).copy(alpha = life), radius = 1.6.dp.toPx() * (1f - b * 0.5f), center = pos)
    }
}

/** Sparks flying out from a ✓. */
private fun DrawScope.sparks(c: Offset, half: Float, b: Float) {
    for (i in 0 until 18) {
        val ang = (i * 2.0 * Math.PI / 18 + rnd(i, 7f)).toFloat()
        val dist = half * (1f + b * (2f + 1.5f * rnd(i, 8f)))
        val col = if (rnd(i, 9f) < 0.5f) FireGold else FireOrange
        val pos = Offset(c.x + kotlin.math.cos(ang) * dist, c.y + kotlin.math.sin(ang) * dist - b * 10.dp.toPx())
        drawCircle(col.copy(alpha = (1f - b) * 0.35f), radius = 6.dp.toPx() * (1f - b * 0.5f), center = pos)
        drawCircle(col.copy(alpha = 1f - b), radius = 2.5.dp.toPx() * (1f - b * 0.5f), center = pos)
    }
}

// ---------------- Embers left behind by a screen that's thrown away ----------------

/** Fired when you leave 1 Rep Max with "Thrown upward" or "Dropped out the bottom". */
object LeaveTrail {
    var trigger by mutableIntStateOf(0)
    var up = true
    private const val TOTAL = 900
    const val MOVE = 170
    fun fire(up: Boolean) { this.up = up; trigger++ }

    @Composable
    fun Overlay() {
        val p = remember { Animatable(1f) }
        LaunchedEffect(trigger) {
            if (trigger == 0) return@LaunchedEffect
            p.snapTo(0f); p.animateTo(1f, tween(TOTAL, easing = androidx.compose.animation.core.LinearEasing))
        }
        Box(Modifier.fillMaxSize().drawWithContent { trail(p.value, up, MOVE / TOTAL.toFloat()) })
    }
}

/**
 * Sparse twinkling embers dropped along the edge of a screen as it flies off: each one appears where the edge was,
 * drifts a little and twinkles out. [p] runs 0–1 over the whole effect; the screen moves during the first [move].
 */
private fun DrawScope.trail(p: Float, up: Boolean, move: Float) {
    if (p <= 0f || p >= 1f) return
    val w = size.width; val h = size.height
    for (i in 0 until 30) {
        val born = rnd(i, 31f) * move
        if (p < born) continue
        val age = (p - born) / (1f - move)
        if (age >= 1f) continue
        val t = born / move
        val e = t * t // the edge speeds up as the screen flies off
        val edge = if (up) h * (1f - e) else h * e
        val x = rnd(i, 32f) * w + kotlin.math.sin(age * 6f + i) * 6.dp.toPx()
        val y = edge + (if (up) -1f else 1f) * (rnd(i, 33f) * 18.dp.toPx()) - age * 26.dp.toPx()
        val twinkle = 0.45f + 0.55f * kotlin.math.abs(kotlin.math.sin(age * 22f + i * 3.1f))
        val a = (1f - age) * twinkle
        val col = if (rnd(i, 34f) < 0.55f) FireGold else FireOrange
        val r = (1.4f + rnd(i, 35f) * 2f).dp.toPx()
        drawCircle(col.copy(alpha = a * 0.3f), radius = r * 3f, center = Offset(x, y))
        drawCircle(col.copy(alpha = a), radius = r, center = Offset(x, y))
    }
}
