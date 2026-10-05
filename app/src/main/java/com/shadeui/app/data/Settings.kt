package com.shadeui.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PopupStyle { BRIEF, DETAILED }
enum class LightStyle { GLOW, LINE, GRADIENT, PULSE }
enum class LightColorMode { APP_ICON, NOTIFICATION, CUSTOM, RAINBOW }
enum class LockStyle { CARDS, ICONS, OFF }
enum class PullArea { FULL, LEFT_HALF, RIGHT_HALF }
enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Every global option. Per-app rules ([AppRule]) can override the alert-related ones. */
data class ShadeSettings(
    /** Master switch for the whole app. */
    val enabled: Boolean = true,
    // Panel
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val pullArea: PullArea = PullArea.FULL,
    val pullInFullscreen: Boolean = false,
    val blurRadius: Int = 60,
    val dimPercent: Int = 55,
    val collapsedTileCount: Int = 6,
    val tiles: List<String> = DEFAULT_TILES,
    val showTileLabels: Boolean = false,
    val showBrightnessCollapsed: Boolean = true,
    val showMediaCard: Boolean = true,
    val showCarrier: Boolean = true,
    val closeAfterTile: Boolean = false,
    val haptics: Boolean = true,
    // Notification list
    val groupByApp: Boolean = true,
    val separateSilent: Boolean = true,
    val showTimestamps: Boolean = true,
    val closeAfterOpen: Boolean = true,
    val maxLinesCollapsed: Int = 2,
    // Pop-ups
    val popupStyle: PopupStyle = PopupStyle.DETAILED,
    val popupSeconds: Int = 5,
    val popupSkipForegroundApp: Boolean = true,
    val popupInFullscreen: Boolean = true,
    val popupOnLockScreen: Boolean = true,
    // Edge lighting
    val lightStyle: LightStyle = LightStyle.GLOW,
    val lightColorMode: LightColorMode = LightColorMode.APP_ICON,
    val lightCustomColor: Int = 0xFF4FC3F7.toInt(),
    val lightThicknessDp: Int = 6,
    val lightOpacity: Int = 100,
    val lightRepeats: Int = 3,
    val defaultLightLocked: Boolean = true,
    val defaultWakeScreen: Boolean = true,
    val defaultLightUnlocked: Boolean = false,
    val wakeSeconds: Int = 6,
    val screenOffAfterWake: Boolean = true,
    val previewWithLighting: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietStartMinutes: Int = 23 * 60,
    val quietEndMinutes: Int = 7 * 60,
    // Lock screen
    val lockStyle: LockStyle = LockStyle.CARDS,
    val lockHideContent: Boolean = false,
    val lockMaxCards: Int = 4,
    val lockTopPercent: Int = 42,
    val lockCardOpacity: Int = 85,
) {
    companion object {
        /** Order matches the screenshots: first six show in the collapsed row. */
        val DEFAULT_TILES = listOf(
            "wifi", "bluetooth", "flashlight", "rotate", "screen_recorder", "airplane",
            "mobile_data", "smart_view", "dnd", "sound", "location", "hotspot",
            "power_saving", "dark_mode", "eye_comfort", "nfc",
        )
    }
}

/** Loads and saves [ShadeSettings]; [flow] updates live so overlays react immediately. */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val flow: StateFlow<ShadeSettings> = state.asStateFlow()
    val value: ShadeSettings get() = state.value

    fun update(change: (ShadeSettings) -> ShadeSettings) {
        val next = change(state.value)
        state.value = next
        save(next)
    }

    private fun load(): ShadeSettings {
        val d = ShadeSettings()
        val p = prefs
        return ShadeSettings(
            enabled = p.getBoolean("enabled", d.enabled),
            theme = enum(p.getString("theme", null), d.theme),
            pullArea = enum(p.getString("pullArea", null), d.pullArea),
            pullInFullscreen = p.getBoolean("pullInFullscreen", d.pullInFullscreen),
            blurRadius = p.getInt("blurRadius", d.blurRadius),
            dimPercent = p.getInt("dimPercent", d.dimPercent),
            collapsedTileCount = p.getInt("collapsedTileCount", d.collapsedTileCount),
            tiles = p.getString("tiles", null)?.split(",")?.filter { it.isNotBlank() } ?: d.tiles,
            showTileLabels = p.getBoolean("showTileLabels", d.showTileLabels),
            showBrightnessCollapsed = p.getBoolean("showBrightnessCollapsed", d.showBrightnessCollapsed),
            showMediaCard = p.getBoolean("showMediaCard", d.showMediaCard),
            showCarrier = p.getBoolean("showCarrier", d.showCarrier),
            closeAfterTile = p.getBoolean("closeAfterTile", d.closeAfterTile),
            haptics = p.getBoolean("haptics", d.haptics),
            groupByApp = p.getBoolean("groupByApp", d.groupByApp),
            separateSilent = p.getBoolean("separateSilent", d.separateSilent),
            showTimestamps = p.getBoolean("showTimestamps", d.showTimestamps),
            closeAfterOpen = p.getBoolean("closeAfterOpen", d.closeAfterOpen),
            maxLinesCollapsed = p.getInt("maxLinesCollapsed", d.maxLinesCollapsed),
            popupStyle = enum(p.getString("popupStyle", null), d.popupStyle),
            popupSeconds = p.getInt("popupSeconds", d.popupSeconds),
            popupSkipForegroundApp = p.getBoolean("popupSkipForegroundApp", d.popupSkipForegroundApp),
            popupInFullscreen = p.getBoolean("popupInFullscreen", d.popupInFullscreen),
            popupOnLockScreen = p.getBoolean("popupOnLockScreen", d.popupOnLockScreen),
            lightStyle = enum(p.getString("lightStyle", null), d.lightStyle),
            lightColorMode = enum(p.getString("lightColorMode", null), d.lightColorMode),
            lightCustomColor = p.getInt("lightCustomColor", d.lightCustomColor),
            lightThicknessDp = p.getInt("lightThicknessDp", d.lightThicknessDp),
            lightOpacity = p.getInt("lightOpacity", d.lightOpacity),
            lightRepeats = p.getInt("lightRepeats", d.lightRepeats),
            defaultLightLocked = p.getBoolean("defaultLightLocked", d.defaultLightLocked),
            defaultWakeScreen = p.getBoolean("defaultWakeScreen", d.defaultWakeScreen),
            defaultLightUnlocked = p.getBoolean("defaultLightUnlocked", d.defaultLightUnlocked),
            wakeSeconds = p.getInt("wakeSeconds", d.wakeSeconds),
            screenOffAfterWake = p.getBoolean("screenOffAfterWake", d.screenOffAfterWake),
            previewWithLighting = p.getBoolean("previewWithLighting", d.previewWithLighting),
            quietHoursEnabled = p.getBoolean("quietHoursEnabled", d.quietHoursEnabled),
            quietStartMinutes = p.getInt("quietStartMinutes", d.quietStartMinutes),
            quietEndMinutes = p.getInt("quietEndMinutes", d.quietEndMinutes),
            lockStyle = enum(p.getString("lockStyle", null), d.lockStyle),
            lockHideContent = p.getBoolean("lockHideContent", d.lockHideContent),
            lockMaxCards = p.getInt("lockMaxCards", d.lockMaxCards),
            lockTopPercent = p.getInt("lockTopPercent", d.lockTopPercent),
            lockCardOpacity = p.getInt("lockCardOpacity", d.lockCardOpacity),
        )
    }

    private fun save(s: ShadeSettings) {
        prefs.edit()
            .putBoolean("enabled", s.enabled)
            .putString("theme", s.theme.name)
            .putString("pullArea", s.pullArea.name)
            .putBoolean("pullInFullscreen", s.pullInFullscreen)
            .putInt("blurRadius", s.blurRadius)
            .putInt("dimPercent", s.dimPercent)
            .putInt("collapsedTileCount", s.collapsedTileCount)
            .putString("tiles", s.tiles.joinToString(","))
            .putBoolean("showTileLabels", s.showTileLabels)
            .putBoolean("showBrightnessCollapsed", s.showBrightnessCollapsed)
            .putBoolean("showMediaCard", s.showMediaCard)
            .putBoolean("showCarrier", s.showCarrier)
            .putBoolean("closeAfterTile", s.closeAfterTile)
            .putBoolean("haptics", s.haptics)
            .putBoolean("groupByApp", s.groupByApp)
            .putBoolean("separateSilent", s.separateSilent)
            .putBoolean("showTimestamps", s.showTimestamps)
            .putBoolean("closeAfterOpen", s.closeAfterOpen)
            .putInt("maxLinesCollapsed", s.maxLinesCollapsed)
            .putString("popupStyle", s.popupStyle.name)
            .putInt("popupSeconds", s.popupSeconds)
            .putBoolean("popupSkipForegroundApp", s.popupSkipForegroundApp)
            .putBoolean("popupInFullscreen", s.popupInFullscreen)
            .putBoolean("popupOnLockScreen", s.popupOnLockScreen)
            .putString("lightStyle", s.lightStyle.name)
            .putString("lightColorMode", s.lightColorMode.name)
            .putInt("lightCustomColor", s.lightCustomColor)
            .putInt("lightThicknessDp", s.lightThicknessDp)
            .putInt("lightOpacity", s.lightOpacity)
            .putInt("lightRepeats", s.lightRepeats)
            .putBoolean("defaultLightLocked", s.defaultLightLocked)
            .putBoolean("defaultWakeScreen", s.defaultWakeScreen)
            .putBoolean("defaultLightUnlocked", s.defaultLightUnlocked)
            .putInt("wakeSeconds", s.wakeSeconds)
            .putBoolean("screenOffAfterWake", s.screenOffAfterWake)
            .putBoolean("previewWithLighting", s.previewWithLighting)
            .putBoolean("quietHoursEnabled", s.quietHoursEnabled)
            .putInt("quietStartMinutes", s.quietStartMinutes)
            .putInt("quietEndMinutes", s.quietEndMinutes)
            .putString("lockStyle", s.lockStyle.name)
            .putBoolean("lockHideContent", s.lockHideContent)
            .putInt("lockMaxCards", s.lockMaxCards)
            .putInt("lockTopPercent", s.lockTopPercent)
            .putInt("lockCardOpacity", s.lockCardOpacity)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enum(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
