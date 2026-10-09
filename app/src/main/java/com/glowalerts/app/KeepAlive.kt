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
    private var shownChannel: String? = null

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
        // A switched-off channel: the service still runs, but its notification is never shown.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_HIDDEN, "Always ready (hidden)", NotificationManager.IMPORTANCE_NONE).apply {
                description = "Used when the \"ready\" notification is switched off in Glow Alerts."
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, if (s.readyNotification) CHANNEL else CHANNEL_HIDDEN)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Glow Alerts is ready")
            .setContentText("Watching for notifications")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        // Switching between shown and hidden: take the old notification down first.
        if (shownChannel != null && shownChannel != n.channelId) runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        shownChannel = n.channelId
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
        private const val CHANNEL_HIDDEN = "ready_hidden"
        private const val ID = 0x6A
        private const val WATCH_MS = 15_000L

        /**
         * Starts (or updates, or stops) "Always ready" to match the settings. Safe to call any
         * time. With the notification shown it runs as a foreground service; with it hidden the
         * same keep-awake and reconnect checks run inside the app's accessibility service, which
         * Android keeps running anyway, so there's no notification at all.
         */
        fun start(context: Context) {
            val app = context.applicationContext
            val intent = Intent(app, KeepAlive::class.java)
            val s = Prefs.get(app)
            if (s.alwaysReady && s.readyNotification) {
                Background.stop()
                runCatching { app.startForegroundService(intent) }
            } else {
                app.stopService(intent)
                if (s.alwaysReady) Background.start(app) else Background.stop()
            }
        }
    }

    /** "Always ready" without a notification: wake lock and reconnect checks, no service. */
    private object Background {
        private val main = Handler(Looper.getMainLooper())
        private var wakeLock: PowerManager.WakeLock? = null
        private var app: Context? = null

        private val watchdog = object : Runnable {
            override fun run() {
                val c = app ?: return
                if (GlowListener.isEnabled(c) && GlowListener.instance == null) {
                    runCatching {
                        NotificationListenerService.requestRebind(ComponentName(c, GlowListener::class.java))
                    }
                }
                main.postDelayed(this, WATCH_MS)
            }
        }

        fun start(context: Context) {
            app = context
            if (Prefs.get(context).keepAwake) {
                if (wakeLock == null) {
                    wakeLock = context.getSystemService(PowerManager::class.java)
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "glowalerts:ready")
                        .apply { setReferenceCounted(false); acquire() }
                }
            } else {
                wakeLock?.let { if (it.isHeld) it.release() }
                wakeLock = null
            }
            main.removeCallbacks(watchdog)
            main.post(watchdog)
        }

        fun stop() {
            main.removeCallbacks(watchdog)
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        }
    }
}

/** Starts "Always ready" again after the phone restarts or the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        KeepAlive.start(context)
    }
}
