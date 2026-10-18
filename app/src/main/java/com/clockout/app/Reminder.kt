package com.clockout.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime

/**
 * A notification a few minutes before clock-out time, and one at clock-out time. While a clock-out time is set (with
 * reminders on), an always-there notification shows "Clock out at …" in big letters; swiping it away just puts it back.
 */
object Reminder {
    private const val CHANNEL = "clock_out"
    private const val CHANNEL_TIME = "clock_out_time"
    private const val EXTRA_EARLY = "early"
    private const val ACTION_REPOST = "com.clockout.app.REPOST"
    private const val PINNED = 3

    private fun pi(c: Context, early: Boolean) = PendingIntent.getBroadcast(
        c, if (early) 1 else 2,
        Intent(c, ReminderReceiver::class.java).putExtra(EXTRA_EARLY, early),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Re-plans the reminders for the current rings and settings. */
    fun update(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        am.cancel(pi(c, true))
        am.cancel(pi(c, false))
        val s = Store.settings.value
        val now = LocalDateTime.now()
        val out = if (s.remind) Calc.compute(Store.rings.value, s, now)?.clockOut else null
        if (out == null || out <= now) { unpin(c); return }
        pin(c)
        fun at(t: LocalDateTime, early: Boolean) {
            if (t <= now) return
            val ms = Fmt.epochMillis(t)
            runCatching {
                if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi(c, early))
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi(c, early))
            }
        }
        if (s.remindBefore > 0) at(out.minusMinutes(s.remindBefore.toLong()), true)
        at(out, false)
    }

    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Clock out reminders", NotificationManager.IMPORTANCE_HIGH))
        // Quiet: it just sits there.
        nm.createNotificationChannel(NotificationChannel(CHANNEL_TIME, "Clock-out time", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        })
    }

    /** The always-there "Clock out at …" notification (re-posted if you swipe it away). */
    fun pin(c: Context) {
        val s = Store.settings.value
        val out = if (s.remind) Calc.compute(Store.rings.value, s, LocalDateTime.now())?.clockOut else null
        if (out == null || out <= LocalDateTime.now()) { unpin(c); return }
        ensureChannel(c)
        val text = "Clock out at ${Fmt.show(out, s)}"
        val view = android.widget.RemoteViews(c.packageName, R.layout.notif_clock_out).apply { setTextViewText(R.id.clock_out_text, text) }
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val repost = PendingIntent.getBroadcast(c, 3, Intent(c, ReminderReceiver::class.java).setAction(ACTION_REPOST),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(c, CHANNEL_TIME)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(text)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(view)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setDeleteIntent(repost)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        n.flags = n.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
        runCatching { c.getSystemService(NotificationManager::class.java).notify(PINNED, n) }
    }

    fun unpin(c: Context) { runCatching { c.getSystemService(NotificationManager::class.java).cancel(PINNED) } }

    fun isRepost(intent: Intent) = intent.action == ACTION_REPOST

    fun show(c: Context, early: Boolean) {
        ensureChannel(c)
        val s = Store.settings.value
        val out = Calc.compute(Store.rings.value, s, LocalDateTime.now())?.clockOut ?: return
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (early) "Clock out in ${s.remindBefore} min" else "Time to clock out")
            .setContentText("${Fmt.dur(java.time.Duration.ofSeconds(s.targetSeconds))} reached at ${Fmt.show(out, s)}")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { c.getSystemService(NotificationManager::class.java).notify(if (early) 1 else 2, n) }
        // At clock-out time the always-there one has done its job.
        if (!early) unpin(c)
    }

    fun isEarly(intent: Intent) = intent.getBooleanExtra(EXTRA_EARLY, false)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.load(context)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Reminder.update(context)
        } else if (Reminder.isRepost(intent)) {
            Reminder.pin(context)
        } else {
            Reminder.show(context, Reminder.isEarly(intent))
        }
    }
}
