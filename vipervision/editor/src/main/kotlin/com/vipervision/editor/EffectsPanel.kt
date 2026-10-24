package com.vipervision.editor

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t8rin.imagetoolbox.core.filters.presentation.widget.FilterItem
import com.t8rin.imagetoolbox.core.filters.presentation.widget.FilterReorderSheet
import com.t8rin.imagetoolbox.core.filters.presentation.widget.FilterTemplateCreationSheet
import com.t8rin.imagetoolbox.core.filters.presentation.widget.addFilters.AddFiltersSheet
import com.t8rin.imagetoolbox.core.resources.Icons
import com.t8rin.imagetoolbox.core.resources.icons.Add
import kotlinx.coroutines.flow.debounce

/**
 * The Effects tab: Image Toolbox's whole filter catalogue (with its search, categories, favourites,
 * templates and LUTs) added as a stack of effects. Tap an effect to change its settings below the
 * photo, paint the area it applies to, duplicate, reorder or remove it.
 */
@OptIn(ExperimentalFoundationApi::class, kotlinx.coroutines.FlowPreview::class)
@Composable
internal fun EffectsPanel(
    controller: EditorController,
    tools: ToolboxComponents,
    thumbnail: Bitmap?
) {
    var showAdd by remember { mutableStateOf(false) }
    var showReorder by remember { mutableStateOf(false) }
    var showTemplate by remember { mutableStateOf(false) }
    val effects = controller.state.effects
    val selected = effects.firstOrNull { it.id == controller.selectedEffectId }

    // Slider drags change the effect live; add one undo step once they settle.
    LaunchedEffect(Unit) {
        snapshotFlow { controller.state.effects }.debounce(700).collect { controller.commit() }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (selected != null) stringResource(selected.filter.title) else if (effects.isEmpty()) {
                "Effects — tap + to add one"
            } else "Tap an effect to change it · hold to reorder",
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            item {
                EffectBubble(label = "Add", selected = false, onClick = { showAdd = true }) {
                    Icon(Icons.Rounded.Add, "Add effect", tint = Color.Black, modifier = Modifier.size(30.dp))
                }
            }
            items(effects, key = { it.id }) { effect ->
                val name = stringResource(effect.filter.title)
                EffectBubble(
                    label = name,
                    selected = effect.id == controller.selectedEffectId,
                    masked = !effect.mask.isNullOrEmpty(),
                    onClick = {
                        controller.commit()
                        controller.selectedEffectId = if (controller.selectedEffectId == effect.id) null else effect.id
                    },
                    onLongClick = { if (effects.size > 1) showReorder = true }
                ) {
                    Text(
                        name.initials(),
                        color = if (effect.id == controller.selectedEffectId) Color.Black else Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        if (selected != null) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipButton(if (selected.mask.isNullOrEmpty()) "Paint area" else "Paint more") {
                    controller.commit()
                    controller.paint = PaintSession(PaintPurpose.EffectMask, selected.id)
                }
                if (!selected.mask.isNullOrEmpty()) {
                    ChipButton(if (selected.invertMask) "Inside area" else "Outside area") {
                        controller.updateEffect(selected.id) { it.copy(invertMask = !it.invertMask) }
                        controller.commit()
                    }
                    ChipButton("Whole photo") {
                        controller.updateEffect(selected.id) { it.copy(mask = null, invertMask = false) }
                        controller.commit()
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            // Image Toolbox's own settings card for this filter: every parameter it has.
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                FilterItem(
                    filter = selected.filter,
                    showDragHandle = false,
                    canHide = false,
                    backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
                    onRemove = { controller.removeEffect(selected.id) },
                    onDuplicate = { controller.duplicateEffect(selected.id) },
                    onLongPress = { if (effects.size > 1) showReorder = true },
                    onCreateTemplate = { showTemplate = true },
                    onFilterChange = { value ->
                        controller.updateEffect(selected.id) { it.copy(filter = it.filter.copy(value)) }
                    }
                )
            }
        }
    }

    AddFiltersSheet(
        component = tools.addFilters,
        filterTemplateCreationSheetComponent = tools.filterTemplates,
        visible = showAdd,
        onVisibleChange = { showAdd = it },
        previewBitmap = thumbnail,
        onFilterPicked = { controller.addEffects(listOf(it.newInstance())) },
        onFilterPickedWithParams = { controller.addEffects(listOf(it)) },
        onFiltersPickedWithParams = { controller.addEffects(it) }
    )
    FilterReorderSheet(
        filterList = effects.map { it.filter },
        visible = showReorder,
        onDismiss = { showReorder = false },
        onReorder = controller::reorderEffects
    )
    FilterTemplateCreationSheet(
        component = tools.filterTemplates,
        visible = showTemplate,
        onDismiss = { showTemplate = false }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EffectBubble(
    label: String,
    selected: Boolean,
    masked: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (selected || label == "Add") Accent else BubbleGrey)
                .border(2.dp, if (masked) Color(0xFFFF3B30) else Color.Transparent, CircleShape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            contentAlignment = Alignment.Center
        ) { content() }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp,
            lineHeight = 12.sp,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChipButton(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Color.White,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(BubbleGrey)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

/** "Gaussian Blur" -> "GB", "Sepia" -> "Se". */
private fun String.initials(): String {
    val words = split(' ', '-', '_').filter { it.isNotBlank() }
    return if (words.size >= 2) "${words[0].first()}${words[1].first()}".uppercase()
    else take(2).replaceFirstChar { it.uppercase() }
}
