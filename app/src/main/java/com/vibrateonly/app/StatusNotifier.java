package com.vibrateonly.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.media.AudioManager;

/** The "Vibrate Only Mode is on" notification, with a Turn off button. */
final class StatusNotifier {
    private static final String CHANNEL = "status";
    private static final int ID = 1;

    private StatusNotifier() {}

    static void update(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        boolean allowed = c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        if (!ModeController.isActive(c) || !Prefs.showNotification(c) || !allowed) {
            nm.cancel(ID);
            return;
        }
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Vibrate Only status", NotificationManager.IMPORTANCE_LOW));

        String media;
        if (!Prefs.muteMedia(c)) {
            media = "Media unchanged";
        } else if (AudioRouting.headphonesConnected(c)) {
            media = "Media on (headphones)";
        } else {
            media = "Media muted";
        }
        AudioManager am = c.getSystemService(AudioManager.class);
        int ringMax = am.getStreamMaxVolume(AudioManager.STREAM_RING);
        String calls = Prefs.callsRing(c) && Prefs.callVolume(c) > 0
                ? "Calls ring at " + Prefs.callVolume(c) + "/" + ringMax
                : "Calls vibrate";

        PendingIntent open = PendingIntent.getActivity(c, 0,
                new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent turnOff = PendingIntent.getBroadcast(c, 0,
                new Intent(c, ActionReceiver.class).setAction(ActionReceiver.ACTION_TURN_OFF),
                PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle("Vibrate Only Mode is on")
                .setContentText(calls + " · " + media)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(c, R.drawable.ic_tile), "Turn off", turnOff).build())
                .build();
        nm.notify(ID, n);
    }
}
