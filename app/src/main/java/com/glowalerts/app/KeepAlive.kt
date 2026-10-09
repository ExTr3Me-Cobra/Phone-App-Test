package com.glowalerts.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.service.notification.NotificationListenerService

/**
 * "Always ready": a foreground service (with a small silent notification) so Android and
 * Samsung's battery saver never put Glow Alerts to sleep or delay it. Optionally keeps the
 * processor awake too, and checks every few seconds that the notification connection and the
 * drawing window are alive, reconnecting them if not. Uses more battery, by design.
 */
class KeepAlive : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    private val watchdog = object : Runnable {
        override fun run() {
            if (GlowListener.isEnabled(this@KeepAlive) && GlowListener.instance == null) {
                runCatching {
                    NotificationListenerService.requestRebind(ComponentName(this@KeepAlive, GlowListener::class.java))
                }
            }
            main.postDelayed(this, WATCH_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val s = Prefs.get(this)
        if (!s.alwaysReady) {
            stopSelf()
            return START_NOT_STICKY
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Always ready", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps Glow Alerts running so it lights up instantly. You can hide this channel."
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Glow Alerts is ready")
            .setContentText("Watching for notifications")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        runCatching { startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) }
            .onFailure { Log.add("Always ready: couldn't start (${it.message})") }

        if (s.keepAwake && wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "glowalerts:ready")
                .apply { setReferenceCounted(false); acquire() }
        } else if (!s.keepAwake) {
            releaseWakeLock()
        }
        main.removeCallbacks(watchdog)
        main.post(watchdog)
        return START_STICKY
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        main.removeCallbacks(watchdog)
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "ready"
        private const val ID = 0x6A
        private const val WATCH_MS = 15_000L

        /** Starts (or updates, or stops) the service to match the settings. Safe to call any time. */
        fun start(context: Context) {
            val app = context.applicationContext
            val intent = Intent(app, KeepAlive::class.java)
            if (Prefs.get(app).alwaysReady) {
                runCatching { app.startForegroundService(intent) }
            } else {
                app.stopService(intent)
            }
        }
    }
}

/** Starts "Always ready" again after the phone restarts or the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        KeepAlive.start(context)
    }
}
