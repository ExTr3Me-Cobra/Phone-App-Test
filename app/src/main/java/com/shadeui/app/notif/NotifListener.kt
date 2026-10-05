package com.shadeui.app.notif

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.shadeui.app.ShadeApp
import com.shadeui.app.overlay.AlertController

/**
 * Receives every notification on the phone (with the user's "Notification access" permission),
 * keeps [NotifRepo] up to date and hands new ones to [AlertController].
 */
class NotifListener : NotificationListenerService() {
    private val repo get() = ShadeApp.instance.notifs

    override fun onListenerConnected() {
        instance = this
        reloadAll()
        ShadeApp.instance.media.connect(this, ComponentName(this, NotifListener::class.java))
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        ShadeApp.instance.media.disconnect()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        if (sbn.packageName == packageName) return
        val ranking = Ranking().takeIf { rankingMap.getRanking(sbn.key, it) }
        val isUpdate = repo.value.any { it.key == sbn.key }
        val item = runCatching { NotifItem.from(this, sbn, ranking) }.getOrNull() ?: return
        repo.upsert(item, rankingMap.orderedKeys.toList())
        AlertController.onPosted(this, item, isUpdate)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) {
        repo.remove(sbn.key)
        AlertController.onRemoved(sbn.key)
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) {
        val importance = HashMap<String, Int>()
        val r = Ranking()
        for (key in rankingMap.orderedKeys) {
            if (rankingMap.getRanking(key, r)) importance[key] = r.importance
        }
        repo.reorder(rankingMap.orderedKeys.toList(), importance)
    }

    private fun reloadAll() {
        val map = currentRanking
        val items = runCatching { activeNotifications }.getOrNull().orEmpty()
            .filter { it.packageName != packageName }
            .mapNotNull { sbn ->
                val r = Ranking().takeIf { map.getRanking(sbn.key, it) }
                runCatching { NotifItem.from(this, sbn, r) }.getOrNull()
            }
        val rank = map.orderedKeys.withIndex().associate { it.value to it.index }
        repo.replaceAll(items.sortedBy { rank[it.key] ?: Int.MAX_VALUE })
    }

    fun dismiss(key: String) = runCatching { cancelNotification(key) }
    fun dismissAll() = runCatching { cancelAllNotifications() }
    fun snooze(key: String, millis: Long) = runCatching { snoozeNotification(key, millis) }

    companion object {
        @Volatile
        var instance: NotifListener? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?: return false
            val me = ComponentName(context, NotifListener::class.java)
            return flat.split(":").any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
