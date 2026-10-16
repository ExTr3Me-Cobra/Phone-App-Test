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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

/** The muscle heatmap: your body picture with muscles shaded white → light red → dark red. */
object BodyMap {
    enum class View(val box: FloatArray) {
        BOTH(floatArrayOf(60f, 70f, 1022f, 910f)),
        FRONT(BodyMapData.frontBox),
        BACK(BodyMapData.backBox),
        FRONT_UPPER(floatArrayOf(95f, 160f, 475f, 540f)),
        FRONT_LOWER(floatArrayOf(95f, 450f, 475f, 830f)),
        BACK_UPPER(floatArrayOf(610f, 160f, 990f, 540f)),
        BACK_LOWER(floatArrayOf(610f, 450f, 990f, 830f)),
    }

    @Volatile private var base: Bitmap? = null

    fun init(c: Context) {
        if (base == null) base = runCatching { c.assets.open("body.jpg").use { BitmapFactory.decodeStream(it) } }.getOrNull()
    }

    private val cache = object : LruCache<String, Bitmap>(28 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun path(r: BodyMapData.Region, mirrorX: Float?): Path {
        val pts = r.pts
        fun x(i: Int): Float { val v = pts[2 * i]; return if (mirrorX != null) 2 * mirrorX - v else v }
        val p = Path()
        p.moveTo(x(0), pts[1])
        for (i in 1 until pts.size / 2) p.lineTo(x(i), pts[2 * i + 1])
        p.close()
        return p
    }

    private class Shape(val muscle: String, val path: Path, val front: Boolean)

    private val shapes: List<Shape> by lazy {
        BodyMapData.regions.flatMap { r ->
            listOfNotNull(Shape(r.muscle, path(r, null), r.front), r.mirrorX?.let { Shape(r.muscle, path(r, it), r.front) })
        }
    }

    private val LEGS = setOf("quadriceps", "hamstrings", "calves", "glutes", "adductors", "abductors", "shins", "hip flexors")

    /** The best crop for an exercise's small picture. */
    fun thumbView(e: Exercise): View {
        val muscles = e.muscles.filterValues { it >= 0.6f }.keys
        val front = shapes.count { it.front && it.muscle in muscles }
        val back = shapes.count { !it.front && it.muscle in muscles }
        val legs = e.primary.firstOrNull() in LEGS
        return when {
            back > front -> if (legs) View.BACK_LOWER else View.BACK_UPPER
            else -> if (legs) View.FRONT_LOWER else View.FRONT_UPPER
        }
    }

    // White -> pale pink -> salmon -> red -> deep dark red.
    private val STOPS = floatArrayOf(0f, 0.12f, 0.35f, 0.65f, 1f)
    private val COLORS = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFD2CB.toInt(), 0xFFF4887C.toInt(), 0xFFCF3029.toInt(), 0xFF780006.toInt())

    /** 0 = not worked (white) … 1 = worked hardest (dark red), with every shade in between. */
    fun heat(v: Float): Int {
        if (v <= 0f) return COLORS[0]
        val t = v.coerceIn(0f, 1f)
        for (i in 1 until STOPS.size) {
            if (t <= STOPS[i]) {
                val f = (t - STOPS[i - 1]) / (STOPS[i] - STOPS[i - 1])
                val a = COLORS[i - 1]; val b = COLORS[i]
                fun ch(sh: Int) = (((a shr sh) and 255) + (((b shr sh) and 255) - ((a shr sh) and 255)) * f).toInt()
                return android.graphics.Color.rgb(ch(16), ch(8), ch(0))
            }
        }
        return COLORS.last()
    }

    /**
     * Draws the picture with [levels] (muscle -> 0..1), cropped to [view], about [widthPx] wide.
     * With [colors] (muscle -> colour), muscles are shaded in those colours instead (rank map).
     */
    fun render(levels: Map<String, Float>, view: View, widthPx: Int, colors: Map<String, Int>? = null): Bitmap? {
        val src = base ?: return null
        val key = "${view.name}:$widthPx:" + levels.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${"%.3f".format(it.value)}" } +
            (colors?.entries?.sortedBy { it.key }?.joinToString(",", prefix = "|") { "${it.key}=${it.value}" } ?: "")
        cache.get(key)?.let { return it }
        val box = view.box
        val bw = box[2] - box[0]; val bh = box[3] - box[1]
        val s = (widthPx / bw).coerceIn(0.05f, 1.5f)
        val w = (bw * s).toInt().coerceAtLeast(1); val h = (bh * s).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        c.scale(s, s); c.translate(-box[0], -box[1])
        c.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        if (levels.values.any { it > 0f }) {
            val tint = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val tc = android.graphics.Canvas(tint)
            tc.drawColor(android.graphics.Color.WHITE)
            tc.scale(s, s); tc.translate(-box[0], -box[1])
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                maskFilter = android.graphics.BlurMaskFilter((3f * s).coerceAtLeast(0.6f), android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            // Lighter shades first so darker muscles win where shapes touch.
            for (sh in shapes.sortedBy { levels[it.muscle] ?: 0f }) {
                val v = levels[sh.muscle] ?: 0f
                if (v <= 0f) continue
                paint.color = colors?.get(sh.muscle) ?: heat(v)
                tc.drawPath(sh.path, paint)
            }
            android.graphics.Canvas(out).drawBitmap(tint, 0f, 0f, Paint().apply {
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.MULTIPLY)
            })
            tint.recycle()
        }
        cache.put(key, out)
        return out
    }

    fun levelsFor(e: Exercise): Map<String, Float> = e.muscles
}

/** The body picture with a heatmap, fitted into [modifier]'s space. */
@Composable
fun BodyMapView(levels: Map<String, Float>, modifier: Modifier, view: BodyMap.View = BodyMap.View.BOTH, colors: Map<String, Int>? = null) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val px = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx().toInt().coerceIn(64, 1100) }
        val bmp by androidx.compose.runtime.produceState<ImageBitmap?>(null, levels, view, px, colors) {
            value = withContext(Dispatchers.Default) { BodyMap.render(levels, view, px, colors)?.asImageBitmap() }
        }
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
    }
}

/** Exercise pictures saved on the phone (your own, or made by AI). */
object Pictures {
    private val cache = object : LruCache<String, ImageBitmap>(48) {}

    fun load(id: String, version: Int): ImageBitmap? {
        val key = "$id#$version"
        cache.get(key)?.let { return it }
        val f = Store.imageFile(id)
        // Your own / generated picture first, then one built into the app.
        val bmp = runCatching {
            if (f.exists()) BitmapFactory.decodeFile(f.path)?.asImageBitmap()
            else Store.bundledImage(id)?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        }.getOrNull() ?: return null
        cache.put(key, bmp)
        return bmp
    }

    /** Saved pictures are small: square, 512 x 512, compressed (about 30–60 KB each). */
    private const val SIZE = 512

    private fun save(id: String, bmp: Bitmap) {
        val side = minOf(bmp.width, bmp.height)
        val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        val out = if (side > SIZE) Bitmap.createScaledBitmap(square, SIZE, SIZE, true) else square
        Store.imageFile(id).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        Store.imageChanged()
    }

    // ---- Which pictures are your own photos (no "AI generated" note); everything else is treated as AI-made. ----
    private const val OWN = "own_photos.json"
    private fun own(): Set<String> = runCatching {
        org.json.JSONArray(Store.readFile(OWN) ?: "[]").let { a -> List(a.length()) { a.getString(it) }.toSet() }
    }.getOrDefault(emptySet())
    fun isOwn(id: String) = Store.imageName(id) in own()
    fun markOwn(id: String, mine: Boolean) = setOwn(listOf(Store.imageName(id)), mine)
    private fun setOwn(names: Collection<String>, mine: Boolean) {
        val now = own().let { if (mine) it + names else it - names.toSet() }
        Store.writeFile(OWN, org.json.JSONArray(now.sorted()).toString())
    }

    /** Your own picture from the gallery or camera. */
    fun import(c: Context, id: String, uri: Uri) {
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.contentResolver, uri)) { d, info, _ ->
            val s = (maxOf(info.size.width, info.size.height) / 1024).coerceAtLeast(1)
            d.setTargetSize(info.size.width / s, info.size.height / s)
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        save(id, bmp)
        setOwn(listOf(Store.imageName(id)), true)
    }

    /** Removes an exercise's picture; it goes to the Recycle Bin. */
    fun remove(id: String) {
        Store.exercise(id)?.let { RecycleBin.picture(it) }
        setOwn(listOf(Store.imageName(id)), false)
        Store.imageFile(id).delete()
        Store.imageChanged()
    }

    private fun muscleName(m: String) = when (m) {
        "forearms (finger flexors)" -> "finger flexors of the forearm (grip)"
        "serratus (serratus anterior)" -> "serratus anterior"
        else -> m
    }

    fun prompt(e: Exercise): String {
        val ms = e.muscles.entries.sortedByDescending { it.value }
        fun list(m: List<Map.Entry<String, Float>>) = m.joinToString(", ") { muscleName(it.key) }
        val main = ms.filter { it.value >= 0.7f }.ifEmpty { ms.take(1) }
        val helpers = ms.filter { it.value in 0.25f..0.7f && it !in main }
        val sec = if (helpers.isNotEmpty()) " Shade the ${list(helpers)} in light red." else ""
        val eq = when (e.equipment) {
            "Bodyweight", "Other" -> ""
            else -> " Show the ${e.equipment.lowercase()} and any bench or machine in matte black and dark grey."
        }
        return "A single, clean, highly detailed 3D anatomical fitness illustration on a pure white background, " +
            "showing exactly ONE person: a fit, muscular man with short dark hair, wearing only black athletic shorts, barefoot, " +
            "performing the exercise \"${e.name}\" with correct form, shown at the most recognisable point of the movement. " +
            "His body is rendered in light grey monochrome with clearly defined individual muscles and fine muscle-fibre detail, " +
            "like a medical anatomy drawing. Highlight the ${list(main)} in dark red.$sec$eq " +
            "One figure in one scene only: no second person, no extra views of him, no close-up or inset of a body part, " +
            "no split panels, no collage, no diagrams. " +
            "Square 1:1 image, full body visible, three-quarter view, centred, soft shadow, no text, no labels, no watermark."
    }

    /** What happened when asking for a picture. [code] is the HTTP status (0 = couldn't connect). */
    class Result(val ok: Boolean, val code: Int, val message: String?) {
        val rateLimited get() = code == 429 && message?.contains("quota", true) != true
        /** Wrong key, no credit, model not allowed: no point trying more pictures. */
        val fatal get() = code == 401 || code == 403 || (code == 429 && !rateLimited) || message?.contains("billing", true) == true
    }

    /** The exercise's current picture as JPEG bytes (yours, or the one built into the app), or null. */
    private fun currentBytes(id: String): ByteArray? = runCatching {
        val f = Store.imageFile(id)
        if (f.exists()) f.readBytes() else Store.bundledImage(id)?.use { it.readBytes() }
    }.getOrNull()

    /** The same picture request, sent with the exercise's current picture as a reference (OpenAI's image edit). */
    private fun editRequest(p: Profile, prompt: String, image: ByteArray): HttpURLConnection {
        val boundary = "----loadout" + System.currentTimeMillis()
        val conn = (URL("https://api.openai.com/v1/images/edits").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 30_000; readTimeout = 240_000; doOutput = true
            setRequestProperty("Authorization", "Bearer ${p.apiKey.trim()}")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        conn.outputStream.use { os ->
            fun field(name: String, value: String) =
                os.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
            field("model", p.imageModel.ifBlank { "gpt-image-1" })
            field("prompt", prompt)
            field("size", "1024x1024")
            field("n", "1")
            if (p.imageQuality.isNotBlank()) field("quality", p.imageQuality)
            os.write("--$boundary\r\nContent-Disposition: form-data; name=\"image\"; filename=\"reference.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray())
            os.write(image)
            os.write("\r\n--$boundary--\r\n".toByteArray())
        }
        return conn
    }

    /** Makes a picture with OpenAI and saves it ([reference]: send the current picture along with the prompt). */
    suspend fun generate(e: Exercise, p: Profile, promptText: String = prompt(e), reference: Boolean = false): Result = withContext(Dispatchers.IO) {
        if (p.apiKey.isBlank()) return@withContext Result(false, 401, "Add your OpenAI API key in Settings first.")
        val body = JSONObject()
            .put("model", p.imageModel.ifBlank { "gpt-image-1" })
            .put("prompt", promptText.ifBlank { prompt(e) })
            .put("size", "1024x1024")
            .put("n", 1)
        if (p.imageQuality.isNotBlank()) body.put("quality", p.imageQuality)
        // A compressed JPEG is a much smaller download (gpt-image models only).
        if (p.imageModel.ifBlank { "gpt-image-1" }.startsWith("gpt-image")) {
            body.put("output_format", "jpeg").put("output_compression", 80)
        }
        try {
            val ref = if (reference) currentBytes(e.id) ?: return@withContext Result(false, 0, "This exercise has no picture to use as a reference.") else null
            val conn = if (ref != null) editRequest(p, promptText.ifBlank { prompt(e) }, ref) else
                (URL("https://api.openai.com/v1/images/generations").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 30_000
                    readTimeout = 240_000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer ${p.apiKey.trim()}")
                    setRequestProperty("Content-Type", "application/json")
                }
            if (ref == null) conn.outputStream.use { it.write(body.toString().toByteArray()) }
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
            setOwn(listOf(Store.imageName(e.id)), false)
            Result(true, code, null)
        } catch (t: Throwable) {
            Result(false, 0, "Couldn't reach OpenAI (${t.message ?: t.javaClass.simpleName}).")
        }
    }

    /**
     * Saves your pictures as zip files of up to ~20 MB each (small enough for GitHub's upload
     * page) into [folder]. Returns how many pictures and files.
     */
    fun exportPack(c: Context, folder: Uri): Pair<Int, Int> {
        val files = Store.imagesDir().listFiles { f -> f.name.endsWith(".jpg") }?.sortedBy { it.name } ?: emptyList()
        val parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(
            folder, android.provider.DocumentsContract.getTreeDocumentId(folder),
        )
        val limit = 20L * 1024 * 1024
        val parts = mutableListOf<MutableList<File>>(mutableListOf())
        var size = 0L
        for (f in files) {
            if (size + f.length() > limit && parts.last().isNotEmpty()) { parts += mutableListOf<File>(); size = 0 }
            parts.last() += f
            size += f.length()
        }
        val stamp = java.time.LocalDate.now().toString()
        parts.forEachIndexed { i, part ->
            val name = "iron-log-pictures-$stamp-part${i + 1}-of-${parts.size}.zip"
            val doc = android.provider.DocumentsContract.createDocument(c.contentResolver, parent, "application/zip", name)
                ?: error("Couldn't create $name")
            c.contentResolver.openOutputStream(doc)!!.use { os ->
                java.util.zip.ZipOutputStream(os).use { zip ->
                    // The first file also carries every exercise as it is now (names, directions, muscles, ...) –
                    // no routines, workouts or settings.
                    if (i == 0) {
                        zip.putNextEntry(java.util.zip.ZipEntry("exercises.json"))
                        val all = org.json.JSONArray(Store.exercises.value.values.sortedBy { it.name.lowercase() }.map { ex ->
                            ex.toJson().put("custom", ex.custom).put("edited", ex.edited).put("ownPhoto", isOwn(ex.id))
                        })
                        zip.write(all.toString(1).toByteArray())
                        zip.closeEntry()
                        // Your changes to the app's wording (developer: Edit app text), if any.
                        if (AppText.edits.isNotEmpty()) {
                            zip.putNextEntry(java.util.zip.ZipEntry("modified_app_text.json"))
                            zip.write(AppText.export().toString(1).toByteArray())
                            zip.closeEntry()
                        }
                    }
                    for (f in part) {
                        zip.putNextEntry(java.util.zip.ZipEntry("pictures/" + f.name))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
        return files.size to parts.size
    }

    /** Adds the pictures from picture-pack zips. Returns how many were added. */
    fun importPack(c: Context, uris: List<Uri>): Int {
        var n = 0
        for (u in uris) {
            c.contentResolver.openInputStream(u)?.use { input ->
                java.util.zip.ZipInputStream(input).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        val name = File(e.name).name
                        if (!e.isDirectory && name.endsWith(".jpg") && name.matches(Regex("[A-Za-z0-9_-]+\\.jpg"))) {
                            File(Store.imagesDir(), name).outputStream().use { zip.copyTo(it) }
                            setOwn(listOf(name), false)
                            n++
                        }
                        zip.closeEntry()
                    }
                }
            }
        }
        Store.imageChanged()
        return n
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
        Modifier.size(size).clip(CircleShape).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = img
        if (bmp != null) {
            Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (e != null) {
            BodyMapView(BodyMap.levelsFor(e), Modifier.fillMaxSize(), BodyMap.thumbView(e))
        }
    }
}

/** Exercise page: your picture (if there is one) shown large, and always the muscle heatmap. */
@Composable
fun ExercisePicture(e: Exercise, modifier: Modifier) {
    val version by Store.imageVersion.collectAsState()
    var img by remember(e.id, version) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(e.id, version) { img = withContext(Dispatchers.IO) { Pictures.load(e.id, version) } }
    androidx.compose.foundation.layout.Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        img?.let { bmp ->
            Box(Modifier.padding(top = 16.dp).fillMaxWidth(0.85f).aspectRatio(1f)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(Color.White)) {
                Image(bmp, e.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                // A small note on AI-made pictures (your own photos don't get it).
                if (remember(e.id, version) { !Pictures.isOwn(e.id) }) androidx.compose.material3.Text(
                    "AI generated image.\nMay be inaccurate", textAlign = androidx.compose.ui.text.style.TextAlign.End, lineHeight = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp), color = Color(0xFFF2EFE8), fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                    style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0x99000000), androidx.compose.ui.geometry.Offset(0f, 1f), 3f)),
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
        }
        BodyMapView(BodyMap.levelsFor(e), Modifier.padding(top = if (img != null) 12.dp else 0.dp).fillMaxWidth().height(320.dp).background(Color.Black))
        HeatKey(Modifier.background(Color.Black).padding(bottom = 8.dp))
    }
}

fun File.sizeOk() = exists() && length() > 0
