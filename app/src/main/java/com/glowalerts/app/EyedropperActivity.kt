package com.glowalerts.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Eyedropper: open any picture or screenshot, drag your finger over it (a magnifier shows the
 * exact pixel), and use that colour.
 */
class EyedropperActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)) {
                Surface(Modifier.fillMaxSize()) {
                    EyedropperScreen(
                        load = ::load,
                        onDone = { c ->
                            setResult(RESULT_OK, Intent().putExtra(EXTRA_COLOR, c))
                            finish()
                        },
                        onCancel = { finish() },
                    )
                }
            }
        }
    }

    /** Reads the picture into memory (scaled down if huge) so its pixels can be read. */
    private fun load(uri: Uri): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val biggest = max(info.size.width, info.size.height)
            if (biggest > 2400) decoder.setTargetSampleSize(ceil(biggest / 2400.0).toInt())
        }
    }.getOrNull()

    companion object {
        const val EXTRA_COLOR = "color"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EyedropperScreen(load: (Uri) -> Bitmap?, onDone: (Int) -> Unit, onCancel: () -> Unit) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    // The chosen point in picture pixels (fractional while dragging), and whether a finger is down.
    var point by remember { mutableStateOf<Offset?>(null) }
    var dragging by remember { mutableStateOf(false) }
    // Fine: the point moves a quarter as far as your finger, for precise picking.
    var fine by remember { mutableStateOf(true) }
    // Recent positions while dragging, so lifting the finger can't nudge the choice.
    val history = remember { ArrayDeque<Pair<Long, Offset>>() }

    fun open(uri: Uri?) {
        if (uri == null) return
        val b = load(uri)
        failed = b == null
        if (b != null) {
            bitmap = b
            point = Offset(b.width / 2f, b.height / 2f)
        }
    }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { open(it) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { open(it) }
    LaunchedEffect(Unit) { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    val bmp = bitmap
    val image = remember(bmp) { bmp?.asImageBitmap() }
    val pixel = bmp?.let { b -> point?.let { IntOffset(it.x.toInt().coerceIn(0, b.width - 1), it.y.toInt().coerceIn(0, b.height - 1)) } }
    val picked = bmp?.let { b -> pixel?.let { p -> b.getPixel(p.x, p.y) or 0xFF000000.toInt() } }

    fun clampTo(b: Bitmap, o: Offset) = Offset(o.x.coerceIn(0f, b.width - 0.01f), o.y.coerceIn(0f, b.height - 0.01f))
    fun nudge(dx: Int, dy: Int) {
        val b = bmp ?: return
        val p = pixel ?: return
        point = clampTo(b, Offset(p.x + dx + 0.5f, p.y + dy + 0.5f))
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Text("Pick a colour", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Photos & screenshots") }
            OutlinedButton(onClick = { files.launch(arrayOf("image/*")) }) { Text("Files") }
        }
        Text(
            when {
                failed -> "Couldn't open that picture. Try another one."
                bmp == null -> "Choose a picture or screenshot."
                else -> "Tap to jump to a spot, then drag anywhere to fine-tune (the point moves slower than your finger). " +
                    "Lifting your finger won't move it. Arrows move one pixel."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp, 8.dp),
        )

        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF111111))) {
            if (bmp != null && image != null) {
                Canvas(
                    Modifier.fillMaxSize()
                        .pointerInput(bmp) {
                            detectTapGestures(onTap = { pos -> point = clampTo(bmp, toPicture(pos, size, bmp)) })
                        }
                        .pointerInput(bmp, fine) {
                            detectDragGestures(
                                onDragStart = {
                                    dragging = true
                                    history.clear()
                                },
                                onDragEnd = {
                                    // Undo the little slip as the finger lifts: go back to where the
                                    // point was a moment ago.
                                    val cutoff = System.currentTimeMillis() - 120
                                    history.lastOrNull { it.first <= cutoff }?.let { point = it.second }
                                    dragging = false
                                },
                                onDragCancel = { dragging = false },
                            ) { change, amount ->
                                change.consume()
                                val f = fit(size, bmp)
                                val factor = if (fine) 0.25f else 1f
                                val p = point ?: return@detectDragGestures
                                val next = clampTo(bmp, p + amount / f.scale * factor)
                                point = next
                                history.addLast(System.currentTimeMillis() to next)
                                while (history.size > 40) history.removeFirst()
                            }
                        },
                ) {
                    val fit = fit(IntSize(size.width.toInt(), size.height.toInt()), bmp)
                    drawImage(
                        image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bmp.width, bmp.height),
                        dstOffset = IntOffset(fit.left.roundToInt(), fit.top.roundToInt()),
                        dstSize = IntSize((bmp.width * fit.scale).roundToInt(), (bmp.height * fit.scale).roundToInt()),
                    )
                    val p = pixel ?: return@Canvas
                    // Marker on the chosen pixel.
                    val mark = Offset(fit.left + (p.x + 0.5f) * fit.scale, fit.top + (p.y + 0.5f) * fit.scale)
                    drawCircle(Color.Black, 13.dp.toPx(), mark, style = Stroke(3.dp.toPx()))
                    drawCircle(Color.White, 11.dp.toPx(), mark, style = Stroke(2.dp.toPx()))

                    // Magnifier docked in a top corner (the other one if the point is under it).
                    val r = 64.dp.toPx()
                    val m = 12.dp.toPx()
                    val leftSide = mark.x > size.width / 2 || mark.y > 2 * r + 2 * m
                    val cx = if (leftSide) m + r else size.width - m - r
                    val cy = m + r
                    val zoomPixels = 15
                    val half = zoomPixels / 2
                    val src = IntOffset((p.x - half).coerceIn(0, max(0, bmp.width - zoomPixels)), (p.y - half).coerceIn(0, max(0, bmp.height - zoomPixels)))
                    val srcSize = IntSize(min(zoomPixels, bmp.width), min(zoomPixels, bmp.height))
                    val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(cx, cy), r)) }
                    clipPath(circle) {
                        drawRect(Color.Black, Offset(cx - r, cy - r), Size(2 * r, 2 * r))
                        drawImage(
                            image, srcOffset = src, srcSize = srcSize,
                            dstOffset = IntOffset((cx - r).roundToInt(), (cy - r).roundToInt()),
                            dstSize = IntSize((2 * r).roundToInt(), (2 * r).roundToInt()),
                            filterQuality = FilterQuality.None,
                        )
                        // Box around the exact pixel.
                        val cell = 2 * r / zoomPixels
                        val px = cx - r + (p.x - src.x) * cell
                        val py = cy - r + (p.y - src.y) * cell
                        drawRect(Color.White, Offset(px, py), Size(cell, cell), style = Stroke(2.dp.toPx()))
                    }
                    drawCircle(picked?.let { Color(it) } ?: Color.White, r, Offset(cx, cy), style = Stroke(6.dp.toPx()))
                    drawCircle(if (dragging) Color(0xFF7FE9FF) else Color.White, r + 3.dp.toPx(), Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
                }
            }
        }

        if (bmp != null) {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(selected = fine, onClick = { fine = true }, label = { Text("Fine drag") })
                FilterChip(selected = !fine, onClick = { fine = false }, label = { Text("Normal drag") })
                OutlinedButton(onClick = { nudge(-1, 0) }) { Text("◀") }
                OutlinedButton(onClick = { nudge(0, -1) }) { Text("▲") }
                OutlinedButton(onClick = { nudge(0, 1) }) { Text("▼") }
                OutlinedButton(onClick = { nudge(1, 0) }) { Text("▶") }
            }
        }

        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(picked?.let { Color(it) } ?: Color.Transparent)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Text(
                picked?.let { hex(it) } ?: "—",
                style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
            )
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = { picked?.let(onDone) }, enabled = picked != null, modifier = Modifier.padding(start = 8.dp)) { Text("Use") }
        }
    }
}

private class Fit(val scale: Float, val left: Float, val top: Float)

/** How the picture is fitted into the area (whole picture visible, centred). */
private fun fit(area: IntSize, bmp: Bitmap): Fit {
    val scale = min(area.width / bmp.width.toFloat(), area.height / bmp.height.toFloat())
    return Fit(scale, (area.width - bmp.width * scale) / 2f, (area.height - bmp.height * scale) / 2f)
}

/** Screen position to picture position (in picture pixels, fractional). */
private fun toPicture(pos: Offset, area: IntSize, bmp: Bitmap): Offset {
    val f = fit(area, bmp)
    return Offset((pos.x - f.left) / f.scale, (pos.y - f.top) / f.scale)
}
