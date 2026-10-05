package com.shadeui.app.overlay

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last few things Shade saw from Samsung's system UI. Shown in the app so the names Samsung
 * uses for its panel can be checked and matched.
 */
object Diagnostics {
    val lines = mutableStateListOf<String>()
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun add(line: String) {
        if (lines.firstOrNull()?.endsWith(line) == true) return
        lines.add(0, "${time.format(Date())}  $line")
        while (lines.size > 25) lines.removeAt(lines.lastIndex)
    }
}
