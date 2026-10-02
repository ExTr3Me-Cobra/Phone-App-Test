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
 * normal). An overlay can't multiply colours, only blend a see-through colour on top, so the
 * overlay is chosen so that white comes out exactly as the chosen levels. Equal levels give pure
 * dimming; unequal levels tint, and the darkest parts of the picture pick up some of the tint.
 */
final class Tint {
    static final String KEY_ENABLED = "enabled";
    static final String KEY_RED = "red";
    static final String KEY_GREEN = "green";
    static final String KEY_BLUE = "blue";

    /** Never cover the screen completely, so it can always be seen and turned off. */
    static final float MAX_OPACITY = 0.85f;

    private Tint() {}

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("tint", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean(KEY_ENABLED, true);
    }

    static int level(Context c, String key) {
        return prefs(c).getInt(key, 100);
    }

    static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_ENABLED, on).apply();
        try {
            TileService.requestListeningState(c, new ComponentName(c, TintTileService.class));
        } catch (RuntimeException ignored) {
            // Tile not added to quick settings.
        }
    }

    static void setLevels(Context c, int r, int g, int b) {
        prefs(c).edit().putInt(KEY_RED, r).putInt(KEY_GREEN, g).putInt(KEY_BLUE, b).apply();
    }

    /** ARGB colour for the overlay, or transparent when the tint is off or neutral. */
    static int overlayColor(Context c) {
        if (!enabled(c)) return Color.TRANSPARENT;
        return overlayColor(level(c, KEY_RED), level(c, KEY_GREEN), level(c, KEY_BLUE));
    }

    static int overlayColor(int r, int g, int b) {
        float gr = r / 100f;
        float gg = g / 100f;
        float gb = b / 100f;
        // Blending colour C at opacity a turns white into (1 - a) + a * C. Pick the smallest a that
        // can bring the lowest channel down to its level, then solve for C.
        float a = Math.min(1f - Math.min(gr, Math.min(gg, gb)), MAX_OPACITY);
        if (a <= 0.001f) return Color.TRANSPARENT;
        return Color.argb(a, channel(gr, a), channel(gg, a), channel(gb, a));
    }

    private static float channel(float level, float a) {
        return Math.max(0f, Math.min(1f, (level - (1f - a)) / a));
    }
}
