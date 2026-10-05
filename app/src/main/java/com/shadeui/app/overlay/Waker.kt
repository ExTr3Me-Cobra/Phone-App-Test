package com.shadeui.app.overlay

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

/**
 * Turns the screen on for a new notification. Android 14+ restricts this, so three methods are
 * tried in turn, and each attempt is recorded in [Diagnostics]:
 * 1. a wake lock that switches the screen on (works where Android still allows it);
 * 2. [WakeActivity], an invisible screen that asks to turn the display on;
 * 3. if the screen is still off, a "full-screen notification" (the way alarm and calling apps
 *    wake the phone), which needs the user's one-time "Full screen notifications" permission.
 */
object Waker {
    private const val CHANNEL = "wake"
    private const val NOTIFICATION_ID = 7001
    private val handler = Handler(Looper.getMainLooper())

    fun wake(context: Context, seconds: Int) {
        val app = context.applicationContext
        val power = app.getSystemService(PowerManager::class.java)

        @Suppress("DEPRECATION")
        val lockResult = runCatching {
            power.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "shade:wake",
            ).acquire(2_000)
        }
        val activityResult = runCatching { WakeActivity.start(app, seconds) }

        handler.postDelayed({
            if (power.isInteractive) {
                Diagnostics.add("wake: screen on (wake lock ${ok(lockResult)}, wake screen ${ok(activityResult)})")
                return@postDelayed
            }
            val fsi = fullScreenNotification(app, seconds)
            Diagnostics.add(
                "wake: screen still off after wake lock (${ok(lockResult)}) and wake screen (${ok(activityResult)}); " +
                    "full-screen notification: $fsi",
            )
        }, 400)
    }

    /** True when the alarm-style fallback is allowed. */
    fun fullScreenAllowed(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        val notifyOk = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            nm.areNotificationsEnabled()
        val fsiOk = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()
        return notifyOk && fsiOk
    }

    private fun fullScreenNotification(context: Context, seconds: Int): String {
        if (!fullScreenAllowed(context)) return "not allowed yet (see Shade's setup step 6)"
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Screen wake for edge lighting", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                description = "Used only to turn the screen on for edge lighting. Disappears immediately."
            },
        )
        val intent = Intent(context, WakeActivity::class.java)
            .putExtra(WakeActivity.EXTRA_SECONDS, seconds)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        val pi = PendingIntent.getActivity(
            context, 70, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Shade")
            .setContentText("Waking the screen")
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)
            .setTimeoutAfter(4_000)
            .build()
        return runCatching {
            nm.notify(NOTIFICATION_ID, n)
            // Its only job is to wake the screen; remove it right after.
            handler.postDelayed({ nm.cancel(NOTIFICATION_ID) }, 1_500)
            "sent"
        }.getOrElse { "failed: ${it.message}" }
    }

    private fun ok(r: Result<*>) = if (r.isSuccess) "ok" else "failed: ${r.exceptionOrNull()?.javaClass?.simpleName}"
}
