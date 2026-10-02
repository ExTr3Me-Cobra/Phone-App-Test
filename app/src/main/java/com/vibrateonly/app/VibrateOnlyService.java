package com.vibrateonly.app;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Always-running background service (Android keeps accessibility services alive and restarts
 * them after a reboot). Watches for Volume Up + Volume Down pressed together.
 *
 * While the screen is on, volume presses are held back for a split second to see whether the
 * other button follows. If it does, the mode toggles and the volume is left untouched; if not,
 * the press is passed on as a volume change (see {@link #adjustVolume}).
 */
public class VibrateOnlyService extends AccessibilityService {
    static VibrateOnlyService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager audio;
    private CallRinger callRinger;
    private ScreenOffKeyCatcher screenOffCatcher;

    /** Buttons whose press we swallowed, so we also swallow their release and repeats. */
    private final Set<Integer> consumedDown = new HashSet<>();
    /** Buttons held long enough to count as a normal press; their repeats change the volume. */
    private final Set<Integer> passthroughHeld = new HashSet<>();
    private int pendingKey;
    private boolean comboActive;

    private final Runnable forwardPending = () -> {
        int key = pendingKey;
        pendingKey = 0;
        if (key == 0) return;
        adjustVolume(key);
        if (consumedDown.contains(key)) passthroughHeld.add(key);
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                // Power button during an incoming call means "silence".
                if (callRinger.isRinging()) callRinger.silence();
                screenOffCatcher.enable();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                screenOffCatcher.disable();
            } else if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(action)) {
                if (ModeController.isActive(context)
                        && audio.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE) {
                    if (Prefs.keepVibrate(context)) {
                        try {
                            audio.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                        } catch (SecurityException e) {
                            ModeController.endedExternally(context);
                        }
                    } else {
                        ModeController.endedExternally(context);
                    }
                }
            }
        }
    };

    private final AudioManager.OnModeChangedListener audioModeListener = mode -> {
        if (mode != AudioManager.MODE_NORMAL) {
            screenOffCatcher.disable();
        } else if (!isScreenOn()) {
            screenOffCatcher.enable();
        }
    };

    /** Headphones plugged in or out: unmute or mute media, update the notification. */
    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
            ModeController.refresh(VibrateOnlyService.this);
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            ModeController.refresh(VibrateOnlyService.this);
        }
    };

    @Override
    protected void onServiceConnected() {
        instance = this;
        audio = getSystemService(AudioManager.class);
        callRinger = new CallRinger(this);
        callRinger.register();
        screenOffCatcher = new ScreenOffKeyCatcher(this, () -> ModeController.toggle(this));

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        audio.addOnModeChangedListener(getMainExecutor(), audioModeListener);
        audio.registerAudioDeviceCallback(deviceCallback, handler);

        if (!isScreenOn()) screenOffCatcher.enable();
        ModeController.refresh(this);
    }

    /** Called by the app screens after permissions or settings change. */
    void refresh() {
        callRinger.register();
        if (isScreenOn() || !Prefs.screenOff(this)) {
            screenOffCatcher.disable();
        } else {
            screenOffCatcher.enable();
        }
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (audio != null) {
            unregisterReceiver(receiver);
            audio.removeOnModeChangedListener(audioModeListener);
            audio.unregisterAudioDeviceCallback(deviceCallback);
            callRinger.unregister();
            screenOffCatcher.disable();
        }
        handler.removeCallbacks(forwardPending);
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (key != KeyEvent.KEYCODE_VOLUME_UP && key != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false;
        }

        if (event.getAction() == KeyEvent.ACTION_UP) {
            passthroughHeld.remove(key);
            if (consumedDown.remove(key)) {
                if (consumedDown.isEmpty()) comboActive = false;
                return true;
            }
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

        if (event.getRepeatCount() > 0) {
            if (!consumedDown.contains(key)) return false;
            if (passthroughHeld.contains(key)) adjustVolume(key);
            return true;
        }

        // Ringing or in a call: let the buttons do their usual job (e.g. silence the ringer).
        if (callRinger.isRinging() || audio.getMode() != AudioManager.MODE_NORMAL) {
            callRinger.silence();
            return false;
        }

        if (comboActive) {
            consumedDown.add(key);
            return true;
        }

        if (pendingKey != 0 && pendingKey != key) {
            // The other button arrived in time: this is the combo.
            handler.removeCallbacks(forwardPending);
            pendingKey = 0;
            comboActive = true;
            consumedDown.add(key);
            ModeController.toggle(this);
            return true;
        }

        if (pendingKey == key) {
            // Same button tapped twice very fast: let the first tap through right away.
            handler.removeCallbacks(forwardPending);
            adjustVolume(key);
        }
        pendingKey = key;
        consumedDown.add(key);
        handler.postDelayed(forwardPending, Prefs.comboWindowMs(this));
        return true;
    }

    /**
     * A single (non-combo) volume press. Normally does what the button would have done; while the
     * mode is on, changes headphone media or call/alarm volume instead so vibrate is kept.
     */
    private void adjustVolume(int key) {
        int direction = key == KeyEvent.KEYCODE_VOLUME_UP
                ? AudioManager.ADJUST_RAISE
                : AudioManager.ADJUST_LOWER;
        if (ModeController.isActive(this)) {
            AudioRouting.adjustWhileActive(this, direction, true);
            return;
        }
        audio.adjustSuggestedStreamVolume(
                direction, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
    }

    private boolean isScreenOn() {
        return getSystemService(PowerManager.class).isInteractive();
    }
}
