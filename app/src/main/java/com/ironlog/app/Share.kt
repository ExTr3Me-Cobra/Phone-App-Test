package com.ironlog.app

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Home's Share: tick what goes on the picture, watch the preview change, then save it or send it as a JPG. */
@Composable
fun ShareScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workouts by Store.workouts.collectAsState()
    val profile by Store.profile.collectAsState()
    val exercises by Store.exercises.collectAsState()
    val ranks = remember(workouts, profile, exercises) { Ranks.all(workouts, profile) }
    val overall = Ranks.overallOf(ranks, profile)
    val ranked = ranks.values.sortedByDescending { it.percentile }
    var withOverall by rememberSaveable { mutableStateOf(true) }
    var withGroups by rememberSaveable { mutableStateOf(false) }
    var withExercise by rememberSaveable { mutableStateOf(false) }
    var exId by rememberSaveable { mutableStateOf<String?>(null) }
    var exMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val ex = ranked.firstOrNull { it.exercise.id == exId } ?: ranked.firstOrNull()
    val layer = rememberGraphicsLayer()

    Column(Modifier.fillMaxSize()) {
        TopBar("Share")
        if (Standards.mode(profile) == Standards.Mode.OFF || overall == null || ranked.isEmpty()) {
            Text(stars(
                if (Standards.mode(profile) == Standards.Mode.OFF) "Ranks are off. Turn them on in Settings to share your ranks."
                else "Nothing to share yet – log an exercise marked ★ to get a rank first."),
                color = Sub, modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("What goes on the picture", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            @Composable fun Tick(label: String, on: Boolean, set: (Boolean) -> Unit) =
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { set(!on) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(on, set)
                    Text(label)
                }
            Tick("Overall rank + heatmap", withOverall) { withOverall = it }
            Tick("Muscle group ranks", withGroups) { withGroups = it }
            Tick("An exercise's rank + where you sit chart", withExercise) { withExercise = it }
            if (withExercise && ex != null) Box(Modifier.padding(start = 48.dp)) {
                GreyButton(ex.exercise.name + "  ▾") { exMenu = true }
                DropdownMenu(exMenu, { exMenu = false }) {
                    ranked.forEach { r ->
                        DropdownMenuItem({
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RankBadge(r.tier, 20.dp)
                                Text("  " + r.exercise.name, Modifier.weight(1f, fill = false))
                                Text("  " + r.tier.label, color = Color(r.tier.color), fontSize = 12.sp)
                            }
                        }, { exId = r.exercise.id; exMenu = false })
                    }
                }
            }
            val any = withOverall || withGroups || (withExercise && ex != null)
            Text("Preview", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            if (!any) Text("Tick at least one thing above.", color = Sub)
            else {
                // The picture is exactly what's drawn here.
                Box(Modifier.fillMaxWidth().drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }) {
                    ShareCard(ranks, profile, overall, withOverall, withGroups, if (withExercise) ex?.exercise?.id else null)
                }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    fun make(then: suspend (Bitmap) -> Unit) {
                        if (busy) return
                        busy = true
                        scope.launch {
                            runCatching {
                                val bmp = layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
                                then(bmp)
                            }.onFailure { Toast.makeText(context, "Couldn't make the picture: ${it.message}", Toast.LENGTH_LONG).show() }
                            busy = false
                        }
                    }
                    GreyButton("⬇  Save to gallery", Modifier.weight(1f)) {
                        make { bmp ->
                            val ok = withContext(Dispatchers.IO) { ShareImage.save(context, bmp) }
                            Toast.makeText(context, if (ok) "Saved to Pictures/Loadout" else "Couldn't save the picture", Toast.LENGTH_SHORT).show()
                        }
                    }
                    BigButton("Share", modifier = Modifier.weight(1f)) {
                        make { bmp ->
                            val f = withContext(Dispatchers.IO) { ShareImage.file(context, bmp) }
                            ShareImage.send(context, f)
                        }
                    }
                }
            }
            Spacer(30)
        }
    }
}

/** The picture itself: the parts you ticked, stacked, with the Loadout logo at the bottom. */
@Composable
private fun ShareCard(ranks: Map<String, Ranks.Ranked>, profile: Profile, overall: Double, withOverall: Boolean, withGroups: Boolean, exerciseId: String?) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF0B0B0D)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (withOverall) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
            val t = Tier.of(overall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RankBadge(t, 84.dp)
                HSpacer(12)
                Column {
                    Text("Overall rank", color = Sub, fontSize = 13.sp)
                    Text(t.label, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(t.color))
                    Text(topLabel(overall), fontSize = 15.sp)
                }
            }
            RankHeatmap(ranks, profile)
        }
        if (withGroups) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Muscle ranks", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Ranks.groupRanks(ranks, profile.picksNow).entries.sortedByDescending { it.value.first }.forEach { (g, v) ->
                val tier = Tier.of(v.first)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(Color(tier.color)))
                    HSpacer(8)
                    Text(titleCase(g), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    RankBadge(tier, 22.dp)
                    HSpacer(4)
                    Text(rankText(v.first), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(tier.color), maxLines = 1, softWrap = false)
                }
            }
        }
        if (exerciseId != null) {
            val e = Store.exercise(exerciseId)
            val panels = rankPanels(exerciseId, share = true)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NameText(starredName(e), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                panels.firstOrNull { it.key == "header" }?.content?.invoke()
                // The chart sits on its own darker card inside.
                panels.firstOrNull { it.key == "chart" }?.let { pn -> Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF0B0B0D))) { pn.content() } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_launcher_fg), null, Modifier.size(34.dp))
            Text("Loadout", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

/** Turning the picture into a JPG in your gallery, or handing it to another app. */
object ShareImage {
    /** Saves into Pictures/Loadout (no permission needed). */
    fun save(context: Context, bmp: Bitmap): Boolean {
        val r = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Loadout_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Loadout")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = runCatching { r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull() ?: return false
        return runCatching {
            r.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            r.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            true
        }.getOrElse { r.delete(uri, null, null); false }
    }

    fun file(context: Context, bmp: Bitmap): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        return File(dir, "Loadout.jpg").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) } }
    }

    /** Android's share menu with the JPG attached. */
    fun send(context: Context, f: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri("Loadout", uri)
        context.startActivity(Intent.createChooser(send, "Share your ranks"))
    }
}
