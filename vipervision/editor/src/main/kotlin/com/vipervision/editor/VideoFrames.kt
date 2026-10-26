package com.vipervision.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Reads single frames of a video for the video editor: the paused preview (so the dials show on
 * the exact frame without playing) and the trim bar's frame strips. One reader is kept open for
 * the editor's lifetime; calls are taken one at a time.
 */
class VideoFrames(context: Context, uri: Uri) : Closeable {
    private val retriever = MediaMetadataRetriever().apply { setDataSource(context, uri) }
    private val lock = Mutex()

    /** Width / height as shown (with the video's rotation applied), or null if unknown. */
    val aspect: Float? = run {
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull()
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull()
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (width == null || height == null || width <= 0f || height <= 0f) null
        else if (rotation % 180 != 0) height / width else width / height
    }

    val durationMs: Long =
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

    /** The frame at [ms] (the exact frame, not the nearest key frame), at most [maxSide] px. */
    suspend fun frame(ms: Long, maxSide: Int): Bitmap? = read(ms, maxSide, MediaMetadataRetriever.OPTION_CLOSEST)

    /** [count] frames spread over [fromMs]..[toMs], small, for the trim bar. */
    suspend fun strip(fromMs: Long, toMs: Long, count: Int, exact: Boolean): List<Bitmap> =
        (0 until count).mapNotNull { i ->
            val ms = fromMs + (toMs - fromMs) * (2 * i + 1) / (2 * count)
            read(ms, 160, if (exact) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }

    /** One small frame for the trim bar. */
    suspend fun thumbnail(ms: Long, exact: Boolean): Bitmap? =
        read(ms, 160, if (exact) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

    private suspend fun read(ms: Long, maxSide: Int, option: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching {
                val us = ms.coerceAtLeast(0) * 1000
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(us, option, maxSide, maxSide)
                } else {
                    retriever.getFrameAtTime(us, option)?.let { frame ->
                        val scale = maxSide.toFloat() / max(frame.width, frame.height)
                        if (scale >= 1f) frame else Bitmap.createScaledBitmap(
                            frame,
                            max(1, (frame.width * scale).roundToInt()),
                            max(1, (frame.height * scale).roundToInt()),
                            true
                        ).also { frame.recycle() }
                    }
                }
            }.getOrNull()
        }
    }

    override fun close() {
        runCatching { retriever.release() }
    }
}
