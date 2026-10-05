package com.shadeui.app.media

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.shadeui.app.notif.NotifItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaState(
    val pkg: String,
    val appName: String,
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
)

/** The current media session (song, podcast, video), via the notification-listener permission. */
class MediaRepo {
    private val state = MutableStateFlow<MediaState?>(null)
    val flow: StateFlow<MediaState?> = state.asStateFlow()

    private var manager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var appContext: Context? = null
    private val handler = Handler(Looper.getMainLooper())

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pick(list.orEmpty())
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() {
            controller = null
            publish()
        }
    }

    fun connect(context: Context, listener: ComponentName) {
        appContext = context.applicationContext
        val msm = context.getSystemService(MediaSessionManager::class.java) ?: return
        manager = msm
        runCatching {
            msm.addOnActiveSessionsChangedListener(sessionsListener, listener, handler)
            pick(msm.getActiveSessions(listener))
        }
    }

    fun disconnect() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(callback)
        controller = null
        state.value = null
    }

    private fun pick(list: List<MediaController>) {
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        if (best?.sessionToken == controller?.sessionToken) {
            publish()
            return
        }
        controller?.unregisterCallback(callback)
        controller = best
        best?.registerCallback(callback, handler)
        publish()
    }

    private fun publish() {
        val c = controller
        val ctx = appContext
        if (c == null || ctx == null) {
            state.value = null
            return
        }
        val md = c.metadata
        state.value = MediaState(
            pkg = c.packageName,
            appName = NotifItem.appInfo(ctx, c.packageName).first,
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = (md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty(),
            art = (md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART))?.asImageBitmap(),
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
        )
    }

    fun playPause(context: Context) {
        val c = controller
        if (c == null) {
            // Nothing active: "Play last song", like One UI.
            sendKey(context, KeyEvent.KEYCODE_MEDIA_PLAY)
            return
        }
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() = controller?.transportControls?.skipToNext()
    fun previous() = controller?.transportControls?.skipToPrevious()

    private fun sendKey(context: Context, code: Int) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val t = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }
}
