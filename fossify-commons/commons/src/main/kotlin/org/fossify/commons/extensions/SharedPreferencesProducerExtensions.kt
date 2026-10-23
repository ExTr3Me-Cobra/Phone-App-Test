package org.fossify.commons.extensions

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// PhotoBox: was a context receiver ("context (SharedPreferences)"), which newer Kotlin no longer
// accepts. An extension function works the same for the callers, which use prefs.run { ... }.
fun <T> SharedPreferences.sharedPreferencesCallback(
    sendOnCollect: Boolean = false,
    value: () -> T?,
): Flow<T?> = callbackFlow {
    val sharedPreferencesListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(value())
        }

    if (sendOnCollect) {
        trySend(value())
    }
    registerOnSharedPreferenceChangeListener(sharedPreferencesListener)
    awaitClose { unregisterOnSharedPreferenceChangeListener(sharedPreferencesListener) }
}
