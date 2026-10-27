package com.vipervision.editor.search

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vipervision.editor.appAccentColor
import com.vipervision.editor.contentOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Settings → AI models (also offered once when the app first opens): the one-time download of
 * every AI model ([ModelPack]), its progress, and what's already here.
 */
class AiModelsActivity : ComponentActivity() {

    private class State(val here: List<Boolean>, val running: Boolean, val progress: Float, val current: String?, val error: String?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Offered once: opening this screen counts.
        getSharedPreferences("Prefs", MODE_PRIVATE).edit().putBoolean(ModelPack.KEY_PROMPTED, true).apply()
        setContent { Screen() }
    }

    @Composable
    private fun Screen() {
        val accent = remember { appAccentColor(this) }
        val items = ModelPack.items
        val state by produceState<State?>(null) {
            while (true) {
                value = withContext(Dispatchers.IO) {
                    State(
                        here = items.map { runCatching { it.isHere(this@AiModelsActivity) }.getOrDefault(false) },
                        running = ModelPack.isRunning(this@AiModelsActivity),
                        progress = ModelPack.progress(this@AiModelsActivity),
                        current = ModelPack.current(this@AiModelsActivity),
                        error = ModelPack.lastError(this@AiModelsActivity)
                    )
                }
                delay(1500)
            }
        }
        val s = state
        val hereBytes = s?.let { items.filterIndexed { i, _ -> it.here[i] }.sumOf { it.size } } ?: 0L
        val complete = s != null && s.here.all { it }

        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Text("AI models", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Every AI tool in Viper Vision and the AI search use these: about ${gb(ModelPack.totalSize)} in all. " +
                        "Downloaded once, they stay through every update. They come from Viper Vision's own " +
                        "permanent copy (falling back to the original sites).",
                color = Color.Gray, fontSize = 14.sp
            )
            Spacer(Modifier.height(16.dp))
            when {
                s == null -> Unit
                complete -> Text("All downloaded (${gb(hereBytes)}).", color = accent, fontSize = 15.sp)
                s.running -> {
                    Text(
                        "Downloading… ${(s.progress * 100).toInt()}%" + (s.current?.let { " · $it" } ?: ""),
                        color = Color.White, fontSize = 15.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { s.progress }, color = accent, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text("It keeps going in the background; you can leave this screen.", color = Color.Gray, fontSize = 12.sp)
                }
                else -> {
                    Text(
                        "${gb(hereBytes)} of ${gb(ModelPack.totalSize)} here." +
                                (s.error?.let { "\nLast try stopped: $it (it will try again)." } ?: ""),
                        color = Color.White, fontSize = 15.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { ModelPack.start(this@AiModelsActivity, allowMobileData = false) },
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = accent.contentOn())
                        ) { Text("Download on Wi-Fi") }
                        TextButton(onClick = { ModelPack.start(this@AiModelsActivity, allowMobileData = true) }) {
                            Text("Use mobile data too", color = Color.White)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { finish() }) { Text("Later", color = Color.Gray) }
                }
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(items.indices.toList()) { i ->
                    val item = items[i]
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(mb(item.size), color = Color.Gray, fontSize = 13.sp)
                        Text(
                            if (s?.here?.getOrNull(i) == true) "  ✓" else "   ",
                            color = accent, fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    private fun gb(bytes: Long) = "%.1f GB".format(bytes / 1e9)
    private fun mb(bytes: Long) = if (bytes >= 1e9) gb(bytes) else "%.0f MB".format(bytes / 1e6)
}
