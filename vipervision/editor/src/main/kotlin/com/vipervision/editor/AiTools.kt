package com.vipervision.editor

import android.graphics.Bitmap
import androidx.compose.ui.graphics.vector.ImageVector
import com.t8rin.imagetoolbox.core.resources.Icons
import com.t8rin.imagetoolbox.core.resources.icons.AutoFixHigh
import com.t8rin.imagetoolbox.core.resources.icons.BrightnessAuto
import com.t8rin.imagetoolbox.core.resources.icons.Cloud
import com.t8rin.imagetoolbox.core.resources.icons.Eraser
import com.t8rin.imagetoolbox.core.resources.icons.Face6
import com.t8rin.imagetoolbox.core.resources.icons.Jpg
import com.t8rin.imagetoolbox.core.resources.icons.Palette
import com.t8rin.imagetoolbox.feature.ai_tools.domain.AiProgressListener
import com.t8rin.imagetoolbox.feature.ai_tools.domain.AiToolsRepository
import com.t8rin.imagetoolbox.feature.ai_tools.domain.model.NeuralModel
import com.t8rin.imagetoolbox.feature.ai_tools.domain.model.NeuralParams
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/**
 * The AI bubbles (sparkle tab). Each runs one of Image Toolbox's on-device models, picked for
 * everyday photos and kept small where a small one does the job well. Models download from Image
 * Toolbox's model library the first time a bubble is used (except Remove background, which is
 * built in).
 */
enum class AiTool(val label: String, val modelName: String, val description: String) {
    RestoreFaces("Restore faces", "gfpgan_v1.4.onnx", "Sharpens and repairs blurry or low-quality faces."),
    LowLight("Low light", "zero_dcepp.onnx", "Brightens dark, underexposed photos naturally."),
    FixExposure("Fix exposure", "1xOverExposureCorrection_compact-fp16.onnx", "Recovers washed-out, overexposed photos."),
    Dehaze("Dehaze", "dehazeformer_t.onnx", "Clears haze, fog and smog."),
    Denoise("Denoise", "scunet_color_15_fp16.onnx", "Removes grain and noise from low-light shots."),
    Deblur("Deblur", "deblurring_nafnet_2025may.onnx", "Reduces blur from shaky hands or slight misfocus."),
    JpegCleanup("JPEG cleanup", "1x_SBDV-DeJPEG-Lite_130000_G.ort", "Removes blocky compression artefacts."),
    Colorize("Colorize", "1x-SpongeColor-Lite-fp16.onnx", "Adds colour to black-and-white photos."),
    RemoveBackground("Remove background", "u2netp.onnx", "Cuts out the subject and makes the background transparent.");

    val model: NeuralModel get() = NeuralModel.entries.first { it.name == modelName }

    val downloadSizeMb: Int get() = (model.downloadSize / 1_000_000).toInt().coerceAtLeast(1)

    /** Remove background is bundled with the app, so it never needs a download. */
    val isBundled: Boolean get() = this == RemoveBackground

    val icon: ImageVector
        get() = when (this) {
            RestoreFaces -> Icons.Outlined.Face6
            LowLight -> Icons.Outlined.BrightnessAuto
            FixExposure -> EditorIcons.Exposure
            Dehaze -> Icons.Rounded.Cloud
            Denoise -> Icons.Rounded.AutoFixHigh
            Deblur -> EditorIcons.Sharpness
            JpegCleanup -> Icons.Outlined.Jpg
            Colorize -> Icons.Rounded.Palette
            RemoveBackground -> Icons.Rounded.Eraser
        }
}

/** Runs [AiTool]s through Image Toolbox's AI tools repository (the same engine as its AI Tools screen). */
class AiRunner(private val repository: AiToolsRepository<Bitmap>) {

    val downloadedModels get() = repository.downloadedModels

    fun isReady(tool: AiTool): Boolean =
        tool.isBundled || repository.downloadedModels.value.any { it.name == tool.modelName }

    /** Downloads the tool's model if needed, reporting progress from 0 to 1. */
    suspend fun download(tool: AiTool, onProgress: (Float) -> Unit) {
        if (isReady(tool) && !tool.isBundled) return
        repository.downloadModel(tool.model).collect { onProgress(it.currentPercent) }
    }

    /** Runs the tool on [image] and returns the result. Throws with a readable message on failure. */
    suspend fun run(tool: AiTool, image: Bitmap, onProgress: (done: Int, total: Int) -> Unit): Bitmap {
        val model = tool.model
        repository.selectModel(model, forced = true)
        // The selection is stored in settings and read back; wait until it has landed.
        withTimeout(10_000) { repository.selectedModel.first { it?.name == model.name } }

        var failure: String? = null
        val result = repository.processImage(
            image = image,
            listener = object : AiProgressListener {
                override fun onError(error: String) {
                    failure = error
                }

                override fun onProgress(currentChunkIndex: Int, totalChunks: Int) {
                    onProgress(currentChunkIndex, totalChunks)
                }
            },
            // Image Toolbox's default settings for its AI tools.
            params = NeuralParams(
                strength = 65f,
                chunkSize = 512,
                overlap = 16,
                enableChunking = !model.isNonChunkable
            )
        )
        return result ?: error(failure ?: "${tool.label} didn't work on this photo")
    }
}
