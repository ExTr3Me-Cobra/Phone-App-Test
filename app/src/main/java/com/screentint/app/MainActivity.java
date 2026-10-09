package com.screentint.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Setup, the on/off switch, colour sliders, presets and saved looks. */
public class MainActivity extends Activity {
    private static final String KEY_SAVED = "saved_looks";

    /** Built-in starting points: name, red, green, blue. */
    private static final Object[][] PRESETS = {
            {"Normal", 100, 100, 100},
            {"Warm", 100, 86, 66},
            {"Very warm", 100, 72, 40},
            {"Night red", 100, 35, 15},
            {"Cool", 84, 92, 100},
            {"Dim", 60, 60, 60},
            {"Very dim", 30, 30, 30},
    };

    private LinearLayout content;
    /** Slider steps: 1000 per bar, so each colour moves in 0.1% steps. */
    private static final int STEPS = 1000;

    private final SeekBar[] bars = new SeekBar[3];
    private final TextView[] labels = new TextView[3];
    /** The exact colour levels (percent, one decimal); the sliders only show them. */
    private final float[] levels = new float[3];
    private SeekBar strengthBar;
    private TextView strengthLabel;
    private View swatch;
    private Switch master;
    private boolean updatingBars;

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
        render();
    }

    private void render() {
        content.removeAllViews();
        TextView title = text("Screen Tint", 26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(title);

        if (!isServiceEnabled()) {
            heading("Setup (once)");
            content.addView(text("Turn on Screen Tint in Accessibility settings: tap \"Installed "
                    + "apps\" (or \"Downloaded apps\"), then \"Screen Tint\", and switch it on. "
                    + "After that it runs all the time, even after restarts.", 15));
            content.addView(button("Open Accessibility settings",
                    () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
            content.addView(text("If the switch is greyed out or says \"Restricted setting\": "
                    + "open App info, tap the ⋮ menu (top right), choose \"Allow restricted "
                    + "settings\", then try again.", 14));
            content.addView(button("Open App info", () -> startActivity(
                    new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName())))));
        }

        // On/off
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(20), 0, dp(4));
        TextView onLabel = text("Tint on", 20);
        onLabel.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(onLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        master = new Switch(this);
        master.setChecked(Tint.enabled(this));
        master.setOnCheckedChangeListener((b, on) -> Tint.setEnabled(this, on));
        row.addView(master);
        content.addView(row);

        // Colour
        heading("Colour");
        content.addView(text("How much of each colour reaches the screen. 100% = normal. Moves in "
                + "0.1% steps; the sliders are extra fine near 100%, and − / + nudge by 0.1%.", 14));
        swatch = new View(this);
        content.addView(swatch, new LinearLayout.LayoutParams(-1, dp(36)));
        String[] names = {"Red", "Green", "Blue"};
        String[] keys = {Tint.KEY_RED, Tint.KEY_GREEN, Tint.KEY_BLUE};
        int[] colors = {0xFFE53935, 0xFF43A047, 0xFF1E88E5};
        for (int i = 0; i < 3; i++) {
            final int ch = i;
            levels[i] = Tint.level(this, keys[i]);
            labels[i] = text("", 16);
            labels[i].setTypeface(Typeface.DEFAULT_BOLD);
            labels[i].setTextColor(colors[i]);
            labels[i].setPadding(0, dp(12), 0, 0);
            labels[i].setTag(names[i]);
            content.addView(labels[i]);

            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.addView(nudge("−", () -> setLevel(ch, levels[ch] - 0.1f)));
            SeekBar bar = new SeekBar(this);
            bar.setMax(STEPS);
            bar.setProgress(toSlider(levels[i]));
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                    if (updatingBars || !fromUser) return;
                    levels[ch] = fromSlider(p);
                    saveLevels();
                }

                @Override
                public void onStartTrackingTouch(SeekBar s) {}

                @Override
                public void onStopTrackingTouch(SeekBar s) {}
            });
            bars[i] = bar;
            line.addView(bar, new LinearLayout.LayoutParams(0, -2, 1f));
            line.addView(nudge("+", () -> setLevel(ch, levels[ch] + 0.1f)));
            content.addView(line);
        }

        // Strength: fades the whole look in and out, keeping its colour balance.
        strengthLabel = text("", 16);
        strengthLabel.setTypeface(Typeface.DEFAULT_BOLD);
        strengthLabel.setPadding(0, dp(16), 0, 0);
        content.addView(strengthLabel);
        content.addView(text("Fades the whole tint in or out smoothly, keeping the same colour "
                + "balance. 100% = exactly the levels above.", 14));
        LinearLayout sLine = new LinearLayout(this);
        sLine.setGravity(Gravity.CENTER_VERTICAL);
        sLine.addView(nudge("−", () -> setStrength(Tint.strength(this) - 0.1f)));
        strengthBar = new SeekBar(this);
        strengthBar.setMax(STEPS);
        strengthBar.setProgress(Math.round(Tint.strength(this) * 10));
        strengthBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser) return;
                Tint.setStrength(MainActivity.this, p / 10f);
                if (!Tint.enabled(MainActivity.this)) master.setChecked(true);
                updateLabels();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {}
        });
        sLine.addView(strengthBar, new LinearLayout.LayoutParams(0, -2, 1f));
        sLine.addView(nudge("+", () -> setStrength(Tint.strength(this) + 0.1f)));
        content.addView(sLine);
        updateLabels();

        // Presets
        heading("Presets");
        LinearLayout presetRow = chipRow();
        for (Object[] p : PRESETS) {
            presetRow.addView(chip((String) p[0], () -> setLevels((int) p[1], (int) p[2], (int) p[3])));
        }

        heading("My looks");
        List<String[]> saved = loadSaved();
        if (saved.isEmpty()) {
            content.addView(text("Save the current colours to come back to them later.", 14));
        } else {
            content.addView(text("Tap to use. Long-press to delete.", 14));
            LinearLayout savedRow = chipRow();
            for (String[] look : saved) {
                Button chip = chip(look[0], () -> setLevels(Float.parseFloat(look[1]),
                        Float.parseFloat(look[2]), Float.parseFloat(look[3])));
                chip.setOnLongClickListener(v -> {
                    confirmDelete(look[0]);
                    return true;
                });
                savedRow.addView(chip);
            }
        }
        content.addView(button("Save current colours", this::promptSave));

        heading("Good to know");
        content.addView(text("• It's a see-through colour layer, so it can make colours weaker "
                + "or the screen dimmer, but never brighter. The stronger the tint, the more the "
                + "darkest parts of the picture take on the tint colour.\n"
                + "• To keep the screen usable, it never covers it completely.\n"
                + "• Add a quick on/off tile: swipe down twice from the top, tap the pencil (edit), "
                + "and drag in \"Screen Tint\".\n"
                + "• The tint also shows up in screenshots.", 14));
    }

    private void saveLevels() {
        Tint.setLevels(this, levels[0], levels[1], levels[2]);
        if (!Tint.enabled(this)) master.setChecked(true); // also turns the tint on
        updateLabels();
    }

    /** One colour to an exact value (from the − / + buttons). */
    private void setLevel(int ch, float value) {
        levels[ch] = Math.round(Math.max(0f, Math.min(100f, value)) * 10f) / 10f;
        updatingBars = true;
        bars[ch].setProgress(toSlider(levels[ch]));
        updatingBars = false;
        saveLevels();
    }

    private void setStrength(float value) {
        float v = Math.round(Math.max(0f, Math.min(100f, value)) * 10f) / 10f;
        Tint.setStrength(this, v);
        strengthBar.setProgress(Math.round(v * 10));
        if (!Tint.enabled(this)) master.setChecked(true);
        updateLabels();
    }

    private void setLevels(float r, float g, float b) {
        Tint.setLevels(this, r, g, b);
        Tint.setStrength(this, 100f);
        Tint.setEnabled(this, true);
        render();
    }

    /**
     * Slider position to level: curved so the top of the range (90-100%, where small tweaks
     * matter most) gets far more of the slider's length.
     */
    private static float fromSlider(int p) {
        double x = 1.0 - p / (double) STEPS;
        return Math.round((float) (100.0 * (1.0 - x * x)) * 10f) / 10f;
    }

    private static int toSlider(float level) {
        double x = Math.sqrt(Math.max(0.0, 1.0 - level / 100.0));
        return (int) Math.round((1.0 - x) * STEPS);
    }

    private void updateLabels() {
        for (int i = 0; i < 3; i++) {
            labels[i].setText(String.format(java.util.Locale.US, "%s: %.1f%%", labels[i].getTag(), levels[i]));
        }
        float s = Tint.strength(this);
        strengthLabel.setText(String.format(java.util.Locale.US, "Strength: %.1f%%", s));
        // What white looks like through the overlay with the current settings.
        swatch.setBackgroundColor(Tint.whiteThrough(Tint.effective(levels[0], s),
                Tint.effective(levels[1], s), Tint.effective(levels[2], s)));
    }

    /** A small − / + button for 0.1% nudges (hold-free: tap repeatedly). */
    private Button nudge(String label, Runnable action) {
        Button b = button(label, action);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(0, 0, 0, 0);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        return b;
    }

    private List<String[]> loadSaved() {
        List<String[]> out = new ArrayList<>();
        String raw = Tint.prefs(this).getString(KEY_SAVED, "");
        if (raw.isEmpty()) return out;
        for (String entry : raw.split("\n")) {
            String[] parts = entry.split("\t");
            if (parts.length == 4) out.add(parts);
        }
        return out;
    }

    private void storeSaved(List<String[]> looks) {
        List<String> lines = new ArrayList<>();
        for (String[] l : looks) lines.add(TextUtils.join("\t", l));
        Tint.prefs(this).edit().putString(KEY_SAVED, TextUtils.join("\n", lines)).apply();
    }

    private void promptSave() {
        EditText input = new EditText(this);
        input.setHint("Name, e.g. Evening");
        input.setSingleLine();
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle("Save current colours")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    String typed = input.getText().toString().replaceAll("[\\t\\n]", " ").trim();
                    List<String[]> looks = loadSaved();
                    String name = typed.isEmpty() ? "Look " + (looks.size() + 1) : typed;
                    looks.removeIf(l -> l[0].equals(name)); // same name replaces the old one
                    float st = Tint.strength(this);
                    looks.add(new String[] {name,
                            String.valueOf(Tint.effective(levels[0], st)),
                            String.valueOf(Tint.effective(levels[1], st)),
                            String.valueOf(Tint.effective(levels[2], st))});
                    storeSaved(looks);
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(String name) {
        new AlertDialog.Builder(this)
                .setTitle("Delete \"" + name + "\"?")
                .setPositiveButton("Delete", (d, w) -> {
                    List<String[]> looks = loadSaved();
                    looks.removeIf(l -> l[0].equals(name));
                    storeSaved(looks);
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName me = new ComponentName(this, TintService.class);
        for (String s : enabled.split(":")) {
            if (me.equals(ComponentName.unflattenFromString(s))) return true;
        }
        return false;
    }

    private LinearLayout chipRow() {
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        scroller.addView(row);
        content.addView(scroller);
        return row;
    }

    private Button chip(String label, Runnable action) {
        Button b = button(label, action);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0xFF888888);
        b.setBackground(bg);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(40));
        lp.setMarginEnd(dp(8));
        b.setLayoutParams(lp);
        return b;
    }

    private void heading(String s) {
        TextView h = text(s, 18);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(0, dp(24), 0, dp(6));
        content.addView(h);
    }

    private Button button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(v -> action.run());
        return b;
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
