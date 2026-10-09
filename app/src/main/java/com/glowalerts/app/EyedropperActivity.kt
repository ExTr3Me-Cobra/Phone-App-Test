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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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

@Composable
private fun EyedropperScreen(load: (Uri) -> Bitmap?, onDone: (Int) -> Unit, onCancel: () -> Unit) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    // Where you're pointing, in picture pixels; and whether the finger is down (for the magnifier).
    var pixel by remember { mutableStateOf<IntOffset?>(null) }
    var finger by remember { mutableStateOf<Offset?>(null) }

    fun open(uri: Uri?) {
        if (uri == null) return
        val b = load(uri)
        failed = b == null
        if (b != null) {
            bitmap = b
            pixel = IntOffset(b.width / 2, b.height / 2)
            finger = null
        }
    }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { open(it) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { open(it) }
    LaunchedEffect(Unit) { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    val bmp = bitmap
    val image = remember(bmp) { bmp?.asImageBitmap() }
    val picked = bmp?.let { b -> pixel?.let { p -> b.getPixel(p.x.coerceIn(0, b.width - 1), p.y.coerceIn(0, b.height - 1)) or 0xFF000000.toInt() } }

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
                else -> "Touch and drag over the picture. The magnifier shows exactly which pixel you're on."
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
                            detectTapGestures(onPress = { pos ->
                                finger = pos
                                pixel = toPixel(pos, size, bmp)
                                tryAwaitRelease()
                                finger = null
                            })
                        }
                        .pointerInput(bmp) {
                            detectDragGestures(
                                onDragStart = { finger = it; pixel = toPixel(it, size, bmp) },
                                onDragEnd = { finger = null },
                                onDragCancel = { finger = null },
                            ) { change, _ ->
                                finger = change.position
                                pixel = toPixel(change.position, size, bmp)
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

                    // Magnifier above the finger (below it near the top edge).
                    val f = finger ?: return@Canvas
                    val r = 64.dp.toPx()
                    val lift = 96.dp.toPx()
                    val cx = f.x.coerceIn(r, size.width - r)
                    val cy = if (f.y - lift - r > 0) f.y - lift else f.y + lift
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
                        // Box around the exact pixel in the middle.
                        val cell = 2 * r / zoomPixels
                        val px = cx - r + (p.x - src.x) * cell
                        val py = cy - r + (p.y - src.y) * cell
                        drawRect(Color.White, Offset(px, py), Size(cell, cell), style = Stroke(2.dp.toPx()))
                    }
                    drawCircle(picked?.let { Color(it) } ?: Color.White, r, Offset(cx, cy), style = Stroke(6.dp.toPx()))
                    drawCircle(Color.White, r + 3.dp.toPx(), Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
                }
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

private fun toPixel(pos: Offset, area: IntSize, bmp: Bitmap): IntOffset {
    val f = fit(area, bmp)
    return IntOffset(
        ((pos.x - f.left) / f.scale).toInt().coerceIn(0, bmp.width - 1),
        ((pos.y - f.top) / f.scale).toInt().coerceIn(0, bmp.height - 1),
    )
}
