package com.ironlog.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** A tap target that sinks a little while your finger is on it and springs back when you let go. */
class Press(val source: MutableInteractionSource, val scale: State<Float>)

@Composable
fun rememberPress(pressed: Float = 0.96f): Press {
    val source = remember { MutableInteractionSource() }
    val down by source.collectIsPressedAsState()
    val scale = animateFloatAsState(
        if (down) pressed else 1f,
        if (down) tween(90) else spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return Press(source, scale)
}

/** Put first in the chain so the whole thing (background and all) sinks. Read while drawing, so it never recomposes. */
fun Modifier.pressScale(p: Press) = graphicsLayer { val s = p.scale.value; scaleX = s; scaleY = s }

/** A click that drives [p] (use with [pressScale]). */
fun Modifier.pressClick(p: Press, enabled: Boolean = true, onClick: () -> Unit) =
    clickable(p.source, ripple(), enabled = enabled, onClick = onClick)

/** Shows or hides [content] by smoothly opening and closing downwards. */
@Composable
fun Reveal(visible: Boolean, content: @Composable () -> Unit) = AnimatedVisibility(
    visible,
    enter = expandVertically(tween(260)) + fadeIn(tween(220, delayMillis = 40)),
    exit = shrinkVertically(tween(220)) + fadeOut(tween(140)),
) { content() }
