package com.shadeui.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Samsung's own pop-ups and lock screen notifications, which would otherwise show next to
 * Shade's. Needs WRITE_SECURE_SETTINGS (granted once with adb). Remembers what Shade switched
 * off, so turning Shade off brings Samsung's back and turning it on hides them again.
 */
object SamsungDuplicates {
    private const val HEADS_UP = "heads_up_notifications_enabled"
    private const val LOCK = "lock_screen_show_notifications"

    fun canManage(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun popupsOn(context: Context) = Settings.Global.getInt(context.contentResolver, HEADS_UP, 1) == 1
    fun lockOn(context: Context) = Settings.Secure.getInt(context.contentResolver, LOCK, 1) == 1

    fun setPopups(context: Context, on: Boolean) {
        Settings.Global.putInt(context.contentResolver, HEADS_UP, if (on) 1 else 0)
        prefs(context).edit().putBoolean("popups_off", !on).apply()
    }

    fun setLock(context: Context, on: Boolean) {
        Settings.Secure.putInt(context.contentResolver, LOCK, if (on) 1 else 0)
        prefs(context).edit().putBoolean("lock_off", !on).apply()
    }

    /** Called when the master switch changes. */
    fun onShadeEnabled(context: Context, enabled: Boolean) {
        if (!canManage(context)) return
        val p = prefs(context)
        runCatching {
            if (p.getBoolean("popups_off", false)) {
                Settings.Global.putInt(context.contentResolver, HEADS_UP, if (enabled) 0 else 1)
            }
            if (p.getBoolean("lock_off", false)) {
                Settings.Secure.putInt(context.contentResolver, LOCK, if (enabled) 0 else 1)
            }
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("duplicates", Context.MODE_PRIVATE)
}
