package com.vipervision.editor.search

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The AI search's index: for each photo (by its media library id), its SigLIP point and the text
 * read from it (OCR). Kept in the app's private storage; rebuilt for a photo when it changes.
 */
class SearchIndex private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "vv_ai_search.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE photos (id INTEGER PRIMARY KEY, modified INTEGER NOT NULL, " +
                    "embedding BLOB, ocr TEXT, ocr_done INTEGER NOT NULL DEFAULT 0)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** Every indexed photo's id and the modified time it was indexed at. */
    fun known(): Map<Long, Long> = readableDatabase.rawQuery("SELECT id, modified FROM photos", null).use { c ->
        HashMap<Long, Long>(c.count).apply { while (c.moveToNext()) put(c.getLong(0), c.getLong(1)) }
    }

    fun putEmbedding(id: Long, modified: Long, embedding: FloatArray) {
        val bytes = ByteBuffer.allocate(embedding.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        bytes.asFloatBuffer().put(embedding)
        writableDatabase.insertWithOnConflict("photos", null, ContentValues().apply {
            put("id", id)
            put("modified", modified)
            put("embedding", bytes.array())
            put("ocr_done", 0)
            putNull("ocr")
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun putText(id: Long, text: String) {
        writableDatabase.update("photos", ContentValues().apply {
            put("ocr", text)
            put("ocr_done", 1)
        }, "id = ?", arrayOf(id.toString()))
    }

    /** Photos whose text hasn't been read yet, newest first. */
    fun needingText(limit: Int): List<Pair<Long, Long>> =
        readableDatabase.rawQuery("SELECT id, modified FROM photos WHERE ocr_done = 0 ORDER BY modified DESC LIMIT $limit", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0) to c.getLong(1)) }
        }

    fun counts(): Triple<Int, Int, Int> = readableDatabase.rawQuery(
        "SELECT COUNT(*), SUM(embedding IS NOT NULL), SUM(ocr_done) FROM photos", null
    ).use { c -> if (c.moveToFirst()) Triple(c.getInt(0), c.getInt(1), c.getInt(2)) else Triple(0, 0, 0) }

    fun remove(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        writableDatabase.beginTransaction()
        try {
            for (id in ids) writableDatabase.delete("photos", "id = ?", arrayOf(id.toString()))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun clear() = writableDatabase.delete("photos", null, null)

    /** Everything needed to search, loaded at once (about 3 KB per photo). */
    class Snapshot(val ids: LongArray, val modified: LongArray, val embeddings: FloatArray, val texts: Array<String?>)

    fun snapshot(): Snapshot = readableDatabase.rawQuery(
        "SELECT id, modified, embedding, ocr FROM photos WHERE embedding IS NOT NULL", null
    ).use { c ->
        val n = c.count
        val ids = LongArray(n)
        val modified = LongArray(n)
        val embeddings = FloatArray(n * Siglip.DIMENSION)
        val texts = arrayOfNulls<String>(n)
        var row = 0
        while (c.moveToNext()) {
            ids[row] = c.getLong(0)
            modified[row] = c.getLong(1)
            ByteBuffer.wrap(c.getBlob(2)).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                .get(embeddings, row * Siglip.DIMENSION, Siglip.DIMENSION)
            texts[row] = c.getString(3)
            row++
        }
        Snapshot(ids, modified, embeddings, texts)
    }

    companion object {
        @Volatile private var instance: SearchIndex? = null
        fun get(context: Context): SearchIndex = instance ?: synchronized(this) {
            instance ?: SearchIndex(context).also { instance = it }
        }
    }
}
