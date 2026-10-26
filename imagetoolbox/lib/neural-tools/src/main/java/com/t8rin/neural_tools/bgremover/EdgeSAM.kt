package com.t8rin.neural_tools.bgremover

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.t8rin.neural_tools.ModelFiles
import com.t8rin.neural_tools.NeuralTool
import com.t8rin.neural_tools.runWithOptions
import java.io.Closeable
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Viper Vision: EdgeSAM (the "3x" release), a Segment Anything model distilled for phones. Same
 * prompts as [MobileSAM] (points and a box), but it reads the photo about twice as fast and is a
 * little more accurate. Models come from the authors' Hugging Face repo (chongzhou/EdgeSAM).
 */
object EdgeSAM : NeuralTool() {
    private val models = ModelFiles(
        "edge_sam",
        ModelFiles.Model(
            name = "edge_sam_3x_encoder.onnx",
            size = 22_098_300L,
            sha256 = "719a498cf5b3fe9be9f01ee513e13d3915f9028aa4f23dfd30eaaa0a17143159"
        ),
        ModelFiles.Model(
            name = "edge_sam_3x_decoder.onnx",
            size = 15_937_006L,
            sha256 = "83a2174d54571596913dcb7455d021e713623c3dca30a31c8c41ab98c9fb0863"
        ),
        urlTemplate = "https://huggingface.co/chongzhou/EdgeSAM/resolve/main/*?download=true"
    )

    private val mean = floatArrayOf(123.675f, 116.28f, 103.53f)
    private val std = floatArrayOf(58.395f, 57.12f, 57.375f)

    fun isDownloaded() = models.isDownloaded()
    suspend fun download(onProgress: suspend (Float) -> Unit) = models.download(onProgress)
    fun deleteModels() = models.delete()

    fun openSession(image: Bitmap, runOptions: OrtSession.RunOptions? = null): Session {
        check(isDownloaded()) { "Download EdgeSAM models first" }
        return Session(image, runOptions)
    }

    class Session internal constructor(
        image: Bitmap,
        runOptions: OrtSession.RunOptions?
    ) : Closeable {
        private val environment = OrtEnvironment.getEnvironment()
        private val width: Int
        private val height: Int
        private val embeddings: OnnxTensor
        private val decoder: OrtSession

        init {
            val scale = 1024f / maxOf(image.width, image.height)
            width = (image.width * scale).roundToInt().coerceIn(1, 1024)
            height = (image.height * scale).roundToInt().coerceIn(1, 1024)
            val resized = image.scale(width, height)
            val pixels = IntArray(width * height)
            resized.getPixels(pixels, 0, width, 0, 0, width, height)
            if (resized !== image) resized.recycle()
            // Normalised, channels first, padded to 1024×1024 (padding is 0 after normalising).
            val plane = 1024 * 1024
            val buffer = FloatBuffer.allocate(plane * 3)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = pixels[y * width + x]
                    val offset = y * 1024 + x
                    buffer.put(offset, ((pixel shr 16 and 255) - mean[0]) / std[0])
                    buffer.put(plane + offset, ((pixel shr 8 and 255) - mean[1]) / std[1])
                    buffer.put(2 * plane + offset, ((pixel and 255) - mean[2]) / std[2])
                }
            }
            embeddings = options().use { options ->
                environment.createSession(models.file("edge_sam_3x_encoder.onnx").absolutePath, options).use { encoder ->
                    OnnxTensor.createTensor(environment, buffer, longArrayOf(1, 3, 1024, 1024)).use { input ->
                        encoder.runWithOptions(mapOf("image" to input), runOptions).use { result ->
                            val tensor = result[0] as OnnxTensor
                            val values = tensor.floatBuffer
                            val copy = FloatArray(values.remaining()).also(values::get)
                            OnnxTensor.createTensor(environment, FloatBuffer.wrap(copy), tensor.info.shape)
                        }
                    }
                }
            }
            decoder = runCatching {
                options().use {
                    environment.createSession(models.file("edge_sam_3x_decoder.onnx").absolutePath, it)
                }
            }.onFailure {
                embeddings.close()
            }.getOrThrow()
        }

        /** Same as [MobileSAM.Session.segment]: [outputImage] with everything outside the object made transparent. */
        fun segment(
            points: List<MobileSAM.Point>,
            runOptions: OrtSession.RunOptions? = null,
            box: RectF? = null,
            outputImage: Bitmap
        ): Bitmap {
            require(points.any { it.positive } || box != null) { "Select a foreground point first" }
            val outputWidth = outputImage.width
            val outputHeight = outputImage.height
            val count = points.size + if (box != null) 2 else 0
            val coords = FloatArray(count * 2)
            val labels = FloatArray(count)
            points.forEachIndexed { index, point ->
                coords[index * 2] = point.x.coerceIn(0f, 1f) * width
                coords[index * 2 + 1] = point.y.coerceIn(0f, 1f) * height
                labels[index] = if (point.positive) 1f else 0f
            }
            if (box != null) {
                val index = points.size
                coords[index * 2] = box.left * width
                coords[index * 2 + 1] = box.top * height
                coords[index * 2 + 2] = box.right * width
                coords[index * 2 + 3] = box.bottom * height
                labels[index] = 2f
                labels[index + 1] = 3f
            }
            val tensors = mutableMapOf<String, OnnxTensor>()
            try {
                tensors["point_coords"] = OnnxTensor.createTensor(
                    environment, FloatBuffer.wrap(coords), longArrayOf(1, count.toLong(), 2)
                )
                tensors["point_labels"] = OnnxTensor.createTensor(
                    environment, FloatBuffer.wrap(labels), longArrayOf(1, count.toLong())
                )
                val inputs = tensors + ("image_embeddings" to embeddings)
                return decoder.runWithOptions(inputs, runOptions).use { output ->
                    // Four candidate masks (256×256 over the padded 1024 square); keep the best scored.
                    val scores = (output[0] as OnnxTensor).floatBuffer
                    val best = (0 until scores.remaining()).maxBy { scores.get(it) }
                    val masks = (output[1] as OnnxTensor).floatBuffer
                    val logits = FloatArray(256 * 256)
                    masks.position(best * 256 * 256)
                    masks.get(logits)
                    val pixels = IntArray(outputWidth * outputHeight)
                    outputImage.getPixels(pixels, 0, outputWidth, 0, 0, outputWidth, outputHeight)
                    val maxU = width / 4f - 1f
                    val maxV = height / 4f - 1f
                    for (oy in 0 until outputHeight) {
                        val v = ((oy + 0.5f) * height / outputHeight / 4f - 0.5f).coerceIn(0f, maxV.coerceAtLeast(0f))
                        val v0 = floor(v).toInt()
                        val v1 = minOf(v0 + 1, 255)
                        val fv = v - v0
                        for (ox in 0 until outputWidth) {
                            val u = ((ox + 0.5f) * width / outputWidth / 4f - 0.5f).coerceIn(0f, maxU.coerceAtLeast(0f))
                            val u0 = floor(u).toInt()
                            val u1 = minOf(u0 + 1, 255)
                            val fu = u - u0
                            val top = logits[v0 * 256 + u0] * (1 - fu) + logits[v0 * 256 + u1] * fu
                            val bottom = logits[v1 * 256 + u0] * (1 - fu) + logits[v1 * 256 + u1] * fu
                            val logit = top * (1 - fv) + bottom * fv
                            val opacity = 1f / (1f + exp(-logit.coerceIn(-20f, 20f)))
                            val i = oy * outputWidth + ox
                            val alpha = ((pixels[i] ushr 24) * opacity).roundToInt().coerceIn(0, 255)
                            pixels[i] = (alpha shl 24) or (pixels[i] and 0xFFFFFF)
                        }
                    }
                    createBitmap(outputWidth, outputHeight).apply {
                        setPixels(pixels, 0, outputWidth, 0, 0, outputWidth, outputHeight)
                        setHasAlpha(true)
                    }
                }
            } finally {
                tensors.values.forEach(OnnxTensor::close)
            }
        }

        override fun close() {
            try {
                decoder.close()
            } finally {
                embeddings.close()
            }
        }

        private fun options() = OrtSession.SessionOptions().apply {
            addConfigEntry("mlas.disable_kleidiai", "1")
            setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
        }
    }
}
