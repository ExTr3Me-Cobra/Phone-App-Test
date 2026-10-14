package com.ironlog.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val PAGES = 5

/**
 * First-run welcome. Covers the whole app until you've entered your body weight, age and sex
 * (saved straight into your profile) and stepped through the intro. It can't be skipped or closed:
 * the back button and taps behind it do nothing.
 */
@Composable
fun Onboarding() {
    val p by Store.profile.collectAsState()
    if (p.onboarded) return
    var page by rememberSaveable { mutableStateOf(0) }
    val focus = LocalFocusManager.current
    LaunchedEffect(page) { focus.clearFocus() }
    BackHandler { if (page > 0) page-- } // never closes it

    Box(
        Modifier.fillMaxSize().background(Bg)
            // Swallow every tap so nothing behind it can be used.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val up = waitForUpOrCancellation(PointerEventPass.Initial)
                    if (up != null && !TextBoxes.contains(up.position)) focus.clearFocus()
                }
            }
            .systemBarsPadding().imePadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.Center) {
                repeat(PAGES) { i ->
                    Box(Modifier.padding(4.dp).size(if (i == page) 10.dp else 8.dp).clip(CircleShape)
                        .background(if (i <= page) Blue else Card2))
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (page) {
                    0 -> WelcomePage()
                    1 -> AboutYouPage(p)
                    2 -> RanksPage()
                    3 -> PercentPage()
                    else -> ReadyPage()
                }
            }
            val ok = page != 1 || biometricsMissing(p) == null
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (page == 1) biometricsMissing(p)?.let { Text(it, color = Sub, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (page > 0) GreyButton("Back", Modifier.weight(1f)) { page-- }
                    BigButton(
                        when (page) { 0 -> "Get started"; PAGES - 1 -> "Start lifting"; else -> "Next" },
                        color = if (ok) Blue else Card2, modifier = Modifier.weight(2f),
                    ) {
                        if (!ok) return@BigButton
                        if (page < PAGES - 1) page++ else Store.setProfile(Store.profile.value.copy(onboarded = true))
                    }
                }
            }
        }
    }
}

/** What still needs filling in on the About you page, or null when it's complete. */
private fun biometricsMissing(p: Profile): String? {
    val bw = p.bodyweight
    val age = p.age
    return when {
        bw == null -> "Enter your body weight to continue."
        bw < 60 || bw > 700 -> "That body weight doesn't look right."
        age == null -> "Enter your age to continue."
        age < 13 || age > 100 -> "Age must be between 13 and 100."
        else -> null
    }
}

@Composable
private fun Title(text: String) = Text(text, fontSize = 26.sp, fontWeight = FontWeight.Bold)

@Composable
private fun Body(text: String) = Text(text, color = Color(0xFFD1D1D6), fontSize = 15.sp, lineHeight = 21.sp)

@Composable
private fun InfoCard(content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card).padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) { content() }

@Composable
private fun WelcomePage() {
    Spacer(30)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { RankBadge(Tier.LEGEND, 120.dp) }
    Text("Welcome to Loadout", fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Body("Log your workouts, build routines, see which muscles you've trained – and find out how strong you really are " +
        "compared with other people who lift.")
    InfoCard {
        Text("🏋️  Log every set, rep and weight", fontSize = 15.sp)
        Text("🔥  Muscle heatmaps for every exercise", fontSize = 15.sp)
        Text("🏅  Ranks from Wood to Legend", fontSize = 15.sp)
        Text("📊  Percentile scores for your lifts", fontSize = 15.sp)
    }
    Body("First, a few details about you so your ranks are fair.")
}

@Composable
private fun AboutYouPage(p: Profile) {
    fun set(np: Profile) = Store.setProfile(np)
    Title("About you")
    Body("Strength depends a lot on body weight, sex and age, so you're only ranked against lifters like you. " +
        "You can change these any time on the Profile tab.")

    Text("Units", fontWeight = FontWeight.Medium)
    var unitsTick by remember { mutableStateOf(0) } // refreshes the weight box when units change
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!p.kg, { set(p.copy(kg = false)); unitsTick++ }, { Text("lbs") })
        FilterChip(p.kg, { set(p.copy(kg = true)); unitsTick++ }, { Text("kg") })
    }

    Text("Sex", fontWeight = FontWeight.Medium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(p.male, { set(p.copy(male = true)) }, { Text("Male") })
        FilterChip(!p.male, { set(p.copy(male = false)) }, { Text("Female") })
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        var bw by remember(unitsTick) { mutableStateOf(p.bodyweight?.let { Units.weight(it) } ?: "") }
        OutlinedTextField(bw, { t -> bw = t; set(Store.profile.value.copy(bodyweight = t.toDoubleOrNull()?.let(Units::fromShown))) },
            Modifier.weight(1f).typing(), label = { Text("Body weight (${Units.w})") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        var age by remember { mutableStateOf(p.age?.toString() ?: "") }
        OutlinedTextField(age, { t -> age = t; set(Store.profile.value.copy(age = t.toIntOrNull())) },
            Modifier.weight(1f).typing(), label = { Text("Age") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        var ft by remember { mutableStateOf(p.heightIn?.let { (it / 12).toString() } ?: "") }
        var inch by remember { mutableStateOf(p.heightIn?.let { (it % 12).toString() } ?: "") }
        fun saveH() { val f = ft.toIntOrNull(); val i = inch.toIntOrNull() ?: 0; set(Store.profile.value.copy(heightIn = f?.let { it * 12 + i })) }
        OutlinedTextField(ft, { ft = it; saveH() }, Modifier.weight(1f).typing(), label = { Text("Height ft (optional)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(inch, { inch = it; saveH() }, Modifier.weight(1f).typing(), label = { Text("in") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }
    Text("Height isn't used for ranking – the strength data is based on body weight, sex and age.", color = Sub, fontSize = 12.sp)
}

@Composable
private fun RanksPage() {
    Title("Ranks")
    Body("Every exercise with reliable strength data gets a rank. There are 20 ranks, each one 5% of lifters wide:")
    InfoCard {
        val groups = listOf(
            "Wood" to Tier.WOOD_1, "Bronze" to Tier.BRONZE_1, "Silver" to Tier.SILVER_1,
            "Gold" to Tier.GOLD_1, "Platinum" to Tier.PLATINUM_1, "Diamond" to Tier.DIAMOND_1,
        )
        groups.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (name, first) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row { (0..2).forEach { RankBadge(Tier.entries[first.ordinal + it], 30.dp) } }
                        Text(name, fontSize = 13.sp, color = Color(first.color))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(Tier.OLYMPIAN, Tier.LEGEND).forEach { t ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RankBadge(t, 56.dp)
                    Text("${t.label} · top ${t.topPct}%", fontSize = 13.sp, color = Color(t.color), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    Body("Each rank comes from your best set in the most recent session of that exercise. Your 1-rep max is estimated " +
        "from weight and reps, so a heavy 5 counts as much as the single it equals.")
    Body("Ranks go up the moment you tick a set – you'll get a celebration – and only go down if a later session is weaker. " +
        "Skipping an exercise never lowers it.")
    Body("Your overall rank and the home-screen muscle map are built from all your ranked exercises.")
}

@Composable
private fun PercentPage() {
    Title("Percentile scores")
    Body("Next to every rank is where you stand among lifters your size, sex and age, as a \"top %\". Top 42% means you're stronger than 58% of them.")
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RankBadge(Tier.GOLD_2, 48.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text("Bench Press ★", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Gold 2 (top 42%)", color = Color(Tier.GOLD_2.color), fontSize = 14.sp)
            }
        }
        Text("Stronger than 58% of comparable lifters. Your overall rank is the average across all your ranked exercises.", color = Sub, fontSize = 13.sp)
    }
    Body("Ranked exercises have a star after their name:")
    InfoCard {
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = StarGold)) { append("★  Gold") }
            append(" – accurate: data for this exact exercise (or a near-identical one) that fits you.")
        }, fontSize = 14.sp)
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = StarEstimate)) { append("★  Blue") }
            append(" – estimate: worked out from a close variation, or from data that doesn't fully fit you.")
        }, fontSize = 14.sp)
    }
    Body("By default only accurate ranks are shown. In Settings → Ranks you can show estimates too, or turn ranks off.")
}

@Composable
private fun ReadyPage() {
    Title("You're set")
    InfoCard {
        Text("🏠  Home – your week or all-time stats, calendar, and rank map", fontSize = 15.sp)
        Text("🏋️  Workout – start empty or from a routine", fontSize = 15.sp)
        Text("👤  Profile – your details, exercises and settings", fontSize = 15.sp)
    }
    Body("Tap any exercise to see its muscle heatmap, what weight to log, and your rank.")
    Body("Coming from Hevy? Settings → Restore / Import accepts your Hevy CSV export.")
}
