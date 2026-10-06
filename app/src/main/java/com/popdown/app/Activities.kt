package com.popdown.app

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
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
        // Leave straight away: the screen stays on (lock screen with the notification) for the
        // normal screen timeout.
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1_200)
    }
}

/** Ways of turning the screen on. */
object Waker {
    fun fullScreenAllowed(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        val notify = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            nm.areNotificationsEnabled()
        return notify && (Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent())
    }

    fun wakeIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 1,
        Intent(context, WakeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Extra nudge where Android still allows it. */
    @Suppress("DEPRECATION")
    fun wakeLock(context: Context) {
        runCatching {
            context.getSystemService(PowerManager::class.java).newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "popdown:wake",
            ).acquire(1_500)
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
