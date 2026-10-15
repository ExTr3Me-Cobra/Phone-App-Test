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
    override fun onCreate() {
        super.onCreate()
        log(this, "Android started Loadout's backup helper")
    }

    override fun onFullBackup(data: FullBackupDataOutput) {
        val toNewPhone = (data.transportFlags and FLAG_DEVICE_TO_DEVICE_TRANSFER) != 0
        try {
            val choice = runCatching { JSONObject(File(filesDir, "profile.json").readText()).optString("bk") }.getOrDefault("")
            log(this, "Backup requested (" + (if (toNewPhone) "phone-to-phone transfer" else "Google account") +
                ", quota ${data.quota / 1024 / 1024} MB, your setting: ${choice.ifEmpty { "not chosen" }})")
            // "Keep on this phone": nothing goes to Google (a cable/phone-to-phone transfer still works).
            if (choice != "google" && !toNewPhone) { log(this, "Skipped – Google backup isn't chosen"); return }
            var room = if (toNewPhone) Long.MAX_VALUE else minOf(20L * 1024 * 1024, data.quota - 512 * 1024)
            val files = filesDir.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }
                .sortedBy { if (it.extension == "json") 0 else 1 }.toList()
            var n = 0; var bytes = 0L; var left = 0
            for (f in files) {
                if (f.length() > room) { left++; continue }
                room -= f.length()
                fullBackupFile(f, data)
                n++; bytes += f.length()
            }
            log(this, "Handed $n files (${bytes / 1024} KB) to Android" + if (left > 0) ", $left pictures didn't fit" else "")
            // Remember when a Google backup last ran, for the green tick in Settings.
            if (!toNewPhone) File(noBackupFilesDir, "last_backup.txt").writeText(System.currentTimeMillis().toString())
        } catch (e: Throwable) {
            log(this, "Backup failed: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    override fun onQuotaExceeded(backupDataBytes: Long, quotaBytes: Long) {
        log(this, "Google rejected the backup: ${backupDataBytes / 1024} KB is over the ${quotaBytes / 1024} KB limit")
        runCatching { File(noBackupFilesDir, "last_backup.txt").delete() }
    }

    override fun onRestoreFinished() {
        log(this, "Restored from a backup")
    }

    // Loadout only uses full backups; these are required but unused.
    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) {}
    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) {}

    companion object {
        private const val GMS = "com.google.android.gms"

        /**
         * Tries to open Google's backup page, then Play services' "Google" settings (which lists Backup).
         * Returns false if neither opens – it never falls back to the phone maker's own backup page,
         * which on Samsung phones is Samsung Cloud, not Google.
         */
        fun openGoogle(c: Context): Boolean {
            val backup = listOf(
                "com.google.android.gms.backup.component.BackupSettingsActivity",
                "com.google.android.gms.backup.settings.component.BackupSettingsActivity",
                "com.google.android.gms.backup.BackupSettingsActivity",
            )
            val found = runCatching {
                @Suppress("DEPRECATION")
                c.packageManager.getPackageInfo(GMS, android.content.pm.PackageManager.GET_ACTIVITIES).activities.orEmpty()
                    .filter { it.exported && it.name.contains("backup", true) && it.name.contains("setting", true) }.map { it.name }
            }.getOrDefault(emptyList())
            val google = listOf(
                "com.google.android.gms.app.settings.GoogleSettingsLink",
                "com.google.android.gms.app.settings.GoogleSettingsIALink",
                "com.google.android.gms.app.settings.GoogleSettingsActivity",
            )
            for (name in (backup + found + google).distinct()) {
                val i = Intent().setClassName(GMS, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (runCatching { c.startActivity(i) }.isSuccess) { log(c, "Opened $name"); return true }
            }
            log(c, "Couldn't open Google's backup or Google settings directly")
            return false
        }

        /** The phone's main Settings app. */
        fun openPhoneSettings(c: Context) {
            runCatching { c.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }

        val isSamsung get() = android.os.Build.MANUFACTURER.equals("samsung", ignoreCase = true)

        /** A short record of what Android's backup did with Loadout (newest last), for Developer settings. */
        fun log(c: Context, msg: String) {
            runCatching {
                val f = File(c.noBackupFilesDir, "backup_log.txt")
                val time = java.text.SimpleDateFormat("MMM d HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                val lines = (if (f.exists()) f.readLines() else emptyList()) + "$time  $msg"
                f.writeText(lines.takeLast(40).joinToString("\n"))
            }
        }

        fun readLog(c: Context): List<String> =
            runCatching { File(c.noBackupFilesDir, "backup_log.txt").readLines() }.getOrDefault(emptyList())

        /** When a Google backup last ran (since you chose Google backup), or null. */
        fun lastBackup(c: Context): Long? =
            runCatching { File(c.noBackupFilesDir, "last_backup.txt").readText().trim().toLong() }.getOrNull()

        fun resetStatus() { runCatching { File(Store.noBackupDir(), "last_backup.txt").delete() } }

    }
}
