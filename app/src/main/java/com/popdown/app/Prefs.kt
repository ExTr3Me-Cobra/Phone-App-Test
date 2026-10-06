package com.popdown.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PopSettings(
    /** Master switch. */
    val enabled: Boolean = true,
    /** Turn the screen on when a notification arrives while it's off. */
    val wakeScreen: Boolean = true,
    /** Leave notifications that already pop down on their own alone (avoids double pop-ups). */
    val skipIfAlreadyPops: Boolean = true,
    /** Also pop down notifications the app marked as silent. */
    val includeSilent: Boolean = true,
    /** How long the pop-down copy stays in the notification list before removing itself. */
    val keepSeconds: Int = 8,
    /** Apps that never get a pop-down copy. */
    val excluded: Set<String> = emptySet(),
)

/** Settings, saved in SharedPreferences and observable for the screen. */
object Prefs {
    private val state = MutableStateFlow(PopSettings())
    val flow: StateFlow<PopSettings> = state.asStateFlow()
    private var loaded = false

    fun get(context: Context): PopSettings {
        if (!loaded) {
            val p = prefs(context)
            state.value = PopSettings(
                enabled = p.getBoolean("enabled", true),
                wakeScreen = p.getBoolean("wakeScreen", true),
                skipIfAlreadyPops = p.getBoolean("skipIfAlreadyPops", true),
                includeSilent = p.getBoolean("includeSilent", true),
                keepSeconds = p.getInt("keepSeconds", 8),
                excluded = p.getStringSet("excluded", emptySet()).orEmpty().toSet(),
            )
            loaded = true
        }
        return state.value
    }

    fun update(context: Context, change: (PopSettings) -> PopSettings) {
        val next = change(get(context))
        state.value = next
        prefs(context).edit()
            .putBoolean("enabled", next.enabled)
            .putBoolean("wakeScreen", next.wakeScreen)
            .putBoolean("skipIfAlreadyPops", next.skipIfAlreadyPops)
            .putBoolean("includeSilent", next.includeSilent)
            .putInt("keepSeconds", next.keepSeconds)
            .putStringSet("excluded", next.excluded)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
}
