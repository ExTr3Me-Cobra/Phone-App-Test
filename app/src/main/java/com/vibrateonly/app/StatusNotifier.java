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

        String text = "Calls, texts and notifications vibrate · "
                + (Prefs.muteMedia(c) ? "media muted · " : "")
                + "alarms ring";

        PendingIntent open = PendingIntent.getActivity(c, 0,
                new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent turnOff = PendingIntent.getBroadcast(c, 0,
                new Intent(c, ActionReceiver.class).setAction(ActionReceiver.ACTION_TURN_OFF),
                PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle("Vibrate Only Mode is on")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(c, R.drawable.ic_tile), "Turn off", turnOff).build())
                .build();
        nm.notify(ID, n);
    }
}
