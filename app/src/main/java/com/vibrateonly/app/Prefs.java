package com.vibrateonly.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;

/** User settings, all stored in one SharedPreferences file. */
final class Prefs {
    static final String FILE = "vibrate_only";

    static final String SCREEN_OFF = "screen_off";
    static final String COMBO_MS = "combo_ms";
    static final String MUTE_MEDIA = "mute_media";
    static final String CALLS_RING = "calls_ring";
    static final String CALL_VOLUME = "call_volume";
    static final String KEYS_TARGET = "keys_target";
    static final String VIB_STRENGTH = "vib_strength";
    static final String VIB_LENGTH = "vib_length";
    static final String NOTIFICATION = "notification";
    static final String AUTO_OFF_MIN = "auto_off_min";
    static final String KEEP_VIBRATE = "keep_vibrate";

    /** What volume buttons change while the mode is on and no headphones are connected. */
    static final int TARGET_RING = 0;
    static final int TARGET_ALARM = 1;
    static final int TARGET_BOTH = 2;

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean screenOff(Context c) {
        return get(c).getBoolean(SCREEN_OFF, true);
    }

    static int comboWindowMs(Context c) {
        return get(c).getInt(COMBO_MS, 150);
    }

    static boolean muteMedia(Context c) {
        return get(c).getBoolean(MUTE_MEDIA, true);
    }

    static boolean callsRing(Context c) {
        return get(c).getBoolean(CALLS_RING, true);
    }

    /** Loudness for calls while the mode is on, on the ringtone scale (0..ring max). */
    static int callVolume(Context c) {
        SharedPreferences p = get(c);
        if (!p.contains(CALL_VOLUME)) {
            AudioManager am = c.getSystemService(AudioManager.class);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_RING);
            int current = am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL
                    ? am.getStreamVolume(AudioManager.STREAM_RING) : 0;
            int initial = current > 0 ? current : Math.round(max * 0.7f);
            p.edit().putInt(CALL_VOLUME, initial).apply();
            return initial;
        }
        return p.getInt(CALL_VOLUME, 0);
    }

    static void setCallVolume(Context c, int v) {
        get(c).edit().putInt(CALL_VOLUME, v).apply();
    }

    static int keysTarget(Context c) {
        return get(c).getInt(KEYS_TARGET, TARGET_RING);
    }

    static int vibrationStrength(Context c) {
        return get(c).getInt(VIB_STRENGTH, 255);
    }

    static int vibrationLengthMs(Context c) {
        return get(c).getInt(VIB_LENGTH, 900);
    }

    static boolean showNotification(Context c) {
        return get(c).getBoolean(NOTIFICATION, true);
    }

    static int autoOffMinutes(Context c) {
        return get(c).getInt(AUTO_OFF_MIN, 0);
    }

    /** If the sound mode is changed some other way, put it back to vibrate (true) or end the mode. */
    static boolean keepVibrate(Context c) {
        return get(c).getBoolean(KEEP_VIBRATE, true);
    }
}
