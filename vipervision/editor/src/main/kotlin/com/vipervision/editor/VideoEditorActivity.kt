package com.vipervision.editor

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import android.view.TextureView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextAlign
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.t8rin.imagetoolbox.core.resources.Icons
import com.t8rin.imagetoolbox.core.resources.icons.Close
import com.t8rin.imagetoolbox.core.resources.icons.Scissors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Viper Vision's video editor, opened from Gallery's Edit button for videos: trim (the first
 * bubble) and the Adjust dials, shown live while the video plays. Saving makes a new video next
 * to the original ("<original>_edited.mp4"); the original is never changed.
 */
@OptIn(UnstableApi::class)
class VideoEditorActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private var frames: VideoFrames? = null
    private lateinit var scrubber: Scrubber
    private val state = VideoEditState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent.data ?: run { finish(); return }
        val realPath = intent.getStringExtra(EditorActivity.EXTRA_REAL_PATH)
        enableEdgeToEdge()
        frames = runCatching { VideoFrames(this, source) }.getOrNull()
        frames?.aspect?.let { state.aspect = it }

        player = ExoPlayer.Builder(this).build().apply {
            // The dials are read for every frame, so the preview follows them while playing.
            setVideoEffects(listOf(VideoAdjustEffect { state.liveAdjustments }))
            setMediaItem(MediaItem.fromUri(source))
            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    // Only a fallback: with effects on, the player's reported size can be the
                    // stored (unrotated) one, so the size read from the file comes first.
                    if (frames?.aspect == null && videoSize.width > 0 && videoSize.height > 0) {
                        val rotated = videoSize.unappliedRotationDegrees % 180 != 0
                        val ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                        state.aspect = if (rotated) 1f / ratio else ratio
                    }
                }

                override fun onRenderedFirstFrame() {
                    scrubber.frameShown()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    state.playing = isPlaying
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) scrubber.frameShown()
                    if (playbackState == Player.STATE_READY && state.durationMs == 0L) {
                        state.durationMs = duration.coerceAtLeast(1)
                        state.trimEnd = state.durationMs
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        seekTo(state.trimStart)
                        pause()
                    }
                }
            })
            prepare()
        }
        scrubber = Scrubber(player)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent)) {
                val scope = rememberCoroutineScope()
                LaunchedEffect(Unit) {
                    val reader = frames ?: return@LaunchedEffect
                    state.thumbnails = reader.strip(0, reader.durationMs, STRIP_FRAMES, exact = false)
                }
                VideoEditorScreen(
                    state = state,
                    player = player,
                    scrubber = scrubber,
                    frames = frames,
                    onClose = ::finish,
                    onSave = { scope.launch { save(source, realPath) } }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::player.isInitialized) player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::player.isInitialized) player.release()
        frames?.close()
    }

    /** Exports the trimmed, adjusted video and saves it as a new video next to the original. */
    private suspend fun save(source: Uri, realPath: String?) {
        if (state.exportProgress != null) return
        player.pause()
        state.exportProgress = 0f
        val output = File(cacheDir, "vv_video").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            .let { File(it, "export_${System.currentTimeMillis()}.mp4") }
        runCatching {
            export(source, output)
            withContext(Dispatchers.IO) { saveVideoCopy(this@VideoEditorActivity, output, source, realPath) }
        }.onSuccess {
            Toast.makeText(this, "Saved as a new video", Toast.LENGTH_SHORT).show()
            finish()
        }.onFailure {
            ViperLog.error("VIDEO", "save failed", it)
            Toast.makeText(this, "Couldn't save the video: ${it.message}", Toast.LENGTH_LONG).show()
        }
        output.delete()
        state.exportProgress = null
    }

    private suspend fun export(source: Uri, output: File) {
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(state.trimStart)
            .apply { if (state.trimEnd < state.durationMs) setEndPositionMs(state.trimEnd) }
            .build()
        val item = MediaItem.Builder().setUri(source).setClippingConfiguration(clipping).build()
        val adjustments = state.adjustments
        val effects = if (adjustments.isIdentity) Effects.EMPTY else Effects(emptyList(), listOf(VideoAdjustEffect { adjustments }))
        val edited = EditedMediaItem.Builder(item).setEffects(effects).build()
        val composition = Composition.Builder(EditedMediaItemSequence.Builder(listOf(edited)).build())
            // The dials are made for normal (SDR) video, so HDR video is converted to SDR.
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()

        suspendCancellableCoroutine { continuation ->
            val transformer = Transformer.Builder(this)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        continuation.resume(Unit)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        continuation.resumeWithException(exportException)
                    }
                })
                .build()
            transformer.start(composition, output.absolutePath)
            val stopProgress = pollProgress(transformer)
            continuation.invokeOnCancellation {
                stopProgress.run()
                transformer.cancel()
            }
        }
    }

    /** Updates the progress shown while exporting, until the export finishes. */
    private fun pollProgress(transformer: Transformer): Runnable {
        val holder = ProgressHolder()
        val handler = android.os.Handler(mainLooper)
        val tick = object : Runnable {
            override fun run() {
                if (state.exportProgress == null) return
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    state.exportProgress = holder.progress / 100f
                }
                handler.postDelayed(this, 200)
            }
        }
        handler.post(tick)
        return Runnable { handler.removeCallbacks(tick) }
    }
}

/** What's being edited: the trim range, the dials and what the screen shows. */
class VideoEditState {
    var durationMs by mutableLongStateOf(0L)
    var trimStart by mutableLongStateOf(0L)
    var trimEnd by mutableLongStateOf(0L)
    var positionMs by mutableLongStateOf(0L)
    var playing by mutableStateOf(false)
    var aspect by mutableFloatStateOf(16f / 9f)
    var thumbnails by mutableStateOf<List<Bitmap>>(emptyList())

    /** Null while trimming, otherwise the dial being turned. */
    var selected by mutableStateOf<Adjustment?>(null)
    var adjustments by mutableStateOf(Adjustments())

    /** The dials for the video renderer, which runs on its own thread. */
    @Volatile
    var liveAdjustments = Adjustments()

    /** While paused: the shown frame with the dials applied (exact, unlike the player's). */
    var pausedPreview by mutableStateOf<Bitmap?>(null)
    var pausedPreviewMs by mutableLongStateOf(-1L)

    /** Pinch zoom of the video. */
    var zoom by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)

    /** While holding on the trim bar: the part of the video it's zoomed into, and its frames. */
    var timelineWindow by mutableStateOf<LongRange?>(null)
    var windowThumbnails by mutableStateOf<List<Bitmap?>>(emptyList())

    /** 0..1 while saving. */
    var exportProgress by mutableStateOf<Float?>(null)

    val changed: Boolean get() = !adjustments.isIdentity || trimStart > 0 || (durationMs > 0 && trimEnd < durationMs)
}

private const val MIN_TRIM_MS = 500L
private const val STRIP_FRAMES = 10

/**
 * Seeks for scrubbing: while the player is still finding one frame, newer positions wait and only
 * the latest is sought next, so the video keeps up with the finger instead of every seek
 * cancelling the one before it (which showed nothing until the finger stopped).
 */
class Scrubber(private val player: ExoPlayer) {
    private var seekingSince = 0L
    private var pending: Long? = null

    fun seekTo(ms: Long) {
        val now = android.os.SystemClock.uptimeMillis()
        // If the player never confirms a frame, don't wait on it forever.
        if (seekingSince != 0L && now - seekingSince < 700) {
            pending = ms
            return
        }
        pending = null
        seekingSince = now
        player.seekTo(ms)
    }

    /** The player has shown the frame it was seeking to. */
    fun frameShown() {
        seekingSince = 0L
        pending?.let(::seekTo)
    }
}

@Composable
private fun VideoEditorScreen(
    state: VideoEditState,
    player: ExoPlayer,
    scrubber: Scrubber,
    frames: VideoFrames?,
    onClose: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    var askDiscard by remember { mutableStateOf(false) }
    val close = { if (state.changed) askDiscard = true else onClose() }
    BackHandler(onBack = close)

    // Follow playback, and loop inside the trimmed part.
    LaunchedEffect(player) {
        while (isActive) {
            val position = player.currentPosition
            state.positionMs = position
            if (state.playing && state.trimEnd > 0 && position >= state.trimEnd) {
                player.seekTo(state.trimStart)
            }
            delay(30)
        }
    }
    // Playing video picks the dials up by itself (the effect reads them every frame).
    LaunchedEffect(Unit) {
        snapshotFlow { state.adjustments }.collectLatest { state.liveAdjustments = it }
    }
    // Paused: show the exact frame with the dials applied by the photo editor's maths, redone on
    // every change, so the preview never waits for the player to redraw.
    LaunchedEffect(frames) {
        val reader = frames ?: return@LaunchedEffect
        var frame: Bitmap? = null
        var frameMs = -1L
        snapshotFlow { Triple(state.playing, state.positionMs, state.adjustments) }.collectLatest { (playing, position, adjustments) ->
            if (playing) {
                state.pausedPreview = null
                return@collectLatest
            }
            if (position != frameMs || frame == null) {
                delay(80) // wait for scrubbing to settle
                frame = reader.frame(position, 1440) ?: return@collectLatest
                frameMs = position
            }
            val shown = frame ?: return@collectLatest
            state.pausedPreview = withContext(Dispatchers.Default) { applyAdjustments(context, shown, adjustments) }
            state.pausedPreviewMs = position
        }
    }
    // The zoomed trim bar's frames, exact ones for fine tuning.
    LaunchedEffect(frames) {
        val reader = frames ?: return@LaunchedEffect
        snapshotFlow { state.timelineWindow }.collectLatest { window ->
            state.windowThumbnails = List(STRIP_FRAMES) { null }
            if (window == null) return@collectLatest
            delay(150) // the window may still be sliding
            // Exact frames, nearest the middle (where the finger is) first; until each one
            // arrives the bar shows the normal thumbnail for that moment.
            val order = (0 until STRIP_FRAMES).sortedBy { abs(it - (STRIP_FRAMES - 1) / 2f) }
            for (i in order) {
                val ms = window.first + (window.last - window.first) * (2 * i + 1) / (2 * STRIP_FRAMES)
                val frame = reader.thumbnail(ms, exact = true) ?: continue
                state.windowThumbnails = state.windowThumbnails.toMutableList().also { it[i] = frame }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = close) { Icon(Icons.Rounded.Close, "Close", tint = Color.White) }
            Text("Edit video", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onSave, enabled = state.changed && state.exportProgress == null) {
                Text("Save", color = if (state.changed) Color.White else Color.Gray, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // The video, as large as fits. Tap to play or pause, pinch to zoom (drag to move around
        // while zoomed), double-tap to zoom back out.
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (player.isPlaying) player.pause() else {
                                if (player.currentPosition !in state.trimStart until state.trimEnd) player.seekTo(state.trimStart)
                                player.play()
                            }
                        },
                        onDoubleTap = {
                            state.zoom = 1f
                            state.pan = Offset.Zero
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        state.zoom = (state.zoom * zoom).coerceIn(1f, 6f)
                        val maxX = size.width * (state.zoom - 1f) / 2f
                        val maxY = size.height * (state.zoom - 1f) / 2f
                        state.pan = Offset(
                            (state.pan.x + pan.x).coerceIn(-maxX, maxX),
                            (state.pan.y + pan.y).coerceIn(-maxY, maxY)
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val fitsWidth = maxWidth / maxHeight < state.aspect
            val videoWidth = if (fitsWidth) maxWidth else maxHeight * state.aspect
            val videoHeight = if (fitsWidth) maxWidth / state.aspect else maxHeight
            Box(
                Modifier
                    .size(videoWidth, videoHeight)
                    .graphicsLayer {
                        scaleX = state.zoom
                        scaleY = state.zoom
                        translationX = state.pan.x
                        translationY = state.pan.y
                    }
            ) {
                AndroidView(
                    factory = { TextureView(it).also(player::setVideoTextureView) },
                    modifier = Modifier.fillMaxSize()
                )
                val preview = state.pausedPreview
                if (!state.playing && preview != null && state.pausedPreviewMs == state.positionMs) {
                    Image(
                        preview.asImageBitmap(), null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            if (!state.playing) PlayBadge()
        }

        Text(
            "${formatTime(state.positionMs)} / ${formatTime(state.durationMs)}",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp)
        )
        Spacer(Modifier.height(8.dp))

        // Trim first, then the Adjust dials.
        Text(
            state.selected?.label ?: "Trim",
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 2.dp)
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            state = rememberLazyListState(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp)
        ) {
            item {
                val selected = state.selected == null
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(if (selected) Color.White else BubbleGrey)
                        .clickable { state.selected = null },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Scissors, "Trim", tint = if (selected) Color.Black else Color.White, modifier = Modifier.size(26.dp))
                }
            }
            items(Adjustment.entries) { adjustment ->
                AdjustBubble(
                    adjustment = adjustment,
                    value = state.adjustments[adjustment],
                    selected = adjustment == state.selected,
                    onClick = { state.selected = adjustment }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(84.dp),
            contentAlignment = Alignment.Center
        ) {
            val selected = state.selected
            if (selected == null) {
                TrimBar(state, player, scrubber)
            } else {
                ValueWheel(
                    value = state.adjustments[selected],
                    onValueChange = { state.adjustments = state.adjustments.with(selected, it) },
                    onValueChangeFinished = {},
                    modifier = Modifier.padding(horizontal = 36.dp)
                )
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(8.dp))
    }

    state.exportProgress?.let { progress ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(progress = { progress }, color = Accent)
                Spacer(Modifier.height(12.dp))
                Text("Saving video… ${(progress * 100).roundToInt()}%", color = Color.White)
            }
        }
    }

    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to this video haven't been saved.") },
            confirmButton = { TextButton(onClick = { askDiscard = false; onClose() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { askDiscard = false }) { Text("Keep editing") } }
        )
    }
}

/**
 * Frames from the video with two handles: drag either end to trim, or drag anywhere else to move
 * the playhead (white line). Press and hold to zoom the bar into the couple of seconds around
 * your finger for frame-accurate moves; it zooms back out when you let go. The part that's kept
 * is framed in yellow.
 */
@Composable
private fun TrimBar(state: VideoEditState, player: ExoPlayer, scrubber: Scrubber) {
    val haptics = LocalHapticFeedback.current
    val duration = state.durationMs.coerceAtLeast(1)
    val window = state.timelineWindow ?: 0L..duration
    Column(Modifier.padding(horizontal = 20.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            val strip: List<Bitmap?> = if (state.timelineWindow == null) state.thumbnails else {
                // Zoomed: the exact frames as they arrive, the normal thumbnail meanwhile.
                List(STRIP_FRAMES) { i ->
                    state.windowThumbnails.getOrNull(i) ?: run {
                        val ms = window.first + (window.last - window.first) * (2 * i + 1) / (2 * STRIP_FRAMES)
                        state.thumbnails.getOrNull((ms * state.thumbnails.size / duration).toInt().coerceIn(0, state.thumbnails.size - 1))
                    }
                }
            }
            Row(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BubbleGrey)
            ) {
                strip.forEach {
                    if (it == null) Spacer(Modifier.weight(1f)) else Image(
                        it.asImageBitmap(), null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    )
                }
            }
            Canvas(
                Modifier
                    .matchParentSize()
                    .pointerInput(duration) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val width = size.width.toFloat()
                            fun msAt(x: Float, range: LongRange): Long =
                                (range.first + (x / width).coerceIn(0f, 1f) * (range.last - range.first)).toLong().coerceIn(0, duration)
                            fun xOf(ms: Long): Float = ms.toFloat() / duration * width
                            val reach = 24.dp.toPx()
                            val toStart = abs(down.position.x - xOf(state.trimStart))
                            val toEnd = abs(down.position.x - xOf(state.trimEnd))
                            // -1 start handle, 1 end handle, 0 playhead
                            val target = when {
                                toStart <= reach && toStart <= toEnd -> -1
                                toEnd <= reach -> 1
                                else -> 0
                            }
                            fun moveTo(ms: Long) {
                                when (target) {
                                    -1 -> state.trimStart = ms.coerceAtMost(state.trimEnd - MIN_TRIM_MS).coerceAtLeast(0)
                                    1 -> state.trimEnd = ms.coerceAtLeast(state.trimStart + MIN_TRIM_MS).coerceAtMost(duration)
                                }
                                scrubber.seekTo(
                                    when (target) {
                                        -1 -> state.trimStart
                                        1 -> state.trimEnd
                                        else -> ms.coerceIn(state.trimStart, state.trimEnd)
                                    }
                                )
                            }
                            player.pause()

                            // Moved, lifted, or held still long enough to zoom?
                            var lifted = false
                            val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val change = awaitPointerEvent().changes.first()
                                    if (!change.pressed) {
                                        lifted = true
                                        break
                                    }
                                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                                }
                            } == null
                            var range = 0L..duration
                            if (lifted) {
                                if (target == 0) moveTo(msAt(down.position.x, range))
                                return@awaitEachGesture
                            }
                            if (held) {
                                // Zoom into about 2 seconds (at most the whole video) around the finger.
                                val span = minOf(duration, maxOf(2000L, duration / 12))
                                val center = msAt(down.position.x, range)
                                val from = (center - span / 2).coerceIn(0, duration - span)
                                range = from..(from + span)
                                state.timelineWindow = range
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (target == 0) moveTo(center)
                            }
                            try {
                                // While zoomed, holding near either edge slides the zoomed part of
                                // the video that way (faster the closer to the edge), so trimming
                                // can go past what's on screen. Ticks keep it sliding while the
                                // finger rests there.
                                val edge = maxOf(width * 0.12f, 32.dp.toPx())
                                var x = down.position.x
                                var lastTick = android.os.SystemClock.uptimeMillis()
                                var lastMs = -1L
                                while (true) {
                                    val event = withTimeoutOrNull(16L) { awaitPointerEvent() }
                                    if (event != null) {
                                        val change = event.changes.first()
                                        if (!change.pressed) break
                                        change.consume()
                                        x = change.position.x
                                    }
                                    val now = android.os.SystemClock.uptimeMillis()
                                    val dt = (now - lastTick).coerceAtMost(100) / 1000f
                                    lastTick = now
                                    if (state.timelineWindow != null) {
                                        val depth = when {
                                            x < edge -> -(1f - x.coerceAtLeast(0f) / edge)
                                            x > width - edge -> 1f - (width - x.coerceAtMost(width)) / edge
                                            else -> 0f
                                        }
                                        if (depth != 0f) {
                                            val span = range.last - range.first
                                            // Up to 1.5 zoomed windows per second at the very edge.
                                            val shift = (depth * span * 1.5f * dt).toLong()
                                            val from = (range.first + shift).coerceIn(0, duration - span)
                                            if (from != range.first) {
                                                range = from..(from + span)
                                                state.timelineWindow = range
                                            }
                                        }
                                    }
                                    val ms = msAt(x, range)
                                    if (ms != lastMs) {
                                        lastMs = ms
                                        moveTo(ms)
                                    }
                                }
                            } finally {
                                state.timelineWindow = null
                            }
                        }
                    }
            ) {
                val span = (window.last - window.first).coerceAtLeast(1).toFloat()
                fun x(ms: Long) = ((ms - window.first) / span * size.width).coerceIn(-size.width, 2 * size.width)
                val startX = x(state.trimStart)
                val endX = x(state.trimEnd)
                val dim = Color.Black.copy(alpha = 0.65f)
                clipRect {
                    drawRect(dim, Offset.Zero, Size(startX.coerceIn(0f, size.width), size.height))
                    drawRect(dim, Offset(endX.coerceIn(0f, size.width), 0f), Size(size.width - endX.coerceIn(0f, size.width), size.height))
                    val handle = 10.dp.toPx()
                    val border = 3.dp.toPx()
                    drawRoundRect(
                        Accent,
                        Offset(startX, border / 2),
                        Size(max(endX - startX, handle), size.height - border),
                        CornerRadius(6.dp.toPx()),
                        style = Stroke(border)
                    )
                    drawRect(Accent, Offset(startX, 0f), Size(handle, size.height))
                    drawRect(Accent, Offset(endX - handle, 0f), Size(handle, size.height))
                    val playX = x(state.positionMs)
                    if (state.positionMs in state.trimStart..state.trimEnd) {
                        drawLine(Color.White, Offset(playX, 0f), Offset(playX, size.height), 2.dp.toPx())
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            val zoomed = state.timelineWindow
            Text(formatTime(zoomed?.first ?: state.trimStart), color = if (zoomed != null) Accent else Color.Gray, fontSize = 12.sp)
            Text(
                if (zoomed != null) "Zoomed · ${formatTime(state.positionMs)}" else "Length ${formatTime(state.trimEnd - state.trimStart)}",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Text(formatTime(zoomed?.last ?: state.trimEnd), color = if (zoomed != null) Accent else Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PlayBadge() {
    Canvas(
        Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        val w = size.width
        val path = Path().apply {
            moveTo(w * 0.38f, w * 0.28f)
            lineTo(w * 0.74f, w * 0.5f)
            lineTo(w * 0.38f, w * 0.72f)
            close()
        }
        drawPath(path, Color.White)
    }
}

private fun formatTime(ms: Long): String {
    val hundredths = (ms.coerceAtLeast(0) / 10) % 100
    val seconds = ms.coerceAtLeast(0) / 1000
    return "%d:%02d.%02d".format(seconds / 60, seconds % 60, hundredths)
}

/**
 * Copies the exported video into the gallery as "<original>_edited.mp4", next to the original
 * when Android allows that folder (DCIM, Movies or Pictures), otherwise in Movies/Viper Vision.
 */
private fun saveVideoCopy(context: Context, exported: File, source: Uri, realPath: String?) {
    val info = sourceInfo(context, source, realPath)
    val baseName = info.displayName.substringBeforeLast('.') + "_edited"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$baseName.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            val folder = info.relativeFolder?.takeIf { f -> listOf("DCIM/", "Movies/", "Pictures/").any(f::startsWith) }
            put(MediaStore.Video.Media.RELATIVE_PATH, folder ?: "Movies/Viper Vision/")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("couldn't create the new video")
        runCatching {
            resolver.openOutputStream(uri)?.use { out -> exported.inputStream().use { it.copyTo(out) } } ?: error("couldn't write the new video")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        }.onFailure {
            ViperLog.error("VIDEO", "saveVideoCopy failed", it)
            resolver.delete(uri, null, null)
            throw it
        }
    } else {
        @Suppress("DEPRECATION")
        val folder = info.absoluteFolder
            ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Viper Vision")
        folder.mkdirs()
        var file = File(folder, "$baseName.mp4")
        var n = 1
        while (file.exists()) file = File(folder, "$baseName (${n++}).mp4")
        exported.copyTo(file)
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("video/mp4"), null)
    }
}
