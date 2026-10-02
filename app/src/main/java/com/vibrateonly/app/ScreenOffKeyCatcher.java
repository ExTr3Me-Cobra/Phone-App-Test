package com.vibrateonly.app;

import android.content.Context;
import android.media.AudioManager;
import android.media.VolumeProvider;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Catches the volume buttons while the screen is off.
 *
 * Android does not send button presses to accessibility services when the screen is off, but it
 * does send volume presses to an active "remote playback" media session. While the screen is
 * off this keeps such a session open (it plays no sound) so the two-button combo still works.
 * Single presses change media volume when that's what the buttons would normally do with the
 * screen off (something playing; and, while the mode is on, only through headphones).
 */
final class ScreenOffKeyCatcher {
    /** Screen-off presses arrive less precisely, so allow a bit more time than with the screen on. */
    private static final long EXTRA_WINDOW_MS = 100;
    /** Ignore held-button repeats for a moment after the combo fires. */
    private static final long AFTER_COMBO_QUIET_MS = 600;

    private final Context context;
    private final AudioManager audio;
    private final Runnable onCombo;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable forwardPending = this::forwardPending;
    private MediaSession session;
    private int pendingDirection;
    private long lastComboAt;

    ScreenOffKeyCatcher(Context context, Runnable onCombo) {
        this.context = context;
        this.audio = context.getSystemService(AudioManager.class);
        this.onCombo = onCombo;
    }

    void enable() {
        if (session != null || !Prefs.screenOff(context)) return;
        if (audio.getMode() != AudioManager.MODE_NORMAL) return; // in a call: leave volume keys alone
        session = new MediaSession(context, "VibrateOnlyKeys");
        session.setPlaybackToRemote(
                new VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE, 100, 50) {
                    @Override
                    public void onAdjustVolume(int direction) {
                        onPress(direction);
                    }
                });
        session.setPlaybackState(new PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                .build());
        session.setActive(true);
    }

    void disable() {
        handler.removeCallbacks(forwardPending);
        pendingDirection = 0;
        if (session != null) {
            session.setActive(false);
            session.release();
            session = null;
        }
    }

    private void onPress(int direction) {
        if (direction == 0) return; // button released
        long now = SystemClock.uptimeMillis();
        if (now - lastComboAt < AFTER_COMBO_QUIET_MS) return;

        if (pendingDirection != 0 && direction == -pendingDirection) {
            handler.removeCallbacks(forwardPending);
            pendingDirection = 0;
            lastComboAt = now;
            onCombo.run();
            return;
        }
        if (pendingDirection == direction) return; // same button still held
        pendingDirection = direction;
        handler.postDelayed(forwardPending, Prefs.comboWindowMs(context) + EXTRA_WINDOW_MS);
    }

    private void forwardPending() {
        int direction = pendingDirection;
        pendingDirection = 0;
        if (direction == 0) return;
        if (ModeController.isActive(context)) {
            if (AudioRouting.headphonesConnected(context)) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
            }
        } else if (audio.isMusicActive()) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
        }
    }
}
