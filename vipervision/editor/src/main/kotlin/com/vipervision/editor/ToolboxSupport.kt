package com.vipervision.editor

import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.t8rin.imagetoolbox.core.filters.presentation.widget.FilterTemplateCreationSheetComponent
import com.t8rin.imagetoolbox.core.filters.presentation.widget.addFilters.AddFiltersSheetComponent
import com.t8rin.imagetoolbox.core.settings.domain.model.NightMode
import com.t8rin.imagetoolbox.core.settings.domain.model.SettingsState

/**
 * Image Toolbox's settings as the editor uses them: always dark with black backgrounds and the
 * editor's accent colour, so Image Toolbox's sheets and controls match Viper Vision's editor.
 * Everything else (haptics, fonts, favourite filters...) follows the user's settings.
 */
internal fun SettingsState.forEditor(): SettingsState = copy(
    nightMode = NightMode.Dark,
    isAmoledMode = true,
    isDynamicColors = false,
    allowChangeColorByImage = false,
    isInvertThemeColors = false,
    appColorTuple = Accent.toArgb().toString()
)

/**
 * The parts of Image Toolbox's screens the editor reuses (pickers, sheets), created once per
 * editor and kept across rotation.
 */
internal class ToolboxComponents(
    componentContext: ComponentContext,
    addFiltersFactory: AddFiltersSheetComponent.Factory,
    templateFactory: FilterTemplateCreationSheetComponent.Factory
) : ComponentContext by componentContext {
    val addFilters: AddFiltersSheetComponent = addFiltersFactory(childContext("vvAddFilters"))
    val filterTemplates: FilterTemplateCreationSheetComponent = templateFactory(childContext("vvFilterTemplates"))
}

/** A small copy of the photo for filter thumbnails in Image Toolbox's pickers. */
internal fun Bitmap.thumbnail(side: Int = 300): Bitmap = scaledToFit(side)
