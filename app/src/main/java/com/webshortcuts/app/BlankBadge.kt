package com.webshortcuts.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager

/**
 * Experimental "invisible badge" option.
 *
 * One UI draws a small badge on pinned shortcuts using the icon of the launcher entry the shortcut
 * belongs to. Shortcuts created with this option belong to a second launcher entry whose icon is
 * fully transparent, so the badge should be invisible. That entry only exists (is enabled) while
 * the option is on or some shortcut still uses it, because disabling it would break those
 * shortcuts.
 */
object BlankBadge {
    private const val ALIAS = "com.webshortcuts.app.BlankBadge"
    private const val PREF = "blank_badge"

    fun component(context: Context) = ComponentName(context.packageName, ALIAS)

    fun isOn(context: Context): Boolean = prefs(context).getBoolean(PREF, false)

    fun setOn(context: Context, on: Boolean, savedShortcuts: List<WebShortcut>) {
        prefs(context).edit().putBoolean(PREF, on).apply()
        sync(context, savedShortcuts)
    }

    /** Enables the blank entry exactly when something needs it. */
    fun sync(context: Context, savedShortcuts: List<WebShortcut>) {
        val needed = isOn(context) ||
            savedShortcuts.any { it.blankBadge } ||
            pinnedShortcutsUseIt(context)
        val state = if (needed) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT // = disabled, as in the manifest
        }
        context.packageManager.setComponentEnabledSetting(
            component(context),
            state,
            PackageManager.DONT_KILL_APP,
        )
    }

    /** Also covers pinned icons whose list entry was deleted but which are still on the home screen. */
    private fun pinnedShortcutsUseIt(context: Context): Boolean = runCatching {
        val alias = component(context)
        context.getSystemService(ShortcutManager::class.java)
            ?.pinnedShortcuts
            ?.any { it.activity == alias && it.isEnabled } == true
    }.getOrDefault(false)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
}
