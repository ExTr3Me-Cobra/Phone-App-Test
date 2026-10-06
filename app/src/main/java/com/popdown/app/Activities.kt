package com.popdown.app

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.app.AppOpsManager
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
import android.os.Process
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tapping a pop-down copy: opens the original notification, then disappears. */
class OpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pi = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_PI, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_PI)
        }
        val key = intent.getStringExtra(EXTRA_KEY)
        if (pi != null) {
            val options = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= 34) {
                @Suppress("DEPRECATION")
                options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }
            runCatching { pi.send(this, 0, null, null, null, null, options.toBundle()) }
        }
        if (key != null && intent.getBooleanExtra(EXTRA_CANCEL, false)) {
            runCatching { PopListener.instance?.cancelNotification(key) }
        }
        finish()
    }

    companion object {
        const val EXTRA_PI = "pi"
        const val EXTRA_KEY = "key"
        const val EXTRA_CANCEL = "cancel"
    }
}

/** Shown for a moment by the full-screen alert: its only job is turning the screen on. */
class WakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        Waker.started = true
        getSystemService(NotificationManager::class.java).cancel(Waker.WAKE_ID)
        // Stay a moment so One UI has really switched the screen on, then leave: the screen stays
        // on (lock screen with the notification) for the normal screen timeout.
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 2_500)
    }
}

/**
 * Turns the screen on. Three routes, because each Android/One UI version blocks a different one:
 * a "wake" wake lock, and a separate alarm-style full-screen alert that opens [WakeActivity].
 * Afterwards it checks whether the screen really came on and logs why if it didn't.
 */
object Waker {
    const val WAKE_CHANNEL = "wake"
    const val WAKE_ID = 0x5747

    @Volatile
    var started = false

    fun fullScreenAllowed(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        val notify = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            nm.areNotificationsEnabled()
        return notify && (Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent())
    }

    /** Android's "Turn screen on" permission, without which wake locks can't switch the screen on. */
    fun turnScreenOnAllowed(context: Context): Boolean = runCatching {
        context.getSystemService(AppOpsManager::class.java).unsafeCheckOpNoThrow(
            "android:turn_screen_on", Process.myUid(), context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    private fun isOn(context: Context) = context.getSystemService(PowerManager::class.java).isInteractive

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(WAKE_CHANNEL, "Screen wake", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Briefly used to turn the screen on. Removes itself straight away."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            },
        )
    }

    fun wakeIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 1,
        Intent(context, WakeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Turns the screen on now; [label] is used in the activity log. */
    fun wake(context: Context, label: String) {
        val app = context.applicationContext
        started = false
        wakeLock(app)
        val fsi = fullScreenAllowed(app)
        if (fsi) runCatching { postWakeAlert(app) }
        val main = Handler(Looper.getMainLooper())
        main.postDelayed({
            if (isOn(app)) {
                Log.add("$label: screen turned on ✓")
            } else {
                Log.add(
                    "$label: screen stayed OFF ✗ (full screen alerts: ${if (fsi) "allowed" else "NOT allowed"}, " +
                        "wake screen permission: ${if (turnScreenOnAllowed(app)) "allowed" else "not allowed"}, " +
                        "wake activity ${if (started) "opened" else "never opened"})",
                )
            }
        }, 1_800)
        // Tidy up if One UI showed the alert as a normal notification instead.
        main.postDelayed({ app.getSystemService(NotificationManager::class.java).cancel(WAKE_ID) }, 4_000)
    }

    private fun postWakeAlert(context: Context) {
        ensureChannel(context)
        val n = Notification.Builder(context, WAKE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Pop Down")
            .setContentText("Turning the screen on")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(4_000)
            .setFullScreenIntent(wakeIntent(context), true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(WAKE_ID, n)
    }

    /** Works when Android's "Turn screen on" permission is allowed. */
    @Suppress("DEPRECATION")
    fun wakeLock(context: Context) {
        runCatching {
            context.getSystemService(PowerManager::class.java).newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "popdown:wake",
            ).acquire(3_000)
        }
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
