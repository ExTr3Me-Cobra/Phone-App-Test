/*
 * Viper Vision: Markup Layers' canvas, layer menu and "add layer" pickers as separate pieces, so
 * Viper Vision's photo editor can host this tool in its own layout. The code is the same as in
 * MarkupLayersContent (which still uses its own copy).
 */

package com.t8rin.imagetoolbox.feature.markup_layers.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.zIndex
import com.t8rin.imagetoolbox.core.resources.Icons
import com.t8rin.imagetoolbox.core.resources.emoji.Emoji
import com.t8rin.imagetoolbox.core.resources.icons.EmojiSticky
import com.t8rin.imagetoolbox.core.ui.theme.outlineVariant
import com.t8rin.imagetoolbox.core.ui.theme.toColor
import com.t8rin.imagetoolbox.core.ui.utils.content_pickers.rememberImagePicker
import com.t8rin.imagetoolbox.core.ui.utils.helper.toBrush
import com.t8rin.imagetoolbox.core.ui.widget.image.Picture
import com.t8rin.imagetoolbox.core.ui.widget.modifier.ShapeDefaults
import com.t8rin.imagetoolbox.core.ui.widget.modifier.transparencyChecker
import com.t8rin.imagetoolbox.core.ui.widget.sheets.EmojiSelectionSheet
import com.t8rin.imagetoolbox.feature.markup_layers.domain.LayerType
import com.t8rin.imagetoolbox.feature.markup_layers.domain.withPreferredInitialGeometryFor
import com.t8rin.imagetoolbox.feature.markup_layers.presentation.components.AddShapeLayerDialog
import com.t8rin.imagetoolbox.feature.markup_layers.presentation.components.AddTextLayerDialog
import com.t8rin.imagetoolbox.feature.markup_layers.presentation.components.Layer
import com.t8rin.imagetoolbox.feature.markup_layers.presentation.components.LayerSnapGuides
import com.t8rin.imagetoolbox.feature.markup_layers.presentation.components.LayerSnapResult
import com.t8rin.imagetoolbox.feature.markup_layers.presentation.components.MarkupLayersSideMenu
import com.t8rin.imagetoolbox.feature.markup_layers.presentation.components.activeLayerGestures
import com.t8rin.imagetoolbox.feature.markup_layers.presentation.components.model.BackgroundBehavior
import com.t8rin.imagetoolbox.feature.markup_layers.presentation.components.model.UiMarkupLayer
import com.t8rin.imagetoolbox.feature.markup_layers.presentation.screenLogic.MarkupLayersComponent
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

/** What the host's buttons ask Markup Layers to show. */
@Stable
class ViperMarkupUiState {
    var snappingEnabled by mutableStateOf(true)
    var showLayersMenu by mutableStateOf(false)
    var showTextDialog by mutableStateOf(false)
    var showEmojiPicker by mutableStateOf(false)
    var showShapePicker by mutableStateOf(false)
    internal var pickImageRequests by mutableIntStateOf(0)
    internal var isContextOptionsVisible by mutableStateOf(false)
    internal var shouldOpenContextOptions by mutableStateOf(false)

    fun pickImage() {
        pickImageRequests++
    }

    /** Opens the layer menu with the selected layer's options. */
    fun showLayerOptions() {
        showLayersMenu = true
        shouldOpenContextOptions = true
    }
}

/** The layer canvas over the photo: tap a layer to select it, then drag, pinch and rotate it. */
@Composable
fun ViperMarkupCanvas(
    component: MarkupLayersComponent,
    uiState: ViperMarkupUiState,
    modifier: Modifier = Modifier
) {
    val activeLayer by remember(component) {
        derivedStateOf { component.layers.firstOrNull { it.state.isActive } }
    }
    var snapResult by remember(activeLayer, uiState.snappingEnabled) {
        mutableStateOf<LayerSnapResult?>(null)
    }
    val colorBackground = component.backgroundBehavior as? BackgroundBehavior.Color
    val imageBitmap = component.bitmap
    val canvasAspectRatio = when {
        imageBitmap != null -> imageBitmap.width / imageBitmap.height.toFloat()
        colorBackground != null -> colorBackground.width / colorBackground.height.toFloat()
        else -> 1f
    }

    LaunchedEffect(uiState.showLayersMenu, activeLayer, uiState.shouldOpenContextOptions) {
        if (uiState.showLayersMenu && uiState.shouldOpenContextOptions && activeLayer != null) {
            withFrameNanos { }
            uiState.isContextOptionsVisible = true
            uiState.shouldOpenContextOptions = false
        }
    }

    val zoomState = rememberZoomState(maxScale = 10f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .zoomable(
                zoomState = zoomState,
                zoomEnabled = !component.layers.fastAny { it.state.isActive }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .aspectRatio(canvasAspectRatio)
                .fillMaxSize()
                .clip(ShapeDefaults.extremeSmall)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant(), ShapeDefaults.extremeSmall)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .zIndex(-1f)
                    .matchParentSize()
                    .clipToBounds()
                    .transparencyChecker()
            )
            BoxWithConstraints(
                modifier = Modifier
                    .matchParentSize()
                    .activeLayerGestures(
                        component = component,
                        activeLayer = activeLayer,
                        snappingEnabled = uiState.snappingEnabled,
                        canvasScale = zoomState.scale,
                        onSnap = { snapResult = it }
                    )
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
                contentAlignment = Alignment.Center
            ) {
                if (colorBackground != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clipToBounds()
                            .drawWithCache {
                                val brush = colorBackground.gradient?.toBrush()
                                onDrawBehind {
                                    if (brush != null) drawRect(brush)
                                    else drawRect(colorBackground.color.toColor())
                                }
                            }
                    )
                }
                component.layers.forEachIndexed { index, layer ->
                    if (layer.isBackground) {
                        if (layer.state.isVisible) {
                            Picture(
                                model = imageBitmap,
                                contentDescription = null,
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier
                                    .matchParentSize()
                                    .clipToBounds()
                                    .graphicsLayer { alpha = layer.state.alpha },
                                showTransparencyChecker = false
                            )
                        }
                        return@forEachIndexed
                    }
                    Layer(
                        component = component,
                        layer = layer,
                        onActivate = { component.activateLayer(layer) },
                        onUpdateLayer = { updatedLayer, commitToHistory ->
                            component.updateLayerAt(index = index, layer = updatedLayer, commitToHistory = commitToHistory)
                        },
                        onShowContextOptions = { uiState.showLayerOptions() }
                    )
                }
                LayerSnapGuides(snapResult)
            }
        }
    }
}

/** The layer menu (every layer's options) and the pickers for adding text, images, emoji and shapes. */
@Composable
fun ViperMarkupSheets(
    component: MarkupLayersComponent,
    uiState: ViperMarkupUiState
) {
    val layerImagePicker = rememberImagePicker { uri: android.net.Uri ->
        component.addLayer(UiMarkupLayer(type = LayerType.Picture.Image(uri)))
    }
    LaunchedEffect(uiState.pickImageRequests) {
        if (uiState.pickImageRequests > 0) layerImagePicker.pickImage()
    }

    MarkupLayersSideMenu(
        component = component,
        visible = uiState.showLayersMenu,
        onDismiss = {
            uiState.showLayersMenu = false
            uiState.isContextOptionsVisible = false
            uiState.shouldOpenContextOptions = false
            component.cancelGroupingSelection()
        },
        isContextOptionsVisible = uiState.isContextOptionsVisible,
        onContextOptionsVisibleChange = { uiState.isContextOptionsVisible = it }
    )

    EmojiSelectionSheet(
        selectedEmojiIndex = null,
        onEmojiPicked = {
            component.addLayer(UiMarkupLayer(type = LayerType.Picture.Sticker(Emoji.allIcons[it])))
            uiState.showEmojiPicker = false
        },
        visible = uiState.showEmojiPicker,
        onDismiss = { uiState.showEmojiPicker = false },
        icon = Icons.Outlined.EmojiSticky,
        useAnimatedEmojis = false
    )

    AddShapeLayerDialog(
        visible = uiState.showShapePicker,
        onDismiss = { uiState.showShapePicker = false },
        onShapePicked = { mode ->
            component.addLayer(UiMarkupLayer(type = LayerType.Shape.Default.withPreferredInitialGeometryFor(mode)))
            uiState.showShapePicker = false
        }
    )

    AddTextLayerDialog(
        visible = uiState.showTextDialog,
        onDismiss = { uiState.showTextDialog = false },
        onAddLayer = component::addLayer
    )
}
