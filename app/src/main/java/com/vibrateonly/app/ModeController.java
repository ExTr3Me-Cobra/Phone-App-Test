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
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Turns Vibrate Only Mode on and off.
 *
 * On: the phone's ringer goes to Vibrate, so calls, texts, notifications and system sounds
 * vibrate instead of making noise, and media is muted. Alarms are not affected by the ringer.
 *
 * The mode is "wanted" (switched on by the buttons, the tile or the workplace) but only applied
 * while no headphones or speaker are connected ({@link AudioRouting#mediaDeviceConnected}): with
 * one connected the phone behaves normally, and when it disconnects Vibrate Only comes back.
 *
 * Off: the ringer goes back to whatever it was before and media is unmuted.
 */
final class ModeController {
    private static final String TAG = "VibrateOnly";
    /** Wanted on (kept under the old key so an update keeps the current state). */
    private static final String KEY_WANTED = "active";
    /** The ringer is currently on vibrate because of us. */
    private static final String KEY_APPLIED = "applied";
    /** A headphone / speaker is connected, so the mode isn't applied for now. */
    private static final String KEY_DEVICE = "device_connected";
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

    /** Vibrate Only is actually in force right now. */
    static boolean isActive(Context c) {
        return isWanted(c) && !deviceConnected(c);
    }

    /** Switched on, though it may be waiting for headphones / a speaker to disconnect. */
    static boolean isWanted(Context c) {
        return Prefs.get(c).getBoolean(KEY_WANTED, false);
    }

    static boolean deviceConnected(Context c) {
        return Prefs.get(c).getBoolean(KEY_DEVICE, false);
    }

    /**
     * The two volume buttons. Ignored while headphones or a speaker are connected; otherwise
     * toggles the mode with the long buzz feedback.
     */
    static void buttonsPressed(Context c) {
        if (deviceConnected(c)) {
            Log.i(TAG, "Combo ignored: headphones or speaker connected");
            return;
        }
        setWanted(c, !isWanted(c));
        vibrate(c, isWanted(c));
    }

    /** The app screen or quick settings tile: toggles without the buzz. */
    static void toggleFromScreen(Context c) {
        if (deviceConnected(c) && !isWanted(c)) {
            Toast.makeText(c.getApplicationContext(),
                    "Disconnect your headphones / speaker first", Toast.LENGTH_SHORT).show();
            return;
        }
        setWanted(c, !isWanted(c));
    }

    static void turnOff(Context c) {
        setWanted(c, false);
    }

    /** Arriving at / leaving the workplace. */
    static void setByLocation(Context c, boolean on) {
        if (isWanted(c) != on) setWanted(c, on);
    }

    private static void setWanted(Context c, boolean on) {
        Prefs.get(c).edit()
                .putBoolean(KEY_WANTED, on)
                .putLong(KEY_ACTIVATED_AT, System.currentTimeMillis())
                .apply();
        apply(c);
    }

    /** Headphones / speaker connected or (5 s after) disconnected. */
    static void setDeviceConnected(Context c, boolean connected) {
        if (deviceConnected(c) == connected) return;
        Prefs.get(c).edit().putBoolean(KEY_DEVICE, connected).apply();
        apply(c);
    }

    /** Puts the ringer in the state the mode calls for, then updates everything else. */
    static void apply(Context c) {
        AudioManager am = c.getSystemService(AudioManager.class);
        boolean applied = Prefs.get(c).getBoolean(KEY_APPLIED, false);
        boolean should = isActive(c);
        if (should && !applied) {
            int current = am.getRingerMode();
            Prefs.get(c).edit()
                    .putBoolean(KEY_APPLIED, true)
                    .putInt(KEY_PREV_RINGER, current == AudioManager.RINGER_MODE_VIBRATE
                            ? AudioManager.RINGER_MODE_NORMAL : current)
                    .apply();
            setRinger(am, AudioManager.RINGER_MODE_VIBRATE);
        } else if (!should && applied) {
            int prev = Prefs.get(c).getInt(KEY_PREV_RINGER, AudioManager.RINGER_MODE_NORMAL);
            Prefs.get(c).edit().putBoolean(KEY_APPLIED, false).apply();
            if (!setRinger(am, prev)) setRinger(am, AudioManager.RINGER_MODE_NORMAL);
        } else if (should && am.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE) {
            setRinger(am, AudioManager.RINGER_MODE_VIBRATE);
        }
        refresh(c);
    }

    private static boolean setRinger(AudioManager am, int mode) {
        try {
            am.setRingerMode(mode);
            return true;
        } catch (SecurityException e) {
            // Happens when Do Not Disturb is on and the app lacks DND access.
            Log.w(TAG, "Could not set ringer mode " + mode, e);
            return false;
        }
    }

    /** The sound mode was switched away from vibrate by something else; the mode is over. */
    static void endedExternally(Context c) {
        Prefs.get(c).edit().putBoolean(KEY_WANTED, false).putBoolean(KEY_APPLIED, false).apply();
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
        if (!isWanted(c) || minutes <= 0) {
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
