package com.ironlog.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    /** Whether Google backup was chosen before the wipe (affects what reinstalling does). */
    var hadGoogle = false
        private set

    fun build(c: Context): Long = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).longVersionCode }.getOrDefault(0L)

    fun link(build: Long) = "https://github.com/ExTr3Me-Cobra/Phone-App-Test/releases/download/loadout-$build/Loadout.apk"

    fun wipe(c: Context) {
        hadGoogle = Store.profile.value.cloud == "google"
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
    val build = remember { DevWipe.build(c) }
    val link = DevWipe.link(build)
    var copied by remember { mutableStateOf(false) }
    BackHandler {}
    Column(
        Modifier.fillMaxSize().background(Bg).clickable(remember { MutableInteractionSource() }, indication = null) {}
            .systemBarsPadding().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(20)
        Text("🧹", fontSize = 56.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("Loadout data wiped", fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("Now uninstall Loadout, then reinstall build $build from this link:", color = Sub, fontSize = 15.sp)
        SelectionContainer {
            Text(link, color = Blue, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card).padding(12.dp))
        }
        GreyButton(if (copied) "✓ Link copied" else "Copy link") {
            (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Loadout download", link))
            copied = true
        }
        BigButton("Uninstall Loadout", color = Red) {
            runCatching { c.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        if (DevWipe.hadGoogle) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Google backup", fontWeight = FontWeight.Bold, color = Color(0xFFFF9F0A))
                Text("Google keeps its last copy until the next backup runs (charging on Wi-Fi) and replaces it with this empty one. " +
                    "If you reinstall before then, Android may restore your old data. For a guaranteed fresh start, turn off " +
                    "Automatic restore in your phone's backup settings before reinstalling (turn it back on afterwards).",
                    color = Sub, fontSize = 13.sp, lineHeight = 18.sp)
                GreyButton("Open backup settings") { CloudBackup.openSettings(c) }
            }
        }
        Text("Or skip reinstalling: close Loadout and open it again for the first-run experience.", color = Sub, fontSize = 13.sp)
        GreyButton("Close Loadout") {
            (c as? android.app.Activity)?.finishAndRemoveTask()
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
