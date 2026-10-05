package com.shadeui.app.ui

import android.graphics.Matrix
import android.graphics.SweepGradient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadeui.app.ShadeApp
import com.shadeui.app.data.LightStyle
import com.shadeui.app.data.LockStyle
import com.shadeui.app.data.LockVisibility
import com.shadeui.app.data.PopupStyle
import com.shadeui.app.overlay.OverlayService
import com.shadeui.app.overlay.PopupState
import kotlin.math.PI
import kotlin.math.sin

// ---------------- Pop-ups ----------------

@Composable
fun PopupView(host: OverlayService) {
    val current = host.ui.popup
    var last by remember { mutableStateOf<PopupState?>(null) }
    if (current != null) last = current
    val s by ShadeApp.instance.settings.flow.collectAsState()
    val density = LocalDensity.current
    AnimatedVisibility(
        visible = current != null,
        enter = slideInVertically(tween(260)) { -it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(220)) { -it } + fadeOut(tween(180)),
    ) {
        val st = last ?: return@AnimatedVisibility
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .pointerInput(st.id) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            val t = with(density) { 40.dp.toPx() }
                            when {
                                total < -t -> host.hidePopup()
                                total > t -> {
                                    host.hidePopup()
                                    host.openShade()
                                }
                            }
                        },
                    ) { _, dy -> total += dy }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            SwipeDismiss(enabled = true, onDismiss = { host.hidePopup() }) {
                if (st.style == PopupStyle.BRIEF) BriefPopup(host, st) else DetailedPopup(host, st, s)
            }
        }
    }
}

@Composable
private fun BriefPopup(host: OverlayService, st: PopupState) {
    val p = LocalPalette.current
    val item = st.item
    Row(
        Modifier.widthIn(max = 420.dp).clip(RoundedCornerShape(30.dp)).background(p.pill)
            .clickable { host.open(item) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppBadge(item, 30)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.widthIn(max = 320.dp)) {
            Text(
                if (st.hideContent) item.appName else item.title.ifEmpty { item.appName },
                color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (st.hideContent) "New notification" else item.text,
                color = p.subText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DetailedPopup(host: OverlayService, st: PopupState, s: com.shadeui.app.data.ShadeSettings) {
    val p = LocalPalette.current
    val item = st.item
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(p.card)
            .clickable { host.open(item) }.padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppBadge(item, 20)
            Spacer(Modifier.width(8.dp))
            Text(
                item.appName + if (s.showTimestamps) " • now" else "",
                color = p.subText, fontSize = 13.sp,
            )
        }
        Spacer(Modifier.size(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (st.hideContent) "New notification" else item.title,
                    color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (!st.hideContent && item.text.isNotEmpty()) {
                    Text(item.text, color = p.text.copy(alpha = 0.9f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!st.hideContent) item.largeIcon?.let {
                Spacer(Modifier.width(10.dp))
                Image(it, null, Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            }
        }
        if (!st.hideContent && item.actions.isNotEmpty()) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                item.actions.take(3).forEach { a ->
                    TextButton(onClick = { host.runAction(item, a) }) { Text(a.title, color = p.text, maxLines = 1) }
                }
            }
        }
    }
}

// ---------------- Edge lighting ----------------

@Composable
fun EdgeLighting(host: OverlayService) {
    val st = host.ui.lighting ?: return
    val progress = remember(st.id) { Animatable(0f) }
    LaunchedEffect(st.id) {
        repeat(st.repeats) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1500, easing = LinearEasing))
        }
        host.lightingFinished(st.id)
    }
    val colors = st.colors.map { Color(it) }
    Canvas(Modifier.fillMaxSize()) {
        val t = progress.value
        val envelope = sin(PI * t).toFloat().coerceIn(0f, 1f)
        val w = st.thicknessPx
        val inset = w / 2f
        val r = (st.cornerPx - inset).coerceAtLeast(0f)
        val path = Path().apply {
            addRoundRect(RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius(r, r)))
        }
        val base = colors.first()
        val alpha = st.opacity
        when (st.style) {
            LightStyle.LINE -> drawPath(path, base, alpha = alpha * envelope, style = Stroke(w))
            LightStyle.PULSE -> drawPath(path, base, alpha = alpha * envelope, style = Stroke(w * (0.5f + envelope * 1.2f)))
            LightStyle.GLOW -> {
                // Soft halo made of wide, faint strokes under a bright core line.
                for (i in 5 downTo 1) {
                    drawPath(path, base, alpha = alpha * envelope * 0.12f, style = Stroke(w * (1f + i * 1.4f)))
                }
                drawPath(path, base, alpha = alpha * envelope, style = Stroke(w))
            }
            LightStyle.GRADIENT -> {
                val list = if (colors.size > 1) colors else listOf(base, base.copy(alpha = 0.2f), base)
                val shader = SweepGradient(
                    size.width / 2f, size.height / 2f,
                    (list + list.first()).map { it.toArgb() }.toIntArray(), null,
                ).apply { setLocalMatrix(Matrix().apply { setRotate(t * 360f, size.width / 2f, size.height / 2f) }) }
                drawPath(path, ShaderBrush(shader), alpha = alpha * envelope.coerceAtLeast(0.35f), style = Stroke(w))
            }
        }
    }
}

// ---------------- Lock screen notifications ----------------

@Composable
fun LockNotifications(host: OverlayService) {
    val p = LocalPalette.current
    val s by ShadeApp.instance.settings.flow.collectAsState()
    val rules by ShadeApp.instance.rules.flow.collectAsState()
    val all by ShadeApp.instance.notifs.flow.collectAsState()
    val items = remember(all, rules) { host.lockItems() }
    if (items.isEmpty()) return
    val alpha = s.lockCardOpacity / 100f
    if (s.lockStyle == LockStyle.ICONS) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            items.distinctBy { it.pkg }.take(8).forEach { n ->
                Box(Modifier.clip(RoundedCornerShape(50)).background(p.card.copy(alpha = alpha)).padding(8.dp)) {
                    AppBadge(n, 26)
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.take(s.lockMaxCards).forEach { n ->
            val hide = s.lockHideContent || rules[n.pkg]?.lockScreen == LockVisibility.HIDE_CONTENT
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.card.copy(alpha = alpha))
                    .clickable { host.open(n) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppBadge(n, 30)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (hide) n.appName else n.title.ifEmpty { n.appName },
                        color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (hide) "Content hidden" else n.text,
                        color = p.subText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(relativeTime(n.time), color = p.subText, fontSize = 12.sp)
            }
        }
        if (items.size > s.lockMaxCards) {
            Text(
                "+${items.size - s.lockMaxCards} more", color = p.text, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(4.dp),
            )
        }
    }
}
