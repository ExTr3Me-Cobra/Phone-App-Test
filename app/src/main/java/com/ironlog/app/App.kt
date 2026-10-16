package com.ironlog.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

val Bg = Color(0xFF000000)
val Card = Color(0xFF1C1C1E)
val Card2 = Color(0xFF2C2C2E)
val Blue = Color(0xFF2D8CFF)
val Sub = Color(0xFF8E8E93)
val Red = Color(0xFFFF453A)
val Green = Color(0xFF30D158)

/** Where you are in the app. */
sealed interface Route {
    data class Tabs(val tab: Int) : Route
    data class RoutineView(val id: String) : Route
    data class RoutineEdit(val id: String?) : Route
    /** [confirm]: the button text when picking several (default "Add N exercises"). */
    class Picker(val title: String, val multi: Boolean, val confirm: String? = null, val only: ((Exercise) -> Boolean)? = null, val onPick: (List<String>) -> Unit) : Route
    data class ExerciseDetail(val id: String) : Route
    data class EditExercise(val id: String?) : Route
    data object Active : Route
    data class Summary(val id: String) : Route
    data class WorkoutDetail(val id: String) : Route
    data object Settings : Route
    data object Library : Route
    data object HevyMatch : Route
    data class RankDetail(val id: String) : Route
    data object OneRmList : Route
    data object RankHelp : Route
    data class OneRmTest(val id: String) : Route
}

object Nav {
    val stack = mutableStateListOf<Route>(Route.Tabs(0))
    /** A unique key per screen in [stack], so each screen keeps its state (like a search) while you're deeper in. */
    private val keys = mutableListOf(0L)
    private var next = 1L
    /** Keys of closed screens, whose saved state can be thrown away. */
    val closed = mutableListOf<Long>()
    val top get() = stack.last()
    val topKey get() = keys.last()
    fun go(r: Route) { keys += next++; stack += r }
    fun back() { if (stack.size > 1) { closed += keys.removeAt(keys.lastIndex); stack.removeAt(stack.lastIndex) } }
    fun home(tab: Int) { closed += keys; keys.clear(); keys += next++; stack.clear(); stack += Route.Tabs(tab) }
    fun replace(r: Route) { if (stack.isNotEmpty()) { closed += keys.removeAt(keys.lastIndex); stack.removeAt(stack.lastIndex) }; keys += next++; stack += r }
}

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("openWorkout", false) && Store.active.value != null && Nav.top != Route.Active) Nav.go(Route.Active)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StandardsData.init(this)
        OneRm.init(this)
        Store.load(this)
        BodyMap.init(this)
        Rest.ensureChannel(this)
        WorkoutService.start(this)
        if (intent?.getBooleanExtra("openWorkout", false) == true && Store.active.value != null) Nav.go(Route.Active)
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Blue, onPrimary = Color.White, background = Bg, surface = Bg,
                    surfaceVariant = Card, onSurfaceVariant = Sub, surfaceContainerHigh = Card2,
                    surfaceContainer = Card, error = Red,
                ),
            ) {
                Surface(Modifier.fillMaxSize(), color = Bg) { Root() }
            }
        }
    }
}

@Composable
private fun Root() {
    BackHandler(enabled = Nav.stack.size > 1) { Nav.back() }
    val route = Nav.top
    val key = Nav.topKey
    val saved = rememberSaveableStateHolder()
    LaunchedEffect(key) { Nav.closed.forEach { saved.removeState(it) }; Nav.closed.clear() }
    // Tapping outside a text box or scrolling with your finger puts the keyboard away.
    val focus = LocalFocusManager.current
    val hideOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available != Offset.Zero) focus.clearFocus()
                return Offset.Zero
            }
        }
    }
    Column(
        Modifier.fillMaxSize().background(Bg)
            // Watches every tap before buttons and rows handle it, so tapping an exercise or a button closes the keyboard too.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val up = waitForUpOrCancellation(PointerEventPass.Initial)
                    if (up != null && !TextBoxes.contains(up.position)) focus.clearFocus()
                }
            }
            .nestedScroll(hideOnScroll),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
          saved.SaveableStateProvider(key) {
            when (route) {
                is Route.Tabs -> when (route.tab) {
                    0 -> HomeScreen()
                    1 -> WorkoutTab()
                    else -> ProfileScreen()
                }
                is Route.RoutineView -> RoutineViewScreen(route.id)
                is Route.RoutineEdit -> RoutineEditScreen(route.id)
                is Route.Picker -> PickerScreen(route)
                is Route.ExerciseDetail -> ExerciseDetailScreen(route.id)
                is Route.EditExercise -> EditExerciseScreen(route.id)
                Route.Active -> ActiveWorkoutScreen()
                is Route.Summary -> SummaryScreen(route.id)
                is Route.WorkoutDetail -> WorkoutDetailScreen(route.id)
                Route.Settings -> SettingsScreen()
                Route.Library -> PickerScreen(null)
                Route.HevyMatch -> HevyMatchScreen()
                is Route.RankDetail -> RankDetailScreen(route.id)
                Route.OneRmList -> OneRmListScreen()
                Route.RankHelp -> RankHelpScreen()
                is Route.OneRmTest -> OneRmTestScreen(route.id)
            }
          }
        }
        val active by Store.active.collectAsState()
        if (active != null && route != Route.Active) ResumeBar()
        if (route is Route.Tabs) BottomBar(route.tab)
    }
    Onboarding()
    RankUpOverlay()
    WipedScreen()
}

@Composable
private fun ResumeBar() {
    val a = Store.active.collectAsState().value ?: return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    Row(
        Modifier.fillMaxWidth().background(Card2).clickable { Nav.go(Route.Active) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Workout in progress", fontWeight = FontWeight.Bold)
            Text(a.name + if (a.start > 0) " · " + Units.clock(now - a.start) else " · not started", color = Sub, fontSize = 13.sp)
        }
        Text("Resume", color = Blue, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BottomBar(tab: Int) {
    HorizontalDivider(color = Card2)
    Row(Modifier.fillMaxWidth().background(Card).navigationBarsPadding().padding(vertical = 6.dp)) {
        TabItem("Home", "🏠", tab == 0) { Nav.home(0) }
        TabItem("Workout", "🏋", tab == 1) { Nav.home(1) }
        TabItem("Settings", "⚙️", tab == 2) { Nav.home(2) }
    }
}

@Composable
private fun RowScope.TabItem(label: String, icon: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(16.dp)).background(if (selected) Blue.copy(alpha = 0.18f) else Color.Transparent)
                .padding(horizontal = 18.dp, vertical = 2.dp),
        ) { Text(icon, fontSize = 20.sp, color = if (selected) Blue else Color.White) }
        Text(label, fontSize = 12.sp, color = if (selected) Blue else Color.White)
    }
}

// ---------- shared pieces ----------

/** Top bar like the reference screens: back arrow, centred title, optional action. */
@Composable
fun TopBar(title: String, back: Boolean = true, action: String? = null, onBack: () -> Unit = { Nav.back() }, lead: String? = null, onLead: () -> Unit = {}, onAction: () -> Unit = {}) {
    Box(Modifier.fillMaxWidth().background(Card).statusBarsPadding().height(56.dp)) {
        if (back) Text(
            "←", fontSize = 26.sp, modifier = Modifier.align(Alignment.CenterStart).clickable(onClick = onBack).padding(horizontal = 16.dp),
        ) else if (lead != null) Text(
            lead, color = Blue, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterStart).clickable(onClick = onLead).padding(horizontal = 18.dp, vertical = 8.dp),
        )
        // Long titles shrink to two lines and never run under the back arrow or the action.
        val side = maxOf(56, if (action != null) action.length * 10 + 36 else 0)
        val long = title.length > 24
        Text(
            title, fontSize = if (long) 15.sp else 19.sp, lineHeight = if (long) 18.sp else 24.sp, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = side.dp),
        )
        if (action != null) Text(
            action, color = Blue, fontSize = 17.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.CenterEnd).clickable(onClick = onAction).padding(horizontal = 16.dp),
        )
    }
}

@Composable
fun BigButton(text: String, color: Color = Blue, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.height(50.dp), shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }
}

@Composable
fun GreyButton(text: String, modifier: Modifier = Modifier.fillMaxWidth(), textColor: Color = Color.White, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.height(46.dp), shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Card2, contentColor = textColor),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
    ) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, color = Sub, fontSize = 15.sp, modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp))

@Composable
fun Chip(text: String) = Text(
    text, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Card2).padding(horizontal = 8.dp, vertical = 3.dp),
)

@Composable
fun StatBox(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Sub, fontSize = 13.sp)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

/** Header of a set table: SET / LBS / REPS etc. */
@Composable
fun TableHeader(cols: List<Pair<String, Float>>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        cols.forEach { (t, w) -> Text(t, color = Sub, fontSize = 13.sp, modifier = Modifier.weight(w), textAlign = TextAlign.Start) }
    }
}

@Composable
fun ConfirmText(text: String, onYes: () -> Unit, onNo: () -> Unit, yes: String = "Yes") {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onNo,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onYes) { Text(yes, color = Red) } },
        dismissButton = { TextButton(onClick = onNo) { Text("Cancel") } },
    )
}

@Composable
fun Spacer(h: Int) = androidx.compose.foundation.layout.Spacer(Modifier.height(h.dp))

@Composable
fun HSpacer(w: Int) = androidx.compose.foundation.layout.Spacer(Modifier.width(w.dp))

fun columnsFor(t: ExType, withPrevious: Boolean): List<Pair<String, Float>> {
    val prev = if (withPrevious) listOf("PREVIOUS" to 2.2f) else emptyList()
    return listOf("SET" to 0.9f) + prev + when (t) {
        ExType.WEIGHT -> listOf(Units.w.uppercase() to 1.4f, "REPS" to 1.2f)
        ExType.BODYWEIGHT -> listOf("REPS" to 2.6f)
        ExType.TIMED -> listOf("TIME" to 2.6f)
        ExType.CARDIO -> listOf(Units.dist.uppercase() to 1.4f, "TIME" to 1.2f)
        ExType.WEIGHT_TIME -> listOf(Units.w.uppercase() to 1.2f, "TIME" to 1.4f)
    }
}

@Suppress("unused")
private val unusedArrangement = Arrangement.Center
