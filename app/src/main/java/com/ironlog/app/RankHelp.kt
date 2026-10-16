package com.ironlog.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Home's "?": how ranks work, from what you're compared with to how each number is worked out. */
@Composable
fun RankHelpScreen() {
    Column(Modifier.fillMaxSize()) {
        TopBar("How ranks work")
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Every ranked exercise gets a rank from Wood 1 to Legend, based on how strong you are compared with other people " +
                "who lift. Your muscle ranks and your overall rank are built from those.", color = Sub, fontSize = 14.sp)

            HelpCard("1. The ranks") {
                Para("There are 20 ranks. Each one is a 5% slice of people: Wood 1 is the bottom 5%, Legend is the top 5%.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(Tier.WOOD_1, Tier.BRONZE_1, Tier.SILVER_1, Tier.GOLD_1, Tier.PLATINUM_1, Tier.DIAMOND_1, Tier.OLYMPIAN, Tier.LEGEND)
                        .forEach { RankBadge(it, 30.dp) }
                }
                Para("Wood → Bronze → Silver → Gold → Platinum → Diamond (each 1, 2, 3), then Olympian (top 10%) and Legend (top 5%).")
                Para("\"Top 42%\" means you're stronger than 58% of the people you're compared with.")
            }

            HelpCard("2. Who you're compared with") {
                Point("People who lift", "Strength Level's standards, built from lifts logged by gym-goers, so the bar is higher than for the general public.")
                Point("Your sex and body weight", "You're compared with people of your sex at your body weight (blended between the nearest published weights).")
                Point("Your age", "Ages 25–40 need no adjustment. Younger or older, the levels are scaled to what's typical for your age.")
                Point("Grip Dynamometer", "The exception: compared with everyone aged 20–39 of your sex (not just lifters); body weight and age aren't used.")
                Para("Set your body weight, age and sex in Settings – ranks need them to be accurate.")
            }

            HelpCard("3. The stars") {
                Point("★ Gold", "Ranked from data that closely fits you and that exact exercise.", StarGold)
                Point("★ Blue", "An estimate: a close variation of a lift with data, or data that doesn't fully fit you (e.g. your body weight is outside the published range).", StarEstimate)
                Point("★ Purple", "AI estimate: no published data, so a percentage of a similar lift agreed by several AIs. Least accurate.", StarAi)
                Point("No star", "Not ranked.")
                Para("Settings → Ranks picks which of these count: Off, Accurate only (gold), All on (gold + blue) or All on + AI (adds purple).")
            }

            HelpCard("4. How a set becomes a number") {
                Point("Weight & reps", "Your estimated 1-rep max, using the Wathen formula. Reps above 20 count as 20. A heavier set with fewer reps gives the most accurate estimate.")
                Point("Bodyweight moves", "The most reps in one set (push-ups, pull-ups, dips, sit-ups…).")
                Point("Dynamometer", "Your best single squeeze.")
                Point("One-leg calf raise", "Turned into the two-leg load it matches (body weight + vest on one leg).")
                Point("Never counted", "Warm-up sets (W). A set logged at 0 lb counts as 1 lb so it still gets a rank.")
                Para("Read each exercise's How to log so you enter the weight the way the data expects (total bar weight, per dumbbell, stack weight…).")
            }

            HelpCard("5. Which number sets your rank") {
                Point("From workouts", "Your best set in the 24 hours up to the last time you did the exercise. A weaker session more than 24 hours later lowers it; skipping an exercise never does.")
                Point("1 Rep Max tests", "Your latest test sets the rank. Workouts after it can only raise it. Only a weaker test 24 hours or more after a better one lowers it.")
                Point("Rank changes", "Show as a pop-up the moment you tick the set that causes them, with a 👑 on the set that set a new best.")
            }

            HelpCard("6. Muscle groups and your overall rank") {
                Point("Muscle group rank", "The average of two ranked exercises for that muscle – your top two, or the two you pick with ✎.")
                Point("Grip", "With Separate Grip rank on, Grip comes only from the Grip Dynamometer Test and colours your hands. Off, it's part of Forearms.")
                Point("Overall rank", "The average of all your ranked muscle groups. A group with no ranked exercise doesn't count yet.")
                Point("Heatmap", "Each ranked group is coloured with its rank, front and back. Grey means not ranked yet.")
            }

            HelpCard("7. The levels") {
                Para("Each rank screen lists five levels with the weight you need for each:")
                listOf("Beginner" to 5.0, "Novice" to 20.0, "Intermediate" to 50.0, "Advanced" to 80.0, "Elite" to 95.0).forEach { (n, p) ->
                    val t = Tier.of(p)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n, Modifier.width(110.dp), fontSize = 14.sp)
                        Text("beats ${p.toInt()}%", Modifier.width(80.dp), color = Sub, fontSize = 13.sp)
                        RankBadge(t, 18.dp)
                        Text(" " + t.label, color = Color(t.color), fontSize = 13.sp)
                    }
                }
            }

            HelpCard("8. Getting accurate ranks") {
                Para("• Match imported or new exercises to exactly the same movement – read Proper Form and How to log first.")
                Para("• Keep your body weight up to date in Settings.")
                Para("• For the truest rank, use 1 Rep Max mode, or log heavy sets of 1–5 reps.")
                Para("• Tap any rank to see the chart, the levels and exactly who you're compared with.")
            }
            Spacer(20)
        }
    }
}

@Composable
private fun HelpCard(title: String, content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    content()
}

@Composable
private fun Para(text: String) = Text(text, fontSize = 14.sp, lineHeight = 20.sp)

@Composable
private fun Point(label: String, text: String, color: Color = Color.White) = Column {
    Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = color)
    Text(text, color = Sub, fontSize = 13.sp, lineHeight = 18.sp)
}
