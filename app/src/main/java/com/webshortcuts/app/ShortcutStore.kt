package com.webshortcuts.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A shortcut this app created. */
data class WebShortcut(
    val id: String,
    val label: String,
    val url: String,
    val style: IconStyle,
    /** Changes whenever the icon changes, so cached thumbnails refresh. */
    val updatedAt: Long = System.currentTimeMillis(),
    /** Belongs to the blank-icon launcher entry, for an invisible badge (see [BlankBadge]). */
    val blankBadge: Boolean = false,
)

/**
 * Local storage: the list as JSON, plus for each shortcut the source image (so it can be
 * reframed later) and the rendered icon (for the list thumbnails).
 */
class ShortcutStore(context: Context) {
    private val dir = context.applicationContext.filesDir
    private val listFile = File(dir, "shortcuts.json")
    private val imageDir = File(dir, "images")
    private val iconDir = File(dir, "icons")

    fun load(): List<WebShortcut> {
        if (!listFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(listFile.readText())
            (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun upsert(shortcut: WebShortcut) {
        val list = load().toMutableList()
        val index = list.indexOfFirst { it.id == shortcut.id }
        if (index >= 0) list[index] = shortcut else list.add(shortcut)
        write(list)
    }

    fun delete(id: String) {
        write(load().filterNot { it.id == id })
        sourceFile(id).delete()
        iconFile(id).delete()
    }

    fun saveSource(id: String, bitmap: Bitmap) = savePng(sourceFile(id), bitmap)

    fun loadSource(id: String): Bitmap? = decode(sourceFile(id))

    fun saveIcon(id: String, bitmap: Bitmap) = savePng(iconFile(id), bitmap)

    fun loadIcon(id: String): Bitmap? = decode(iconFile(id))

    private fun sourceFile(id: String) = File(imageDir, "$id.png")

    private fun iconFile(id: String) = File(iconDir, "$id.png")

    private fun decode(file: File): Bitmap? =
        if (file.exists()) BitmapFactory.decodeFile(file.path) else null

    private fun savePng(file: File, bitmap: Bitmap) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        tmp.renameTo(file)
    }

    private fun write(list: List<WebShortcut>) {
        val array = JSONArray()
        list.forEach { array.put(toJson(it)) }
        val tmp = File(listFile.path + ".tmp")
        tmp.writeText(array.toString())
        tmp.renameTo(listFile)
    }

    private fun toJson(s: WebShortcut) = JSONObject()
        .put("id", s.id)
        .put("label", s.label)
        .put("url", s.url)
        .put("mode", s.style.mode.name)
        .put("background", s.style.backgroundColor)
        .put("zoom", s.style.zoom.toDouble())
        .put("offsetX", s.style.offsetX.toDouble())
        .put("offsetY", s.style.offsetY.toDouble())
        .put("updatedAt", s.updatedAt)
        .put("blankBadge", s.blankBadge)

    private fun fromJson(o: JSONObject) = WebShortcut(
        id = o.getString("id"),
        label = o.getString("label"),
        url = o.getString("url"),
        style = IconStyle(
            mode = runCatching { FitMode.valueOf(o.getString("mode")) }.getOrDefault(FitMode.FILL),
            backgroundColor = o.optInt("background", android.graphics.Color.WHITE),
            zoom = o.optDouble("zoom", 1.0).toFloat(),
            offsetX = o.optDouble("offsetX", 0.0).toFloat(),
            offsetY = o.optDouble("offsetY", 0.0).toFloat(),
        ),
        updatedAt = o.optLong("updatedAt", 0L),
        blankBadge = o.optBoolean("blankBadge", false),
    )
}
