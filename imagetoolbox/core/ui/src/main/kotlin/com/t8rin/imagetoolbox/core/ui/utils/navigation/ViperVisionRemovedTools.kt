package com.t8rin.imagetoolbox.core.ui.utils.navigation

/**
 * Viper Vision: tools removed from the app. Their modules are gone from the build, but the screen
 * types stay here because shared code (deep links, help) still mentions them.
 * Removed tools are left out of every tool list, and navigating to one does nothing.
 *
 * Color Picker and Archive Tools are hidden here too, but their modules stay in the build:
 * Draw, Filter and Gradient Maker use the color picker, and Filter uses archive support.
 */
fun Screen.isRemovedTool(): Boolean = when (this) {
    is Screen.ImageCutter,
    is Screen.ResizeAndConvert,
    is Screen.WeightResize,
    is Screen.LimitResize,
    is Screen.EditExif,
    is Screen.DeleteExif,
    is Screen.BatchRename,
    is Screen.CollageMaker,
    is Screen.Photomosaic,
    is Screen.ImageStitching,
    is Screen.ImageStacking,
    is Screen.ImageSplitting,
    is Screen.Watermarking,
    is Screen.ShaderStudio,
    is Screen.CodePreview,
    is Screen.ScreenshotFraming,
    is Screen.NoiseGeneration,
    is Screen.FractalGeneration,
    is Screen.PickColorFromImage,
    is Screen.RecognizeText,
    is Screen.CompressionLab,
    is Screen.DuplicateFinder,
    is Screen.Base64Tools,
    is Screen.SvgMaker,
    is Screen.PaletteTools,
    is Screen.PalettePdfTool,
    is Screen.LoadNetImage,
    is Screen.DocumentScanner,
    is Screen.ScanQrCode,
    is Screen.ColorTools,
    is Screen.ColorLibrary,
    is Screen.GifTools,
    is Screen.Cipher,
    is Screen.ChecksumTools,
    is Screen.ArchiveTools,
    is Screen.AsciiArt,
    is Screen.JxlTools,
    is Screen.ApngTools,
    is Screen.WebpTools,
    is Screen.AudioCoverExtractor,
    is Screen.PdfTools -> true

    else -> Screen.PdfTools.options.any { it::class == this::class }
}
