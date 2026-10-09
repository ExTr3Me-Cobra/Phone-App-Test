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
    /**
     * What each notification last said. Messaging apps put each new message into the same
     * notification, so a changed notification that's allowed to alert again counts as new.
     */
    private val seen = HashMap<String, String>()

    override fun onListenerConnected() {
        instance = this
        runCatching { activeNotifications?.forEach { seen[it.key] = signature(it) } }
        KeepAlive.start(this)
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        // Ask Android to connect us again straight away.
        runCatching { requestRebind(ComponentName(this, GlowListener::class.java)) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) {
        seen.remove(sbn.key)
    }

    /** Title + text + time: changes when an app adds a new message to an existing notification. */
    private fun signature(sbn: StatusBarNotification): String {
        val ex = sbn.notification.extras
        return "${ex.getCharSequence(Notification.EXTRA_TITLE)}|${ex.getCharSequence(Notification.EXTRA_TEXT)}|" +
            "${ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.lastOrNull()}|" +
            "${(ex.get(Notification.EXTRA_MESSAGES) as? Array<*>)?.size}|${sbn.notification.`when`}"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        val sig = signature(sbn)
        val previous = seen.put(sbn.key, sig)
        // An update only counts when its content changed and the app lets it alert again.
        val isUpdate = previous != null && (previous == sig ||
            sbn.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        val ranking = Ranking().takeIf { rankingMap.getRanking(sbn.key, it) }
        val name = appName(this, sbn.packageName)
        val reason = skipReason(sbn, ranking, isUpdate)
        if (reason != null) {
            if (reason != OWN) Log.add("$name: skipped ($reason)")
            return
        }
        val color = LightService.appColor(this, sbn.notification.color, sbn.packageName)
        val late = System.currentTimeMillis() - sbn.postTime
        light(this, if (late > 1500) "$name (arrived ${late / 1000.0}s late)" else name, color)
    }

    /** Null when this notification should light up; otherwise why not. */
    private fun skipReason(sbn: StatusBarNotification, ranking: Ranking?, isUpdate: Boolean): String? {
        val s = Prefs.get(this)
        val n = sbn.notification
        return when {
            !s.enabled -> "Glow Alerts is off"
            sbn.packageName == packageName && n.channelId != TEST_CHANNEL -> OWN
            sbn.packageName in s.excluded -> "app switched off"
            isUpdate -> "same notification again (no new content)"
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
                    if (s.wakeScreen && s.wakeFromAod) {
                        // Samsung may not show other apps' drawing on the AOD: wake to the lock
                        // screen so the lighting is sure to be seen.
                        Waker.wake(context, name)
                        where = "Always On Display – waking to the lock screen"
                    } else {
                        where = "Always On Display"
                    }
                }
                !visible -> {
                    when {
                        s.wakeScreen && (s.onLocked || s.onAod) -> {
                            Waker.wake(context, name)
                            where = "screen off – waking it"
                        }
                        LightService.aodEnabled(context) && s.onAod -> where = "screen off – waiting for the Always On Display (Wake the screen is off)"
                        s.onLocked -> where = "screen off – plays when you turn the screen on (Wake the screen is off)"
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
