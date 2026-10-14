package com.ironlog.app

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.provider.Settings
import org.json.JSONObject
import java.io.File

/**
 * Android's own backup to your Google account (the "Back up data" switch in the phone's settings).
 * Android runs it about once a day while the phone is charging on Wi-Fi, and puts everything back
 * automatically when you install Loadout on a phone signed in to the same Google account.
 *
 * Only runs if you chose Google backup. The OpenAI key is never included (it lives in a folder
 * Android never backs up). Google allows 25 MB per app, so your workouts and settings go first and
 * your own pictures fill what's left.
 */
class CloudBackup : BackupAgent() {
    override fun onFullBackup(data: FullBackupDataOutput) {
        val choice = runCatching { JSONObject(File(filesDir, "profile.json").readText()).optString("bk") }.getOrDefault("")
        val toNewPhone = (data.transportFlags and FLAG_DEVICE_TO_DEVICE_TRANSFER) != 0
        // "Keep on this phone": nothing goes to Google (a cable/phone-to-phone transfer still works).
        if (choice != "google" && !toNewPhone) return
        var room = if (toNewPhone) Long.MAX_VALUE else 20L * 1024 * 1024
        val files = filesDir.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }
            .sortedBy { if (it.extension == "json") 0 else 1 }
        for (f in files) {
            if (f.length() > room) continue
            room -= f.length()
            fullBackupFile(f, data)
        }
        // Remember when a Google backup last ran, for the green tick in Settings.
        if (!toNewPhone) runCatching { File(noBackupFilesDir, "last_backup.txt").writeText(System.currentTimeMillis().toString()) }
    }

    // Loadout only uses full backups; these are required but unused.
    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) {}
    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) {}

    companion object {
        /** Opens the phone's backup settings (Google's page if it can, otherwise general settings). */
        fun openSettings(c: Context) {
            val tries = listOf(
                Intent().setClassName("com.google.android.gms", "com.google.android.gms.backup.component.BackupSettingsActivity"),
                Intent("android.settings.BACKUP_AND_RESET_SETTINGS"),
                Intent(Settings.ACTION_PRIVACY_SETTINGS),
                Intent(Settings.ACTION_SETTINGS),
            )
            for (i in tries) {
                if (runCatching { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
            }
        }

        /** When a Google backup last ran (since you chose Google backup), or null. */
        fun lastBackup(c: Context): Long? =
            runCatching { File(c.noBackupFilesDir, "last_backup.txt").readText().trim().toLong() }.getOrNull()

        fun resetStatus() { runCatching { File(Store.noBackupDir(), "last_backup.txt").delete() } }

    }
}
