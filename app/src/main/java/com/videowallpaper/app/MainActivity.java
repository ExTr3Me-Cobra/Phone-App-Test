package com.videowallpaper.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.TypedValue;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Pick the videos and apply the wallpaper. */
public class MainActivity extends Activity {
    private static final int PICK_HOME = 1;
    private static final int PICK_LOCK = 2;

    private LinearLayout content;

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
        TextView title = text("Video Wallpaper", 26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(title);
        content.addView(text("Pick a video and it loops seamlessly as your wallpaper, forever. "
                + "It starts by itself after every restart; you never need to open this app "
                + "again.", 15));

        heading("Home screen video");
        boolean hasHome = VideoStore.has(this, VideoStore.HOME);
        content.addView(text(hasHome ? "🎬 " + VideoStore.name(this, VideoStore.HOME)
                : "None chosen yet", 15));
        content.addView(button(hasHome ? "Change video" : "Choose video", () -> pick(PICK_HOME)));

        heading("Lock screen video");
        boolean hasLock = VideoStore.has(this, VideoStore.LOCK);
        content.addView(text(hasLock ? "🎬 " + VideoStore.name(this, VideoStore.LOCK)
                : "Same as home screen", 15));
        content.addView(button(hasLock ? "Change video" : "Choose a different video",
                () -> pick(PICK_LOCK)));
        if (hasLock) {
            content.addView(button("Use the home screen video instead", () -> {
                VideoStore.clear(this, VideoStore.LOCK);
                render();
            }));
        }

        heading("Apply");
        content.addView(text(status(), 15));
        Button apply = button("Set as wallpaper", this::applyWallpaper);
        apply.setEnabled(hasHome || hasLock);
        content.addView(apply);
        content.addView(text("Tap \"Set as wallpaper\", then on the preview screen tap the "
                + "set/apply button and choose where to use it:\n"
                + "• Same video everywhere: choose \"Home and lock screens\".\n"
                + "• Different lock screen video: apply it to \"Home screen\", then tap \"Set as "
                + "wallpaper\" again and apply it to \"Lock screen\".\n\n"
                + "Changing a video here updates the wallpaper straight away; no need to apply "
                + "again.", 14));

        heading("Tips");
        content.addView(text("• For a perfectly seamless loop, the video's last frame should "
                + "flow into its first frame (a \"loop\" video). The app adds no gap or flash.\n"
                + "• The video plays silently and pauses whenever it's hidden (screen off or an "
                + "app on top) to save battery, then carries on instantly.\n"
                + "• The app keeps its own copy of each video, so you can delete or move the "
                + "original.\n"
                + "• If your phone only offers \"Home screen\" for this wallpaper, use Samsung's "
                + "own video lock screen: Settings > Wallpaper and style > lock screen > add "
                + "a video.", 14));
    }

    private String status() {
        WallpaperManager wm = WallpaperManager.getInstance(this);
        boolean home = isMine(wm.getWallpaperInfo());
        boolean lock = Build.VERSION.SDK_INT >= 34
                && isMine(wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK));
        if (home && lock) return "✅ Active on home and lock screen";
        if (home) return "✅ Active on home screen";
        if (lock) return "✅ Active on lock screen";
        return "Not set as your wallpaper yet";
    }

    private boolean isMine(WallpaperInfo info) {
        return info != null && getPackageName().equals(info.getPackageName());
    }

    private void applyWallpaper() {
        Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        new ComponentName(this, VideoWallpaperService.class));
        try {
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
                Toast.makeText(this, "Choose \"Video Wallpaper\" from the list",
                        Toast.LENGTH_LONG).show();
            } catch (Exception e2) {
                Toast.makeText(this, "Couldn't open the wallpaper screen", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void pick(int requestCode) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= 33) {
            intent = new Intent(MediaStore.ACTION_PICK_IMAGES).setType("video/*");
        } else {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("video/*");
        }
        try {
            startActivityForResult(intent, requestCode);
        } catch (Exception e) {
            startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("video/*"),
                    requestCode);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        String slot = requestCode == PICK_LOCK ? VideoStore.LOCK : VideoStore.HOME;
        importVideo(slot, data.getData());
    }

    /** Copies the video in the background with a progress bar. */
    private void importVideo(String slot, Uri uri) {
        String name = "video";
        long size = -1;
        try (Cursor c = getContentResolver().query(uri,
                new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                if (!c.isNull(0)) name = c.getString(0);
                if (!c.isNull(1)) size = c.getLong(1);
            }
        } catch (Exception ignored) {
        }

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(size <= 0);
        bar.setMax(1000);
        bar.setPadding(dp(24), dp(16), dp(24), dp(16));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Saving video…")
                .setView(bar)
                .setCancelable(false)
                .show();

        String displayName = name;
        long total = size;
        int[] lastShown = {-1};
        new Thread(() -> {
            String error = null;
            try {
                VideoStore.save(this, slot, uri, displayName, copied -> {
                    if (total <= 0) return;
                    int perMille = (int) Math.min(1000, copied * 1000 / total);
                    if (perMille == lastShown[0]) return;
                    lastShown[0] = perMille;
                    bar.post(() -> bar.setProgress(perMille));
                });
            } catch (Exception e) {
                error = e.getMessage();
            }
            String finalError = error;
            runOnUiThread(() -> {
                dialog.dismiss();
                if (finalError != null) {
                    Toast.makeText(this, "Couldn't save the video: " + finalError,
                            Toast.LENGTH_LONG).show();
                }
                render();
            });
        }).start();
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
