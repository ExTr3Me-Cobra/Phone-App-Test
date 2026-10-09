package com.datetile.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId

/** The 1x1 home screen widget. Redrawn just after midnight, and whenever settings change. */
class DateWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { draw(context, manager, it) }
        scheduleMidnight(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        draw(context, manager, id)
    }

    override fun onEnabled(context: Context) = scheduleMidnight(context)

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(midnightIntent(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MIDNIGHT, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            -> updateAll(context)
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        private const val ACTION_MIDNIGHT = "com.datetile.app.MIDNIGHT"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, DateWidget::class.java)).forEach { draw(context, manager, it) }
            scheduleMidnight(context)
        }

        fun count(context: Context): Int =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, DateWidget::class.java)).size

        /** The widget's size on the home screen, in pixels (square). */
        private fun sizePx(context: Context, manager: AppWidgetManager, id: Int): Int {
            val o = manager.getAppWidgetOptions(id)
            val density = context.resources.displayMetrics.density
            val portrait = context.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val w = o.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val h = o.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            val dp = listOf(w, h).filter { it > 0 }.minOrNull() ?: 72
            // Drawn at twice the size for crisp text, within sensible limits.
            return (dp * density * 2f).toInt().coerceIn(160, 720)
        }

        private fun draw(context: Context, manager: AppWidgetManager, id: Int) {
            val s = Prefs.get(context)
            val views = RemoteViews(context.packageName, R.layout.widget)
            val bmp = Renderer.render(context, s, LocalDate.now(), sizePx(context, manager, id))
            views.setImageViewBitmap(R.id.tile, bmp)
            views.setContentDescription(R.id.tile, LocalDate.now().toString())
            tapIntent(context, s)?.let { views.setOnClickPendingIntent(R.id.tile, it) }
            runCatching { manager.updateAppWidget(id, views) }
        }

        private fun tapIntent(context: Context, s: Style): PendingIntent? {
            val intent = when (s.tap) {
                Tap.NOTHING -> return null
                Tap.APP -> Intent(context, MainActivity::class.java)
                Tap.CALENDAR -> googleCalendar(context) ?: phoneCalendar(context)
                Tap.PHONE_CALENDAR -> phoneCalendar(context)
            }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private const val GOOGLE_CALENDAR = "com.google.android.calendar"

        /** Google Calendar, opened on today. Null if it isn't installed. */
        fun googleCalendar(context: Context): Intent? {
            val pm = context.packageManager
            val today = Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}"))
                .setPackage(GOOGLE_CALENDAR)
            if (today.resolveActivity(pm) != null) return today
            return pm.getLaunchIntentForPackage(GOOGLE_CALENDAR)
        }

        /** Whatever calendar app the phone uses by default (Samsung Calendar on most Galaxies). */
        private fun phoneCalendar(context: Context): Intent {
            val cal = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
            return if (cal.resolveActivity(context.packageManager) != null) cal
            else Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}"))
        }

        private fun midnightIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, DateWidget::class.java).setAction(ACTION_MIDNIGHT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Wakes up a moment after midnight to show the new day. */
        private fun scheduleMidnight(context: Context) {
            if (count(context) == 0) return
            val zone = ZoneId.systemDefault()
            val at = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 2_000
            val am = context.getSystemService(AlarmManager::class.java)
            val pi = midnightIntent(context)
            runCatching {
                if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }
}
