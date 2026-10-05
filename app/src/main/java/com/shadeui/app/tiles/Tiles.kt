package com.shadeui.app.tiles

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * How a tile works.
 * DIRECT: the app can change it itself.
 * SAMSUNG: Android blocks apps from changing it, so Shade presses Samsung's own tile for you
 * (see [SamsungTileTapper]). [samsungLabels] are the names it looks for.
 */
enum class TileKind { DIRECT, SAMSUNG }

data class TileDef(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val kind: TileKind,
    val samsungLabels: List<String> = listOf(label),
    /** True for tiles that do something once (no on/off state). */
    val momentary: Boolean = false,
)

object Tiles {
    val all = listOf(
        TileDef("wifi", "Wi-Fi", Icons.Filled.Wifi, TileKind.SAMSUNG, listOf("Wi-Fi", "Wi‑Fi", "WiFi")),
        TileDef("bluetooth", "Bluetooth", Icons.Filled.Bluetooth, TileKind.SAMSUNG),
        TileDef("flashlight", "Flashlight", Icons.Filled.FlashlightOn, TileKind.DIRECT),
        TileDef("rotate", "Auto rotate", Icons.Filled.ScreenRotation, TileKind.DIRECT),
        TileDef("screen_recorder", "Screen recorder", Icons.Filled.Videocam, TileKind.SAMSUNG, momentary = true),
        TileDef("airplane", "Airplane mode", Icons.Filled.AirplanemodeActive, TileKind.SAMSUNG, listOf("Airplane mode", "Flight mode")),
        TileDef("mobile_data", "Mobile data", Icons.Filled.SwapVert, TileKind.SAMSUNG, listOf("Mobile data", "Data")),
        TileDef("smart_view", "Smart View", Icons.Filled.Cast, TileKind.SAMSUNG, momentary = true),
        TileDef("dnd", "Do not disturb", Icons.Filled.DoNotDisturbOn, TileKind.DIRECT),
        TileDef("sound", "Sound", Icons.AutoMirrored.Filled.VolumeUp, TileKind.DIRECT),
        TileDef("location", "Location", Icons.Filled.LocationOn, TileKind.SAMSUNG),
        TileDef("hotspot", "Mobile Hotspot", Icons.Filled.WifiTethering, TileKind.SAMSUNG, listOf("Mobile Hotspot", "Hotspot")),
        TileDef("power_saving", "Power saving", Icons.Filled.BatterySaver, TileKind.SAMSUNG),
        TileDef("dark_mode", "Dark mode", Icons.Filled.DarkMode, TileKind.SAMSUNG),
        TileDef("eye_comfort", "Eye comfort shield", Icons.Filled.NightlightRound, TileKind.SAMSUNG, listOf("Eye comfort shield", "Eye comfort")),
        TileDef("nfc", "NFC", Icons.Filled.Nfc, TileKind.SAMSUNG, listOf("NFC", "NFC and contactless payments")),
    )

    private val byId = all.associateBy { it.id }
    fun get(id: String): TileDef? = byId[id]
}
