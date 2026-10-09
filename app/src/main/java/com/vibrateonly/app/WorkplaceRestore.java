package com.vibrateonly.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Sets the workplace watch up again after a restart, an app update or Location being switched
 * back on (Android forgets it then).
 */
public class WorkplaceRestore extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Workplace.register(context);
    }
}
