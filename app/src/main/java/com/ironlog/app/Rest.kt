package com.ironlog.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The rest timer between sets. Buzzes when it's up, even with the screen off. */
object Rest {
    private const val CHANNEL = "rest_done"
    private val _endsAt = MutableStateFlow(0L)
    private val _total = MutableStateFlow(0)
    /** When the rest ends (0 = no rest running). */
    val endsAt: StateFlow<Long> get() = _endsAt
    val total: StateFlow<Int> get() = _total

    private fun pi(c: Context) = PendingIntent.getBroadcast(
        c, 7, Intent(c, RestReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun start(c: Context, seconds: Int) {
        if (seconds <= 0) return
        _total.value = seconds
        schedule(c, System.currentTimeMillis() + seconds * 1000L)
    }

    fun add(c: Context, seconds: Int) {
        val end = _endsAt.value
        if (end == 0L) return
        val newEnd = end + seconds * 1000L
        if (newEnd <= System.currentTimeMillis()) return stop(c)
        _total.value = (_total.value + seconds).coerceAtLeast(1)
        schedule(c, newEnd)
    }

    private fun schedule(c: Context, at: Long) {
        _endsAt.value = at
        val am = c.getSystemService(AlarmManager::class.java)
        c.getSystemService(NotificationManager::class.java).cancel(ID)
        runCatching {
            if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c))
        }
    }

    fun stop(c: Context) {
        _endsAt.value = 0L
        c.getSystemService(AlarmManager::class.java).cancel(pi(c))
    }

    fun finished() { _endsAt.value = 0L }

    private const val ID = 77

    private const val ALERT_CHANNEL = "rest_alert"

    /** Silent channel: the sound and buzz are played by the app so you can choose them. */
    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        runCatching { nm.deleteNotificationChannel(CHANNEL) }
        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, "Rest timer", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tells you when your rest is over (sound and vibration are set in Iron Log)"
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private var ringtone: android.media.Ringtone? = null

    /** Plays the chosen rest-over sound and vibration. */
    fun alert(c: Context) {
        Store.load(c)
        val p = Store.profile.value
        if (p.restVibrate) runCatching {
            val v = c.getSystemService(android.os.VibratorManager::class.java).defaultVibrator
            v.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 600), -1))
        }
        if (p.restSound != "none") runCatching {
            val uri = if (p.restSound.isBlank()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            else android.net.Uri.parse(p.restSound)
            ringtone?.stop()
            ringtone = RingtoneManager.getRingtone(c, uri)?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                isLooping = false
                play()
            }
            // Long alarm sounds: stop after a few seconds.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ ringtone?.stop() }, 5_000)
        }
    }

    fun notifyDone(c: Context) {
        ensureChannel(c)
        alert(c)
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(c, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Rest over")
            .setContentText("Time for your next set 💪")
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .build()
        runCatching { c.getSystemService(NotificationManager::class.java).notify(ID, n) }
    }
}

class RestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Rest.finished()
        Rest.notifyDone(context)
    }
}
