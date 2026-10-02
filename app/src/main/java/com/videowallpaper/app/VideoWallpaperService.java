package com.videowallpaper.app;

import android.app.WallpaperManager;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.io.File;

/**
 * The live wallpaper. Android runs this itself, keeps it alive, and starts it again after every
 * reboot, so the app never has to be opened.
 *
 * Each place the wallpaper is shown (home screen, lock screen, or both) gets its own engine.
 * An engine shown only on the lock screen plays the lock-screen video if one was chosen;
 * everything else plays the home-screen video.
 */
public class VideoWallpaperService extends WallpaperService {
    @Override
    public Engine onCreateEngine() {
        return new VideoEngine();
    }

    private final class VideoEngine extends Engine
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        private ExoPlayer player;
        private boolean visible;
        /** Which file (and which version of it) is loaded, to avoid needless reloads. */
        private String loadedKey;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            VideoStore.prefs(VideoWallpaperService.this).registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            player = new ExoPlayer.Builder(VideoWallpaperService.this).build();
            // Wallpapers are silent: drop the audio track entirely so no sound or audio focus.
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build());
            player.setVolume(0f);
            player.setRepeatMode(Player.REPEAT_MODE_ONE); // seamless loop forever
            player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
            player.setVideoSurface(holder.getSurface());
            loadedKey = null;
            load();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            releasePlayer();
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            // Pause while hidden (screen off, app open on top) to save battery; resume instantly.
            if (player != null) player.setPlayWhenReady(visible);
        }

        @Override
        public void onWallpaperFlagsChanged(int which) {
            load();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            load();
        }

        @Override
        public void onDestroy() {
            VideoStore.prefs(VideoWallpaperService.this).unregisterOnSharedPreferenceChangeListener(this);
            releasePlayer();
            super.onDestroy();
        }

        private void load() {
            if (player == null) return;
            String slot = chooseSlot();
            if (slot == null) {
                player.stop();
                player.clearMediaItems();
                loadedKey = null;
                return;
            }
            File file = VideoStore.file(VideoWallpaperService.this, slot);
            String key = slot + ":" + VideoStore.version(VideoWallpaperService.this, slot);
            if (key.equals(loadedKey)) return;
            loadedKey = key;
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));
            player.prepare();
            player.setPlayWhenReady(visible);
        }

        /** Which video this engine should play, or null if none has been chosen yet. */
        private String chooseSlot() {
            boolean lockOnly = Build.VERSION.SDK_INT >= 34 && !isPreview()
                    && getWallpaperFlags() == WallpaperManager.FLAG_LOCK;
            boolean hasHome = VideoStore.has(VideoWallpaperService.this, VideoStore.HOME);
            boolean hasLock = VideoStore.has(VideoWallpaperService.this, VideoStore.LOCK);
            if (lockOnly && hasLock) return VideoStore.LOCK;
            if (hasHome) return VideoStore.HOME;
            if (hasLock) return VideoStore.LOCK;
            return null;
        }

        private void releasePlayer() {
            if (player != null) {
                player.release();
                player = null;
            }
        }
    }
}
