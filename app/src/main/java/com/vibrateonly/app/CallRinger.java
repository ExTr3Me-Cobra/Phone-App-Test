package com.vibrateonly.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * Keeps phone calls audible while Vibrate Only Mode is on.
 *
 * In Vibrate mode Android only vibrates for calls, so when a call starts ringing this plays
 * your ringtone through the alarm channel (which the Vibrate ringer does not mute), at the
 * "call ringtone volume" from the settings. The phone also vibrates as usual.
 */
final class CallRinger {
    private static final String TAG = "VibrateOnly";

    private final Context context;
    private final AudioManager audio;
    private final TelephonyManager telephony;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable stopRunnable = this::stop;
    private CallStateCallback callback;
    private MediaPlayer player;
    private int savedAlarmVolume = -1;
    private boolean ringing;

    CallRinger(Context context) {
        this.context = context;
        this.audio = context.getSystemService(AudioManager.class);
        this.telephony = context.getSystemService(TelephonyManager.class);
    }

    /** Starts watching for incoming calls. Safe to call repeatedly; needs the Phone permission. */
    void register() {
        if (callback != null) return;
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        callback = new CallStateCallback();
        try {
            telephony.registerTelephonyCallback(context.getMainExecutor(), callback);
        } catch (SecurityException e) {
            Log.w(TAG, "Could not watch call state", e);
            callback = null;
        }
    }

    void unregister() {
        if (callback != null) {
            telephony.unregisterTelephonyCallback(callback);
            callback = null;
        }
        stop();
    }

    boolean isRinging() {
        return ringing;
    }

    /** Stops our ringtone for the current call (e.g. a volume or power button was pressed). */
    void silence() {
        stop();
    }

    private final class CallStateCallback extends TelephonyCallback
            implements TelephonyCallback.CallStateListener {
        @Override
        public void onCallStateChanged(int state) {
            ringing = state == TelephonyManager.CALL_STATE_RINGING;
            if (ringing) {
                start();
            } else {
                stop();
            }
        }
    }

    private void start() {
        if (player != null) return;
        if (!ModeController.isActive(context) || !Prefs.callsRing(context)) return;
        if (Prefs.callVolume(context) == 0) return;
        if (audio.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE) return;
        play();
    }

    /** Plays the ringtone for a few seconds exactly as an incoming call would sound. */
    void preview(long millis) {
        stop();
        if (Prefs.callVolume(context) == 0) return;
        play();
        handler.postDelayed(stopRunnable, millis);
    }

    private void play() {
        savedAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM);
        audio.setStreamVolume(AudioManager.STREAM_ALARM, targetAlarmVolume(), 0);

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        Uri[] candidates = {
                Settings.System.DEFAULT_RINGTONE_URI,
                RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        };
        for (Uri uri : candidates) {
            if (uri == null) continue;
            MediaPlayer mp = new MediaPlayer();
            try {
                mp.setAudioAttributes(attrs);
                mp.setDataSource(context, uri);
                mp.setLooping(true);
                mp.prepare();
                mp.start();
                player = mp;
                return;
            } catch (Exception e) {
                Log.w(TAG, "Could not play ringtone " + uri, e);
                mp.release();
            }
        }
        restoreAlarmVolume();
    }

    void stop() {
        handler.removeCallbacks(stopRunnable);
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
            }
            player.release();
            player = null;
        }
        restoreAlarmVolume();
    }

    private void restoreAlarmVolume() {
        if (savedAlarmVolume >= 0) {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0);
            savedAlarmVolume = -1;
        }
    }

    /** Alarm volume matching the "call ringtone volume" setting (which uses the ringtone scale). */
    private int targetAlarmVolume() {
        int alarmMax = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM);
        int ringMax = audio.getStreamMaxVolume(AudioManager.STREAM_RING);
        float fraction = ringMax > 0 ? (float) Prefs.callVolume(context) / ringMax : 0.7f;
        return Math.max(1, Math.round(fraction * alarmMax));
    }
}
