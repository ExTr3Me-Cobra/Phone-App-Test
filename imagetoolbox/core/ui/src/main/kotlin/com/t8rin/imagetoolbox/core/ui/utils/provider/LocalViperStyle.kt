package com.t8rin.imagetoolbox.core.ui.utils.provider

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Viper Vision: set by Viper Vision's Studio screen so Image Toolbox's tool screens use its
 * layout (black, compact top bar, the picture as large as possible, settings below it).
 */
val LocalViperStyle = staticCompositionLocalOf { false }
