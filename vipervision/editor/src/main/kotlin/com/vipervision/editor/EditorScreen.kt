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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t8rin.imagetoolbox.core.resources.Icons
import com.t8rin.imagetoolbox.core.resources.icons.Apps
import com.t8rin.imagetoolbox.core.resources.icons.Tune
import com.t8rin.imagetoolbox.core.resources.icons.Draw
import com.t8rin.imagetoolbox.core.resources.icons.TextFields
import com.t8rin.imagetoolbox.core.resources.icons.Curve
import com.t8rin.imagetoolbox.core.resources.icons.Scissors
import com.t8rin.imagetoolbox.core.resources.icons.AutoAwesome
import com.t8rin.imagetoolbox.core.resources.icons.Flip
import com.t8rin.imagetoolbox.core.resources.icons.Eraser
import com.t8rin.imagetoolbox.core.resources.icons.Healing
import com.t8rin.imagetoolbox.core.resources.icons.LightMode
import com.t8rin.imagetoolbox.core.resources.icons.MoreVert
import com.t8rin.imagetoolbox.core.resources.icons.Redo
import com.t8rin.imagetoolbox.core.resources.icons.RotateLeft
import com.t8rin.imagetoolbox.core.resources.icons.RotateRight
import com.t8rin.imagetoolbox.core.resources.icons.Undo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlin.math.abs

private val PanelGrey = Color(0xFF1C1C1E)

@Composable
internal fun EditorScreen(
    controller: EditorController,
    finisher: EditFinisher,
    aiReady: (AiChoice) -> Boolean,
    onRunAi: (AiTool, AiChoice) -> Unit,
    lamaReady: Boolean,
    onStartObjectEraser: () -> Unit,
    onStartCutOut: () -> Unit,
    onApplyCutOut: () -> Unit,
    onCancelCutOut: () -> Unit,
    onNeedSamDownload: () -> Unit,
    onStartDraw: () -> Unit,
    onApplyDraw: () -> Unit,
    onCancelDraw: () -> Unit,
    onStartMarkup: () -> Unit,
    onApplyMarkup: () -> Unit,
    onCancelMarkup: () -> Unit,
    onStartCropStudio: () -> Unit,
    onApplyCropStudio: () -> Unit,
    onCancelCropStudio: () -> Unit,
    onApplyPaint: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onSaveWithOptions: () -> Unit,
    onOpenMoreTools: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    // The photo the edits apply to: the original, or the result of any AI steps.
    val base = controller.workingPreview

    // In the Rotate & crop tab the whole photo is shown with the crop frame over it; elsewhere,
    // the cropped result.
    val cropMode = controller.tab == EditorTab.Transform

    // Re-render the preview whenever the edits change; a newer change cancels an older render.
    // Dragging the crop frame doesn't re-render (the frame is drawn on top instead).
    // While a dial is moving, a smaller copy is rendered so the photo keeps up with the finger;
    // the full-quality preview follows as soon as it stops.
    LaunchedEffect(base) {
        if (base == null) return@LaunchedEffect
        val live = withContext(Dispatchers.Default) { base.scaledToFit(LIVE_PREVIEW_SIDE) }
        // Rotating and cropping only changes when the geometry does, so keep the last result.
        var turnedKey: Pair<Geometry, Boolean>? = null
        var turned: Bitmap? = null
        snapshotFlow {
            val state = controller.state
            val geometry = if (controller.tab == EditorTab.Transform) {
                state.geometry.copy(crop = CropRect())
            } else {
                state.geometry
            }
            state.copy(geometry = geometry, pixelSteps = emptyList()) to controller.isInteracting
        }.collectLatest { (state, interacting) ->
            val source = if (interacting) live else base
            val edited = withContext(Dispatchers.Default) {
                val key = state.geometry to interacting
                val input = turned.takeIf { turnedKey == key } ?: source.transformed(state.geometry).also {
                    turned = it
                    turnedKey = key
                }
                finisher.finish(context, input, state, source.width, source.height)
            }
            controller.editedPreview = edited
        }
    }

    var showOriginal by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val changed = !controller.state.isUnchanged

    BackHandler(enabled = changed) { showDiscardDialog = true }

    controller.cropStudio?.let { session ->
        CropStudioScreen(session = session, onCancel = onCancelCropStudio, onApply = onApplyCropStudio)
        if (controller.isBusy) BusyOverlay(controller.busyMessage)
        return
    }

    controller.markup?.let { session ->
        MarkupScreen(session = session, onCancel = onCancelMarkup, onApply = onApplyMarkup)
        if (controller.isBusy) BusyOverlay(controller.busyMessage)
        return
    }

    controller.draw?.let { session ->
        DrawScreen(session = session, onCancel = onCancelDraw, onApply = onApplyDraw)
        if (controller.isBusy) BusyOverlay(controller.busyMessage)
        return
    }

    controller.cutOut?.let { session ->
        CutOutScreen(
            session = session,
            onCancel = onCancelCutOut,
            onApply = onApplyCutOut,
            onNeedSamDownload = onNeedSamDownload
        )
        if (controller.isBusy) BusyOverlay(controller.busyMessage)
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        TopBar(
            controller = controller,
            changed = changed,
            onSave = onSave,
            onSaveAs = onSaveAs,
            onOpenMoreTools = onOpenMoreTools,
            onDiscard = { if (changed) showDiscardDialog = true else onClose() }
        )

        // The photo, as large as the space allows. Press and hold to see the original (except
        // while cropping, where dragging moves the crop frame).
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(cropMode, controller.paint) {
                    if (cropMode || controller.paint != null) return@pointerInput
                    detectTapGestures(onPress = {
                        showOriginal = true
                        tryAwaitRelease()
                        showOriginal = false
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            val shown = if (showOriginal) controller.basePreview else controller.editedPreview ?: base
            // Room around the photo for the crop handles.
            val imageModifier = if (cropMode) Modifier.fillMaxSize().padding(20.dp) else Modifier.fillMaxSize()
            // Pinch-zoom while painting; back to the whole photo when painting ends.
            val paintZoom = remember(controller.paint) { PaintZoom() }
            if (shown != null) {
                Image(
                    bitmap = shown.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = if (controller.paint != null) with(paintZoom) { imageModifier.zoomed() } else imageModifier
                )
            }
            val uncroppedSize = base?.let { controller.state.geometry.turnedSize(it.width, it.height) }
            // Only draw the frame once the uncropped preview is showing, so the two line up.
            if (cropMode && shown != null && uncroppedSize != null &&
                abs(shown.width.toFloat() / shown.height - uncroppedSize.first.toFloat() / uncroppedSize.second) < 0.02f
            ) {
                CropOverlay(
                    imageWidth = uncroppedSize.first,
                    imageHeight = uncroppedSize.second,
                    crop = controller.state.geometry.crop,
                    aspectRatio = controller.cropAspect.ratio(uncroppedSize.first, uncroppedSize.second),
                    onCropChange = controller::setCrop,
                    onCropChangeFinished = controller::commit,
                    modifier = imageModifier
                )
            }
            // Painting over something to heal or erase.
            val paint = controller.paint
            if (paint != null && shown != null && !cropMode) {
                PaintOverlay(
                    imageWidth = shown.width,
                    imageHeight = shown.height,
                    session = paint,
                    zoom = paintZoom,
                    modifier = imageModifier
                )
                var adjustingBrush by remember { mutableStateOf(false) }
                if (adjustingBrush) {
                    // How big the brush is, at the same size it paints on screen.
                    val radius = paint.brushRadiusDp.dp
                    Box(
                        Modifier
                            .size(radius * 2)
                            .border(2.dp, Color.White, CircleShape)
                            .background(Color(0x55FF3B30), CircleShape)
                    )
                }
                BrushSizeSlider(
                    session = paint,
                    onAdjusting = { adjustingBrush = it },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 6.dp)
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
            if (base == null || controller.isBusy) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(20.dp)
                ) {
                    CircularProgressIndicator(color = Accent)
                    controller.busyMessage?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = Color.White, fontSize = 14.sp)
                    }
                }
            }
            controller.error?.let {
                Text(it, color = Color.White, modifier = Modifier.padding(24.dp))
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .then(
                    if (controller.paint == null && controller.tab == EditorTab.Adjust && controller.curvesOpen) {
                        Modifier.heightIn(min = 184.dp)
                    } else Modifier.height(184.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            val paint = controller.paint
            if (paint != null) {
                PaintPanel(
                    session = paint,
                    onCancel = { controller.paint = null },
                    onApply = onApplyPaint
                )
            } else {
                when (controller.tab) {
                    EditorTab.Adjust -> if (controller.curvesOpen) {
                        CurvesPanel(controller, remember(base) { base?.scaledToFit(512) })
                    } else AdjustPanel(controller)
                    EditorTab.Ai -> AiPanel(controller, aiReady, onRunAi, lamaReady, onStartObjectEraser, onStartCutOut)
                    EditorTab.Transform -> TransformPanel(controller, onStartCropStudio)
                    EditorTab.Decorate -> DecoratePanel(controller, onStartDraw, onStartMarkup)
                }
            }
        }

        if (controller.paint == null) BottomTabs(controller, onOpenMoreTools)
    }

    controller.saveAs?.let { request ->
        SaveOptionsSheet(
            options = request.options,
            source = request.source,
            photoWidth = request.width,
            photoHeight = request.height,
            onSave = onSaveWithOptions,
            onDismiss = { controller.saveAs = null }
        )
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
    onSaveAs: () -> Unit,
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
                        text = { Text("Save as… (format, size, details)") },
                        onClick = { menuOpen = false; onSaveAs() }
                    )
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
    val base = controller.workingPreview
    val autoValues = remember(base) { base?.let(::autoAdjust) }
    val autoActive = autoValues != null && adjustments == autoValues

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
                        controller.change(controller.state.copy(adjustments = Adjustments()))
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
                // Heal: paint over spots or small objects and fill them in (no AI, works offline).
                item {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(BubbleGrey)
                            .clickable(enabled = !controller.isBusy) {
                                controller.paint = PaintSession(PaintPurpose.Heal)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Healing, "Heal", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
                // Curves: Image Toolbox's tone curve editor.
                item {
                    val active = controller.state.curves?.isDefault() == false
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(if (active) Accent.copy(alpha = 0.45f) else BubbleGrey)
                            .clickable(enabled = !controller.isBusy) { controller.curvesOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Curve, "Curves", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ValueWheel(
            value = adjustments[selected],
            onValueChange = {
                controller.isInteracting = true
                controller.setAdjustment(selected, it)
            },
            onValueChangeFinished = {
                controller.isInteracting = false
                controller.commit()
            },
            modifier = Modifier.padding(horizontal = 36.dp)
        )
    }
}

@Composable
private fun AiPanel(
    controller: EditorController,
    aiReady: (AiChoice) -> Boolean,
    onRunAi: (AiTool, AiChoice) -> Unit,
    lamaReady: Boolean,
    onStartObjectEraser: () -> Unit,
    onStartCutOut: () -> Unit
) {
    var selected by remember { mutableStateOf<AiTool?>(null) }
    var choosing by remember { mutableStateOf<AiTool?>(null) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            selected?.description ?: "AI tools — tap one to apply it to the photo",
            color = Color.White,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            // Object eraser: paint over something and the LaMa AI model fills in what was behind it.
            item {
                AiBubble(
                    icon = Icons.Rounded.Eraser,
                    label = "Object eraser",
                    download = if (lamaReady) null else "~200 MB",
                    applied = false,
                    selected = false,
                    enabled = !controller.isBusy,
                    onClick = onStartObjectEraser
                )
            }
            // Cut out: Image Toolbox's full background remover, with Smart select (paint roughly,
            // MobileSAM finds the edge), all its models and erase/restore brushes.
            item {
                AiBubble(
                    icon = Icons.Rounded.Scissors,
                    label = "Cut out",
                    download = null,
                    applied = controller.state.pixelSteps.any { it is PixelStep.CutOut },
                    selected = false,
                    enabled = !controller.isBusy,
                    onClick = onStartCutOut
                )
            }
            itemsIndexed(AiTool.entries) { _, tool ->
                val single = tool.choices.singleOrNull()
                AiBubble(
                    icon = tool.icon,
                    label = tool.label,
                    download = when {
                        tool.choices.any(aiReady) -> null
                        single != null -> "${single.downloadSizeMb} MB"
                        else -> "${tool.choices.size} choices"
                    },
                    applied = controller.state.pixelSteps.any { it is PixelStep.Ai && it.tool == tool },
                    selected = selected == tool,
                    enabled = !controller.isBusy,
                    onClick = {
                        selected = tool
                        // One model that's already here: just run it. Otherwise show the choice
                        // (which also says how big the download is).
                        if (single != null && aiReady(single)) onRunAi(tool, single) else choosing = tool
                    }
                )
            }
        }
    }

    choosing?.let { tool ->
        AiChoiceSheet(
            tool = tool,
            ready = aiReady,
            onPick = { choice ->
                choosing = null
                onRunAi(tool, choice)
            },
            onDismiss = { choosing = null }
        )
    }
}

/** The models offered for an AI tool, with what each is best at, its size and whether it's here. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AiChoiceSheet(
    tool: AiTool,
    ready: (AiChoice) -> Boolean,
    onPick: (AiChoice) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PanelGrey
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(tool.label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(tool.description, color = Color.Gray, fontSize = 14.sp)
            tool.choices.forEach { choice ->
                val here = ready(choice)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(BubbleGrey)
                        .clickable { onPick(choice) }
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            choice.title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (here) "Ready ✓" else "${choice.downloadSizeMb} MB",
                            color = if (here) Accent else Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(choice.about, color = Color(0xFFCCCCCC), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (here) "Use this" else "Download and use (one time, then works offline)",
                        color = Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** One AI tool: a round button with its full name (up to two lines, centred) and download size. */
@Composable
private fun AiBubble(
    icon: ImageVector,
    label: String,
    download: String?,
    applied: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (applied) Color.White else BubbleGrey)
                .border(2.dp, if (selected) Accent else Color.Transparent, CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = if (applied) Color.Black else Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 12.sp,
            lineHeight = 13.sp,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(download ?: "", color = Color.Gray, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun PaintPanel(session: PaintSession, onCancel: () -> Unit, onApply: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            when (session.purpose) {
                PaintPurpose.Erase -> "Object eraser — paint over what to remove"
                PaintPurpose.Heal -> "Heal — paint over spots or objects"
            },
            color = Color.White,
            fontSize = 14.sp
        )
        Text(
            "Pinch to zoom · brush size on the right",
            color = Color.Gray,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("Cancel", color = Color.White) }
            TextButton(
                onClick = { session.strokes.removeLastOrNull() },
                enabled = session.strokes.isNotEmpty()
            ) { Text("Undo stroke", color = if (session.strokes.isNotEmpty()) Color.White else Color.Gray) }
            Text(
                if (session.generative) "Erase" else "Heal",
                color = if (session.strokes.isNotEmpty()) Color.Black else Color.DarkGray,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (session.strokes.isNotEmpty()) Accent else BubbleGrey)
                    .clickable(enabled = session.strokes.isNotEmpty(), onClick = onApply)
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun TransformPanel(controller: EditorController, onStartCropStudio: () -> Unit) {
    val base = controller.workingPreview
    val size = base?.let { controller.state.geometry.turnedSize(it.width, it.height) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            itemsIndexed(CropAspect.entries) { _, aspect ->
                val selected = controller.cropAspect == aspect
                Text(
                    aspect.label,
                    color = if (selected) Color.Black else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Accent else BubbleGrey)
                        .clickable {
                            if (size != null) controller.selectCropAspect(aspect, size.first, size.second)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolBubble(Icons.Rounded.RotateLeft, "Rotate left") { controller.rotate(clockwise = false) }
            ToolBubble(Icons.Rounded.RotateRight, "Rotate right") { controller.rotate(clockwise = true) }
            ToolBubble(Icons.Outlined.Flip, "Flip") { controller.flip() }
            ToolBubble(EditorIcons.CropRotate, "Reset crop") { controller.resetCrop() }
            // Straighten by any angle, perspective crop and crop shapes (Image Toolbox's Crop).
            ToolBubble(Icons.Rounded.Tune, "Advanced") { onStartCropStudio() }
        }
    }
}

@Composable
private fun ToolBubble(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(68.dp)) {
        Box(
            Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(BubbleGrey)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp, maxLines = 1)
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
        // AI tools, styled like the sparkle button in Samsung's editor.
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF8E7CF0), Color(0xFF5B8CFF))))
                .border(2.dp, if (controller.tab == EditorTab.Ai) Color.White else Color.Transparent, CircleShape)
                .clickable { controller.tab = EditorTab.Ai },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.AutoAwesome, "AI tools", tint = Color.White, modifier = Modifier.size(26.dp))
        }
        TabIcon(EditorIcons.CropRotate, "Rotate and crop", controller.tab == EditorTab.Transform) {
            controller.tab = EditorTab.Transform
        }
        TabIcon(Icons.Outlined.LightMode, "Adjust", controller.tab == EditorTab.Adjust) {
            controller.tab = EditorTab.Adjust
        }
        TabIcon(Icons.Rounded.Draw, "Draw and decorate", controller.tab == EditorTab.Decorate) {
            controller.tab = EditorTab.Decorate
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

/** Draw and decorate: Image Toolbox's Draw tool (and more decorating tools). */
@Composable
private fun DecoratePanel(controller: EditorController, onStartDraw: () -> Unit, onStartMarkup: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Draw and decorate", color = Color.White, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            item {
                AiBubble(
                    icon = Icons.Rounded.Draw,
                    label = "Draw",
                    download = null,
                    applied = controller.state.pixelSteps.any { it is PixelStep.Drawing },
                    selected = false,
                    enabled = !controller.isBusy,
                    onClick = onStartDraw
                )
            }
            item {
                AiBubble(
                    icon = Icons.Rounded.TextFields,
                    label = "Text & layers",
                    download = null,
                    applied = false,
                    selected = false,
                    enabled = !controller.isBusy,
                    onClick = onStartMarkup
                )
            }
        }
    }
}

@Composable
private fun BusyOverlay(message: String?) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(20.dp)
        ) {
            CircularProgressIndicator(color = Accent)
            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

/** Longest side of the quick preview shown while a dial is moving. */
private const val LIVE_PREVIEW_SIDE = 900

internal fun Bitmap.scaledToFit(maxSide: Int): Bitmap {
    val longSide = maxOf(width, height)
    if (longSide <= maxSide) return this
    val scale = maxSide.toFloat() / longSide
    return Bitmap.createScaledBitmap(this, (width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1), true)
}
