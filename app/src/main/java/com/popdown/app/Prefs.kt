package com.popdown.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LightGroup(val label: String) { AROUND("Around the screen"), ECHO("Echo"), SIDES("Sides only") }

enum class LightEffect(val label: String, val group: LightGroup = LightGroup.AROUND) {
    GLOW("Glow"),
    LINE("Line"),
    PULSE("Pulse"),
    HEARTBEAT("Heartbeat"),
    COMET("Comet"),
    TWIN("Twin comets"),
    WAVE("Wave"),
    GLITTER("Glitter"),
    RAINBOW("Rainbow"),
    FLASH("Flash"),
    BUBBLES("Bubbles"),
    ECLIPSE("Eclipse"),
    SPOTLIGHT("Spotlight"),
    ECHO("Echo", LightGroup.ECHO),
    ECHO_DOUBLE("Double echo", LightGroup.ECHO),
    ECHO_TOP("Echo from the top", LightGroup.ECHO),
    ECHO_CAMERA("Echo from the camera", LightGroup.ECHO),
    ECHO_SIDES("Echo on the sides", LightGroup.ECHO),
    SIDE_GLOW("Side glow", LightGroup.SIDES),
    SIDE_PULSE("Side pulse", LightGroup.SIDES),
    SIDE_SWEEP("Side sweep", LightGroup.SIDES),
    SIDE_BOUNCE("Side bounce", LightGroup.SIDES),
    SIDE_WAVE("Side wave", LightGroup.SIDES),
    SIDE_RAIN("Side rain", LightGroup.SIDES),
    SIDE_FLASH("Side flash", LightGroup.SIDES),
}

enum class LightColorMode(val label: String) {
    APP("App's colour"),
    ONE("My colour"),
    TWO("Two-colour gradient"),
}

data class LightSettings(
    val enabled: Boolean = true,
    val effect: LightEffect = LightEffect.GLOW,
    val colorMode: LightColorMode = LightColorMode.APP,
    val color1: Int = 0xFF3D8BFF.toInt(),
    val color2: Int = 0xFFB04DFF.toInt(),
    /** 0.25..3, 1 = normal. */
    val speed: Float = 1f,
    val thicknessDp: Float = 6f,
    /** Follow the screen's own rounded corners. */
    val matchCorners: Boolean = true,
    val cornerDp: Float = 40f,
    /** 20..100 % */
    val brightness: Int = 100,
    val seconds: Int = 5,
    /** Lighting while the phone is unlocked and in use. */
    val onUnlocked: Boolean = true,
    /** Lighting on the lock screen. */
    val onLocked: Boolean = true,
    /** On the lock screen, keep the lighting going until you unlock (or the screen goes off). */
    val untilUnlocked: Boolean = false,
    /** With the Always On Display showing, play the lighting on it instead of waking the screen. */
    val onAodNoWake: Boolean = true,
    /** If Pop Down turned the screen on, turn it back off once the lighting has finished. */
    val screenOffAfter: Boolean = false,
)

data class PopSettings(
    /** Master switch. */
    val enabled: Boolean = true,
    /** Turn the screen on when a notification arrives while it's off. */
    val wakeScreen: Boolean = true,
    /** Leave notifications that already pop down on their own alone (avoids double pop-ups). */
    val skipIfAlreadyPops: Boolean = true,
    /** Also pop down notifications the app marked as silent. */
    val includeSilent: Boolean = true,
    /** How long the pop-down copy stays in the notification list before removing itself. */
    val keepSeconds: Int = 8,
    /** Apps that never get a pop-down copy. */
    val excluded: Set<String> = emptySet(),
    /** Also drop the pop-down from the top on the lock screen (off = lock screen just lights up). */
    val popOnLock: Boolean = false,
    val light: LightSettings = LightSettings(),
)

/** Settings, saved in SharedPreferences and observable for the screen. */
object Prefs {
    private val state = MutableStateFlow(PopSettings())
    val flow: StateFlow<PopSettings> = state.asStateFlow()
    private var loaded = false

    fun get(context: Context): PopSettings {
        if (!loaded) {
            val p = prefs(context)
            val d = LightSettings()
            state.value = PopSettings(
                enabled = p.getBoolean("enabled", true),
                wakeScreen = p.getBoolean("wakeScreen", true),
                skipIfAlreadyPops = p.getBoolean("skipIfAlreadyPops", true),
                includeSilent = p.getBoolean("includeSilent", true),
                keepSeconds = p.getInt("keepSeconds", 8),
                excluded = p.getStringSet("excluded", emptySet()).orEmpty().toSet(),
                popOnLock = p.getBoolean("popOnLock", false),
                light = LightSettings(
                    enabled = p.getBoolean("l.enabled", d.enabled),
                    effect = p.enum("l.effect", d.effect),
                    colorMode = p.enum("l.colorMode", d.colorMode),
                    color1 = p.getInt("l.color1", d.color1),
                    color2 = p.getInt("l.color2", d.color2),
                    speed = p.getFloat("l.speed", d.speed),
                    thicknessDp = p.getFloat("l.thickness", d.thicknessDp),
                    matchCorners = p.getBoolean("l.matchCorners", d.matchCorners),
                    cornerDp = p.getFloat("l.corner", d.cornerDp),
                    brightness = p.getInt("l.brightness", d.brightness),
                    seconds = p.getInt("l.seconds", d.seconds),
                    onUnlocked = p.getBoolean("l.onUnlocked", d.onUnlocked),
                    onLocked = p.getBoolean("l.onLocked", d.onLocked),
                    untilUnlocked = p.getBoolean("l.untilUnlocked", d.untilUnlocked),
                    onAodNoWake = p.getBoolean("l.onAodNoWake", d.onAodNoWake),
                    screenOffAfter = p.getBoolean("l.screenOffAfter", d.screenOffAfter),
                ),
            )
            loaded = true
        }
        return state.value
    }

    fun update(context: Context, change: (PopSettings) -> PopSettings) {
        val next = change(get(context))
        state.value = next
        val l = next.light
        prefs(context).edit()
            .putBoolean("enabled", next.enabled)
            .putBoolean("wakeScreen", next.wakeScreen)
            .putBoolean("skipIfAlreadyPops", next.skipIfAlreadyPops)
            .putBoolean("includeSilent", next.includeSilent)
            .putInt("keepSeconds", next.keepSeconds)
            .putStringSet("excluded", next.excluded)
            .putBoolean("l.enabled", l.enabled)
            .putString("l.effect", l.effect.name)
            .putString("l.colorMode", l.colorMode.name)
            .putInt("l.color1", l.color1)
            .putInt("l.color2", l.color2)
            .putFloat("l.speed", l.speed)
            .putFloat("l.thickness", l.thicknessDp)
            .putBoolean("l.matchCorners", l.matchCorners)
            .putFloat("l.corner", l.cornerDp)
            .putInt("l.brightness", l.brightness)
            .putInt("l.seconds", l.seconds)
            .putBoolean("l.onUnlocked", l.onUnlocked)
            .putBoolean("l.onLocked", l.onLocked)
            .putBoolean("l.untilUnlocked", l.untilUnlocked)
            .putBoolean("l.onAodNoWake", l.onAodNoWake)
            .putBoolean("l.screenOffAfter", l.screenOffAfter)
            .putBoolean("popOnLock", next.popOnLock)
            .apply()
    }

    fun updateLight(context: Context, change: (LightSettings) -> LightSettings) =
        update(context) { it.copy(light = change(it.light)) }

    private inline fun <reified E : Enum<E>> SharedPreferences.enum(key: String, default: E): E =
        runCatching { enumValueOf<E>(getString(key, null) ?: return default) }.getOrDefault(default)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
}
