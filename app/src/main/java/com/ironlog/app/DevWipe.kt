package com.ironlog.app

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** Developer: wipe everything Loadout has saved, to try the first-run experience again. */
object DevWipe {
    var wiped by mutableStateOf(false)
        private set
    fun wipe(c: Context) {
        runCatching { Rest.stop(c) }
        runCatching { WorkoutService.stop(c) }
        // Everything in the app's private storage: data, pictures you added, API key, backup status, caches, settings.
        val data = c.filesDir.parentFile
        listOfNotNull(c.filesDir, c.noBackupFilesDir, c.cacheDir, c.codeCacheDir, data?.let { File(it, "shared_prefs") }, data?.let { File(it, "databases") })
            .forEach { dir -> dir.listFiles()?.forEach { runCatching { it.deleteRecursively() } } }
        // With no data left, the next Google backup replaces the old copy with an empty one.
        runCatching { android.app.backup.BackupManager(c).dataChanged() }
        wiped = true
    }
}

/** Shown after a wipe, over everything, until you close the app. */
@Composable
fun WipedScreen() {
    if (!DevWipe.wiped) return
    val c = LocalContext.current
    BackHandler {}
    Column(
        Modifier.fillMaxSize().background(Bg).clickable(remember { MutableInteractionSource() }, indication = null) {}
            .systemBarsPadding().padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, androidx.compose.ui.Alignment.CenterVertically),
    ) {
        Text("🧹", fontSize = 56.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("Loadout data wiped", fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("Close Loadout, then open it again to start fresh.", color = Sub, fontSize = 15.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        BigButton("Close Loadout") {
            (c as? android.app.Activity)?.finishAndRemoveTask()
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
