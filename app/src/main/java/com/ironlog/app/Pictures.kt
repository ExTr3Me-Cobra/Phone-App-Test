package com.ironlog.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** The front-and-back muscle chart, drawn from [BodyMapData]. */
object BodyMap {
    enum class View { BOTH, FRONT, BACK }

    private fun smooth(pts: FloatArray, mirrorX: Float?, closed: Boolean): Path {
        val n = pts.size / 2
        fun x(i: Int): Float { val v = pts[2 * ((i % n + n) % n)]; return if (mirrorX != null) 2 * mirrorX - v else v }
        fun y(i: Int): Float = pts[2 * ((i % n + n) % n) + 1]
        val p = Path()
        p.moveTo(x(0), y(0))
        val last = if (closed) n else n - 1
        for (i in 0 until last) {
            val i0 = if (!closed && i == 0) 0 else i - 1
            val i3 = if (!closed && i + 2 > n - 1) n - 1 else i + 2
            val c1x = x(i) + (x(i + 1) - x(i0)) / 6f
            val c1y = y(i) + (y(i + 1) - y(i0)) / 6f
            val c2x = x(i + 1) - (x(i3) - x(i)) / 6f
            val c2y = y(i + 1) - (y(i3) - y(i)) / 6f
            p.cubicTo(c1x, c1y, c2x, c2y, x(i + 1), y(i + 1))
        }
        if (closed) p.close()
        return p
    }

    private class Shape(val muscle: String, val path: Path, val front: Boolean)

    private val outlines by lazy { listOf(smooth(BodyMapData.frontOutline, null, true), smooth(BodyMapData.backOutline, null, true)) }
    private val shapes: List<Shape> by lazy {
        BodyMapData.regions.flatMap { r ->
            val front = r.pts[0] < 200f
            listOfNotNull(
                Shape(r.muscle, smooth(r.pts, null, true), front),
                r.mirrorX?.let { Shape(r.muscle, smooth(r.pts, it, true), front) },
            )
        }
    }
    private val lines: List<Pair<Path, Boolean>> by lazy {
        BodyMapData.lines.flatMap { r -> listOf(smooth(r.pts, null, false) to (r.pts[0] < 200f), smooth(r.pts, r.mirrorX, false) to (r.pts[0] < 200f)) } +
            listOf(
                Path().apply { moveTo(100f, 140f); lineTo(100f, 234f) } to true,
                Path().apply { moveTo(300f, 62f); lineTo(300f, 228f) } to false,
            )
    }

    /** Which side shows these muscles best. */
    fun bestView(muscles: List<String>): View {
        val f = shapes.count { it.front && it.muscle in muscles }
        val b = shapes.count { !it.front && it.muscle in muscles }
        return if (b > f) View.BACK else View.FRONT
    }

    /** 0 -> white, a little -> light red, 1 -> dark red. */
    fun heat(v: Float): Int {
        if (v <= 0f) return 0xFFF6F6F6.toInt()
        val t = v.coerceIn(0f, 1f)
        val lo = intArrayOf(248, 182, 176); val hi = intArrayOf(146, 14, 20)
        val c = IntArray(3) { (lo[it] + (hi[it] - lo[it]) * t).toInt() }
        return android.graphics.Color.rgb(c[0], c[1], c[2])
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }

    /** Draws into a canvas of [w] x [h] pixels. [levels]: muscle -> 0..1. */
    fun draw(c: android.graphics.Canvas, w: Float, h: Float, levels: Map<String, Float>, view: View) {
        val (x0, x1) = when (view) {
            View.BOTH -> 0f to 400f
            View.FRONT -> 0f to 200f
            View.BACK -> 200f to 400f
        }
        val bw = x1 - x0
        val bh = 445f
        val k = minOf(w / bw, h / bh)
        c.save()
        c.translate((w - bw * k) / 2f - x0 * k, (h - bh * k) / 2f)
        c.scale(k, k)
        val thin = 1f / k.coerceAtLeast(0.01f)
        fun visible(front: Boolean) = view == View.BOTH || (view == View.FRONT) == front
        outlines.forEachIndexed { i, p ->
            if (!visible(i == 0)) return@forEachIndexed
            fill.color = 0xFFF6F6F6.toInt(); c.drawPath(p, fill)
        }
        for (s in shapes) {
            if (!visible(s.front)) continue
            fill.color = heat(levels[s.muscle] ?: 0f)
            c.drawPath(s.path, fill)
            stroke.color = 0xFF555555.toInt(); stroke.strokeWidth = maxOf(0.8f, thin * 0.9f)
            c.drawPath(s.path, stroke)
        }
        stroke.color = 0xFF8A8A8A.toInt(); stroke.strokeWidth = maxOf(0.6f, thin * 0.7f)
        for ((p, front) in lines) if (visible(front)) c.drawPath(p, stroke)
        stroke.color = 0xFF141414.toInt(); stroke.strokeWidth = maxOf(1.4f, thin * 1.2f)
        outlines.forEachIndexed { i, p -> if (visible(i == 0)) c.drawPath(p, stroke) }
        c.restore()
    }

    fun levelsFor(e: Exercise): Map<String, Float> =
        e.secondary.associateWith { 0.35f } + e.primary.associateWith { 1f }
}

@Composable
fun BodyMapView(levels: Map<String, Float>, modifier: Modifier, view: BodyMap.View = BodyMap.View.BOTH) {
    Canvas(modifier) {
        drawIntoCanvas { BodyMap.draw(it.nativeCanvas, size.width, size.height, levels, view) }
    }
}

/** Exercise pictures saved on the phone (your own, or made by AI). */
object Pictures {
    private val cache = object : LruCache<String, ImageBitmap>(48) {}

    fun load(id: String, version: Int): ImageBitmap? {
        val key = "$id#$version"
        cache.get(key)?.let { return it }
        val f = Store.imageFile(id)
        if (!f.exists()) return null
        val bmp = runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() ?: return null
        cache.put(key, bmp)
        return bmp
    }

    private fun save(id: String, bmp: Bitmap) {
        val scale = minOf(1f, 768f / maxOf(bmp.width, bmp.height))
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        Store.imageFile(id).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        Store.imageChanged()
    }

    /** Your own picture from the gallery or camera. */
    fun import(c: Context, id: String, uri: Uri) {
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.contentResolver, uri)) { d, info, _ ->
            val s = (maxOf(info.size.width, info.size.height) / 1024).coerceAtLeast(1)
            d.setTargetSize(info.size.width / s, info.size.height / s)
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        save(id, bmp)
    }

    fun remove(id: String) {
        Store.imageFile(id).delete()
        Store.imageChanged()
    }

    private fun muscleName(m: String) = when (m) {
        "grip" -> "hand and finger (grip) muscles"
        "serratus" -> "serratus anterior"
        "rotator cuff" -> "rotator cuff (infraspinatus, teres minor)"
        "abdominals" -> "abdominal muscles"
        "middle back" -> "rhomboids and middle back"
        else -> m
    }

    fun prompt(e: Exercise): String {
        fun list(m: List<String>) = m.joinToString(", ") { muscleName(it) }
        val sec = if (e.secondary.isNotEmpty()) " Shade the ${list(e.secondary)} in light red." else ""
        val eq = when (e.equipment) {
            "Bodyweight", "Other" -> ""
            else -> " Show the ${e.equipment.lowercase()} and any bench or machine in matte black and dark grey."
        }
        return "Clean, highly detailed 3D anatomical fitness illustration on a pure white background. " +
            "A fit, muscular man with short dark hair, wearing only black athletic shorts, barefoot, " +
            "performing the exercise \"${e.name}\" with correct form, shown at the most recognisable point of the movement. " +
            "His body is rendered in light grey monochrome with clearly defined individual muscles and fine muscle-fibre detail, " +
            "like a medical anatomy drawing. Highlight the ${list(e.primary)} in dark red.$sec$eq " +
            "Full body visible, three-quarter view, centred, soft shadow, no text, no labels, no watermark."
    }

    /** What happened when asking for a picture. [code] is the HTTP status (0 = couldn't connect). */
    class Result(val ok: Boolean, val code: Int, val message: String?) {
        val rateLimited get() = code == 429 && message?.contains("quota", true) != true
        /** Wrong key, no credit, model not allowed: no point trying more pictures. */
        val fatal get() = code == 401 || code == 403 || (code == 429 && !rateLimited) || message?.contains("billing", true) == true
    }

    /** Makes a picture with OpenAI and saves it. */
    suspend fun generate(e: Exercise, p: Profile): Result = withContext(Dispatchers.IO) {
        if (p.apiKey.isBlank()) return@withContext Result(false, 401, "Add your OpenAI API key in Profile → Settings first.")
        val body = JSONObject()
            .put("model", p.imageModel.ifBlank { "gpt-image-1" })
            .put("prompt", prompt(e))
            .put("size", "1024x1024")
            .put("n", 1)
        if (p.imageQuality.isNotBlank()) body.put("quality", p.imageQuality)
        try {
            val conn = (URL("https://api.openai.com/v1/images/generations").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 240_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer ${p.apiKey.trim()}")
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrNull()
                return@withContext Result(false, code, "OpenAI said: ${msg ?: "error $code"}")
            }
            val item = JSONObject(text).getJSONArray("data").getJSONObject(0)
            val bytes = when {
                item.has("b64_json") -> Base64.decode(item.getString("b64_json"), Base64.DEFAULT)
                item.has("url") -> URL(item.getString("url")).openStream().use { it.readBytes() }
                else -> return@withContext Result(false, code, "OpenAI didn't send a picture back.")
            }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext Result(false, code, "Couldn't read the picture.")
            save(e.id, bmp)
            Result(true, code, null)
        } catch (t: Throwable) {
            Result(false, 0, "Couldn't reach OpenAI (${t.message ?: t.javaClass.simpleName}).")
        }
    }

    /** OpenAI's approximate price per 1024×1024 gpt-image-1 picture, in US dollars. */
    fun price(quality: String) = when (quality) { "low" -> 0.011; "high" -> 0.167; else -> 0.042 }
}

/** Round picture for an exercise: your picture if there is one, otherwise its muscle chart. */
@Composable
fun ExerciseThumb(e: Exercise?, size: Dp = 56.dp) {
    val version by Store.imageVersion.collectAsState()
    var img by remember(e?.id, version) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(e?.id, version) {
        img = if (e == null) null else withContext(Dispatchers.IO) { Pictures.load(e.id, version) }
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = img
        if (bmp != null) {
            Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (e != null) {
            BodyMapView(BodyMap.levelsFor(e), Modifier.fillMaxSize().padding(size / 14), BodyMap.bestView(e.primary))
        }
    }
}

/** Large picture for the exercise page. */
@Composable
fun ExercisePicture(e: Exercise, modifier: Modifier) {
    val version by Store.imageVersion.collectAsState()
    var img by remember(e.id, version) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(e.id, version) { img = withContext(Dispatchers.IO) { Pictures.load(e.id, version) } }
    Box(modifier.background(Color.White), contentAlignment = Alignment.Center) {
        val bmp = img
        if (bmp != null) Image(bmp, e.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else BodyMapView(BodyMap.levelsFor(e), Modifier.fillMaxSize().padding(12.dp))
    }
}

fun File.sizeOk() = exists() && length() > 0
