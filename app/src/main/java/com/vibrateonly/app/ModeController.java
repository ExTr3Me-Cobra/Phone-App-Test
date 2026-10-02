package com.vibrateonly.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

/**
 * Turns Vibrate Only Mode on and off.
 *
 * On: the phone's ringer goes to Vibrate, so notifications, texts and system sounds vibrate
 * instead of making noise. Alarms and media are not affected by the ringer, so they keep
 * playing at their normal volume. Incoming calls are made audible again by {@link CallRinger}.
 *
 * Off: the ringer goes back to whatever it was before.
 */
final class ModeController {
    private static final String TAG = "VibrateOnly";
    private static final String PREFS = "vibrate_only";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_PREV_RINGER = "prev_ringer";
    private static final String KEY_RING_VOLUME = "ring_volume";

    /** Called whenever the mode changes, so the app screen can refresh. */
    static Runnable onChanged;

    private ModeController() {}

    static boolean isActive(Context c) {
        return prefs(c).getBoolean(KEY_ACTIVE, false);
    }

    /** Ring volume (0..max of STREAM_RING) from just before the mode was turned on. */
    static int savedRingVolume(Context c) {
        return prefs(c).getInt(KEY_RING_VOLUME, -1);
    }

    static void toggle(Context c) {
        if (isActive(c)) {
            deactivate(c);
        } else {
            activate(c);
        }
    }

    static void activate(Context c) {
        AudioManager am = c.getSystemService(AudioManager.class);
        int prevRinger = am.getRingerMode();
        prefs(c).edit()
                .putBoolean(KEY_ACTIVE, true)
                .putInt(KEY_PREV_RINGER, prevRinger)
                .putInt(KEY_RING_VOLUME, am.getStreamVolume(AudioManager.STREAM_RING))
                .apply();
        try {
            am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
        } catch (SecurityException e) {
            // Happens when Do Not Disturb is on and the app lacks DND access.
            Log.w(TAG, "Could not switch ringer to vibrate", e);
        }
        vibrate(c, true);
        notifyChanged();
    }

    static void deactivate(Context c) {
        AudioManager am = c.getSystemService(AudioManager.class);
        int prevRinger = prefs(c).getInt(KEY_PREV_RINGER, AudioManager.RINGER_MODE_NORMAL);
        if (prevRinger == AudioManager.RINGER_MODE_VIBRATE) {
            prevRinger = AudioManager.RINGER_MODE_NORMAL;
        }
        prefs(c).edit().putBoolean(KEY_ACTIVE, false).apply();
        try {
            am.setRingerMode(prevRinger);
        } catch (SecurityException e) {
            Log.w(TAG, "Could not restore ringer mode", e);
            am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        }
        vibrate(c, false);
        notifyChanged();
    }

    /**
     * The ringer was changed by something else (e.g. Volume Up pressed on its own, or the
     * sound mode changed in quick settings), so the mode is effectively over.
     */
    static void endedExternally(Context c) {
        prefs(c).edit().putBoolean(KEY_ACTIVE, false).apply();
        vibrate(c, false);
        notifyChanged();
    }

    /** One long buzz when turning on, two long buzzes when turning off. */
    static void vibrate(Context c, boolean on) {
        Vibrator v = c.getSystemService(VibratorManager.class).getDefaultVibrator();
        if (!v.hasVibrator()) return;
        VibrationEffect effect = on
                ? VibrationEffect.createWaveform(new long[] {0, 900}, new int[] {0, 255}, -1)
                : VibrationEffect.createWaveform(
                        new long[] {0, 500, 250, 500}, new int[] {0, 255, 0, 255}, -1);
        // Alarm usage so the feedback is never suppressed by the ringer or touch-vibration settings.
        v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM));
    }

    private static void notifyChanged() {
        Runnable r = onChanged;
        if (r != null) r.run();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
