/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/LICENSE-2.0>.
 */

@file:Suppress("UNCHECKED_CAST", "PropertyName")

package com.t8rin.neural_tools.bgremover

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.awxkee.aire.Aire
import com.awxkee.aire.ResizeFunction
import com.awxkee.aire.ScaleColorSpace
import com.t8rin.neural_tools.DownloadProgress
import com.t8rin.neural_tools.NeuralTool
import com.t8rin.neural_tools.runWithOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.NotActiveException
import java.nio.FloatBuffer
import kotlin.math.roundToInt

abstract class GenericBackgroundRemover(
    path: String,
    open val trainedSize: Int?
) : NeuralTool() {

    private val downloadLink = baseUrl.replace(
        oldValue = "*",
        newValue = path
    )

    /**
     * Viper Vision: feed the model ImageNet-normalised colours (mean 0.485/0.456/0.406, std
     * 0.229/0.224/0.225), as it was trained. Tested on a real photo: U2NetP kept the whole subject
     * with it, only part of it without.
     */
    protected open val imageNetNormalize: Boolean = false

    /** Viper Vision: run with ONNX Runtime's low-memory settings (big models, see removeBackground). */
    protected open val lowMemory: Boolean = false

    protected val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    protected var session: OrtSession? = null

    val directory: File
        get() = File(context.filesDir, "ai_models").apply(File::mkdirs)

    val modelFile
        get() = File(
            directory,
            downloadLink.substringAfterLast('/')
        )

    protected open val _isDownloaded =
        MutableStateFlow(modelFile.exists() && modelFile.length() > 0)
    val isDownloaded: StateFlow<Boolean> = _isDownloaded

    open fun checkModel(): Boolean {
        if (!modelFile.exists() || modelFile.length() <= 0) {
            _isDownloaded.update { false }
            modelFile.delete()
            close()
            return false
        }

        _isDownloaded.update { true }
        return true
    }

    open fun removeBackground(
        image: Bitmap,
        modelPath: String = modelFile.path,
        trainedSize: Int? = this.trainedSize,
        runOptions: OrtSession.RunOptions? = null
    ): Bitmap {
        if (!modelFile.exists() || modelFile.length() <= 0) {
            _isDownloaded.update { false }
            modelFile.delete()
            close()
            throw NotActiveException("Model ${modelFile.name} is not downloaded")
        }

        val session = session ?: OrtSession.SessionOptions().use { options ->
            options.addConfigEntry("mlas.disable_kleidiai", "1")
            if (lowMemory) {
                // Viper Vision: no memory arena or pattern planning. With them BiRefNet held
                // ~7.7 GB on a Galaxy phone (diagnostic log), enough for Android to kill the app.
                options.setCPUArenaAllocator(false)
                options.setMemoryPatternOptimization(false)
            }
            env.createSession(modelPath, options)
        }.also { session = it }

        val dstWidth = trainedSize ?: image.width
        val dstHeight = trainedSize ?: image.height

        val scaled = if (trainedSize == null) {
            image
        } else {
            Aire.scale(
                bitmap = image,
                dstWidth = dstWidth,
                dstHeight = dstHeight,
                scaleMode = ResizeFunction.Bilinear,
                colorSpace = ScaleColorSpace.SRGB
            )
        }

        val input = bitmapToFloatBuffer(scaled, dstWidth, dstHeight)
        val inputName = session.inputNames.first()
        val inputTensor = OnnxTensor.createTensor(
            env,
            input,
            longArrayOf(1, 3, dstWidth.toLong(), dstHeight.toLong())
        )

        val outputArray = inputTensor.use {
            val inputs = mapOf(inputName to inputTensor)
            session.runWithOptions(inputs, runOptions).use { output ->
                (output[0].value as Array<Array<Array<FloatArray>>>)[0][0]
            }
        }

        val maskBmp = createBitmap(dstWidth, dstHeight)
        var i = 0
        for (y in 0 until dstHeight) {
            for (x in 0 until dstWidth) {
                val alpha = (outputArray[y][x] * 255f).roundToInt().coerceIn(0, 255)
                maskBmp[x, y] = Color.argb(alpha, 255, 255, 255)
                i++
            }
        }

        val maskScaled = if (trainedSize == null) {
            maskBmp
        } else {
            Aire.scale(
                bitmap = maskBmp,
                dstWidth = image.width,
                dstHeight = image.height,
                scaleMode = ResizeFunction.Bilinear,
                colorSpace = ScaleColorSpace.SRGB
            )
        }

        val pixels = IntArray(image.width * image.height)
        val maskPixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        maskScaled.getPixels(maskPixels, 0, image.width, 0, 0, image.width, image.height)

        for (idx in pixels.indices) {
            val alpha = Color.alpha(maskPixels[idx])
            val src = pixels[idx]
            pixels[idx] =
                Color.argb(alpha, Color.red(src), Color.green(src), Color.blue(src))
        }

        val result = createBitmap(image.width, image.height)
        result.setPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        result.setHasAlpha(true)

        return result
    }

    open fun startDownload(forced: Boolean = false): Flow<DownloadProgress> = channelFlow {
        if (!forced && checkModel()) {
            send(DownloadProgress(currentPercent = 1f, currentTotalSize = 0L))
            return@channelFlow
        }

        downloader.download(
            url = downloadLink,
            destinationPath = modelFile.absolutePath,
            onProgress = { send(it) }
        )
        check(checkModel())
    }.flowOn(Dispatchers.IO)

    fun close() {
        session?.close()
        session = null
    }

    protected fun bitmapToFloatBuffer(
        bitmap: Bitmap,
        width: Int,
        height: Int
    ): FloatBuffer {
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val buffer = FloatBuffer.allocate(3 * width * height)
        var offsetR = 0
        var offsetG = width * height
        var offsetB = 2 * width * height

        val normalize = imageNetNormalize
        for (p in pixels) {
            if (normalize) {
                buffer.put(offsetR++, (Color.red(p) / 255f - 0.485f) / 0.229f)
                buffer.put(offsetG++, (Color.green(p) / 255f - 0.456f) / 0.224f)
                buffer.put(offsetB++, (Color.blue(p) / 255f - 0.406f) / 0.225f)
            } else {
                buffer.put(offsetR++, Color.red(p) / 255f)
                buffer.put(offsetG++, Color.green(p) / 255f)
                buffer.put(offsetB++, Color.blue(p) / 255f)
            }
        }

        return buffer
    }

}