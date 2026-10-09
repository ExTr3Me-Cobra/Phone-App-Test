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

/** A notification a few minutes before clock-out time, and one at clock-out time. */
object Reminder {
    private const val CHANNEL = "clock_out"
    private const val EXTRA_EARLY = "early"

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
        if (!s.remind) return
        val out = Calc.compute(Store.rings.value, s, LocalDateTime.now())?.clockOut ?: return
        val now = LocalDateTime.now()
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
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Clock out reminders", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    fun show(c: Context, early: Boolean) {
        ensureChannel(c)
        val s = Store.settings.value
        val out = Calc.compute(Store.rings.value, s, LocalDateTime.now())?.clockOut ?: return
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (early) "Clock out in ${s.remindBefore} min" else "Time to clock out")
            .setContentText("${Fmt.dur(java.time.Duration.ofSeconds(s.targetSeconds))} reached at ${Fmt.time(out)}")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { c.getSystemService(NotificationManager::class.java).notify(if (early) 1 else 2, n) }
    }

    fun isEarly(intent: Intent) = intent.getBooleanExtra(EXTRA_EARLY, false)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.load(context)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Reminder.update(context)
        } else {
            Reminder.show(context, Reminder.isEarly(intent))
        }
    }
}
