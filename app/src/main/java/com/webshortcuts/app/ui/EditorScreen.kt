package com.webshortcuts.app.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.webshortcuts.app.AdaptiveIcon
import com.webshortcuts.app.BlankBadge
import com.webshortcuts.app.FitMode
import com.webshortcuts.app.IconStyle
import com.webshortcuts.app.PinnedShortcuts
import com.webshortcuts.app.ShortcutStore
import com.webshortcuts.app.WebShortcut
import com.webshortcuts.app.defaultLabel
import com.webshortcuts.app.loadPickedImage
import com.webshortcuts.app.normalizeUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.exp
import kotlin.math.ln

private const val PIN_UNSUPPORTED =
    "Your home screen app doesn't let apps add shortcuts. Samsung's One UI Home supports it, so " +
        "if you've changed home screen apps, set One UI Home back as the default " +
        "(Settings > Apps > Choose default apps > Home app)."

private const val PIN_REFUSED =
    "The home screen didn't accept the shortcut. On Samsung, check that Settings > Home screen > " +
        "\"Lock Home screen layout\" is off, then try again."

private val SWATCHES = listOf(
    0x00000000, // transparent
    0xFFFFFFFF, 0xFF000000, 0xFFE0E0E0, 0xFF424242, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835,
    0xFF43A047, 0xFF00897B, 0xFF1E88E5, 0xFF3949AB, 0xFF8E24AA, 0xFFD81B60, 0xFF6D4C41,
).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    existing: WebShortcut?,
    isPinned: Boolean,
    store: ShortcutStore,
    onBack: () -> Unit,
    onFinished: (message: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var url by rememberSaveable { mutableStateOf(existing?.url ?: "") }
    var label by rememberSaveable { mutableStateOf(existing?.label ?: "") }
    var style by remember { mutableStateOf(existing?.style ?: IconStyle()) }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var sourceChanged by remember { mutableStateOf(false) }
    var loadingImage by remember { mutableStateOf(existing != null) }
    var busy by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(existing?.id) {
        if (existing != null) {
            source = withContext(Dispatchers.IO) { store.loadSource(existing.id) }
            loadingImage = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                loadingImage = true
                val bitmap = loadPickedImage(context, uri)
                loadingImage = false
                if (bitmap == null) {
                    message = "Couldn't open that image. Try a different one."
                } else {
                    source = bitmap
                    sourceChanged = true
                    // Logos with see-through parts start with a transparent background.
                    val background = if (AdaptiveIcon.hasTransparency(bitmap)) {
                        AndroidColor.TRANSPARENT
                    } else if (Color(style.backgroundColor).alpha == 0f) {
                        AndroidColor.WHITE
                    } else {
                        style.backgroundColor
                    }
                    style = style.copy(backgroundColor = background, zoom = 1f, offsetX = 0f, offsetY = 0f)
                }
            }
        }
    }

    val normalized = normalizeUrl(url)
    val urlError = when {
        !showErrors -> null
        url.isBlank() -> "Enter a website address"
        normalized == null -> "That doesn't look like a valid web address"
        else -> null
    }

    fun save(pinNow: Boolean) {
        showErrors = true
        val finalUrl = normalized ?: return
        val src = source ?: run {
            message = "Choose an image for the icon first."
            return
        }
        if (pinNow && !PinnedShortcuts.isPinSupported(context)) {
            message = PIN_UNSUPPORTED
            return
        }
        busy = true
        scope.launch {
            val shortcut = WebShortcut(
                id = existing?.id ?: UUID.randomUUID().toString(),
                label = label.trim().ifEmpty { defaultLabel(finalUrl) },
                url = finalUrl,
                style = style,
                // Kept for the shortcut's lifetime; new shortcuts follow the current setting.
                blankBadge = existing?.blankBadge ?: BlankBadge.isOn(context),
            )
            val icon = withContext(Dispatchers.IO) {
                val bitmap = AdaptiveIcon.render(src, style, PinnedShortcuts.iconSizePx(context))
                if (existing == null || sourceChanged) store.saveSource(shortcut.id, src)
                store.saveIcon(shortcut.id, bitmap)
                bitmap
            }
            val info = withContext(Dispatchers.IO) { PinnedShortcuts.buildInfo(context, shortcut, icon) }
            val result: String? = if (pinNow) {
                if (PinnedShortcuts.requestPin(context, info, PinnedShortcuts.usesIconLink(icon))) {
                    store.upsert(shortcut)
                    "Tap \"Add\" on the pop-up to put \"${shortcut.label}\" on your home screen."
                } else {
                    if (existing == null) store.delete(shortcut.id)
                    message = PIN_REFUSED
                    null
                }
            } else {
                store.upsert(shortcut)
                when {
                    !isPinned -> "Saved"
                    PinnedShortcuts.update(context, info) -> "Home screen icon updated"
                    else -> "Saved, but the home screen icon couldn't be updated just now. " +
                        "Open it and tap Save again."
                }
            }
            busy = false
            if (result != null) onFinished(result)
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New shortcut" else "Edit shortcut") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(32.dp))
                    } else if (existing == null) {
                        Button(onClick = { save(pinNow = true) }, Modifier.fillMaxWidth()) {
                            Text("Add to Home Screen")
                        }
                    } else {
                        if (!isPinned) {
                            OutlinedButton(onClick = { save(pinNow = true) }, Modifier.weight(1f)) {
                                Text("Add to Home Screen")
                            }
                        }
                        Button(onClick = { save(pinNow = false) }, Modifier.weight(1f)) {
                            Text("Save changes")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Website address") },
                placeholder = { Text("example.com") },
                singleLine = true,
                isError = urlError != null,
                supportingText = {
                    Text(urlError ?: normalized?.let { "Opens $it" } ?: "https:// is added for you")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Shortcut name") },
                placeholder = { Text(normalized?.let(::defaultLabel) ?: "Name under the icon") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Text(if (source == null) "Choose image" else "Change image")
                }
                if (loadingImage) {
                    CircularProgressIndicator(Modifier.padding(start = 16.dp).size(24.dp))
                }
            }

            source?.let { src ->
                IconDesigner(src, style, onStyleChange = { style = it })
            }
        }
    }

    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconDesigner(source: Bitmap, style: IconStyle, onStyleChange: (IconStyle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            FitMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = style.mode == mode,
                    onClick = { onStyleChange(style.copy(mode = mode, zoom = 1f, offsetX = 0f, offsetY = 0f)) },
                    shape = SegmentedButtonDefaults.itemShape(index, FitMode.entries.size),
                ) { Text(if (mode == FitMode.FILL) "Fill" else "Fit") }
            }
        }
        Text(
            if (style.mode == FitMode.FILL) {
                "Fill: the image covers the whole icon. The dimmed edge is never shown on the " +
                    "home screen; keep anything important inside the dashed circle."
            } else {
                "Fit: the whole image sits inside the safe zone (dashed circle) on a background " +
                    "colour, so nothing gets cut off."
            },
            style = MaterialTheme.typography.bodySmall,
        )

        Text("Drag to move, pinch to zoom", style = MaterialTheme.typography.labelLarge)
        FramingEditor(
            source = source,
            style = style,
            onStyleChange = onStyleChange,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Zoom", Modifier.width(48.dp))
            Slider(
                value = ln(style.zoom),
                onValueChange = { onStyleChange(style.copy(zoom = exp(it))) },
                valueRange = ln(AdaptiveIcon.MIN_ZOOM)..ln(AdaptiveIcon.MAX_ZOOM),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onStyleChange(style.copy(zoom = 1f, offsetX = 0f, offsetY = 0f)) }) {
                Text("Reset")
            }
        }

        BackgroundPicker(source, style, onStyleChange)

        Text("Preview", style = MaterialTheme.typography.titleMedium)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconPreview(source, style, MaskShape.CIRCLE, 72.dp)
                    Text("Circle", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconPreview(source, style, MaskShape.SQUIRCLE, 72.dp)
                    Text("Squircle", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun BackgroundPicker(source: Bitmap, style: IconStyle, onStyleChange: (IconStyle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Background colour", style = MaterialTheme.typography.titleMedium)
        Text(
            "The first (chequered) choice is transparent: see-through parts of a PNG stay " +
                "see-through on the home screen." +
                if (style.mode == FitMode.FILL) " A colour only shows behind transparent parts or if you zoom out." else "",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SWATCHES.forEach { color ->
                val selected = color == style.backgroundColor
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .drawBehind {
                            if (AndroidColor.alpha(color) == 0) {
                                drawIntoCanvas {
                                    AdaptiveIcon.drawCheckerboard(it.nativeCanvas, size.width, size.height, size.width / 4f)
                                }
                            } else {
                                drawRect(Color(color))
                            }
                        }
                        .border(
                            if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            CircleShape,
                        )
                        .clickable { onStyleChange(style.copy(backgroundColor = color)) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            var hex by remember(style.backgroundColor) { mutableStateOf(toHex(style.backgroundColor)) }
            OutlinedTextField(
                value = hex,
                onValueChange = { text ->
                    hex = text
                    parseHex(text)?.let { onStyleChange(style.copy(backgroundColor = it)) }
                },
                label = { Text("Hex") },
                placeholder = { Text("#RRGGBB") },
                singleLine = true,
                modifier = Modifier.width(140.dp),
            )
            OutlinedButton(onClick = {
                onStyleChange(style.copy(backgroundColor = AdaptiveIcon.edgeColor(source)))
            }) { Text("Match image") }
        }
    }
}

private fun toHex(color: Int) =
    if (AndroidColor.alpha(color) == 0) "" else "#%06X".format(color and 0xFFFFFF)

private fun parseHex(text: String): Int? {
    val digits = text.trim().removePrefix("#")
    if (digits.length != 6) return null
    return digits.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
}
