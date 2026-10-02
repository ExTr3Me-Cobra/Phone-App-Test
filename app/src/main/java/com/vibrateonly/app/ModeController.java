package com.vibrateonly.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.service.quicksettings.TileService;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Turns Vibrate Only Mode on and off.
 *
 * On: the phone's ringer goes to Vibrate, so notifications, texts and system sounds vibrate
 * instead of making noise. Media is muted unless headphones are connected ({@link AudioRouting}).
 * Alarms are not affected by the ringer. Incoming calls are made audible by {@link CallRinger}.
 *
 * Off: the ringer goes back to whatever it was before and media is unmuted.
 */
final class ModeController {
    private static final String TAG = "VibrateOnly";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_PREV_RINGER = "prev_ringer";
    private static final String KEY_ACTIVATED_AT = "activated_at";

    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private ModeController() {}

    static void addListener(Runnable r) {
        listeners.add(r);
    }

    static void removeListener(Runnable r) {
        listeners.remove(r);
    }

    static boolean isActive(Context c) {
        return Prefs.get(c).getBoolean(KEY_ACTIVE, false);
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
        Prefs.callVolume(c); // remembers the current ring volume the first time
        Prefs.get(c).edit()
                .putBoolean(KEY_ACTIVE, true)
                .putInt(KEY_PREV_RINGER, am.getRingerMode())
                .putLong(KEY_ACTIVATED_AT, System.currentTimeMillis())
                .apply();
        try {
            am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
        } catch (SecurityException e) {
            // Happens when Do Not Disturb is on and the app lacks DND access.
            Log.w(TAG, "Could not switch ringer to vibrate", e);
        }
        vibrate(c, true);
        refresh(c);
    }

    static void deactivate(Context c) {
        AudioManager am = c.getSystemService(AudioManager.class);
        int prevRinger = Prefs.get(c).getInt(KEY_PREV_RINGER, AudioManager.RINGER_MODE_NORMAL);
        if (prevRinger == AudioManager.RINGER_MODE_VIBRATE) {
            prevRinger = AudioManager.RINGER_MODE_NORMAL;
        }
        Prefs.get(c).edit().putBoolean(KEY_ACTIVE, false).apply();
        try {
            am.setRingerMode(prevRinger);
        } catch (SecurityException e) {
            Log.w(TAG, "Could not restore ringer mode", e);
            am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        }
        vibrate(c, false);
        refresh(c);
    }

    /** The sound mode was switched away from vibrate by something else; the mode is over. */
    static void endedExternally(Context c) {
        Prefs.get(c).edit().putBoolean(KEY_ACTIVE, false).apply();
        vibrate(c, false);
        refresh(c);
    }

    /**
     * Brings everything that depends on the mode or the settings up to date: media mute,
     * status notification, auto-off timer, quick settings tile and any open screens.
     */
    static void refresh(Context c) {
        AudioRouting.applyMediaMute(c);
        StatusNotifier.update(c);
        scheduleAutoOff(c);
        try {
            TileService.requestListeningState(c, new ComponentName(c, VibrateTileService.class));
        } catch (RuntimeException ignored) {
            // Tile not added to quick settings.
        }
        for (Runnable r : listeners) r.run();
    }

    private static void scheduleAutoOff(Context c) {
        AlarmManager alarms = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1,
                new Intent(c, ActionReceiver.class).setAction(ActionReceiver.ACTION_AUTO_OFF),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        int minutes = Prefs.autoOffMinutes(c);
        if (!isActive(c) || minutes <= 0) {
            alarms.cancel(pi);
            return;
        }
        long start = Prefs.get(c).getLong(KEY_ACTIVATED_AT, System.currentTimeMillis());
        long at = Math.max(start + minutes * 60_000L, System.currentTimeMillis() + 5_000L);
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    }

    /** One long buzz when turning on, two long buzzes when turning off. */
    static void vibrate(Context c, boolean on) {
        Vibrator v = c.getSystemService(VibratorManager.class).getDefaultVibrator();
        if (!v.hasVibrator()) return;
        int amp = v.hasAmplitudeControl() ? Prefs.vibrationStrength(c) : 255;
        long len = Prefs.vibrationLengthMs(c);
        long half = Math.max(250, len * 55 / 100);
        VibrationEffect effect = on
                ? VibrationEffect.createWaveform(new long[] {0, len}, new int[] {0, amp}, -1)
                : VibrationEffect.createWaveform(
                        new long[] {0, half, 250, half}, new int[] {0, amp, 0, amp}, -1);
        // Alarm usage so the feedback is never suppressed by the ringer or touch-vibration settings.
        v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM));
    }
}
