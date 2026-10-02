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
    private final SeekBar[] bars = new SeekBar[3];
    private final TextView[] labels = new TextView[3];
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
        content.addView(text("How much of each colour reaches the screen. 100% = normal. "
                + "Changes show instantly.", 14));
        swatch = new View(this);
        content.addView(swatch, new LinearLayout.LayoutParams(-1, dp(36)));
        String[] names = {"Red", "Green", "Blue"};
        String[] keys = {Tint.KEY_RED, Tint.KEY_GREEN, Tint.KEY_BLUE};
        int[] colors = {0xFFE53935, 0xFF43A047, 0xFF1E88E5};
        for (int i = 0; i < 3; i++) {
            labels[i] = text("", 16);
            labels[i].setTypeface(Typeface.DEFAULT_BOLD);
            labels[i].setTextColor(colors[i]);
            labels[i].setPadding(0, dp(12), 0, 0);
            content.addView(labels[i]);
            SeekBar bar = new SeekBar(this);
            bar.setMax(100);
            bar.setProgress(Tint.level(this, keys[i]));
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                    if (updatingBars) return;
                    saveFromBars();
                }

                @Override
                public void onStartTrackingTouch(SeekBar s) {}

                @Override
                public void onStopTrackingTouch(SeekBar s) {}
            });
            bars[i] = bar;
            content.addView(bar);
            labels[i].setTag(names[i]);
        }
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
                Button chip = chip(look[0], () -> setLevels(Integer.parseInt(look[1]),
                        Integer.parseInt(look[2]), Integer.parseInt(look[3])));
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

    private void saveFromBars() {
        Tint.setLevels(this, bars[0].getProgress(), bars[1].getProgress(), bars[2].getProgress());
        if (!Tint.enabled(this)) master.setChecked(true); // also turns the tint on
        updateLabels();
    }

    private void setLevels(int r, int g, int b) {
        Tint.setLevels(this, r, g, b);
        Tint.setEnabled(this, true);
        render();
    }

    private void updateLabels() {
        for (int i = 0; i < 3; i++) {
            labels[i].setText(labels[i].getTag() + ": " + bars[i].getProgress() + "%");
        }
        // What white looks like through the overlay with the current settings.
        int o = Tint.overlayColor(bars[0].getProgress(), bars[1].getProgress(),
                bars[2].getProgress());
        float a = Color.alpha(o) / 255f;
        swatch.setBackgroundColor(Color.rgb(
                (1 - a) + a * Color.red(o) / 255f,
                (1 - a) + a * Color.green(o) / 255f,
                (1 - a) + a * Color.blue(o) / 255f));
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
                    looks.add(new String[] {name, String.valueOf(bars[0].getProgress()),
                            String.valueOf(bars[1].getProgress()),
                            String.valueOf(bars[2].getProgress())});
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
