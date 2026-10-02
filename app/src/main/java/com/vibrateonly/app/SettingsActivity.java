package com.vibrateonly.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** All adjustable options. Every change takes effect immediately. */
public class SettingsActivity extends Activity {
    private LinearLayout content;
    private CallRinger previewRinger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Settings");
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
        render();
    }

    private void render() {
        content.removeAllViews();
        TextView title = text("Settings", 26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(title);

        heading("Button shortcut");
        toggle("Work with the screen off",
                "Catch the two-button press while the screen is off. Turn this off if it "
                        + "causes problems with other apps.",
                Prefs.SCREEN_OFF, true);
        choice("Press timing",
                "How close together the two buttons must be pressed. Longer is easier to hit, "
                        + "but single volume presses respond a little slower.",
                Prefs.COMBO_MS, 150,
                new String[] {"Quick (0.1 s)", "Normal (0.15 s)", "Relaxed (0.25 s)",
                        "Very relaxed (0.4 s)"},
                new int[] {100, 150, 250, 400});

        heading("Media");
        toggle("Mute media without headphones",
                "While the mode is on, music and videos are muted unless headphones or earbuds "
                        + "(wired or Bluetooth) are connected. Any Bluetooth audio device counts as "
                        + "headphones.",
                Prefs.MUTE_MEDIA, true);

        heading("Calls & alarms");
        toggle("Calls ring out loud",
                "While the mode is on, incoming calls ring (and vibrate). Turn off to have calls "
                        + "vibrate only.",
                Prefs.CALLS_RING, true);
        callVolume();
        choice("Volume buttons without headphones change",
                "While the mode is on. With headphones connected they always change headphone "
                        + "volume. The sound mode always stays on vibrate.",
                Prefs.KEYS_TARGET, Prefs.TARGET_RING,
                new String[] {"Call ringtone volume", "Alarm volume", "Both"},
                new int[] {Prefs.TARGET_RING, Prefs.TARGET_ALARM, Prefs.TARGET_BOTH});

        heading("On / off buzz");
        choice("Buzz strength", null, Prefs.VIB_STRENGTH, 255,
                new String[] {"Gentle", "Medium", "Strong"}, new int[] {110, 180, 255});
        choice("Buzz length", null, Prefs.VIB_LENGTH, 900,
                new String[] {"Short (0.5 s)", "Normal (0.9 s)", "Long (1.5 s)"},
                new int[] {500, 900, 1500});
        TextView test = link("Test buzz");
        test.setOnClickListener(v -> ModeController.vibrate(this, true));
        content.addView(test);

        heading("Behaviour");
        toggle("Show a notification while on",
                "A silent notification with a \"Turn off\" button, so you can see the mode is on.",
                Prefs.NOTIFICATION, true);
        choice("Turn off automatically after", null, Prefs.AUTO_OFF_MIN, 0,
                new String[] {"Never", "30 minutes", "1 hour", "2 hours", "4 hours", "8 hours",
                        "12 hours"},
                new int[] {0, 30, 60, 120, 240, 480, 720});
        toggle("Keep vibrate if sound mode is changed elsewhere",
                "On: if the sound mode is switched in quick settings (or by another app) while the "
                        + "mode is on, it goes straight back to vibrate, so only the buttons turn it "
                        + "off. Off: changing the sound mode elsewhere ends Vibrate Only Mode.",
                Prefs.KEEP_VIBRATE, true);

        heading("Tip");
        content.addView(text("You can also add a \"Vibrate Only\" tile to quick settings: swipe down "
                + "twice from the top, tap the pencil (edit) button, and drag the tile in.", 14));
    }

    @Override
    protected void onPause() {
        if (previewRinger != null) previewRinger.stop();
        super.onPause();
    }

    private void changed() {
        ModeController.refresh(this);
        VibrateOnlyService service = VibrateOnlyService.instance;
        if (service != null) service.refresh();
    }

    private void toggle(String name, String summary, String key, boolean def) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(bold(name));
        if (summary != null) texts.addView(text(summary, 14));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(Prefs.get(this).getBoolean(key, def));
        sw.setOnCheckedChangeListener((b, on) -> {
            Prefs.get(this).edit().putBoolean(key, on).apply();
            changed();
        });
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        content.addView(row);
    }

    private void choice(String name, String summary, String key, int def, String[] labels,
            int[] values) {
        int current = Prefs.get(this).getInt(key, def);
        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) selected = i;
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.addView(bold(name));
        if (summary != null) row.addView(text(summary, 14));
        TextView value = link(labels[selected]);
        row.addView(value);
        int initial = selected;
        row.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(name)
                .setSingleChoiceItems(labels, initial, (dialog, which) -> {
                    Prefs.get(this).edit().putInt(key, values[which]).apply();
                    dialog.dismiss();
                    changed();
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show());
        content.addView(row);
    }

    private void callVolume() {
        AudioManager am = getSystemService(AudioManager.class);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_RING);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        TextView label = bold("");
        row.addView(label);
        row.addView(text("How loud calls ring while the mode is on. This is separate from the "
                + "phone's own ringtone slider, which Android always shows at 0 in vibrate mode. "
                + "The volume buttons also change this while the mode is on (without headphones). "
                + "0 = calls vibrate only.", 14));
        SeekBar bar = new SeekBar(this);
        bar.setMax(max);
        bar.setProgress(Prefs.callVolume(this));
        label.setText("Call ringtone volume: " + bar.getProgress() + " / " + max);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                label.setText("Call ringtone volume: " + progress + " / " + max);
                if (fromUser) Prefs.setCallVolume(SettingsActivity.this, progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                StatusNotifier.update(SettingsActivity.this);
            }
        });
        row.addView(bar);
        TextView test = link("Test call ringtone (3 seconds)");
        test.setOnClickListener(v -> {
            if (previewRinger == null) previewRinger = new CallRinger(this);
            previewRinger.preview(3000);
        });
        row.addView(test);
        content.addView(row);
    }

    private void heading(String s) {
        TextView h = text(s, 18);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(0, dp(24), 0, dp(4));
        content.addView(h);
    }

    private TextView bold(String s) {
        TextView t = text(s, 16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView link(String s) {
        TextView t = text(s, 15);
        t.setTextColor(0xFF3D5AFE);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
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
