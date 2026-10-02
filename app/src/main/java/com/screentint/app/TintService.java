package com.screentint.app;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * Always-running service that draws the tint layer over everything, including the status bar,
 * navigation bar and lock screen. Android starts it again by itself after every reboot.
 * The layer ignores touches, so everything underneath works normally.
 */
public class TintService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private WindowManager windowManager;
    private View overlay;

    @Override
    protected void onServiceConnected() {
        windowManager = getSystemService(WindowManager.class);
        overlay = new View(this);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.setFitInsetsTypes(0);
        lp.setFitInsetsSides(0);
        lp.setTitle("Screen Tint");
        windowManager.addView(overlay, lp);
        overlay.setOnApplyWindowInsetsListener((v, insets) -> WindowInsets.CONSUMED);

        Tint.prefs(this).registerOnSharedPreferenceChangeListener(this);
        apply();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        apply();
    }

    private void apply() {
        if (overlay == null) return;
        int color = Tint.overlayColor(this);
        overlay.setBackgroundColor(color);
        overlay.setVisibility(color == 0 ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroy() {
        Tint.prefs(this).unregisterOnSharedPreferenceChangeListener(this);
        if (overlay != null) {
            windowManager.removeView(overlay);
            overlay = null;
        }
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
