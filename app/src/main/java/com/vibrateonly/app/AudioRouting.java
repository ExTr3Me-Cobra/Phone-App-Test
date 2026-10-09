package com.vibrateonly.app;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

/** Media muting, headphone / speaker detection and volume-button behaviour. */
final class AudioRouting {
    private static final String KEY_MUTED_BY_US = "media_muted_by_us";

    private AudioRouting() {}

    /**
     * Any media device other than the phone itself: wired, USB or Bluetooth headphones and
     * earbuds, Bluetooth speakers and car systems, hearing aids, docks and HDMI.
     */
    static boolean mediaDeviceConnected(Context c) {
        AudioManager am = c.getSystemService(AudioManager.class);
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            switch (d.getType()) {
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_LINE_ANALOG:
                case AudioDeviceInfo.TYPE_LINE_DIGITAL:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                case AudioDeviceInfo.TYPE_USB_ACCESSORY:
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                case AudioDeviceInfo.TYPE_BLE_HEADSET:
                case AudioDeviceInfo.TYPE_BLE_SPEAKER:
                case AudioDeviceInfo.TYPE_BLE_BROADCAST:
                case AudioDeviceInfo.TYPE_HEARING_AID:
                case AudioDeviceInfo.TYPE_DOCK:
                case AudioDeviceInfo.TYPE_DOCK_ANALOG:
                case AudioDeviceInfo.TYPE_HDMI:
                case AudioDeviceInfo.TYPE_HDMI_ARC:
                case AudioDeviceInfo.TYPE_HDMI_EARC:
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    /**
     * Mutes media while the mode is in force; unmutes it otherwise. Only undoes a mute this app
     * made, so a mute the user set themselves is left alone.
     */
    static void applyMediaMute(Context c) {
        AudioManager am = c.getSystemService(AudioManager.class);
        boolean shouldMute = ModeController.isActive(c) && Prefs.muteMedia(c);
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
     * A single volume press while the mode is in force: changes the alarm volume (the only sound
     * left), never touching the ringer, so the phone stays on vibrate.
     */
    static void adjustWhileActive(Context c, int direction, boolean showFeedback) {
        AudioManager am = c.getSystemService(AudioManager.class);
        am.adjustStreamVolume(AudioManager.STREAM_ALARM, direction,
                showFeedback ? AudioManager.FLAG_SHOW_UI : 0);
    }
}
