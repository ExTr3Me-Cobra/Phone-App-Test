package com.webshortcuts.app

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
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

    /** Same for a plain (legacy) icon, which has no extra adaptive edge. */
    fun legacyIconSizePx(context: Context): Int = (iconSizePx(context) / 1.5f).roundToInt()

    fun buildInfo(context: Context, shortcut: WebShortcut, icon: Bitmap): ShortcutInfo {
        // Opens the page in the default browser, exactly like tapping a link.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(shortcut.url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val builder = ShortcutInfo.Builder(context, shortcut.id)
            .setShortLabel(shortcut.label)
            .setLongLabel(shortcut.label)
            .setIcon(shortcutIcon(context, shortcut, icon))
            .setIntent(intent)
        if (shortcut.blankBadge) builder.setActivity(BlankBadge.component(context))
        return builder.build()
    }

    /**
     * Icons with see-through parts are passed as a link to a PNG (Android 11+), because a bitmap
     * gets re-saved by the system and can lose its transparency (turning black). Fully opaque
     * icons use the plain bitmap, which every launcher handles.
     *
     * [icon] is the full adaptive canvas, or for a legacy shortcut the plain visible square.
     */
    private fun shortcutIcon(context: Context, shortcut: WebShortcut, icon: Bitmap): Icon {
        val link = usesIconLink(icon)
        return when {
            shortcut.legacyIcon && link -> Icon.createWithContentUri(SharedIcons.publish(context, shortcut, icon))
            shortcut.legacyIcon -> Icon.createWithBitmap(icon)
            link -> Icon.createWithAdaptiveBitmapContentUri(SharedIcons.publish(context, shortcut, icon))
            else -> Icon.createWithAdaptiveBitmap(icon)
        }
    }

    fun usesIconLink(icon: Bitmap): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && AdaptiveIcon.hasTransparency(icon)

    /**
     * Shows the launcher's "Add to Home screen" prompt. False if the launcher refused.
     *
     * The launcher may only open a linked icon file for a shortcut the system already knows, and a
     * brand-new pin request isn't known until it's accepted (so the prompt showed a placeholder).
     * For linked icons the shortcut is therefore first registered as a temporary dynamic shortcut,
     * and the pin request refers to it by id. [removeTemporary] clears it afterwards; the pinned
     * copy stays.
     */
    fun requestPin(context: Context, info: ShortcutInfo, iconIsLink: Boolean): Boolean {
        val sm = manager(context) ?: return false
        if (iconIsLink) {
            val pinned = runCatching {
                sm.addDynamicShortcuts(listOf(info)) &&
                    sm.requestPinShortcut(ShortcutInfo.Builder(context, info.id).build(), null)
            }.getOrDefault(false)
            if (pinned) return true
        }
        return runCatching { sm.requestPinShortcut(info, null) }.getOrDefault(false)
    }

    /**
     * Removes the temporary dynamic shortcuts used by [requestPin]. Called when the app comes back
     * to the foreground, i.e. after the "Add to Home screen" prompt was answered. Pinned shortcuts
     * are unaffected.
     */
    fun removeTemporary(context: Context) {
        runCatching { manager(context)?.removeAllDynamicShortcuts() }
    }

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
