package com.popdown.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap

/**
 * Watches every notification (with "Notification access") and, for each new one, posts a silent
 * copy on an urgent channel. Android and One UI then show that copy as a real pop-down banner,
 * with Samsung's lighting effect, and when the screen is off it's sent as a "full-screen" alert,
 * which turns the screen on. Tapping the copy opens the original; the copy removes itself after a
 * few seconds so the notification list keeps only the originals.
 */
class PopListener : NotificationListenerService() {
    /** Keys already seen, so updates to an existing notification don't pop down again. */
    private val seen = HashSet<String>()

    override fun onListenerConnected() {
        instance = this
        runCatching { activeNotifications?.forEach { seen.add(it.key) } }
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        val isUpdate = !seen.add(sbn.key)
        val ranking = Ranking().takeIf { rankingMap.getRanking(sbn.key, it) }
        val reason = skipReason(sbn, ranking, isUpdate)
        if (reason != null) {
            if (!isOwnCopy(sbn)) Log.add("${label(sbn)}: skipped ($reason)")
            return
        }
        val screenOff = !getSystemService(PowerManager::class.java).isInteractive
        val s = Prefs.get(this)
        val alreadyPops = (ranking?.importance ?: 0) >= NotificationManager.IMPORTANCE_HIGH
        if (!screenOff && alreadyPops && s.skipIfAlreadyPops) {
            Log.add("${label(sbn)}: already pops down by itself")
            return
        }
        val wake = screenOff && s.wakeScreen
        val result = runCatching { postCopy(this, sbn, wake) }
        Log.add("${label(sbn)}: " + (result.exceptionOrNull()?.let { "copy failed: ${it.message}" }
            ?: "popped down${if (wake) " + woke screen" else ""}"))
        if (wake) Waker.wakeLock(this)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) {
        seen.remove(sbn.key)
        if (!isOwnCopy(sbn)) {
            // Original gone (read or dismissed): remove its copy too.
            getSystemService(NotificationManager::class.java).cancel(copyId(sbn.key))
        }
    }

    private fun isOwnCopy(sbn: StatusBarNotification) =
        sbn.packageName == packageName && sbn.notification.channelId != TEST_CHANNEL

    /** Null when this notification should pop down; otherwise why not. */
    private fun skipReason(sbn: StatusBarNotification, ranking: Ranking?, isUpdate: Boolean): String? {
        val s = Prefs.get(this)
        val n = sbn.notification
        return when {
            !s.enabled -> "Pop Down is off"
            isOwnCopy(sbn) -> "own copy"
            sbn.packageName in s.excluded -> "app excluded"
            isUpdate -> "update of an existing notification"
            sbn.isOngoing -> "ongoing (music, navigation, downloads…)"
            n.flags and Notification.FLAG_GROUP_SUMMARY != 0 -> "group summary"
            n.extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().contains("MediaStyle") -> "media"
            n.category == Notification.CATEGORY_CALL -> "call (has its own screen)"
            ranking != null && !ranking.matchesInterruptionFilter() -> "held back by Do not disturb"
            ranking != null && ranking.importance <= NotificationManager.IMPORTANCE_MIN -> "minimised by the app"
            ranking != null && ranking.importance <= NotificationManager.IMPORTANCE_LOW && !s.includeSilent -> "silent"
            else -> null
        }
    }

    private fun label(sbn: StatusBarNotification) = appName(this, sbn.packageName)

    companion object {
        const val CHANNEL = "popdown"
        const val TEST_CHANNEL = "test"

        @Volatile
        var instance: PopListener? = null
            private set

        fun copyId(key: String) = key.hashCode()

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, PopListener::class.java)
            return flat.split(":").any { ComponentName.unflattenFromString(it) == me }
        }

        fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Pop-downs", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Pop-down copies of your notifications. Silent: the original app still plays its own sound."
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(TEST_CHANNEL, "Test notifications", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Used by the Test button."
                },
            )
        }

        fun appName(context: Context, pkg: String): String = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)

        /** Builds and posts the pop-down copy of [sbn]. */
        fun postCopy(context: Context, sbn: StatusBarNotification, wake: Boolean) {
            ensureChannels(context)
            val n = sbn.notification
            val ex = n.extras
            val pm = context.packageManager
            val app = appName(context, sbn.packageName)
            val title = (ex.getCharSequence(Notification.EXTRA_TITLE_BIG) ?: ex.getCharSequence(Notification.EXTRA_TITLE))
                ?.toString()?.takeIf { it.isNotBlank() } ?: app
            val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: text

            // Icons are copied as pictures: the system can't load another app's icon resources
            // for a notification that belongs to Pop Down.
            val small = runCatching { n.smallIcon?.loadDrawable(context)?.toBitmap(96, 96) }.getOrNull()
                ?.let { Icon.createWithBitmap(it) } ?: Icon.createWithResource(context, R.drawable.ic_stat)
            val large = runCatching {
                (n.getLargeIcon()?.loadDrawable(context) ?: pm.getApplicationIcon(sbn.packageName)).toBitmap(192, 192)
            }.getOrNull()

            val open = PendingIntent.getActivity(
                context, copyId(sbn.key),
                Intent(context, OpenActivity::class.java)
                    .putExtra(OpenActivity.EXTRA_KEY, sbn.key)
                    .putExtra(OpenActivity.EXTRA_PI, n.contentIntent)
                    .putExtra(OpenActivity.EXTRA_CANCEL, n.flags and Notification.FLAG_AUTO_CANCEL != 0)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            val keep = Prefs.get(context).keepSeconds.coerceAtLeast(2)
            val b = Notification.Builder(context, CHANNEL)
                .setSmallIcon(small)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(big))
                .setSubText(app)
                .setWhen(if (n.`when` > 0) n.`when` else sbn.postTime)
                .setShowWhen(true)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setTimeoutAfter(keep * 1000L)
                .setCategory(n.category ?: Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
            large?.let { b.setLargeIcon(it) }
            if (n.color != Notification.COLOR_DEFAULT) b.setColor(n.color)
            // The original's buttons (reply, mark as read…) work straight from the pop-down.
            n.actions?.forEach { runCatching { b.addAction(it) } }
            if (wake && Waker.fullScreenAllowed(context)) {
                b.setFullScreenIntent(Waker.wakeIntent(context), true)
            }
            context.getSystemService(NotificationManager::class.java).notify(copyId(sbn.key), b.build())
        }
    }
}
