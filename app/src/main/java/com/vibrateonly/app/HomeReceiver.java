package com.vibrateonly.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

/** Leaving / getting back home (sent by Android's geofencing). */
public class HomeReceiver extends BroadcastReceiver {
    static final String ACTION_GEOFENCE = "com.vibrateonly.app.HOME";

    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null || event.hasError() || !Prefs.homeOn(context)) return;
        int transition = event.getGeofenceTransition();
        if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            Home.arrived(context, false, "area watch");
        } else if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            Home.arrived(context, true, "area watch");
        }
    }
}
