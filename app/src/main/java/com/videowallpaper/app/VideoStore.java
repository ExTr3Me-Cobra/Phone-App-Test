package com.videowallpaper.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Keeps a private copy of each chosen video, so the wallpaper keeps working after a reboot even
 * if the original file is moved, renamed or deleted.
 */
final class VideoStore {
    static final String HOME = "home";
    static final String LOCK = "lock";

    interface Progress {
        void onProgress(long copiedBytes);
    }

    private VideoStore() {}

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("videos", Context.MODE_PRIVATE);
    }

    static File file(Context c, String slot) {
        return new File(c.getFilesDir(), slot + ".video");
    }

    static boolean has(Context c, String slot) {
        File f = file(c, slot);
        return f.isFile() && f.length() > 0;
    }

    static String name(Context c, String slot) {
        return prefs(c).getString("name_" + slot, "video");
    }

    /** Bumped every time a slot's video changes, so the wallpaper knows to reload. */
    static long version(Context c, String slot) {
        return prefs(c).getLong("version_" + slot, 0);
    }

    /** Copies the video in (slow for big files: call off the main thread). */
    static void save(Context c, String slot, Uri source, String displayName, Progress progress)
            throws IOException {
        File tmp = new File(c.getFilesDir(), slot + ".tmp");
        try (InputStream in = c.getContentResolver().openInputStream(source);
                OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) throw new IOException("Could not open the video");
            byte[] buf = new byte[1 << 16];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
                progress.onProgress(total);
            }
        }
        if (!tmp.renameTo(file(c, slot))) {
            tmp.delete();
            throw new IOException("Could not save the video");
        }
        prefs(c).edit()
                .putString("name_" + slot, displayName)
                .putLong("version_" + slot, version(c, slot) + 1)
                .apply();
    }

    static void clear(Context c, String slot) {
        file(c, slot).delete();
        prefs(c).edit()
                .remove("name_" + slot)
                .putLong("version_" + slot, version(c, slot) + 1)
                .apply();
    }
}
