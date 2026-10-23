package com.vipervision.editor

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t8rin.imagetoolbox.core.resources.Icons
import com.t8rin.imagetoolbox.core.resources.icons.Apps
import com.t8rin.imagetoolbox.core.resources.icons.AutoAwesome
import com.t8rin.imagetoolbox.core.resources.icons.FilterVintage
import com.t8rin.imagetoolbox.core.resources.icons.Flip
import com.t8rin.imagetoolbox.core.resources.icons.LightMode
import com.t8rin.imagetoolbox.core.resources.icons.MoreVert
import com.t8rin.imagetoolbox.core.resources.icons.Redo
import com.t8rin.imagetoolbox.core.resources.icons.RotateLeft
import com.t8rin.imagetoolbox.core.resources.icons.RotateRight
import com.t8rin.imagetoolbox.core.resources.icons.Undo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

private val PanelGrey = Color(0xFF1C1C1E)

@Composable
internal fun EditorScreen(
    controller: EditorController,
    onSave: () -> Unit,
    onOpenMoreTools: () -> Unit,
    onClose: () -> Unit
) {
    val base = controller.basePreview

    // Re-render the preview whenever the edits change; a newer change cancels an older render.
    LaunchedEffect(base) {
        if (base == null) return@LaunchedEffect
        snapshotFlow { controller.state }.collectLatest { state ->
            val edited = withContext(Dispatchers.Default) {
                applyAdjustments(base.transformed(state.geometry), state.adjustments)
            }
            controller.editedPreview = edited
        }
    }

    var showOriginal by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val changed = !controller.state.isUnchanged

    BackHandler(enabled = changed) { showDiscardDialog = true }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        TopBar(
            controller = controller,
            changed = changed,
            onSave = onSave,
            onOpenMoreTools = onOpenMoreTools,
            onDiscard = { if (changed) showDiscardDialog = true else onClose() }
        )

        // The photo, as large as the space allows. Press and hold to see the original.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        showOriginal = true
                        tryAwaitRelease()
                        showOriginal = false
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            val shown = if (showOriginal) base else controller.editedPreview ?: base
            if (shown != null) {
                Image(
                    bitmap = shown.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (showOriginal) {
                Text(
                    "Original",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            if (base == null || controller.isBusy) CircularProgressIndicator(color = Accent)
            controller.error?.let {
                Text(it, color = Color.White, modifier = Modifier.padding(24.dp))
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(184.dp),
            contentAlignment = Alignment.Center
        ) {
            when (controller.tab) {
                EditorTab.Adjust -> AdjustPanel(controller)
                EditorTab.Filters -> FiltersPanel(controller)
                EditorTab.Transform -> TransformPanel(controller, onCrop = onOpenMoreTools)
            }
        }

        BottomTabs(controller, onOpenMoreTools)
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to this photo haven't been saved.") },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onClose() }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") }
            }
        )
    }
}

@Composable
private fun TopBar(
    controller: EditorController,
    changed: Boolean,
    onSave: () -> Unit,
    onOpenMoreTools: () -> Unit,
    onDiscard: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(PanelGrey)
        ) {
            IconButton(onClick = controller::undo, enabled = controller.canUndo) {
                Icon(Icons.Rounded.Undo, "Undo", tint = if (controller.canUndo) Color.White else Color.Gray)
            }
            IconButton(onClick = controller::redo, enabled = controller.canRedo) {
                Icon(Icons.Rounded.Redo, "Redo", tint = if (controller.canRedo) Color.White else Color.Gray)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(PanelGrey)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = controller::revert, enabled = changed) {
                Text("Revert", color = if (changed) Color.White else Color.Gray, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onSave, enabled = changed && !controller.isBusy) {
                Text("Save", color = if (changed) Color.White else Color.Gray, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, "More options", tint = Color.White)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("More tools (Image Toolbox)") },
                        onClick = { menuOpen = false; onOpenMoreTools() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (changed) "Discard changes and close" else "Close") },
                        onClick = { menuOpen = false; onDiscard() }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdjustPanel(controller: EditorController) {
    val selected = controller.selected
    val adjustments = controller.state.adjustments
    val listState = rememberLazyListState()
    val base = controller.basePreview
    val autoValues = remember(base) { base?.let(::autoAdjust) }
    val autoActive = autoValues != null &&
            adjustments.copy(monochrome = false) == autoValues.copy(monochrome = false)

    LaunchedEffect(selected) {
        listState.animateScrollToItem(Adjustment.entries.indexOf(selected).coerceAtLeast(0))
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            selected.label,
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 2.dp)
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(12.dp))
            PillButton(
                text = "Auto",
                active = autoActive,
                onClick = {
                    if (autoActive) {
                        controller.change(controller.state.copy(adjustments = Adjustments(monochrome = adjustments.monochrome)))
                    } else {
                        controller.autoEnhance()
                    }
                }
            )
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                itemsIndexed(Adjustment.entries) { _, adjustment ->
                    AdjustBubble(
                        adjustment = adjustment,
                        value = adjustments[adjustment],
                        selected = adjustment == selected,
                        onClick = { controller.selected = adjustment }
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ValueWheel(
            value = adjustments[selected],
            onValueChange = { controller.setAdjustment(selected, it) },
            onValueChangeFinished = controller::commit,
            modifier = Modifier.padding(horizontal = 36.dp)
        )
    }
}

@Composable
private fun FiltersPanel(controller: EditorController) {
    val base = controller.basePreview
    val geometry = controller.state.geometry
    var thumbnails by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    LaunchedEffect(base, geometry) {
        if (base == null) return@LaunchedEffect
        thumbnails = withContext(Dispatchers.Default) {
            val small = base.scaledToFit(220).transformed(geometry)
            looks.map { applyAdjustments(small, it.adjustments) }
        }
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(looks) { index, look ->
            val selected = controller.state.lookName == look.name
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = if (selected) 2.5.dp else 0.dp,
                            color = if (selected) Accent else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .background(BubbleGrey)
                        .clickable { controller.applyLook(look) }
                ) {
                    thumbnails.getOrNull(index)?.let {
                        Image(it.asImageBitmap(), look.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(look.name, color = if (selected) Accent else Color.White, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TransformPanel(controller: EditorController, onCrop: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolBubble(Icons.Rounded.RotateLeft, "Rotate left") { controller.rotate(clockwise = false) }
        ToolBubble(Icons.Rounded.RotateRight, "Rotate right") { controller.rotate(clockwise = true) }
        ToolBubble(Icons.Outlined.Flip, "Flip") { controller.flip() }
        ToolBubble(EditorIcons.CropRotate, "Crop…", onCrop)
    }
}

@Composable
private fun ToolBubble(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(BubbleGrey)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun BottomTabs(controller: EditorController, onOpenMoreTools: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 4.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Auto-enhance, styled like the sparkle button in Samsung's editor.
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF8E7CF0), Color(0xFF5B8CFF))))
                .clickable(onClick = controller::autoEnhance),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.AutoAwesome, "Auto enhance", tint = Color.White, modifier = Modifier.size(26.dp))
        }
        TabIcon(EditorIcons.CropRotate, "Rotate and crop", controller.tab == EditorTab.Transform) {
            controller.tab = EditorTab.Transform
        }
        TabIcon(Icons.Rounded.FilterVintage, "Filters", controller.tab == EditorTab.Filters) {
            controller.tab = EditorTab.Filters
        }
        TabIcon(Icons.Outlined.LightMode, "Adjust", controller.tab == EditorTab.Adjust) {
            controller.tab = EditorTab.Adjust
        }
        TabIcon(Icons.Outlined.Apps, "More tools", selected = false, onClick = onOpenMoreTools)
    }
}

@Composable
private fun TabIcon(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
        Icon(icon, label, tint = if (selected) Accent else Color.White, modifier = Modifier.size(30.dp))
    }
}

private fun Bitmap.scaledToFit(maxSide: Int): Bitmap {
    val longSide = maxOf(width, height)
    if (longSide <= maxSide) return this
    val scale = maxSide.toFloat() / longSide
    return Bitmap.createScaledBitmap(this, (width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1), true)
}
