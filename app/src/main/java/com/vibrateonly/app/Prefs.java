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
    static final String VIB_STRENGTH = "vib_strength";
    static final String VIB_LENGTH = "vib_length";
    static final String NOTIFICATION = "notification";
    static final String AUTO_OFF_MIN = "auto_off_min";
    static final String KEEP_VIBRATE = "keep_vibrate";
    static final String WORK_ON = "work_on";
    static final String WORK_LAT = "work_lat";
    static final String WORK_LNG = "work_lng";
    static final String WORK_RADIUS = "work_radius";

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

    /** Turn the mode on at the workplace and off when leaving. */
    static boolean workplaceOn(Context c) {
        return get(c).getBoolean(WORK_ON, false);
    }

    static boolean hasWorkplace(Context c) {
        return get(c).contains(WORK_LAT) && get(c).contains(WORK_LNG);
    }

    static double workLat(Context c) {
        return Double.longBitsToDouble(get(c).getLong(WORK_LAT, 0));
    }

    static double workLng(Context c) {
        return Double.longBitsToDouble(get(c).getLong(WORK_LNG, 0));
    }

    static void setWorkplace(Context c, double lat, double lng) {
        get(c).edit()
                .putLong(WORK_LAT, Double.doubleToRawLongBits(lat))
                .putLong(WORK_LNG, Double.doubleToRawLongBits(lng))
                .apply();
    }

    /** Metres around the workplace that count as "there". */
    static int workRadius(Context c) {
        return get(c).getInt(WORK_RADIUS, 150);
    }
}
