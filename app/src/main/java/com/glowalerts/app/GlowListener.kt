package com.glowalerts.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings as AndroidSettings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Sees every new notification (with "Notification access") and plays the lighting for it:
 * while you use the phone, on the lock screen, and on the Always On Display.
 */
class GlowListener : NotificationListenerService() {
    /** Keys already seen, so updates to an existing notification don't light up again. */
    private val seen = HashSet<String>()

    override fun onListenerConnected() {
        instance = this
        runCatching { activeNotifications?.forEach { seen.add(it.key) } }
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) {
        seen.remove(sbn.key)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        val isUpdate = !seen.add(sbn.key)
        val ranking = Ranking().takeIf { rankingMap.getRanking(sbn.key, it) }
        val name = appName(this, sbn.packageName)
        val reason = skipReason(sbn, ranking, isUpdate)
        if (reason != null) {
            if (reason != OWN) Log.add("$name: skipped ($reason)")
            return
        }
        val color = LightService.appColor(this, sbn.notification.color, sbn.packageName)
        light(this, name, color)
    }

    /** Null when this notification should light up; otherwise why not. */
    private fun skipReason(sbn: StatusBarNotification, ranking: Ranking?, isUpdate: Boolean): String? {
        val s = Prefs.get(this)
        val n = sbn.notification
        return when {
            !s.enabled -> "Glow Alerts is off"
            sbn.packageName == packageName && n.channelId != TEST_CHANNEL -> OWN
            sbn.packageName in s.excluded -> "app switched off"
            isUpdate -> "update of an existing notification"
            sbn.isOngoing -> "ongoing (music, navigation, downloads…)"
            n.flags and Notification.FLAG_GROUP_SUMMARY != 0 -> "group summary"
            n.extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().contains("MediaStyle") -> "media"
            ranking != null && !ranking.matchesInterruptionFilter() -> "held back by Do not disturb"
            ranking != null && ranking.importance <= NotificationManager.IMPORTANCE_MIN -> "minimised by the app"
            ranking != null && ranking.importance <= NotificationManager.IMPORTANCE_LOW && !s.includeSilent -> "silent"
            else -> null
        }
    }

    companion object {
        const val TEST_CHANNEL = "test"
        private const val OWN = "own"

        @Volatile
        var instance: GlowListener? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val flat = AndroidSettings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, GlowListener::class.java)
            return flat.split(":").any { ComponentName.unflattenFromString(it) == me }
        }

        fun ensureChannels(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(TEST_CHANNEL, "Test notifications", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Used by the Test button."
                },
            )
        }

        fun appName(context: Context, pkg: String): String = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)

        /** Decides by what's on screen right now, then plays (and optionally wakes the screen). */
        fun light(context: Context, name: String, color: Int?) {
            val s = Prefs.get(context)
            val service = LightService.instance
            if (service == null) {
                Log.add("$name: no lighting – switch on \"Glow Alerts Lighting\" in Accessibility")
                return
            }
            val visible = LightService.screenVisible(context)
            val aod = LightService.onAod(context)
            val locked = LightService.isLocked(context)
            val where: String
            when {
                aod -> {
                    if (!s.onAod) return Log.add("$name: Always On Display lighting is off")
                    where = "Always On Display"
                }
                !visible -> {
                    // Screen fully off: wake it if asked; otherwise wait for the AOD (which One UI
                    // shows for new notifications when it's switched on) or for you to turn it on.
                    when {
                        s.wakeScreen && s.onLocked -> {
                            Waker.wake(context)
                            where = "screen off – waking it"
                        }
                        LightService.aodEnabled(context) && s.onAod -> where = "screen off – waiting for the Always On Display"
                        s.onLocked -> where = "screen off – plays when the screen turns on"
                        else -> return Log.add("$name: lock screen lighting is off")
                    }
                }
                locked -> {
                    if (!s.onLocked) return Log.add("$name: lock screen lighting is off")
                    where = "lock screen"
                }
                else -> {
                    if (!s.onUnlocked) return Log.add("$name: lighting while unlocked is off")
                    where = "in use"
                }
            }
            service.play(LightService.spec(context, s, color))
            Log.add("$name: ${s.effect.label} ($where)")
        }
    }
}
