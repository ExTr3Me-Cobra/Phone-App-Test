package com.vipervision.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import android.content.Context
import android.os.Build
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

internal val Accent = Color(0xFFF7DF5A)

/**
 * The highlight colour for shared controls (pills, sliders). The editor's yellow by default; the
 * Manual background remover provides the app's own colour (see [appAccentColor]).
 */
internal val LocalAccent = staticCompositionLocalOf { Accent }

/** Black or white, whichever reads better on this colour. */
internal fun Color.contentOn(): Color = if (luminance() > 0.5f) Color.Black else Color.White

/**
 * The colour picked in Viper Vision's Settings (Fossify's theme: its "Prefs" file), the way
 * Fossify works it out: the system (Material You) colour when that's on, the accent colour for
 * the white and black-and-white themes, otherwise the primary colour. The editor's yellow if no
 * colour has been set.
 */
internal fun appAccentColor(context: Context): Color {
    val prefs = context.getSharedPreferences("Prefs", Context.MODE_PRIVATE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && prefs.getBoolean("is_using_system_theme", true)) {
        return Color(context.getColor(android.R.color.system_accent1_400))
    }
    if (!prefs.contains("primary_color_2")) return Accent
    val primary = prefs.getInt("primary_color_2", 0)
    val text = prefs.getInt("text_color", 0)
    val background = prefs.getInt("background_color", 0)
    val white = android.graphics.Color.WHITE
    val black = android.graphics.Color.BLACK
    val whiteTheme = primary == white && background == white
    val blackAndWhite = text == white && primary == black && background == black
    return Color(if ((whiteTheme || blackAndWhite) && prefs.contains("accent_color")) prefs.getInt("accent_color", primary) else primary)
}
internal val BubbleGrey = Color(0xFF2B2B2D)
internal val BubbleWedge = Color(0xFF3D3D40)

/**
 * A round adjustment button. Its pie wedge shows the current value (clockwise for positive,
 * counter-clockwise for negative); the selected one is white and shows the number.
 */
@Composable
internal fun AdjustBubble(
    adjustment: Adjustment,
    value: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.width(72.dp)) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(if (selected) Color.White else BubbleGrey)
            if (value != 0) {
                drawArc(
                    color = if (selected) Accent else BubbleWedge,
                    startAngle = -90f,
                    sweepAngle = value / 100f * 360f,
                    useCenter = true
                )
            }
        }
        if (selected) {
            Text(
                text = value.toString(),
                color = Color.Black,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Icon(adjustment.icon, contentDescription = adjustment.label, tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
        Spacer(Modifier.height(4.dp))
        Text(
            adjustment.label,
            color = if (selected) Accent else Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/**
 * Tick-mark dial for picking a value from -100 to 100. Drag it sideways; the yellow line in the
 * middle marks the current value and the dot marks zero. Double-tap resets to zero.
 */
@Composable
internal fun ValueWheel(
    value: Int,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val pxPerUnit = with(density) { 4.dp.toPx() }
    // The gesture handlers below are created once, so they read these to always act on the
    // currently selected adjustment.
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    var dragValue by remember { mutableFloatStateOf(0f) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    currentOnValueChange(0)
                    currentOnValueChangeFinished()
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                })
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragValue = currentValue.toFloat() },
                    onDragEnd = { currentOnValueChangeFinished() },
                    onDragCancel = { currentOnValueChangeFinished() },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val before = dragValue.roundToInt()
                        dragValue = (dragValue - dragAmount / pxPerUnit).coerceIn(-100f, 100f)
                        val after = dragValue.roundToInt()
                        if (after != before) {
                            currentOnValueChange(after)
                            if (after == 0 || after % 10 == 0 || abs(after) == 100) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                )
            }
    ) {
        val centerX = size.width / 2f
        val baseline = size.height * 0.8f
        // One tick every 2 units, a taller one every 10.
        for (unit in -100..100 step 2) {
            val x = centerX + (unit - value) * pxPerUnit
            if (x < -4f || x > size.width + 4f) continue
            val major = unit % 10 == 0
            val height = if (major) size.height * 0.42f else size.height * 0.28f
            // Fade the ticks toward the edges.
            val alpha = (1f - abs(x - centerX) / centerX).coerceIn(0.15f, 1f)
            drawLine(
                color = Color.White.copy(alpha = alpha * if (major) 0.9f else 0.55f),
                start = Offset(x, baseline - height),
                end = Offset(x, baseline),
                strokeWidth = with(density) { 1.5.dp.toPx() }
            )
            if (unit == 0) {
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = with(density) { 2.5.dp.toPx() },
                    center = Offset(x, baseline - size.height * 0.62f)
                )
            }
        }
        drawLine(
            color = Accent,
            start = Offset(centerX, baseline - size.height * 0.55f),
            end = Offset(centerX, baseline + 2f),
            strokeWidth = with(density) { 3.dp.toPx() }
        )
    }
}

/** Small rounded text button used for "Auto". */
@Composable
internal fun PillButton(text: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Color.White else BubbleGrey)
            .clickable(onClick = onClick)
            .size(width = 64.dp, height = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (active) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
