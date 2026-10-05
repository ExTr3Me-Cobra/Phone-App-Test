package com.shadeui.app

import android.app.Application
import com.shadeui.app.data.RulesStore
import com.shadeui.app.data.SettingsStore
import com.shadeui.app.media.MediaRepo
import com.shadeui.app.notif.NotifRepo
import com.shadeui.app.tiles.TileStates

/** Holds the app-wide singletons shared by the services and the settings screens. */
class ShadeApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var rules: RulesStore
        private set
    val notifs = NotifRepo()
    val media = MediaRepo()
    lateinit var tiles: TileStates
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            recordError(error)
            previous?.uncaughtException(thread, error)
        }
        settings = SettingsStore(this)
        rules = RulesStore(this)
        tiles = TileStates(this)
    }

    /** Saves the last crash so the app's home screen can show it (survives the restart). */
    fun recordError(error: Throwable) {
        val text = buildString {
            append(java.text.DateFormat.getDateTimeInstance().format(java.util.Date()))
            append("\n").append(error.toString())
            error.stackTrace.take(8).forEach { append("\n  at ").append(it) }
            error.cause?.let { append("\nCaused by: ").append(it) }
        }
        getSharedPreferences("diagnostics", MODE_PRIVATE).edit().putString("last_error", text).commit()
    }

    fun lastError(): String? = getSharedPreferences("diagnostics", MODE_PRIVATE).getString("last_error", null)

    fun clearError() {
        getSharedPreferences("diagnostics", MODE_PRIVATE).edit().remove("last_error").apply()
    }

    companion object {
        lateinit var instance: ShadeApp
            private set
    }
}
