package com.vipervision.editor

import androidx.compose.ui.graphics.toArgb
import com.t8rin.imagetoolbox.core.settings.domain.model.NightMode
import com.t8rin.imagetoolbox.core.settings.domain.model.SettingsState

/**
 * Image Toolbox's settings as the editor uses them: always dark with black backgrounds and the
 * app's colour ([accent], from Settings), so Image Toolbox's sheets and tools (Draw, Text &
 * layers...) match the rest of Viper Vision.
 * Everything else (haptics, fonts, favourite filters...) follows the user's settings.
 */
internal fun SettingsState.forEditor(accent: androidx.compose.ui.graphics.Color = Accent): SettingsState = copy(
    nightMode = NightMode.Dark,
    isAmoledMode = true,
    isDynamicColors = false,
    allowChangeColorByImage = false,
    isInvertThemeColors = false,
    appColorTuple = accent.toArgb().toString()
)
