package com.webshortcuts.app

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import kotlin.math.max
import kotlin.math.roundToInt

/** Thin wrapper around ShortcutManager for pinned home screen shortcuts. */
object PinnedShortcuts {
    private fun manager(context: Context): ShortcutManager? =
        context.getSystemService(ShortcutManager::class.java)

    fun isPinSupported(context: Context): Boolean =
        manager(context)?.isRequestPinShortcutSupported == true

    /**
     * Pixel size for the icon bitmap. The system shrinks shortcut icons to its own maximum
     * (the icon size, plus 50% for the adaptive icon's extra edge), so this is the sharpest size
     * that survives, without sending a needlessly huge bitmap.
     */
    fun iconSizePx(context: Context): Int {
        val sm = manager(context) ?: return 432
        val max = max(sm.iconMaxWidth, sm.iconMaxHeight)
        return (max * 1.5f).roundToInt().coerceIn(288, 1024)
    }

    fun buildInfo(context: Context, shortcut: WebShortcut, icon: Bitmap): ShortcutInfo {
        // Opens the page in the default browser, exactly like tapping a link.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(shortcut.url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val builder = ShortcutInfo.Builder(context, shortcut.id)
            .setShortLabel(shortcut.label)
            .setLongLabel(shortcut.label)
            .setIcon(Icon.createWithAdaptiveBitmap(icon))
            .setIntent(intent)
        if (shortcut.blankBadge) builder.setActivity(BlankBadge.component(context))
        return builder.build()
    }

    /** Shows the launcher's "Add to Home screen" prompt. False if the launcher refused. */
    fun requestPin(context: Context, info: ShortcutInfo): Boolean =
        runCatching { manager(context)?.requestPinShortcut(info, null) == true }.getOrDefault(false)

    /** Updates an already pinned shortcut in place (icon, label and link). */
    fun update(context: Context, info: ShortcutInfo): Boolean =
        runCatching { manager(context)?.updateShortcuts(listOf(info)) == true }.getOrDefault(false)

    fun pinnedIds(context: Context): Set<String> =
        runCatching { manager(context)?.pinnedShortcuts?.map { it.id }?.toSet() }
            .getOrNull() ?: emptySet()

    /** Greys out the home screen icon; tapping it then shows [message]. */
    fun disable(context: Context, id: String, message: String) {
        runCatching { manager(context)?.disableShortcuts(listOf(id), message) }
    }
}
