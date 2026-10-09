package com.vibrateonly.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles the notification's "Turn off" button and the auto-off timer. */
public class ActionReceiver extends BroadcastReceiver {
    static final String ACTION_TURN_OFF = "com.vibrateonly.app.TURN_OFF";
    static final String ACTION_AUTO_OFF = "com.vibrateonly.app.AUTO_OFF";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if ((ACTION_TURN_OFF.equals(action) || ACTION_AUTO_OFF.equals(action))
                && ModeController.isWanted(context)) {
            ModeController.turnOff(context);
        }
    }
}
