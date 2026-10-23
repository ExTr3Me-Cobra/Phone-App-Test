package com.ironlog.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Reference Mode: photos of your gym's bars, dumbbells and other kit, each with its weight, so you remember which bar is
 * 35 lb or what the unmarked dumbbells weigh. Kept on the phone (reference.json and reference/ pictures).
 */
object Reference {
    class Item(val id: String, val name: String, val weightLb: Double?)

    private const val FILE = "reference.json"
    val items = mutableStateListOf<Item>()
    private var loaded = false
    /** Bumped when a picture changes, so the list reloads it. */
    var version by mutableStateOf(0)

    fun load() {
        if (loaded) return
        loaded = true
        runCatching {
            val a = JSONArray(Store.readFile(FILE) ?: "[]")
            for (i in 0 until a.length()) a.getJSONObject(i).let { o ->
                items += Item(o.getString("id"), o.optString("name"), if (o.has("w") && !o.isNull("w")) o.getDouble("w") else null)
            }
        }
    }

    private fun save() = Store.writeFile(FILE, JSONArray(items.map { JSONObject().put("id", it.id).put("name", it.name).put("w", it.weightLb ?: JSONObject.NULL) }).toString())

    fun put(item: Item) {
        val i = items.indexOfFirst { it.id == item.id }
        if (i >= 0) items[i] = item else items += item
        save()
    }

    fun remove(c: Context, id: String) { items.removeAll { it.id == id }; picture(c, id).delete(); save() }

    private fun dir(c: Context) = File(c.filesDir, "reference").apply { mkdirs() }
    fun picture(c: Context, id: String) = File(dir(c), "$id.jpg")

    /** Saves a photo (scaled to at most 1280 px) as this item's picture. */
    fun setPicture(c: Context, id: String, uri: Uri) {
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.contentResolver, uri)) { d, info, _ ->
            val s = (maxOf(info.size.width, info.size.height) / 1280).coerceAtLeast(1)
            d.setTargetSize(info.size.width / s, info.size.height / s)
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        picture(c, id).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        version++
    }

    fun image(c: Context, id: String): ImageBitmap? = picture(c, id).takeIf { it.exists() }?.let { f ->
        runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull()
    }
}

/** A small dumbbell, drawn (there's no dumbbell emoji). */
@Composable
fun DumbbellIcon(size: Dp = 24.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val r = CornerRadius(w * 0.04f)
        drawRoundRect(color, Offset(w * 0.30f, h * 0.45f), Size(w * 0.40f, h * 0.10f), r)          // handle
        drawRoundRect(color, Offset(w * 0.16f, h * 0.22f), Size(w * 0.12f, h * 0.56f), r)          // inner plates
        drawRoundRect(color, Offset(w * 0.72f, h * 0.22f), Size(w * 0.12f, h * 0.56f), r)
        drawRoundRect(color, Offset(w * 0.04f, h * 0.32f), Size(w * 0.10f, h * 0.36f), r)          // outer plates
        drawRoundRect(color, Offset(w * 0.86f, h * 0.32f), Size(w * 0.10f, h * 0.36f), r)
    }
}

/** The dumbbell button at the top of a workout or 1 Rep Max test: opens Reference Mode. */
@Composable
fun ReferenceButton() {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable { Nav.go(Route.Reference) }.padding(horizontal = 8.dp, vertical = 6.dp)) { DumbbellIcon(26.dp) }
}

/** The Settings panel that opens Reference Mode. */
@Composable
fun ReferencePanel() {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).clickable { Nav.go(Route.Reference) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        DumbbellIcon(28.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("Reference Mode", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Photos of your bars and dumbbells with their weights, so you remember which is which.", color = Sub, fontSize = 13.sp)
        }
        Text("›", color = Sub, fontSize = 24.sp)
    }
}

@Composable
fun ReferenceScreen() {
    val context = LocalContext.current
    remember { Reference.load(); 0 }
    var editing by remember { mutableStateOf<Reference.Item?>(null) }
    val v = Reference.version
    Column(Modifier.fillMaxSize()) {
        TopBar("Reference Mode", action = "+ Add") { editing = Reference.Item("r" + System.currentTimeMillis(), "", null) }
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Take a photo of each bar, dumbbell or attachment and note its weight. Tap one to change it.",
                    Modifier.padding(top = 12.dp), color = Sub, fontSize = 13.sp)
            }
            if (Reference.items.isEmpty()) item {
                Text("Nothing yet. Tap + Add to photograph your first bar or dumbbell.", Modifier.padding(vertical = 16.dp), color = Sub)
            }
            items(Reference.items, key = { it.id }) { item ->
                val img = remember(item.id, v) { Reference.image(context, item.id) }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).clickable { editing = item }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(84.dp).clip(RoundedCornerShape(8.dp)).background(Card2), contentAlignment = Alignment.Center) {
                        if (img != null) Image(img, null, Modifier.size(84.dp), contentScale = ContentScale.Crop) else DumbbellIcon(32.dp, Sub)
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(item.name.ifBlank { "Unnamed" }, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Text(item.weightLb?.let { Units.weightU(it) } ?: "No weight set", color = if (item.weightLb != null) Blue else Sub, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { Spacer(24) }
        }
    }
    editing?.let { ReferenceEditor(it) { editing = null } }
}

@Composable
private fun ReferenceEditor(item: Reference.Item, close: () -> Unit) {
    val context = LocalContext.current
    val isNew = Reference.items.none { it.id == item.id }
    var name by remember { mutableStateOf(item.name) }
    var weight by remember { mutableStateOf(item.weightLb?.let { Units.weight(it) } ?: "") }
    var msg by remember { mutableStateOf<String?>(null) }
    val v = Reference.version
    val img = remember(item.id, v) { Reference.image(context, item.id) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) runCatching { Reference.setPicture(context, item.id, uri) }.onFailure { msg = "Couldn't use that picture." }
    }
    val camFile = remember { File(context.cacheDir, "cam").apply { mkdirs() }.let { File(it, "reference.jpg") } }
    val camUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.files", camFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok || camFile.sizeOk()) runCatching { Reference.setPicture(context, item.id, camUri) }.onFailure { msg = "Couldn't use that picture." }
    }
    fun save() {
        Reference.put(Reference.Item(item.id, name.trim(), weight.toDoubleOrNull()?.let(Units::fromShown)))
        close()
    }
    AlertDialog(
        onDismissRequest = close,
        title = { androidx.compose.material3.Text(if (isNew) "Add to Reference Mode" else "Edit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().size(180.dp).clip(RoundedCornerShape(10.dp)).background(Card2), contentAlignment = Alignment.Center) {
                    if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) else DumbbellIcon(48.dp, Sub)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GreyButton("📷 Take photo", Modifier.weight(1f)) { camFile.delete(); camera.launch(camUri) }
                    GreyButton("🖼 Choose", Modifier.weight(1f)) { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                }
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { androidx.compose.material3.Text("Name (e.g. Black EZ bar)") }, singleLine = true)
                OutlinedTextField(weight, { weight = it }, Modifier.fillMaxWidth(), label = { androidx.compose.material3.Text("Weight (${Units.w})") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                msg?.let { androidx.compose.material3.Text(it, color = Red) }
            }
        },
        confirmButton = { TextButton(onClick = { save() }) { androidx.compose.material3.Text("Save") } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { Reference.remove(context, item.id); close() }) { androidx.compose.material3.Text("Delete", color = Red) }
                TextButton(onClick = { if (isNew) Reference.picture(context, item.id).delete(); close() }) { androidx.compose.material3.Text("Cancel") }
            }
        },
    )
}
