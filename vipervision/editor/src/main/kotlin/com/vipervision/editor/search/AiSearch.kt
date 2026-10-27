package com.vipervision.editor.search

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.t8rin.neural_tools.ocr.PaddleOCR
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.vipervision.editor.ViperLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * The gallery's AI search (the magnifier button): two models that work together.
 *
 * - SigLIP 2 ([Siglip]) knows what's in each photo: objects, places, scenes, kinds of picture.
 * - PaddleOCR (English) reads any words in it: receipts, screenshots, signs, documents.
 *
 * A search scores every photo with both and adds them up, so a photo that SigLIP thinks is a
 * receipt *and* that has the searched words on it comes first, and each model also finds what
 * the other can't (a receipt with no recognisable look; a dog with no words).
 *
 * Photos are indexed once, in the background ([IndexWorker]); new ones are added as they come.
 */
object AiSearch {

    class Hit(val id: Long, val score: Float, val matchedText: Boolean)

    class Results(val hits: List<Hit>, val closestOnly: Boolean)

    /** Called when the app starts: keeps the index up to date if the models are here. */
    fun onAppStart(context: Context) {
        // Which AI models are already here (earlier builds downloaded them tool by tool).
        Thread { runCatching { ModelPack.refreshState(context) } }.start()
        runCatching {
            if (!Siglip.isDownloaded(context)) return
            schedule(context)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<IndexWorker>(6, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build()
            )
        }.onFailure { ViperLog.error("SEARCH", "couldn't schedule indexing", it) }
    }

    /** Indexes new and changed photos now (if not already running). */
    fun schedule(context: Context) {
        if (!Siglip.isDownloaded(context)) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<IndexWorker>().build()
        )
    }

    /** Progress for the search screen: photos in total, with pictures indexed, with text read. */
    fun progress(context: Context) = SearchIndex.get(context).counts()

    fun isIndexing(context: Context) = status(context).getBoolean(KEY_RUNNING, false)

    fun totalPhotos(context: Context) = status(context).getInt(KEY_TOTAL, 0)

    private fun status(context: Context) = context.getSharedPreferences("vv_ai_search", Context.MODE_PRIVATE)

    private var snapshot: SearchIndex.Snapshot? = null
    private var snapshotVersion = -1L

    /**
     * Photos matching [query], best first. If nothing clearly matches, the closest few are given
     * instead ([Results.closestOnly]).
     */
    suspend fun search(context: Context, query: String): Results = withContext(Dispatchers.Default) {
        val version = status(context).getLong(KEY_VERSION, 0L)
        val index = snapshot?.takeIf { snapshotVersion == version } ?: SearchIndex.get(context).snapshot().also {
            snapshot = it
            snapshotVersion = version
        }
        val count = index.ids.size
        if (count == 0 || query.isBlank()) return@withContext Results(emptyList(), false)

        val target = Siglip.embedText(context, query)
        val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }
        val scored = ArrayList<Hit>(count)
        val visual = FloatArray(count)
        for (i in 0 until count) {
            var dot = 0f
            val base = i * Siglip.DIMENSION
            for (d in 0 until Siglip.DIMENSION) dot += index.embeddings[base + d] * target[d]
            visual[i] = dot
            // Share of the searched words found in the photo's text.
            val text = index.texts[i]
            val textScore = if (text.isNullOrEmpty() || words.isEmpty()) 0f
            else words.count { text.contains(it) }.toFloat() / words.size
            val allWords = textScore >= 1f
            if (dot >= VISUAL_MATCH || allWords) {
                scored += Hit(index.ids[i], dot + TEXT_WEIGHT * textScore, allWords)
            }
        }
        if (scored.isNotEmpty()) {
            return@withContext Results(scored.sortedByDescending { it.score }.take(MAX_RESULTS), false)
        }
        // Nothing clear: the closest few, so there's always something to look at.
        val closest = (0 until count).sortedByDescending { visual[it] }.take(CLOSEST)
            .map { Hit(index.ids[it], visual[it], false) }
        Results(closest, true)
    }

    // The worker that builds the index.

    class IndexWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            val context = applicationContext
            if (!Siglip.isDownloaded(context) || !hasPhotoPermission(context)) return@withContext Result.success()
            // The "now" and the 6-hourly indexing can both be due at once; they take turns. (Run
            // side by side, the first to finish closed PaddleOCR while the other was still
            // reading a photo with it, a native crash.)
            indexing.withLock {
                status(context).edit().putBoolean(KEY_RUNNING, true).apply()
                try {
                    runCatching { setForeground(foreground(context, "Getting photos ready for AI search")) }
                    indexPictures(context)
                    readText(context)
                    Result.success()
                } catch (t: Throwable) {
                    ViperLog.error("SEARCH", "indexing stopped", t)
                    Result.retry()
                } finally {
                    status(context).edit().putBoolean(KEY_RUNNING, false).apply()
                    Siglip.close()
                    PaddleOCR.close()
                }
            }
        }

        /** SigLIP points for new and changed photos; removed photos leave the index. */
        private suspend fun indexPictures(context: Context) {
            val index = SearchIndex.get(context)
            val known = index.known()
            val photos = allPhotos(context)
            status(context).edit().putInt(KEY_TOTAL, photos.size).apply()
            index.remove(known.keys - photos.keys)
            val todo = photos.filter { (id, modified) -> known[id] != modified }.entries.sortedByDescending { it.value }
            ViperLog.i("SEARCH", "Indexing ${todo.size} of ${photos.size} photos")
            var done = 0
            for ((id, modified) in todo) {
                if (isStopped) return
                val bitmap = load(context, id, PICTURE_SIZE) ?: continue
                runCatching { index.putEmbedding(id, modified, Siglip.embedImage(context, bitmap)) }
                    .onFailure { ViperLog.error("SEARCH", "couldn't index photo $id", it) }
                bitmap.recycle()
                if (++done % 25 == 0) {
                    bump(context)
                    setForegroundAsync(foreground(context, "Getting photos ready for AI search: $done of ${todo.size}"))
                }
            }
            bump(context)
        }

        /** The words in each photo (OCR), newest first; pauses when the battery is low and not charging. */
        private suspend fun readText(context: Context) {
            if (!PaddleOCR.isModelDownloaded(PaddleOCR.Model.English)) return
            val index = SearchIndex.get(context)
            var done = 0
            while (!isStopped) {
                val batch = index.needingText(50)
                if (batch.isEmpty()) break
                for ((id, _) in batch) {
                    if (isStopped || batteryTooLow(context)) return
                    val bitmap = load(context, id, TEXT_SIZE)
                    val text = bitmap?.let {
                        runCatching { PaddleOCR.recognize(it, PaddleOCR.Model.English)?.text }.getOrNull()
                    }
                    bitmap?.recycle()
                    index.putText(id, text.orEmpty().lowercase().replace(Regex("\\s+"), " ").trim())
                    if (++done % 25 == 0) {
                        bump(context)
                        setForegroundAsync(foreground(context, "Reading text in photos for AI search: $done"))
                    }
                }
            }
            bump(context)
        }
    }

    /** Held by the one indexing run at a time (see IndexWorker.doWork). */
    private val indexing = Mutex()

    /** Tells an open search that the index changed. */
    private fun bump(context: Context) {
        status(context).edit().putLong(KEY_VERSION, System.currentTimeMillis()).apply()
    }

    private fun allPhotos(context: Context): Map<Long, Long> {
        val result = HashMap<Long, Long>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_MODIFIED),
            null, null, null
        )?.use { c -> while (c.moveToNext()) result[c.getLong(0)] = c.getLong(1) }
        return result
    }

    fun uriOf(id: Long) = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

    /** The photo at about [side] px (the media library's thumbnail where it can). */
    fun load(context: Context, id: Long, side: Int): Bitmap? = runCatching {
        val uri = uriOf(id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, Size(side, side), null)
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    }.getOrNull()

    private fun hasPhotoPermission(context: Context): Boolean {
        fun has(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager() -> true
            Build.VERSION.SDK_INT >= 33 -> has(Manifest.permission.READ_MEDIA_IMAGES)
            else -> has(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun batteryTooLow(context: Context): Boolean {
        val battery = context.getSystemService(BatteryManager::class.java) ?: return false
        return !battery.isCharging && battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) in 0..20
    }

    internal fun foreground(context: Context, text: String, progress: Int? = null): ForegroundInfo {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "AI search and models", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Viper Vision")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .apply { if (progress != null) setProgress(100, progress, false) }
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private const val NOW_WORK = "vv_ai_search_index"
    private const val PERIODIC_WORK = "vv_ai_search_index_periodic"
    private const val CHANNEL = "vv_ai_search"
    private const val NOTIFICATION_ID = 4711
    private const val KEY_RUNNING = "running"
    private const val KEY_TOTAL = "total"
    private const val KEY_VERSION = "version"
    private const val PICTURE_SIZE = 512
    private const val TEXT_SIZE = 1280
    private const val MAX_RESULTS = 300
    private const val CLOSEST = 12

    /** A photo counts as showing the search above this (tested: matches ≈ 0.09–0.14, others ≤ 0.065). */
    private const val VISUAL_MATCH = 0.07f

    /** How much the searched words being in the photo's text add (enough to put such photos first). */
    private const val TEXT_WEIGHT = 0.12f
}
