package com.shadeui.app.notif

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class NotifAction(
    val title: String,
    val intent: PendingIntent?,
    val remoteInputs: List<RemoteInput>,
) {
    val isReply get() = remoteInputs.isNotEmpty()
}

data class Message(val sender: String?, val text: String)

/** A notification, flattened into what the UI needs. */
data class NotifItem(
    val key: String,
    val pkg: String,
    val appName: String,
    val appIcon: ImageBitmap?,
    val smallIcon: ImageBitmap?,
    val accentColor: Int,
    val title: String,
    val text: String,
    val bigText: String?,
    val subText: String?,
    val messages: List<Message>,
    val largeIcon: ImageBitmap?,
    val picture: ImageBitmap?,
    val progress: Int,
    val progressMax: Int,
    val progressIndeterminate: Boolean,
    val actions: List<NotifAction>,
    val contentIntent: PendingIntent?,
    val autoCancel: Boolean,
    val time: Long,
    val showTime: Boolean,
    val ongoing: Boolean,
    val clearable: Boolean,
    val isGroupSummary: Boolean,
    val groupKey: String,
    val isMedia: Boolean,
    val importance: Int,
    val onlyAlertOnce: Boolean,
    val matchesFilter: Boolean,
) {
    val isSilent get() = importance in 1..android.app.NotificationManager.IMPORTANCE_LOW
    val hasProgress get() = progressMax > 0 || progressIndeterminate

    companion object {
        private val appCache = HashMap<String, Pair<String, ImageBitmap?>>()

        fun appInfo(context: Context, pkg: String): Pair<String, ImageBitmap?> =
            appCache.getOrPut(pkg) {
                val pm = context.packageManager
                runCatching {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(ai).toString() to
                        pm.getApplicationIcon(ai).toBitmap(144, 144).asImageBitmap()
                }.getOrElse { pkg to null }
            }

        fun from(
            context: Context,
            sbn: StatusBarNotification,
            ranking: NotificationListenerService.Ranking?,
        ): NotifItem {
            val n = sbn.notification
            val ex = n.extras
            val (appName, appIcon) = appInfo(context, sbn.packageName)
            val template = ex.getString(Notification.EXTRA_TEMPLATE).orEmpty()
            val title = (ex.getCharSequence(Notification.EXTRA_TITLE_BIG)
                ?: ex.getCharSequence(Notification.EXTRA_TITLE))?.toString().orEmpty()
            val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            return NotifItem(
                key = sbn.key,
                pkg = sbn.packageName,
                appName = appName,
                appIcon = appIcon,
                smallIcon = n.smallIcon?.let { loadIcon(context, it, 64) },
                accentColor = if (n.color != Notification.COLOR_DEFAULT && n.color != 0) n.color else Color.TRANSPARENT,
                title = title,
                text = text,
                bigText = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it != text },
                subText = (ex.getCharSequence(Notification.EXTRA_SUB_TEXT)
                    ?: ex.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))?.toString(),
                messages = messages(ex),
                largeIcon = n.getLargeIcon()?.let { loadIcon(context, it, 160) },
                picture = picture(context, ex),
                progress = ex.getInt(Notification.EXTRA_PROGRESS, 0),
                progressMax = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0),
                progressIndeterminate = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false),
                actions = n.actions.orEmpty().map { a ->
                    NotifAction(a.title?.toString().orEmpty(), a.actionIntent, a.remoteInputs.orEmpty().toList())
                },
                contentIntent = n.contentIntent,
                autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
                time = if (n.`when` > 0) n.`when` else sbn.postTime,
                showTime = ex.getBoolean(Notification.EXTRA_SHOW_WHEN, true),
                ongoing = sbn.isOngoing,
                clearable = sbn.isClearable,
                isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                groupKey = sbn.groupKey,
                isMedia = template.contains("MediaStyle"),
                importance = ranking?.importance ?: android.app.NotificationManager.IMPORTANCE_DEFAULT,
                onlyAlertOnce = n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0,
                matchesFilter = ranking?.matchesInterruptionFilter() ?: true,
            )
        }

        fun loadIcon(context: Context, icon: Icon, size: Int): ImageBitmap? = runCatching {
            icon.loadDrawable(context)?.toBitmap(size, size)?.asImageBitmap()
        }.getOrNull()

        private fun picture(context: Context, ex: Bundle): ImageBitmap? = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                ex.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)?.asImageBitmap()
                    ?: ex.getParcelable(Notification.EXTRA_PICTURE_ICON, Icon::class.java)
                        ?.loadDrawable(context)?.toBitmap()?.asImageBitmap()
            } else {
                @Suppress("DEPRECATION")
                (ex.getParcelable<Bitmap>(Notification.EXTRA_PICTURE))?.asImageBitmap()
            }
        }.getOrNull()

        /** Last few chat messages from MessagingStyle notifications. */
        private fun messages(ex: Bundle): List<Message> = runCatching {
            @Suppress("DEPRECATION")
            val arr = ex.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return emptyList()
            arr.mapNotNull { it as? Bundle }.mapNotNull { b ->
                val text = b.getCharSequence("text")?.toString() ?: return@mapNotNull null
                val sender = b.getCharSequence("sender")?.toString()
                    ?: (if (Build.VERSION.SDK_INT >= 33) {
                        b.getParcelable("sender_person", android.app.Person::class.java)
                    } else {
                        @Suppress("DEPRECATION") b.getParcelable("sender_person")
                    })?.name?.toString()
                Message(sender, text)
            }.takeLast(5)
        }.getOrDefault(emptyList())

        fun isLaunchable(context: Context, pkg: String): Boolean =
            context.packageManager.getLaunchIntentForPackage(pkg) != null

        @Suppress("unused")
        private fun pm(context: Context): PackageManager = context.packageManager
    }
}
