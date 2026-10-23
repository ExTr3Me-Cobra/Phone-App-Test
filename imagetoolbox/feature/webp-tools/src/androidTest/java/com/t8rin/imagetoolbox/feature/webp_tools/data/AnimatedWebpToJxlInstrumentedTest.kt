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

package com.t8rin.imagetoolbox.feature.webp_tools.data

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.awxkee.jxlcoderlibjxl.JxlAnimatedImage
import com.t8rin.awebp.encoder.AnimatedWebpEncoder
import com.t8rin.imagetoolbox.core.domain.coroutines.DispatchersHolder
import com.t8rin.imagetoolbox.core.domain.image.model.AnimationMergeItem
import com.t8rin.imagetoolbox.core.domain.image.model.AnimationMergeParams
import com.t8rin.imagetoolbox.core.domain.image.model.Quality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.CoroutineContext

@RunWith(AndroidJUnit4::class)
class AnimatedWebpToJxlInstrumentedTest {

    @Test
    fun conversionPreservesAnimationTimingAndLoops() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val durations = listOf(120, 230, 340)
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE)
        val webpEncoder = AnimatedWebpEncoder(
            quality = 100,
            loopCount = 2,
            backgroundColor = Color.TRANSPARENT
        )
        colors.zip(durations).forEach { (color, duration) ->
            webpEncoder.addFrame(
                bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(color)
                },
                duration = duration
            )
        }
        val source = File(context.cacheDir, "webp_to_jxl_test.webp").apply {
            writeBytes(webpEncoder.encode())
        }
        var result: ByteArray? = null

        AndroidWebpConverter(
            imageGetter = unused(),
            imageShareProvider = unused(),
            imageScaler = unused(),
            context = context,
            dispatchersHolder = TestDispatchersHolder
        ).convertWebpToJxl(
            webpUris = listOf(source.toUri().toString()),
            quality = Quality.Jxl(qualityValue = 100)
        ) { _, bytes ->
            result = bytes
        }

        val encoded = requireNotNull(result)
        JxlAnimatedImage(encoded).use { decoder ->
            assertEquals(3, decoder.numberOfFrames)
            assertEquals(2, decoder.loopsCount)
            assertEquals(durations, List(decoder.numberOfFrames, decoder::getFrameDuration))
            colors.forEachIndexed { index, expectedColor ->
                val frame = decoder.getFrame(index)
                assertColorClose(expectedColor, frame.getPixel(8, 8))
                frame.recycle()
            }
        }

        val merged = requireNotNull(
            AndroidWebpConverter(
                imageGetter = unused(),
                imageShareProvider = unused(),
                imageScaler = unused(),
                context = context,
                dispatchersHolder = TestDispatchersHolder
            ).mergeWebps(
                items = listOf(
                    AnimationMergeItem(source.toUri().toString()),
                    AnimationMergeItem(
                        uri = source.toUri().toString(),
                        reverse = true,
                        boomerang = true
                    )
                ),
                params = AnimationMergeParams(
                    transitionDelayMillis = 50,
                    repeatCount = 4,
                    quality = Quality.Base(100)
                ),
                onFailure = { throw it },
                onProgress = {}
            )
        )
        val mergedInfo = parseWebp(merged)
        assertEquals(8, mergedInfo.first)
        assertEquals(4, mergedInfo.second)
        assertEquals(
            listOf(120, 230, 390, 340, 230, 120, 230, 340),
            mergedInfo.third
        )
    }

    private fun assertColorClose(expected: Int, actual: Int) {
        assertTrue(kotlin.math.abs(Color.red(expected) - Color.red(actual)) <= 8)
        assertTrue(kotlin.math.abs(Color.green(expected) - Color.green(actual)) <= 8)
        assertTrue(kotlin.math.abs(Color.blue(expected) - Color.blue(actual)) <= 8)
    }

    private fun parseWebp(data: ByteArray): Triple<Int, Int, List<Int>> {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).apply { position(12) }
        var loopCount = 0
        val durations = mutableListOf<Int>()
        while (buffer.remaining() >= 8) {
            val type = ByteArray(4).also(buffer::get).decodeToString()
            val length = buffer.int
            val chunk = ByteArray(length).also(buffer::get)
            if (length % 2 != 0 && buffer.hasRemaining()) buffer.get()
            when (type) {
                "ANIM" -> loopCount = (chunk[4].toInt() and 0xff) or
                        ((chunk[5].toInt() and 0xff) shl 8)

                "ANMF" -> durations += (chunk[12].toInt() and 0xff) or
                        ((chunk[13].toInt() and 0xff) shl 8) or
                        ((chunk[14].toInt() and 0xff) shl 16)
            }
        }
        return Triple(durations.size, loopCount, durations)
    }

    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java)
    ) { _, method, _ -> error("Unexpected call to ${method.name}") } as T

    private data object TestDispatchersHolder : DispatchersHolder {
        override val uiDispatcher: CoroutineContext = Dispatchers.Default
        override val ioDispatcher: CoroutineContext = Dispatchers.Default
        override val encodingDispatcher: CoroutineContext = Dispatchers.Default
        override val decodingDispatcher: CoroutineContext = Dispatchers.Default
        override val defaultDispatcher: CoroutineContext = Dispatchers.Default
    }
}
