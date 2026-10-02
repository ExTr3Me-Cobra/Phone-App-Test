package com.vibrateonly.app;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.widget.Toast;

/** Media muting and volume-button behaviour while Vibrate Only Mode is on. */
final class AudioRouting {
    private static final String KEY_MUTED_BY_US = "media_muted_by_us";
    private static Toast toast;

    private AudioRouting() {}

    /** Wired, USB or Bluetooth headphones/earbuds (any Bluetooth audio device counts). */
    static boolean headphonesConnected(Context c) {
        AudioManager am = c.getSystemService(AudioManager.class);
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            switch (d.getType()) {
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                case AudioDeviceInfo.TYPE_BLE_HEADSET:
                case AudioDeviceInfo.TYPE_BLE_BROADCAST:
                case AudioDeviceInfo.TYPE_HEARING_AID:
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    /**
     * Mutes media while the mode is on and no headphones are connected; unmutes it otherwise.
     * Only undoes a mute this app made, so a mute the user set themselves is left alone.
     */
    static void applyMediaMute(Context c) {
        AudioManager am = c.getSystemService(AudioManager.class);
        boolean shouldMute = ModeController.isActive(c) && Prefs.muteMedia(c)
                && !headphonesConnected(c);
        boolean mutedByUs = Prefs.get(c).getBoolean(KEY_MUTED_BY_US, false);
        boolean muted = am.isStreamMute(AudioManager.STREAM_MUSIC);
        if (shouldMute) {
            if (!muted) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
                Prefs.get(c).edit().putBoolean(KEY_MUTED_BY_US, true).apply();
            }
        } else if (mutedByUs) {
            if (muted) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
            }
            Prefs.get(c).edit().putBoolean(KEY_MUTED_BY_US, false).apply();
        }
    }

    /**
     * A single volume press while the mode is on. With headphones it changes headphone media
     * volume; without, it changes the call ringtone and/or alarm volume (per settings) and never
     * touches the ringer, so the phone stays on vibrate.
     */
    static void adjustWhileActive(Context c, int direction, boolean showFeedback) {
        AudioManager am = c.getSystemService(AudioManager.class);
        int flags = showFeedback ? AudioManager.FLAG_SHOW_UI : 0;
        if (headphonesConnected(c)) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, flags);
            return;
        }

        int target = Prefs.keysTarget(c);
        String message = null;
        if (target == Prefs.TARGET_RING || target == Prefs.TARGET_BOTH) {
            int max = am.getStreamMaxVolume(AudioManager.STREAM_RING);
            int v = Math.max(0, Math.min(max, Prefs.callVolume(c) + direction));
            Prefs.setCallVolume(c, v);
            StatusNotifier.update(c);
            message = "Call ringtone: " + v + " / " + max;
        }
        if (target == Prefs.TARGET_ALARM) {
            am.adjustStreamVolume(AudioManager.STREAM_ALARM, direction, flags);
        } else if (target == Prefs.TARGET_BOTH) {
            am.adjustStreamVolume(AudioManager.STREAM_ALARM, direction, 0);
            message += "   ·   Alarm: " + am.getStreamVolume(AudioManager.STREAM_ALARM)
                    + " / " + am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
        }
        if (message != null && showFeedback) showToast(c, message);
    }

    private static void showToast(Context c, String message) {
        if (toast != null) toast.cancel();
        toast = Toast.makeText(c.getApplicationContext(), message, Toast.LENGTH_SHORT);
        toast.show();
    }
}
