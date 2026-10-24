package com.vipervision.editor

import android.graphics.Bitmap
import androidx.compose.ui.graphics.vector.ImageVector
import com.t8rin.imagetoolbox.core.resources.Icons
import com.t8rin.imagetoolbox.core.resources.icons.AutoFixHigh
import com.t8rin.imagetoolbox.core.resources.icons.BrightnessAuto
import com.t8rin.imagetoolbox.core.resources.icons.Cloud
import com.t8rin.imagetoolbox.core.resources.icons.Face6
import com.t8rin.imagetoolbox.core.resources.icons.ImageResize
import com.t8rin.imagetoolbox.core.resources.icons.Jpg
import com.t8rin.imagetoolbox.core.resources.icons.Palette
import com.t8rin.imagetoolbox.core.resources.icons.Person
import com.t8rin.imagetoolbox.feature.ai_tools.domain.AiProgressListener
import com.t8rin.imagetoolbox.feature.ai_tools.domain.AiToolsRepository
import com.t8rin.imagetoolbox.feature.ai_tools.domain.model.NeuralModel
import com.t8rin.imagetoolbox.feature.ai_tools.domain.model.NeuralParams
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/**
 * One model offered for an AI tool. [title] and [about] tell the user what it's best at; the
 * download size comes from Image Toolbox's model list.
 */
class AiChoice(val modelName: String, val title: String, val about: String) {
    val model: NeuralModel get() = NeuralModel.entries.first { it.name == modelName }

    val downloadSizeMb: Int get() = (model.downloadSize / 1_000_000).toInt().coerceAtLeast(1)

    /** U2NetP comes with the app, so it never needs a download. */
    val isBundled: Boolean get() = modelName == "u2netp.onnx"
}

/**
 * The AI bubbles (sparkle tab). Each offers the one or two models regarded as best for the job,
 * from Image Toolbox's model library plus a few newer ones Viper Vision adds (CodeFormer and two
 * photo upscalers, see vipervision/models). Models download the first time they're used.
 */
enum class AiTool(val label: String, val description: String, val choices: List<AiChoice>) {
    Upscale(
        "Upscale 4×", "Makes the photo 4 times bigger with real detail instead of blur.",
        listOf(
            AiChoice(
                "4xPurePhoto-span.onnx", "PurePhoto (fast)",
                "Tiny and quick. Cleans up and enlarges everyday photos with a soft, natural look. " +
                        "Good for small or slightly blurry shots."
            ),
            AiChoice(
                "4xNomosWebPhoto_RealPLKSR.onnx", "NomosWebPhoto (best detail)",
                "Sharper and more detailed. Trained on real photos with noise, lens blur and " +
                        "compression, so it also repairs them. Slower on big photos."
            )
        )
    ),
    RestoreFaces(
        "Restore faces", "Finds the faces and repairs blurry, low-quality or old ones.",
        listOf(
            AiChoice(
                "gfpgan_v1.4.onnx", "GFPGAN 1.4 (natural)",
                "Keeps people looking like themselves. Best for faces that are just soft or a bit " +
                        "blurry. The long-time standard."
            ),
            AiChoice(
                "codeformer.onnx", "CodeFormer (heavy repair)",
                "Rebuilds faces that are very blurry, tiny or damaged, like old scans. Can change " +
                        "fine features a little on faces that were already fine."
            )
        )
    ),
    Denoise(
        "Denoise", "Removes grain and noise, e.g. from night or indoor shots.",
        listOf(
            AiChoice(
                "scunet_color-GAN.onnx", "SCUNet Real (natural)",
                "Made for real camera noise. Removes it while keeping texture like skin, hair and " +
                        "fabric, so the photo doesn't look plastic."
            ),
            AiChoice(
                "scunet_color-PSNR.onnx", "SCUNet Smooth (cleanest)",
                "Removes the most noise for the cleanest result, but smooths fine texture more."
            )
        )
    ),
    Deblur(
        "Deblur", "Sharpens blur from shaky hands or slight misfocus.",
        listOf(
            AiChoice(
                "deblurring_nafnet_2025may.onnx", "NAFNet (motion blur)",
                "One of the best deblurring models. Fixes blur from a moving camera or subject."
            ),
            AiChoice(
                "1x-Fatality-DeBlur.onnx", "Fatality (soft focus)",
                "Smaller and faster. Firms up photos that are evenly soft or slightly out of focus."
            )
        )
    ),
    JpegCleanup(
        "JPEG cleanup", "Removes the blocks and smudges of heavy compression (e.g. saved from chats).",
        listOf(
            AiChoice(
                "fbcnn_color_fp16.onnx", "FBCNN (best)",
                "Works out how badly the photo was compressed and undoes exactly that much."
            ),
            AiChoice(
                "1x_SBDV-DeJPEG-Lite_130000_G.ort", "DeJPEG Lite (fast)",
                "Much smaller and quicker. Good for mild compression."
            )
        )
    ),
    Colorize(
        "Colorize", "Adds colour to black-and-white photos.",
        listOf(
            AiChoice(
                "ddcolor_paper_tiny.ort", "DDColor Tiny",
                "Realistic colours at a quarter of the size. The everyday choice."
            ),
            AiChoice(
                "ddcolor_modelscope.onnx", "DDColor (best)",
                "The full model: the most accurate, natural colours. A big download (about 900 MB)."
            )
        )
    ),
    LowLight(
        "Low light", "Brightens dark, underexposed photos naturally.",
        listOf(
            AiChoice(
                "zero_dcepp.onnx", "Zero-DCE++",
                "Tiny and instant. Lifts dark areas with smooth curves, without blowing out the sky."
            )
        )
    ),
    FixExposure(
        "Fix exposure", "Recovers washed-out, overexposed photos.",
        listOf(
            AiChoice(
                "1xOverExposureCorrection_compact-fp16.onnx", "Overexposure Correction",
                "Small and fast. Brings back colour and detail in too-bright photos."
            )
        )
    ),
    Dehaze(
        "Dehaze", "Clears haze, fog and smog.",
        listOf(
            AiChoice(
                "dehazeformer_t.onnx", "DehazeFormer",
                "Small and fast. Restores contrast and colour lost to haze."
            )
        )
    ),
    RemoveBackground(
        "Remove background", "Cuts out the subject and makes the background transparent.",
        listOf(
            AiChoice(
                "u2netp.onnx", "U2NetP (instant)",
                "Built in, no download. Fine for clear subjects on simple backgrounds."
            ),
            AiChoice(
                "birefnet_swin_tiny.ort", "BiRefNet (best edges)",
                "The most accurate: clean edges around hair, fur and fine details. For more " +
                        "control, use Cut out."
            )
        )
    );

    val icon: ImageVector
        get() = when (this) {
            Upscale -> Icons.Outlined.ImageResize
            RestoreFaces -> Icons.Outlined.Face6
            LowLight -> Icons.Outlined.BrightnessAuto
            FixExposure -> EditorIcons.Exposure
            Dehaze -> Icons.Rounded.Cloud
            Denoise -> Icons.Rounded.AutoFixHigh
            Deblur -> EditorIcons.Sharpness
            JpegCleanup -> Icons.Outlined.Jpg
            Colorize -> Icons.Rounded.Palette
            RemoveBackground -> Icons.Rounded.Person
        }
}

/** Runs AI tools through Image Toolbox's AI tools repository (the same engine as its AI Tools screen). */
class AiRunner(private val repository: AiToolsRepository<Bitmap>) {

    val downloadedModels get() = repository.downloadedModels

    fun isReady(choice: AiChoice): Boolean =
        choice.isBundled || repository.downloadedModels.value.any { it.name == choice.modelName }

    /** Downloads the model if needed, reporting progress from 0 to 1. */
    suspend fun download(choice: AiChoice, onProgress: (Float) -> Unit) {
        if (isReady(choice) && !choice.isBundled) return
        repository.downloadModel(choice.model).collect { onProgress(it.currentPercent) }
    }

    /** Runs the model on [image] and returns the result. Throws with a readable message on failure. */
    suspend fun run(choice: AiChoice, image: Bitmap, onProgress: (done: Int, total: Int) -> Unit): Bitmap {
        val model = choice.model
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
            // Image Toolbox's default settings for its AI tools; full strength for the models
            // that have a strength setting (face restoration blends by it).
            params = NeuralParams(
                strength = if (model.isFaceRestoration) 100f else 65f,
                chunkSize = 512,
                overlap = 16,
                enableChunking = !model.isNonChunkable
            )
        )
        return result ?: error(failure ?: "${choice.title} didn't work on this photo")
    }
}
