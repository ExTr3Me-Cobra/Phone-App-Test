package com.vibrateonly.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

/** Arriving at / leaving the workplace (sent by Android's geofencing). */
public class WorkplaceReceiver extends BroadcastReceiver {
    static final String ACTION_GEOFENCE = "com.vibrateonly.app.WORKPLACE";

    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null || event.hasError() || !Prefs.workplaceOn(context)) return;
        int transition = event.getGeofenceTransition();
        if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            Workplace.noteEvent(context, "Arrived at work: Vibrate Only on");
            ModeController.setByLocation(context, true);
        } else if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            Workplace.noteEvent(context, "Left work: Vibrate Only off");
            ModeController.setByLocation(context, false);
        }
    }
}
