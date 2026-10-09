package com.screentint.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.service.quicksettings.TileService;

/**
 * The user's tint settings and the maths that turns them into an overlay colour.
 *
 * The user sets how much red, green and blue should reach the screen (0-100%, where 100% is
 * normal), in steps of 0.1%, plus an overall strength that fades the whole look in or out. An
 * overlay can't multiply colours, only blend a see-through colour on top, so the overlay is
 * chosen so that white comes out exactly as the chosen levels. Equal levels give pure dimming;
 * unequal levels tint, and the darkest parts of the picture pick up some of the tint.
 */
final class Tint {
    static final String KEY_ENABLED = "enabled";
    static final String KEY_RED = "red";
    static final String KEY_GREEN = "green";
    static final String KEY_BLUE = "blue";
    /** Overall strength, 0..1000 (tenths of a percent). */
    static final String KEY_STRENGTH = "strength_x10";

    /** Never cover the screen completely, so it can always be seen and turned off. */
    static final float MAX_OPACITY = 0.85f;

    private Tint() {}

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("tint", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean(KEY_ENABLED, true);
    }

    /**
     * A colour level in percent with one decimal (e.g. 99.7). Stored as tenths of a percent;
     * older versions stored whole percents, which are still read.
     */
    static float level(Context c, String key) {
        SharedPreferences p = prefs(c);
        if (p.contains(key + "_x10")) return p.getInt(key + "_x10", 1000) / 10f;
        return p.getInt(key, 100);
    }

    static float strength(Context c) {
        return prefs(c).getInt(KEY_STRENGTH, 1000) / 10f;
    }

    static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_ENABLED, on).apply();
        try {
            TileService.requestListeningState(c, new ComponentName(c, TintTileService.class));
        } catch (RuntimeException ignored) {
            // Tile not added to quick settings.
        }
    }

    static void setLevels(Context c, float r, float g, float b) {
        prefs(c).edit()
                .putInt(KEY_RED + "_x10", tenths(r))
                .putInt(KEY_GREEN + "_x10", tenths(g))
                .putInt(KEY_BLUE + "_x10", tenths(b))
                .apply();
    }

    static void setStrength(Context c, float percent) {
        prefs(c).edit().putInt(KEY_STRENGTH, tenths(percent)).apply();
    }

    private static int tenths(float percent) {
        return Math.round(Math.max(0f, Math.min(100f, percent)) * 10f);
    }

    /** Applies the overall strength: 100% = the levels as set, 0% = no tint at all. */
    static float effective(float level, float strength) {
        return 100f - (strength / 100f) * (100f - level);
    }

    /**
     * Overlay colour as floats {alpha, red, green, blue} (each 0..1), or null when the tint is
     * off or neutral. Kept as floats so very small changes aren't rounded away.
     */
    static float[] overlay(Context c) {
        if (!enabled(c)) return null;
        float s = strength(c);
        return overlay(effective(level(c, KEY_RED), s), effective(level(c, KEY_GREEN), s),
                effective(level(c, KEY_BLUE), s));
    }

    static float[] overlay(float r, float g, float b) {
        float gr = r / 100f;
        float gg = g / 100f;
        float gb = b / 100f;
        // Blending colour C at opacity a turns white into (1 - a) + a * C. Pick the smallest a that
        // can bring the lowest channel down to its level, then solve for C.
        float a = Math.min(1f - Math.min(gr, Math.min(gg, gb)), MAX_OPACITY);
        if (a <= 0.0001f) return null;
        return new float[] {a, channel(gr, a), channel(gg, a), channel(gb, a)};
    }

    /** What white looks like through the overlay (for the preview swatch). */
    static int whiteThrough(float r, float g, float b) {
        float[] o = overlay(r, g, b);
        if (o == null) return Color.WHITE;
        float a = o[0];
        return Color.rgb((1 - a) + a * o[1], (1 - a) + a * o[2], (1 - a) + a * o[3]);
    }

    private static float channel(float level, float a) {
        return Math.max(0f, Math.min(1f, (level - (1f - a)) / a));
    }
}
