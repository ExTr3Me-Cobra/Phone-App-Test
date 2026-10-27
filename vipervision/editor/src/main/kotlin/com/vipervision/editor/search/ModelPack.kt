package com.vipervision.editor.search

import android.content.Context
import android.graphics.Bitmap
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.t8rin.imagetoolbox.core.domain.remote.DownloadManager
import com.t8rin.imagetoolbox.feature.ai_tools.domain.AiToolsRepository
import com.t8rin.imagetoolbox.feature.ai_tools.domain.model.NeuralModel
import com.t8rin.neural_tools.bgremover.BgRemover
import com.t8rin.neural_tools.bgremover.EdgeSAM
import com.t8rin.neural_tools.inpaint.LaMaProcessor
import com.t8rin.neural_tools.ocr.PaddleOCR
import com.vipervision.editor.AiTool
import com.vipervision.editor.ViperLog
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Every AI model Viper Vision uses, fetched once (about 3.7 GB) and kept through updates: the
 * editor's AI bubbles, Blemish remover, Manual background remover (all its models and Smart
 * select) and the AI search. Each comes from the app's own permanent copy first (the
 * "vipervision-ai-models" release; see AndroidDownloadManager.mirrorUrl), else its original site.
 * Each is fetched through its own tool, so it lands exactly where that tool looks for it.
 */
object ModelPack {

    class Item(val name: String, val size: Long, val isHere: (Context) -> Boolean, val fetch: suspend (Context, Deps, suspend (Float) -> Unit) -> Unit)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun downloadManager(): DownloadManager
        fun aiTools(): AiToolsRepository<Bitmap>
    }

    private fun deps(context: Context) = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)

    private suspend fun Flow<com.t8rin.imagetoolbox.core.domain.remote.DownloadProgress>.follow(onProgress: suspend (Float) -> Unit) =
        collect { onProgress(it.currentPercent) }

    private suspend fun Flow<com.t8rin.neural_tools.DownloadProgress>.followTool(onProgress: suspend (Float) -> Unit) =
        collect { onProgress(it.currentPercent) }

    private fun neural(model: NeuralModel) = Item(
        name = model.title,
        size = model.downloadSize,
        isHere = { File(File(it.filesDir, "ai_models"), model.name).let { f -> f.isFile && f.length() == model.downloadSize } },
        fetch = { _, deps, progress -> deps.aiTools().downloadModel(model).follow(progress) }
    )

    private fun remover(type: BgRemover.Type, title: String, size: Long) = Item(
        name = title,
        size = size,
        isHere = { BgRemover.getRemover(type).checkModel() },
        fetch = { _, _, progress -> BgRemover.downloadModel(type).followTool(progress) }
    )

    /** Everything, in the order it's fetched (the editor's bubbles first, search last). */
    val items: List<Item> by lazy {
        val editorModels = AiTool.entries.flatMap { it.choices }
            .filterNot { it.isBundled }
            .map { it.model }
            .distinctBy { it.name }
        editorModels.map(::neural) + listOf(
            Item("LaMa (Blemish remover)", 205_294_245, { LaMaProcessor.isDownloaded.value }) { _, _, progress ->
                LaMaProcessor.startDownload().followTool(progress)
            },
            Item("EdgeSAM (Smart select)", 38_035_306, { EdgeSAM.isDownloaded() }) { _, _, progress ->
                EdgeSAM.download(progress)
            },
            remover(BgRemover.Type.BiRefNet, "BiRefNet", 491_692_464),
            remover(BgRemover.Type.U2Net, "U2Net", 175_997_641),
            remover(BgRemover.Type.RMBG1_4, "RMBG 1.4", 88_704_616),
            remover(BgRemover.Type.InSPyReNet, "InSPyReNet", 395_316_574),
            remover(BgRemover.Type.ISNet, "ISNet", 178_647_984),
            remover(BgRemover.Type.MODNet, "MODNet", 25_888_640),
            remover(BgRemover.Type.YOLO, "YOLO v11", 249_033_763),
            remover(BgRemover.Type.MobileSAM, "MobileSAM", 44_658_416),
            Item("PaddleOCR (reads text, AI search)", 12_265_739, { PaddleOCR.isModelDownloaded(PaddleOCR.Model.English) }) { _, _, progress ->
                PaddleOCR.startDownload(PaddleOCR.Model.English).followTool(progress)
            },
            Item("SigLIP 2 (AI search)", Siglip.files.sumOf { it.size }, { Siglip.isDownloaded(it) }) { context, deps, progress ->
                Siglip.download(context, deps.downloadManager(), progress)
            }
        )
    }

    val totalSize: Long get() = items.sumOf { it.size }

    /** Bytes already here, and whether everything is. */
    fun state(context: Context): Pair<Long, Boolean> {
        val here = items.filter { runCatching { it.isHere(context) }.getOrDefault(false) }
        return here.sumOf { it.size } to (here.size == items.size)
    }

    private fun prefs(context: Context) = context.getSharedPreferences("Prefs", Context.MODE_PRIVATE)

    fun isRunning(context: Context) = prefs(context).getBoolean(KEY_RUNNING, false)
    fun progress(context: Context) = prefs(context).getFloat(KEY_PROGRESS, 0f)
    fun current(context: Context) = prefs(context).getString(KEY_CURRENT, null)
    fun lastError(context: Context) = prefs(context).getString(KEY_ERROR, null)

    /** Starts (or continues) fetching everything missing, in the background. */
    fun start(context: Context, allowMobileData: Boolean) {
        prefs(context).edit().putBoolean(KEY_PROMPTED, true).remove(KEY_ERROR).apply()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<Worker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (allowMobileData) NetworkType.CONNECTED else NetworkType.UNMETERED)
                        .build()
                )
                .build()
        )
    }

    class Worker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            val context = applicationContext
            val deps = deps(context)
            val prefs = prefs(context)
            prefs.edit().putBoolean(KEY_RUNNING, true).apply()
            val total = totalSize.toFloat()
            var done = 0L
            try {
                for (item in items) {
                    if (isStopped) return@withContext Result.retry()
                    if (runCatching { item.isHere(context) }.getOrDefault(false)) {
                        done += item.size
                        continue
                    }
                    ViperLog.i("MODELS", "Downloading ${item.name}")
                    prefs.edit().putString(KEY_CURRENT, item.name).apply()
                    var lastShown = -1
                    item.fetch(context, deps) { fraction ->
                        val overall = (done + fraction.coerceIn(0f, 1f) * item.size) / total
                        val percent = (overall * 100).toInt()
                        if (percent != lastShown) {
                            lastShown = percent
                            prefs.edit().putFloat(KEY_PROGRESS, overall).apply()
                            setForegroundAsync(AiSearch.foreground(context, "Downloading AI models: $percent% (${item.name})", percent))
                        }
                    }
                    done += item.size
                }
                prefs.edit().putFloat(KEY_PROGRESS, 1f).putBoolean(KEY_COMPLETE, true).remove(KEY_CURRENT).apply()
                ViperLog.i("MODELS", "All AI models are here")
                // The search can start indexing now.
                AiSearch.onAppStart(context)
                Result.success()
            } catch (t: Throwable) {
                ViperLog.error("MODELS", "model download stopped", t)
                prefs.edit().putString(KEY_ERROR, t.message ?: t.javaClass.simpleName).apply()
                Result.retry()
            } finally {
                prefs.edit().putBoolean(KEY_RUNNING, false).apply()
            }
        }
    }

    private const val WORK = "vv_model_pack"
    // Shared with the gallery (it offers the download once and shows the status in Settings).
    const val KEY_PROMPTED = "vv_models_prompted"
    const val KEY_COMPLETE = "vv_models_complete"
    private const val KEY_RUNNING = "vv_models_running"
    private const val KEY_PROGRESS = "vv_models_progress"
    private const val KEY_CURRENT = "vv_models_current"
    private const val KEY_ERROR = "vv_models_error"
}
