package com.vipervision.editor

import androidx.compose.ui.graphics.toArgb
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
