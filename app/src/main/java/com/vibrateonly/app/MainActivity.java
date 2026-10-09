package com.vibrateonly.app;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Setup checklist plus an on-screen switch for testing. */
public class MainActivity extends Activity {
    private LinearLayout content;
    private final Runnable onModeChanged = () -> runOnUiThread(this::render);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        content.setPadding(pad, pad, pad, pad);
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
        });
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ModeController.addListener(onModeChanged);
        VibrateOnlyService service = VibrateOnlyService.instance;
        if (service != null) service.refresh();
        render();
    }

    @Override
    protected void onPause() {
        ModeController.removeListener(onModeChanged);
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        VibrateOnlyService service = VibrateOnlyService.instance;
        if (service != null) service.refresh();
        ModeController.refresh(this);
        render();
    }

    private void render() {
        content.removeAllViews();

        TextView title = text("Vibrate Only", 26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(title);

        boolean active = ModeController.isActive(this);
        boolean waiting = ModeController.isWanted(this) && !active;
        TextView status = text(active ? "Vibrate Only Mode is ON"
                : waiting ? "Off while headphones / speaker are connected"
                : "Vibrate Only Mode is OFF", 20);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setTextColor(active ? 0xFF2E7D32 : waiting ? 0xFFEF6C00 : 0xFF9E9E9E);
        status.setPadding(0, dp(8), 0, dp(4));
        content.addView(status);
        content.addView(text(waiting
                ? "Vibrate Only comes back on 5 seconds after they disconnect."
                : "Press Volume Up and Volume Down at the same time to switch it on or "
                        + "off. One long buzz = ON, two long buzzes = OFF.", 15));
        Button toggle = new Button(this);
        toggle.setText(active ? "Turn off now" : waiting ? "Don't turn back on" : "Turn on now");
        toggle.setOnClickListener(v -> ModeController.toggleFromScreen(this));
        content.addView(toggle);
        Button settings = new Button(this);
        settings.setText("Settings");
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        content.addView(settings);

        heading("Setup (do each step once)");

        step("1. Turn on the button listener",
                "Opens Accessibility settings. Tap \"Installed apps\" (or \"Downloaded apps\"), "
                        + "then \"Vibrate Only\", and switch it on. If the switch is greyed out "
                        + "or says \"Restricted setting\", do step 1b first.",
                isServiceEnabled(),
                () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        if (!isServiceEnabled()) {
            step("1b. Only if step 1 is blocked",
                    "Opens this app's info page. Tap the ⋮ menu (top right) and choose "
                            + "\"Allow restricted settings\", then go back and redo step 1.",
                    false,
                    () -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()))));
        }

        step("2. Allow sound-mode changes",
                "Find \"Vibrate Only\" in the list and allow it. Needed so the app can switch "
                        + "sound modes even when Do Not Disturb is involved.",
                getSystemService(NotificationManager.class).isNotificationPolicyAccessGranted(),
                () -> startActivity(
                        new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));

        step("3. Keep it running in the background",
                "Choose \"Allow\" so Samsung's battery saver never stops the app. Also check "
                        + "Settings > Battery > Background usage limits and make sure Vibrate "
                        + "Only is not in \"Sleeping\" or \"Deep sleeping\" apps.",
                getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName()),
                () -> startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()))));

        if (Prefs.showNotification(this)) {
            step("4. Show the \"mode is on\" notification",
                    "Allow notifications so the app can show a silent reminder with a "
                            + "\"Turn off\" button while the mode is on. (Optional; can be "
                            + "switched off in Settings.)",
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED,
                    () -> requestPermissions(
                            new String[] {Manifest.permission.POST_NOTIFICATIONS}, 2));
        }

        heading("How it works");
        content.addView(text("• While ON: calls, texts, notifications and system sounds "
                + "vibrate only, and music and videos are muted. Alarms still sound.\n"
                + "• Connecting headphones, earbuds or any speaker (Bluetooth, wired, USB, car) "
                + "switches it off straight away; 5 seconds after they disconnect it comes back "
                + "on. If it was off, they just work normally.\n"
                + "• While something is connected, the two-button press is ignored.\n"
                + "• While ON, a single volume button changes the media volume when something "
                + "is playing (unmuting it until 30 seconds after it stops), otherwise the alarm volume. It never takes the phone "
                + "off vibrate.\n"
                + "• Optional: switch on automatically when you leave home and off when you get "
                + "back (Settings → Home).", 15));
    }

    private void heading(String s) {
        TextView h = text(s, 18);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(0, dp(24), 0, dp(8));
        content.addView(h);
    }

    private void step(String name, String explanation, boolean done, Runnable action) {
        TextView t = text((done ? "✅ " : "⬜ ") + name, 16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(12), 0, dp(2));
        content.addView(t);
        content.addView(text(explanation, 14));
        if (!done) {
            Button b = new Button(this);
            b.setText("Open");
            b.setOnClickListener(v -> {
                try {
                    action.run();
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
            });
            content.addView(b);
        }
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName me = new ComponentName(this, VibrateOnlyService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        for (String s : splitter) {
            ComponentName c = ComponentName.unflattenFromString(s);
            if (me.equals(c)) return true;
        }
        return false;
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
