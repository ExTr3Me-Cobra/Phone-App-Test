package com.ironlog.app

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** The rank badge pictures (assets/ranks). */
object RankImages {
    private val cache = HashMap<Tier, ImageBitmap>()
    fun get(c: Context, t: Tier): ImageBitmap? = cache[t] ?: runCatching {
        c.assets.open("ranks/${t.badge}.webp").use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    }.getOrNull()?.also { cache[t] = it }
}

@Composable
fun RankBadge(t: Tier, size: Dp, modifier: Modifier = Modifier) {
    val c = LocalContext.current
    val img = remember(t) { RankImages.get(c, t) }
    if (img != null) Image(img, t.label, modifier.size(size)) else Box(modifier.size(size))
}

val StarGold = Color(0xFFC9A94E)
val StarEstimate = Color(0xFF7FA3C6)

/** An exercise name with a small ★: gold = ranked accurately, silver-blue = ranked as an estimate. */
@Composable
fun starredName(e: Exercise?, missing: String = "Missing exercise"): AnnotatedString {
    val p by Store.profile.collectAsState()
    val star = e?.let { Standards.star(it.id, p) } ?: Standards.Star.NONE
    return buildAnnotatedString {
        append(e?.name ?: missing)
        if (star != Standards.Star.NONE) withStyle(
            SpanStyle(color = if (star == Standards.Star.EXACT) StarGold else StarEstimate, fontSize = 0.7.em),
        ) { append("  ★") }
    }
}

fun rankText(pct: Double) = "${Tier.of(pct).label} (${topLabel(pct)})"

// ---------------- Home: overall rank, rank heatmap and legend ----------------

@Composable
fun HomeRankCard() {
    val workouts by Store.workouts.collectAsState()
    val profile by Store.profile.collectAsState()
    val exercises by Store.exercises.collectAsState()
    if (Standards.mode(profile) == Standards.Mode.OFF) return
    val ranks = remember(workouts, profile, exercises) { Ranks.all(workouts, profile) }
    val overall = Ranks.overallOf(ranks, profile)
    var open by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<String?>(null) }
    picking?.let { g -> GroupPickDialog(g, Ranks.groupCandidates(ranks)[g].orEmpty(), profile) { picking = null } }
    Column(Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
        if (overall == null) {
            Text("Your rank", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                when {
                    profile.bodyweight == null -> "Add your body weight (and age) in Settings, then log an exercise marked ★ to get ranked against other lifters."
                    Standards.mode(profile) == Standards.Mode.ACCURATE && StandardsData.all.none { Standards.covers(it.id, profile) } ->
                        "The published data doesn't cover your exact body weight/age closely enough for Accurate-only ranks. " +
                            "Switch Ranks to \"All on\" in Settings to rank anyway."
                    else -> "Log an exercise marked ★ to get ranked against other lifters your size, age and sex."
                },
                color = Sub, fontSize = 14.sp,
            )
            if (profile.bodyweight == null) { Spacer(8); GreyButton("Open Profile") { Nav.home(2) } }
            return@Column
        }
        val t = Tier.of(overall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RankBadge(t, 84.dp)
            HSpacer(12)
            Column {
                Text("Current overall rank", color = Sub, fontSize = 13.sp)
                Text(t.label, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(t.color))
                Text("(${topLabel(overall)})", fontSize = 15.sp)
                val groupCount = Ranks.groupRanks(ranks, profile.picksNow).size
                Text("Average of your $groupCount muscle group${if (groupCount == 1) "" else "s"}", color = Sub, fontSize = 12.sp)
            }
        }
        // Each ranked muscle is coloured by its group's rank: the average of your top two exercises for it (as in the list below).
        val muscles = remember(ranks) {
            val groups = Ranks.groupRanks(ranks, profile.picksNow)
            Ranks.muscles(ranks).mapValues { (m, r) -> groups[Muscles.group(m)]?.first ?: r.percentile }
        }
        val colors = muscles.mapValues { Tier.of(it.value).color.toInt() }
        val levels = muscles.mapValues { (it.value / 100).toFloat().coerceAtLeast(0.01f) }
        BodyMapView(levels, Modifier.padding(top = 10.dp).fillMaxWidth().height(300.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black),
            colors = colors)
        Text(
            if (open) "Hide muscle ranks & colour key ▴" else "Show muscle ranks & colour key ▾",
            color = Blue, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 10.dp),
        )
        if (open) {
            Text("Your muscles", fontWeight = FontWeight.Bold)
            Text("Each muscle's rank is the average of two ranked exercises – your top two unless you choose (tap ✎). " +
                "Tap an exercise to see exactly who you're compared with.", color = Sub, fontSize = 12.sp)
            Ranks.groupRanks(ranks, profile.picksNow).entries.sortedByDescending { it.value.first }.forEach { (g, v) ->
                val (pct, top) = v
                val tier = Tier.of(pct)
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(Color(tier.color)))
                        HSpacer(8)
                        Text(titleCase(g), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("  ✎", color = Blue, fontSize = 15.sp, modifier = Modifier.clickable { picking = g }.padding(horizontal = 4.dp))
                        Box(Modifier.weight(1f))
                        RankBadge(tier, 22.dp)
                        HSpacer(4)
                        Text(rankText(pct), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(tier.color))
                    }
                    top.forEach { r ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { Nav.go(Route.RankDetail(r.exercise.id)) }
                            .padding(start = 22.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(starredName(r.exercise), color = Sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(rankText(r.percentile), fontSize = 13.sp, color = Color(r.tier.color))
                            Text(" ›", color = Sub, fontSize = 16.sp)
                        }
                    }
                    if (top.size == 1) Text("Rank one more exercise for this muscle to average two.", color = Sub, fontSize = 11.sp,
                        modifier = Modifier.padding(start = 22.dp))
                }
            }
            // Muscle groups with rankable exercises that you don't have a rank for yet (e.g. calves, grip).
            val rankedGroups = Ranks.groupRanks(ranks, profile.picksNow).keys
            Muscles.groups.keys.filter { g -> g !in rankedGroups }.forEach { g ->
                val rankable = StandardsData.all.mapNotNull { Store.exercise(it.id) }.filter { g in it.primary && !it.hidden }
                if (rankable.isEmpty()) return@forEach
                val forYou = rankable.filter { Standards.covers(it.id, profile) }
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(Card2))
                        HSpacer(8)
                        Text(titleCase(g), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Sub, modifier = Modifier.weight(1f))
                        Text("Not ranked yet", fontSize = 13.sp, color = Sub)
                    }
                    Text(
                        when {
                            forYou.isNotEmpty() -> "Log " + forYou.take(3).joinToString(", ") { it.name } + (if (forYou.size > 3) " or another ★ exercise" else "") + " to rank it."
                            Standards.mode(profile) == Standards.Mode.ACCURATE -> "Only estimate data so far (" + rankable.take(2).joinToString(", ") { it.name } +
                                ") – switch Ranks to All on in Settings to rank it."
                            else -> "No exercise for it can be ranked with your current details."
                        },
                        color = Sub, fontSize = 11.sp, modifier = Modifier.padding(start = 22.dp),
                    )
                }
            }
            Spacer(10)
            Text("Colour key", fontWeight = FontWeight.Bold)
            Tier.entries.reversed().forEach { tier ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(Color(tier.color)))
                    HSpacer(8)
                    RankBadge(tier, 26.dp)
                    HSpacer(8)
                    Text("${tier.label} (top ${tier.topPct}%)", fontSize = 14.sp)
                }
            }
            Text("Uncoloured muscles have no ranked exercise yet.", color = Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// ---------------- Exercise page ----------------

@Composable
fun ExerciseRankCard(e: Exercise, profile: Profile, workouts: List<Workout>) {
    val mode = Standards.mode(profile)
    if (mode == Standards.Mode.OFF) return
    val info = Standards.info(e.id) ?: return
    val fit = Standards.fit(e.id, profile)
    val r = remember(workouts, profile, e.id) { Ranks.all(workouts, profile)[e.id] }
    var details by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
        Text(buildAnnotatedString {
            append("Your rank ")
            val star = Standards.star(e.id, profile)
            if (star != Standards.Star.NONE) withStyle(SpanStyle(color = if (star == Standards.Star.EXACT) StarGold else StarEstimate)) { append("★") }
        }, fontWeight = FontWeight.Bold)
        when {
            profile.bodyweight == null || fit == null -> Text("Add your body weight (and age) in Settings to see where you rank.", color = Sub)
            mode == Standards.Mode.ACCURATE && !fit.exact -> {
                Text("Not ranked in Accurate-only mode:", color = Sub)
                fit.problems.forEach { Text("• $it", color = Sub, fontSize = 13.sp) }
                Text("Switch Ranks to \"All on\" in Settings to rank it anyway as an estimate.", color = Sub, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
            r == null -> Text("Log this exercise to get your rank.", color = Sub)
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RankBadge(r.tier, 64.dp)
                    HSpacer(12)
                    Column {
                        Text(r.tier.label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(r.tier.color))
                        Text("(${topLabel(r.percentile)})")
                    }
                }
                Text("Stronger than about ${r.percentile.toInt()}% of ${Standards.who(profile)}" + if (profile.compareAll) " – everyone, lifters or not." else " who lift.", fontSize = 15.sp,
                    modifier = Modifier.padding(top = 6.dp))
                Text(
                    "Your best in the 30 days up to your last session: " + if (info.reps) "${r.value.toInt()} reps" else "est. 1-rep max ${Units.weightU(r.value)}",
                    color = Sub, fontSize = 13.sp,
                )
                Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Card2)) {
                    Box(Modifier.fillMaxWidth((r.percentile / 100).toFloat()).height(8.dp).background(Color(r.tier.color)))
                }
                Text("See the chart and who you're compared with ›", color = Blue, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 10.dp).clickable { Nav.go(Route.RankDetail(e.id)) })
            }
        }
        if (fit != null) {
            Text(if (details) "Who you're compared with ▴" else "Who you're compared with ▾", color = Blue, fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp).clickable { details = !details })
            if (details) {
                Text(fit.compared, color = Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                if (!fit.exact && mode == Standards.Mode.ALL) fit.problems.forEach {
                    Text("• $it", color = Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

// ---------------- Rank-up celebration ----------------

/** Shows queued rank-ups one at a time over everything. Tap to continue. */
@Composable
fun RankUpOverlay() {
    val c = Ranks.celebrations.firstOrNull() ?: return
    if (c.down) { RankDownView(c); return }
    val context = LocalContext.current
    val pop = remember(c) { Animatable(0f) }
    val burst = remember(c) { Animatable(0f) }
    LaunchedEffect(c) {
        runCatching {
            val v = context.getSystemService(android.os.Vibrator::class.java)
            v?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 60, 60, 120), -1))
        }
        kotlinx.coroutines.coroutineScope {
            launch { pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessLow)) }
            launch { burst.animateTo(1f, tween(1400)) }
        }
        delay(3500)
        Ranks.celebrations.remove(c)
    }
    val spin = rememberInfiniteTransition(label = "rays")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    val color = Color(c.tier.color)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f))
            .clickable(remember { MutableInteractionSource() }, null) { Ranks.celebrations.remove(c) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(360.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            // Turning rays.
            rotate(angle, center) {
                for (i in 0 until 16) {
                    val a = Math.toRadians(i * 22.5).toFloat()
                    val a2 = a + 0.12f
                    val r = size.minDimension / 2
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(center.x, center.y)
                        lineTo(center.x + r * cos(a), center.y + r * sin(a))
                        lineTo(center.x + r * cos(a2), center.y + r * sin(a2))
                        close()
                    }
                    drawPath(path, color.copy(alpha = 0.18f * pop.value))
                }
            }
            // Sparkles flying out.
            val t = burst.value
            for (i in 0 until 24) {
                val a = i * 15.0 * Math.PI / 180 + (i % 3) * 0.2
                val dist = (60 + (i % 5) * 22) * density * t + 40 * density
                drawCircle(
                    if (i % 2 == 0) color else Color.White,
                    radius = (5 - (i % 3)) * density * (1 - t * 0.6f),
                    center = Offset(center.x + (dist * cos(a)).toFloat(), center.y + (dist * sin(a)).toFloat()),
                    alpha = (1 - t).coerceIn(0f, 1f),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(c.subtitle.uppercase(), color = color, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.graphicsLayer { alpha = pop.value.coerceIn(0f, 1f) })
            Spacer(8)
            RankBadge(c.tier, 170.dp, Modifier.graphicsLayer {
                scaleX = pop.value; scaleY = pop.value; rotationZ = (1 - pop.value) * -40f
            })
            Spacer(8)
            Text(c.tier.label, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("(${topLabel(c.percentile)})", color = Color.White, fontSize = 18.sp)
            Spacer(6)
            Text(c.title, color = Sub, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.width(300.dp))
            c.note?.let { Spacer(6); Text(it, color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.width(300.dp)) }
            Spacer(18)
            Text("Tap to continue", color = Sub, fontSize = 12.sp)
        }
    }
}

/** A rank going down: the old badge sinks and fades, the new one drops in below a red arrow. */
@Composable
private fun RankDownView(c: Ranks.Celebration) {
    val context = LocalContext.current
    val drop = remember(c) { Animatable(0f) }   // 0 = old badge, 1 = new badge
    val arrow = remember(c) { Animatable(0f) }
    LaunchedEffect(c) {
        runCatching {
            context.getSystemService(android.os.Vibrator::class.java)?.vibrate(android.os.VibrationEffect.createOneShot(250, 120))
        }
        delay(700)
        kotlinx.coroutines.coroutineScope {
            launch { arrow.animateTo(1f, tween(500)) }
            launch { drop.animateTo(1f, tween(1100)) }
        }
        delay(4000)
        Ranks.celebrations.remove(c)
    }
    val red = Color(0xFFFF453A)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f))
            .clickable(remember { MutableInteractionSource() }, null) { Ranks.celebrations.remove(c) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("RANK DOWN", color = red, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(8)
            Box(Modifier.size(width = 220.dp, height = 210.dp), contentAlignment = Alignment.Center) {
                val t = drop.value
                // The old rank sinks, shrinks and greys out.
                c.from?.let { old ->
                    RankBadge(old, 150.dp, Modifier.graphicsLayer {
                        translationY = t * 160f; alpha = 1f - t; scaleX = 1f - t * 0.4f; scaleY = 1f - t * 0.4f
                    })
                }
                // The new rank falls into place.
                RankBadge(c.tier, 150.dp, Modifier.graphicsLayer {
                    translationY = (1f - t) * -160f; alpha = t
                })
                Text("▼", color = red, fontSize = 40.sp, modifier = Modifier.align(Alignment.BottomCenter)
                    .graphicsLayer { alpha = arrow.value; translationY = arrow.value * 20f })
            }
            Spacer(8)
            Text(
                buildAnnotatedString {
                    c.from?.let { withStyle(SpanStyle(color = Sub)) { append(it.label + "  →  ") } }
                    append(c.tier.label)
                },
                color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            )
            Text("(${topLabel(c.percentile)})", color = Color.White, fontSize = 18.sp)
            Spacer(6)
            Text(c.title, color = Sub, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.width(300.dp))
            Spacer(6)
            Text(c.note ?: "Your best from the last 30 days is now lower than your old record.", color = Sub, fontSize = 13.sp,
                textAlign = TextAlign.Center, modifier = Modifier.width(280.dp))
            Spacer(18)
            Text("Tap to continue", color = Sub, fontSize = 12.sp)
        }
    }
}

/** Choose which two ranked exercises stand for a muscle group on Home (default: your top two). */
@Composable
private fun GroupPickDialog(group: String, candidates: List<Ranks.Ranked>, profile: Profile, onDone: () -> Unit) {
    val current = Ranks.groupRanks(candidates.associateBy { it.exercise.id }, profile.picksNow)[group]?.second.orEmpty().map { it.exercise.id }
    val chosen = remember { androidx.compose.runtime.mutableStateListOf<String>().apply { addAll(current) } }
    fun save(picks: List<String>) {
        Store.setProfile(Store.profile.value.withPicks(group, picks))
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        title = { Text(titleCase(group)) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Text("Pick up to two exercises to average for this muscle.", color = Sub, fontSize = 13.sp)
                candidates.forEach { r ->
                    val on = r.exercise.id in chosen
                    Row(Modifier.fillMaxWidth().clickable {
                        if (on) chosen.remove(r.exercise.id) else { if (chosen.size >= 2) chosen.removeAt(0); chosen.add(r.exercise.id) }
                    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(on, null)
                        Text(starredName(r.exercise), fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(rankText(r.percentile), fontSize = 12.sp, color = Color(r.tier.color))
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { save(chosen.toList()); onDone() }, enabled = chosen.isNotEmpty()) { Text("Save") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { save(emptyList()); onDone() }) { Text("Use my top two") } },
    )
}
