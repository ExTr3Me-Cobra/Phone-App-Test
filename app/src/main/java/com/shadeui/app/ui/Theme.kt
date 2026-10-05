package com.shadeui.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import com.shadeui.app.ShadeApp
import com.shadeui.app.data.ThemeMode
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** One UI-style colours for the panel (taken from the S26 Ultra screenshots). */
data class ShadePalette(
    val dark: Boolean,
    val scrim: Color,
    val card: Color,
    val text: Color,
    val subText: Color,
    val tileOn: Color,
    val tileOnIcon: Color,
    val tileOff: Color,
    val tileOffIcon: Color,
    val sliderTrack: Color,
    val sliderFill: Color,
    val sliderIcon: Color,
    val pill: Color,
    val pillSelected: Color,
    val chip: Color,
)

val DarkPalette = ShadePalette(
    dark = true,
    scrim = Color(0xFF161616),
    card = Color(0xF02A2631),
    text = Color(0xFFF4F4F4),
    subText = Color(0xFFB9B6BF),
    tileOn = Color(0xFFE4E4E4),
    tileOnIcon = Color(0xFF2A2A2A),
    tileOff = Color(0xB3615D68),
    tileOffIcon = Color(0xFFE6E6E6),
    sliderTrack = Color(0xFF5E5A66),
    sliderFill = Color(0xFFE4E4E4),
    sliderIcon = Color(0xFF3A3A3A),
    pill = Color(0xF0222026),
    pillSelected = Color(0xFF3A3741),
    chip = Color(0xFF1C1A20),
)

val LightPalette = ShadePalette(
    dark = false,
    scrim = Color(0xFFEDEDF0),
    card = Color(0xF5FFFFFF),
    text = Color(0xFF111111),
    subText = Color(0xFF5F5C66),
    tileOn = Color(0xFF3E6FE0),
    tileOnIcon = Color(0xFFFFFFFF),
    tileOff = Color(0xFFDCDAE0),
    tileOffIcon = Color(0xFF3A3A3A),
    sliderTrack = Color(0xFFD4D2D9),
    sliderFill = Color(0xFF3E6FE0),
    sliderIcon = Color(0xFFFFFFFF),
    pill = Color(0xF5FFFFFF),
    pillSelected = Color(0xFFE2E0E7),
    chip = Color(0xFFEDEBF1),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

@Composable
fun ShadeTheme(content: @Composable () -> Unit) {
    val settings by ShadeApp.instance.settings.flow.collectAsState()
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val palette = if (dark) DarkPalette else LightPalette
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        CompositionLocalProvider(LocalPalette provides palette, content = content)
    }
}

/** Sideways swipe to dismiss, like One UI's notification cards. */
@Composable
fun SwipeDismiss(
    enabled: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableFloatStateOf(1f) }
    Box(
        modifier
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .offset { IntOffset(offset.value.roundToInt(), 0) }
            .graphicsLayer { alpha = 1f - (abs(offset.value) / width).coerceIn(0f, 1f) * 0.9f }
            .draggable(
                state = rememberDraggableState { delta ->
                    scope.launch { offset.snapTo(offset.value + if (enabled) delta else delta * 0.2f) }
                },
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    if (enabled && (abs(offset.value) > width * 0.35f || abs(velocity) > 2500f)) {
                        val dir = if (offset.value != 0f) sign(offset.value) else sign(velocity)
                        offset.animateTo(dir * width, tween(160))
                        onDismiss()
                    } else {
                        offset.animateTo(0f, spring())
                    }
                },
            ),
    ) { content() }
}

fun relativeTime(millis: Long): String {
    val diff = (System.currentTimeMillis() - millis) / 1000
    return when {
        diff < 60 -> "now"
        diff < 3600 -> "${diff / 60}m"
        diff < 86400 -> "${diff / 3600}h"
        else -> "${diff / 86400}d"
    }
}
