package com.glowalerts.app

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shown for a moment by the full-screen alert: its only job is turning the screen on. */
class WakeActivity : Activity() {
    companion object {
        /** Set when the wake screen opens, for the log. */
        @Volatile
        var opened = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        opened = true
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        getSystemService(NotificationManager::class.java).cancel(Waker.WAKE_ID)
        // Leave as soon as the screen is on, so the lock screen shows under the lighting.
        val main = Handler(Looper.getMainLooper())
        val power = getSystemService(PowerManager::class.java)
        val started = System.currentTimeMillis()
        fun check() {
            val waited = System.currentTimeMillis() - started
            if ((power.isInteractive && waited >= 150) || waited >= 2_500) finish() else main.postDelayed(::check, 50)
        }
        main.postDelayed(::check, 50)
    }
}

/** Turns the screen on the way alarm apps do (a short full-screen alert), plus a wake lock. */
object Waker {
    const val WAKE_CHANNEL = "wake"
    const val WAKE_ID = 0x5747

    fun fullScreenAllowed(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            nm.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent())
    }

    /** Android's "Turn screen on" permission (needed for the wake lock route). */
    fun turnScreenOnAllowed(context: Context): Boolean = runCatching {
        context.getSystemService(android.app.AppOpsManager::class.java).unsafeCheckOpNoThrow(
            "android:turn_screen_on", android.os.Process.myUid(), context.packageName,
        ) == android.app.AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** Turns the screen on, then checks it worked and logs why if it didn't. */
    fun wake(context: Context, label: String = "Wake") {
        val app = context.applicationContext
        WakeActivity.opened = false
        val fsi = fullScreenAllowed(app)
        Handler(Looper.getMainLooper()).postDelayed({
            val on = app.getSystemService(PowerManager::class.java).isInteractive
            if (on) {
                Log.add("$label: screen turned on ✓")
            } else {
                Log.add(
                    "$label: screen stayed OFF ✗ (wake screen ${if (WakeActivity.opened) "opened" else "never opened"}, " +
                        "full screen alerts: ${if (fsi) "allowed" else "NOT allowed – allow notifications + full screen in Setup"}, " +
                        "turn screen on: ${if (turnScreenOnAllowed(app)) "allowed" else "not allowed"})",
                )
            }
        }, 1_800)
        // Route 1: open the invisible wake screen directly. Apps with a running accessibility
        // service are allowed to open screens from the background, and the wake screen turns
        // the display on as it opens. Doesn't need notifications to be allowed.
        val direct = runCatching {
            app.startActivity(
                Intent(app, WakeActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_NO_USER_ACTION,
                ),
            )
        }.isSuccess
        if (!direct) Log.add("$label: couldn't open the wake screen directly")
        // Route 2: a wake lock that turns the screen on (needs "turn screen on").
        @Suppress("DEPRECATION")
        runCatching {
            app.getSystemService(PowerManager::class.java).newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "glowalerts:wake",
            ).acquire(3_000)
        }
        // Route 3: an alarm-style full-screen alert (needs notifications + full screen allowed).
        if (!fullScreenAllowed(app)) return
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(WAKE_CHANNEL, "Screen wake", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Briefly used to turn the screen on. Removes itself straight away."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            },
        )
        val pi = PendingIntent.getActivity(
            app, 1,
            Intent(app, WakeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        runCatching {
            nm.notify(
                WAKE_ID,
                Notification.Builder(app, WAKE_CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle("Glow Alerts")
                    .setContentText("Turning the screen on")
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setVisibility(Notification.VISIBILITY_SECRET)
                    .setTimeoutAfter(4_000)
                    .setFullScreenIntent(pi, true)
                    .build(),
            )
        }
        Handler(Looper.getMainLooper()).postDelayed({ nm.cancel(WAKE_ID) }, 4_000)
    }
}

/** Recent decisions, shown in the app so you can see what happened to each notification. */
object Log {
    val lines = mutableStateListOf<String>()
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun add(line: String) {
        Handler(Looper.getMainLooper()).post {
            lines.add(0, "${time.format(Date())}  $line")
            while (lines.size > 30) lines.removeAt(lines.lastIndex)
        }
    }
}
