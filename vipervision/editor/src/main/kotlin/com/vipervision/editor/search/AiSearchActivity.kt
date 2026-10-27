package com.vipervision.editor.search

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vipervision.editor.BubbleGrey
import com.vipervision.editor.ViperLog
import com.vipervision.editor.appAccentColor
import com.vipervision.editor.contentOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat

/**
 * The gallery's AI search (the magnifier button at the top of the albums and photos screens):
 * blank until something is typed, then the matching photos (see [AiSearch]). Tap one to open it.
 */
class AiSearchActivity : ComponentActivity() {

    private val thumbnails = object : LruCache<Long, ImageBitmap>(60 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: ImageBitmap) = value.width * value.height * 4
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AiSearch.schedule(this)
        setContent { Screen() }
    }

    override fun onDestroy() {
        super.onDestroy()
        // The models stay loaded while searching; free them when leaving (unless indexing).
        if (!AiSearch.isIndexing(this)) Siglip.close()
    }

    @Composable
    private fun Screen() {
        val accent = remember { appAccentColor(this) }
        var query by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<AiSearch.Results?>(null) }
        var searching by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val focus = remember { FocusRequester() }

        // What's ready: the models, and how many photos are indexed (refreshed every second).
        val status by produceState(Status(), this) {
            while (true) {
                value = withContext(Dispatchers.IO) { readStatus() }
                delay(1000)
            }
        }

        fun run() {
            val text = query.trim()
            if (text.isEmpty()) {
                results = null
                return
            }
            scope.launch {
                searching = true
                results = runCatching { AiSearch.search(this@AiSearchActivity, text) }
                    .onFailure { ViperLog.error("SEARCH", "search failed", it) }
                    .getOrNull()
                searching = false
            }
        }

        // Searches a moment after typing stops.
        LaunchedEffect(query, status.modelsReady) {
            if (!status.modelsReady) return@LaunchedEffect
            delay(450)
            run()
        }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // The search bar, in the app's colour.
            Row(
                Modifier
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(BubbleGrey)
                    .border(1.5.dp, accent, RoundedCornerShape(26.dp))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("Ai Search: \"dog\", \"receipt\", \"bike\"", color = Color(0xFF8E8E93), fontSize = 16.sp, maxLines = 1)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { run() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                    )
                }
                if (searching) CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                else if (query.isNotEmpty()) {
                    Text("✕", color = Color.White, fontSize = 18.sp, modifier = Modifier
                        .clickable { query = ""; results = null }
                        .padding(4.dp))
                }
            }

            StatusLine(status, accent)

            val current = results
            when {
                current == null -> Spacer(Modifier.weight(1f))
                current.hits.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Text("No photos found", color = Color.Gray, fontSize = 15.sp, modifier = Modifier.padding(24.dp))
                }
                else -> Column(Modifier.weight(1f)) {
                    if (current.closestOnly) {
                        Text(
                            "No clear matches. The closest photos:",
                            color = Color.Gray, fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(current.hits, key = { it.id }) { hit -> Thumbnail(hit.id) { open(hit.id) } }
                    }
                }
            }
        }
    }

    private class Status(
        val modelsReady: Boolean = true,
        val downloading: Boolean = false,
        val downloadProgress: Float = 0f,
        val downloadError: String? = null,
        val indexing: Boolean = false,
        val total: Int = 0,
        val indexed: Int = 0,
        val textRead: Int = 0
    )

    private fun readStatus(): Status {
        val modelsReady = Siglip.isDownloaded(this)
        val (all, pictures, text) = runCatching { AiSearch.progress(this) }.getOrDefault(Triple(0, 0, 0))
        return Status(
            modelsReady = modelsReady,
            downloading = ModelPack.isRunning(this),
            downloadProgress = ModelPack.progress(this),
            downloadError = ModelPack.lastError(this),
            indexing = AiSearch.isIndexing(this),
            total = maxOf(AiSearch.totalPhotos(this), all),
            indexed = pictures,
            textRead = text
        )
    }

    @Composable
    private fun StatusLine(status: Status, accent: Color) {
        val number = remember { NumberFormat.getIntegerInstance() }
        Column(Modifier.padding(horizontal = 16.dp)) {
            when {
                !status.modelsReady && status.downloading -> {
                    Text("Downloading the AI models… ${(status.downloadProgress * 100).toInt()}%", color = Color.Gray, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { status.downloadProgress }, color = accent, modifier = Modifier.fillMaxWidth())
                }
                !status.modelsReady -> {
                    Text(
                        "AI search needs Viper Vision's AI models: a one-time download of about " +
                                "%.1f GB (every AI tool in the app, kept through updates).".format(ModelPack.totalSize / 1e9) +
                                (status.downloadError?.let { "\nLast try stopped: $it" } ?: ""),
                        color = Color.Gray, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { ModelPack.start(this@AiSearchActivity, allowMobileData = false) },
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = accent.contentOn())
                        ) { Text("Download on Wi-Fi") }
                        TextButton(onClick = { ModelPack.start(this@AiSearchActivity, allowMobileData = true) }) {
                            Text("Use mobile data too", color = Color.White)
                        }
                    }
                }
                status.total > 0 && (status.indexed < status.total || status.indexing) -> {
                    Text(
                        "Getting photos ready: ${number.format(status.indexed)} of ${number.format(status.total)}. " +
                                "Search works on these already.",
                        color = Color.Gray, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { if (status.total == 0) 0f else status.indexed.toFloat() / status.total },
                        color = accent, modifier = Modifier.fillMaxWidth()
                    )
                }
                status.textRead < status.indexed -> Text(
                    "Reading text in photos: ${number.format(status.textRead)} of ${number.format(status.indexed)}",
                    color = Color.Gray, fontSize = 12.sp
                )
            }
        }
    }

    @Composable
    private fun Thumbnail(id: Long, onClick: () -> Unit) {
        val image by produceState(thumbnails.get(id), id) {
            if (value == null) {
                value = withContext(Dispatchers.IO) {
                    AiSearch.load(this@AiSearchActivity, id, 300)?.asImageBitmap()?.also { thumbnails.put(id, it) }
                }
            }
        }
        Box(
            Modifier
                .aspectRatio(1f)
                .background(BubbleGrey)
                .clickable(onClick = onClick)
        ) {
            image?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
    }

    /** Opens the photo in the gallery's viewer. */
    private fun open(id: Long) {
        val path = runCatching {
            @Suppress("DEPRECATION")
            contentResolver.query(AiSearch.uriOf(id), arrayOf(MediaStore.Images.Media.DATA), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: return
        runCatching {
            startActivity(Intent().apply {
                setClassName(packageName, "org.fossify.gallery.activities.ViewPagerActivity")
                putExtra("path", path)
                putExtra("show_all", false)
            })
        }.onFailure { ViperLog.error("SEARCH", "couldn't open the photo", it) }
    }
}
