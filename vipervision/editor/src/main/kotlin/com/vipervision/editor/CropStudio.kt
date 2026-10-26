package com.vipervision.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.t8rin.cropper.model.AspectRatio
import com.t8rin.imagetoolbox.core.resources.Icons
import com.t8rin.imagetoolbox.core.resources.icons.Close
import com.t8rin.imagetoolbox.core.resources.icons.Done
import com.t8rin.imagetoolbox.core.resources.icons.Redo
import com.t8rin.imagetoolbox.core.resources.icons.Undo
import com.t8rin.imagetoolbox.core.ui.widget.controls.selection.CropOverlayDraggableSelector
import com.t8rin.imagetoolbox.core.ui.widget.controls.selection.MagnifierEnabledSelector
import com.t8rin.imagetoolbox.core.ui.widget.image.AspectRatioSelector
import com.t8rin.imagetoolbox.core.ui.widget.modifier.ShapeDefaults
import com.t8rin.imagetoolbox.feature.crop.presentation.components.CoercePointsToImageBoundsToggle
import com.t8rin.imagetoolbox.feature.crop.presentation.components.CropMaskSelection
import com.t8rin.imagetoolbox.feature.crop.presentation.components.CropRotationSelector
import com.t8rin.imagetoolbox.feature.crop.presentation.components.CropType
import com.t8rin.imagetoolbox.feature.crop.presentation.components.Cropper
import com.t8rin.imagetoolbox.feature.crop.presentation.components.FreeCornersCropToggle
import com.t8rin.imagetoolbox.feature.crop.presentation.screenLogic.CropComponent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Advanced crop: Image Toolbox's Crop tool ([component]) for what the quick crop frame can't do —
 * straightening by any angle, perspective (free corners) crop, and crop shapes (circle, star,
 * heart, your own image as a mask...). Tap "Crop" to cut, as many times as needed; the tick keeps
 * the result as a step.
 */
class CropStudioSession internal constructor(
    val component: CropComponent,
    private val lifecycle: LifecycleRegistry
) {
    fun close() = lifecycle.destroy()
}

@Composable
internal fun CropStudioScreen(
    session: CropStudioSession,
    onCancel: () -> Unit,
    onApply: () -> Unit
) {
    val component = session.component
    val scope = rememberCoroutineScope()
    val cropperState = component.cropperState
    val rotationState = component.rotationState
    var coercePointsToImageArea by component.coercePointsToImageAreaState
    var crop by remember { mutableStateOf(false) }
    var rotationHistoryJob by remember { mutableStateOf<Job?>(null) }

    SideEffect { cropperState.setActiveCropType(component.cropType) }
    BackHandler(onBack = onCancel)

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Cancel", tint = Color.White) }
            Text("Advanced crop", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = cropperState::undo, enabled = cropperState.canUndo) {
                Icon(Icons.Rounded.Undo, "Undo", tint = if (cropperState.canUndo) Color.White else Color.Gray)
            }
            IconButton(onClick = cropperState::redo, enabled = cropperState.canRedo) {
                Icon(Icons.Rounded.Redo, "Redo", tint = if (cropperState.canRedo) Color.White else Color.Gray)
            }
            IconButton(onClick = onApply, enabled = component.isBitmapChanged) {
                Icon(Icons.Rounded.Done, "Apply", tint = if (component.isBitmapChanged) LocalAccent.current else Color.Gray)
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = component.bitmap
            val imageUri = component.currentUri
            if (bitmap != null && imageUri != null) {
                Cropper(
                    bitmap = bitmap,
                    imageUri = imageUri,
                    imageSize = component.imageSize,
                    crop = crop,
                    onImageCropStarted = component::imageCropStarted,
                    onImageCropFinished = { uri ->
                        component.imageCropFinished()
                        if (uri != null) {
                            component.updateImageUri(uri) {
                                cropperState.recordAppliedAction(undo = component::undo, redo = component::redo)
                                if (component.cropType == CropType.NoRotation) cropperState.resetImageCropperState()
                            }
                        }
                        crop = false
                    },
                    rotationState = rotationState,
                    state = cropperState,
                    cropProperties = component.cropProperties,
                    cropType = component.cropType,
                    addVerticalInsets = false,
                    coercePointsToImageArea = coercePointsToImageArea
                )
            }
        }

        // Image Toolbox's crop options.
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FreeCornersCropToggle(
                modifier = Modifier.fillMaxWidth(),
                value = component.cropType == CropType.FreeCorners,
                onClick = {
                    val previous = component.cropType
                    if (component.toggleFreeCornersCrop()) {
                        cropperState.recordExternalAction(
                            previousCropType = previous,
                            currentCropType = component.cropType,
                            undo = component::undo,
                            redo = component::redo
                        )
                    }
                }
            )
            if (component.cropType != CropType.Default || component.cropProperties.aspectRatio == AspectRatio.Original) {
                CropOverlayDraggableSelector(modifier = Modifier.fillMaxWidth(), shape = ShapeDefaults.extraLarge)
            }
            if (component.cropType == CropType.Default) {
                CropRotationSelector(
                    value = rotationState.floatValue,
                    onValueChange = {
                        rotationHistoryJob?.cancel()
                        cropperState.beginRotation()
                        rotationState.floatValue = it
                    },
                    onValueChangeFinished = {
                        rotationHistoryJob?.cancel()
                        rotationHistoryJob = scope.launch {
                            delay(350)
                            cropperState.endRotation()
                        }
                    }
                )
            }
            if (component.cropType == CropType.FreeCorners) {
                CoercePointsToImageBoundsToggle(
                    value = coercePointsToImageArea,
                    onValueChange = { coercePointsToImageArea = it },
                    modifier = Modifier.fillMaxWidth()
                )
                MagnifierEnabledSelector(modifier = Modifier.fillMaxWidth(), shape = ShapeDefaults.extraLarge)
            } else {
                AspectRatioSelector(
                    modifier = Modifier.fillMaxWidth(),
                    selectedAspectRatio = component.selectedAspectRatio,
                    onAspectRatioChange = { domainAspectRatio, aspectRatio ->
                        if (component.setCropAspectRatio(domainAspectRatio, aspectRatio)) {
                            val cropType = component.cropType
                            cropperState.recordExternalAction(
                                previousCropType = cropType,
                                currentCropType = cropType,
                                undo = component::undo,
                                redo = component::redo
                            )
                        }
                    }
                )
                CropMaskSelection(
                    onCropMaskChange = { outline ->
                        val previous = component.cropType
                        if (component.setCropMask(outline)) {
                            cropperState.recordExternalAction(
                                previousCropType = previous,
                                currentCropType = component.cropType,
                                undo = component::undo,
                                redo = component::redo
                            )
                        }
                    },
                    selectedItem = component.cropProperties.cropOutlineProperty,
                    loadImage = { component.loadImage(it)?.asImageBitmap() }
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Pill("Crop", selected = component.bitmap != null, filled = true) {
                if (component.bitmap != null) crop = true
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
