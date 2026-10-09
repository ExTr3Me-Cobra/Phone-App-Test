package com.vibrateonly.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.text.DateFormat;
import java.util.Date;
import java.util.function.Consumer;

/**
 * Turns Vibrate Only Mode on when you leave home and off when you get back, using Android's
 * geofencing (the battery-friendly way location reminders work).
 */
final class Home {
    private static final String TAG = "VibrateOnly";
    private static final String KEY_LAST_EVENT = "home_last_event";
    /** Last known: at home (true) or away (false). Only a change switches the mode. */
    private static final String KEY_AT_HOME = "home_inside";

    private Home() {}

    static boolean hasLocationPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** "Allow all the time": needed for it to work while the app isn't open. */
    static boolean hasBackgroundPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static PendingIntent pendingIntent(Context c) {
        // Mutable: the system adds the arrive / leave details to it.
        return PendingIntent.getBroadcast(c, 7,
                new Intent(c, HomeReceiver.class).setAction(HomeReceiver.ACTION_GEOFENCE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    /** Sets up (or removes) the home watch to match the settings. Safe to call any time. */
    @SuppressLint("MissingPermission")
    static void register(Context c) {
        Context app = c.getApplicationContext();
        GeofencingClient client = LocationServices.getGeofencingClient(app);
        PendingIntent pi = pendingIntent(app);
        client.removeGeofences(pi);
        if (!Prefs.homeOn(app) || !Prefs.hasHome(app)
                || !hasLocationPermission(app) || !hasBackgroundPermission(app)) {
            return;
        }
        Geofence fence = new Geofence.Builder()
                .setRequestId("home")
                .setCircularRegion(Prefs.homeLat(app), Prefs.homeLng(app), Prefs.homeRadius(app))
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER
                        | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();
        GeofencingRequest request = new GeofencingRequest.Builder()
                // Report where you are right away too (only a change from the last known
                // state switches the mode, see atHomeChanged).
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER
                        | GeofencingRequest.INITIAL_TRIGGER_EXIT)
                .addGeofence(fence)
                .build();
        try {
            client.addGeofences(request, pi)
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Home watch failed", e);
                        noteEvent(app, "Couldn't watch home (is Location turned on?)");
                    });
        } catch (SecurityException e) {
            Log.w(TAG, "No location permission", e);
        }
    }

    /** Finds where the phone is right now (for "Use where I am now"). */
    @SuppressLint("MissingPermission")
    static void currentLocation(Context c, Consumer<Location> done) {
        if (!hasLocationPermission(c)) {
            done.accept(null);
            return;
        }
        LocationServices.getFusedLocationProviderClient(c)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(done::accept)
                .addOnFailureListener(e -> done.accept(null));
    }

    /** Remembers what happened last, for the settings screen. */
    static void noteEvent(Context c, String what) {
        String when = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date());
        Prefs.get(c).edit().putString(KEY_LAST_EVENT, what + " (" + when + ")").apply();
    }

    /** Records being at home or away; true when that's a change (so the mode should switch). */
    static boolean atHomeChanged(Context c, boolean atHome) {
        String prev = Prefs.get(c).getString(KEY_AT_HOME, null);
        String now = atHome ? "home" : "away";
        Prefs.get(c).edit().putString(KEY_AT_HOME, now).apply();
        return !now.equals(prev);
    }

    /** Forget where you were (new home spot), so the next report counts as a change. */
    static void forgetState(Context c) {
        Prefs.get(c).edit().remove(KEY_AT_HOME).apply();
    }

    static String lastEvent(Context c) {
        return Prefs.get(c).getString(KEY_LAST_EVENT, null);
    }
}
