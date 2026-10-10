package com.clockout.app

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

/** Keeps the details of a crash so the app can show them next time it opens. */
object Crash {
    private fun file(c: Context) = File(c.filesDir, "last_crash.txt")
    private var installed = false

    fun install(c: Context) {
        if (installed) return
        installed = true
        val app = c.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            note(app, e)
            previous?.uncaughtException(t, e)
        }
    }

    fun note(c: Context, e: Throwable) {
        runCatching {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            file(c).writeText("${LocalDateTime.now()}  v${BuildConfigVersion.name(c)}\n$sw")
        }
    }

    fun last(c: Context): String? = runCatching { file(c).takeIf { it.exists() }?.readText() }.getOrNull()
    fun clear(c: Context) { file(c).delete() }
}

private object BuildConfigVersion {
    fun name(c: Context): String = runCatching {
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")
}
