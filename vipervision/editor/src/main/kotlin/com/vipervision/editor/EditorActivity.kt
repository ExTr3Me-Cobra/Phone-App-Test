package com.vipervision.editor

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import com.t8rin.imagetoolbox.feature.ai_tools.domain.AiToolsRepository
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Viper Vision's photo editor: opened by Gallery's Edit button with the photo's uri as data and
 * its file path in [EXTRA_REAL_PATH]. Saves edits as a new photo next to the original.
 */
@AndroidEntryPoint
class EditorActivity : ComponentActivity() {

    private val controller = EditorController()

    /** Image Toolbox's AI engine (the same one its AI Tools screen uses). */
    @Inject
    lateinit var aiRepository: AiToolsRepository<Bitmap>

    private val aiRunner by lazy { AiRunner(aiRepository) }

    /** AI tool waiting for the user to agree to its first-time model download. */
    private var pendingDownload by mutableStateOf<AiTool?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val source = intent.data ?: run {
            Toast.makeText(this, "No photo to edit", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val realPath = intent.getStringExtra(EXTRA_REAL_PATH)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, surface = Color(0xFF1C1C1E))) {
                val scope = rememberCoroutineScope()

                LaunchedEffect(source) {
                    if (controller.basePreview != null) return@LaunchedEffect
                    val metrics = resources.displayMetrics
                    val side = min(2048, max(metrics.widthPixels, metrics.heightPixels))
                    runCatching { withContext(Dispatchers.IO) { decodeImage(this@EditorActivity, source, side) } }
                        .onSuccess { controller.basePreview = it }
                        .onFailure { controller.error = "Couldn't open this photo." }
                }

                // Re-read which models are downloaded whenever that list changes.
                val downloaded by aiRunner.downloadedModels.collectAsState()

                EditorScreen(
                    controller = controller,
                    aiReady = { tool -> downloaded.let { aiRunner.isReady(tool) } },
                    onRunAi = { tool ->
                        if (aiRunner.isReady(tool)) scope.runAi(source, tool) else pendingDownload = tool
                    },
                    onSave = { scope.save(source, realPath) },
                    onOpenMoreTools = { scope.openMoreTools(source) },
                    onClose = ::finish
                )

                pendingDownload?.let { tool ->
                    AlertDialog(
                        onDismissRequest = { pendingDownload = null },
                        title = { Text("Download ${tool.label}?") },
                        text = {
                            Text(
                                "${tool.description}\n\nThis AI tool needs a one-time download of " +
                                        "${tool.downloadSizeMb} MB. After that it works offline."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                pendingDownload = null
                                scope.runAi(source, tool)
                            }) { Text("Download") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingDownload = null }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }

    private fun CoroutineScope.save(source: Uri, realPath: String?) = launch {
        if (controller.isBusy) return@launch
        controller.isBusy = true
        val state = controller.state
        val renderFrom = controller.workingFile?.let(Uri::fromFile) ?: source
        val forcePng = controller.needsPng
        runCatching {
            withContext(Dispatchers.IO) {
                saveEditedCopy(
                    context = this@EditorActivity,
                    source = source,
                    realPath = realPath,
                    geometry = state.geometry,
                    adjustments = state.adjustments,
                    renderFrom = renderFrom,
                    forcePng = forcePng
                )
            }
        }.onSuccess {
            Toast.makeText(this@EditorActivity, "Saved as a new photo", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK, Intent().setData(it))
            finish()
        }.onFailure {
            Toast.makeText(this@EditorActivity, "Couldn't save: ${it.message}", Toast.LENGTH_LONG).show()
        }
        controller.isBusy = false
    }

    /**
     * Runs an AI tool on the full-size photo (downloading its model first if needed), then keeps
     * the result as a new step: the rotate, crop and adjustments apply on top of it, and undo
     * steps back to the photo before it.
     */
    private fun CoroutineScope.runAi(source: Uri, tool: AiTool) = launch {
        if (controller.isBusy) return@launch
        controller.isBusy = true
        val steps = controller.state.aiSteps + tool
        runCatching {
            if (!aiRunner.isReady(tool) || tool.isBundled) {
                controller.busyMessage = "Downloading ${tool.label}…"
                aiRunner.download(tool) { progress ->
                    controller.busyMessage = "Downloading ${tool.label}… ${(progress * 100).toInt()}%"
                }
            }
            controller.busyMessage = "${tool.label}…"
            val input = controller.workingFile?.let(Uri::fromFile) ?: source
            val full = withContext(Dispatchers.IO) { decodeImage(this@EditorActivity, input, maxSafeSide(this@EditorActivity, input)) }
            val output = aiRunner.run(tool, full) { done, total ->
                if (total > 1) controller.busyMessage = "${tool.label}… ${min(done + 1, total)} of $total"
            }
            controller.busyMessage = "Finishing…"
            withContext(Dispatchers.IO) {
                val folder = File(cacheDir, "vv_ai").apply { mkdirs() }
                val file = File(folder, "step_${System.nanoTime()}.png")
                file.outputStream().use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val metrics = resources.displayMetrics
                val side = min(2048, max(metrics.widthPixels, metrics.heightPixels))
                AiResult(file, output.scaledToFit(side))
            }
        }.onSuccess { result ->
            controller.addAiStep(steps, result)
        }.onFailure {
            Toast.makeText(this@EditorActivity, "${tool.label} failed: ${it.message}", Toast.LENGTH_LONG).show()
        }
        controller.busyMessage = null
        controller.isBusy = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) File(cacheDir, "vv_ai").deleteRecursively()
    }

    /** Opens Image Toolbox's tool picker with the photo, including any edits made here. */
    private fun CoroutineScope.openMoreTools(source: Uri) = launch {
        if (controller.isBusy) return@launch
        controller.isBusy = true
        val state = controller.state
        runCatching {
            if (state.isUnchanged) {
                source
            } else {
                val renderFrom = controller.workingFile?.let(Uri::fromFile) ?: source
                val forcePng = controller.needsPng
                withContext(Dispatchers.IO) {
                    saveForHandoff(this@EditorActivity, source, state.geometry, state.adjustments, renderFrom, forcePng)
                }
            }
        }.onSuccess { uri ->
            val intent = Intent(Intent.ACTION_EDIT)
                .setDataAndType(uri, "image/*")
                .setClassName(packageName, IMAGE_TOOLBOX_ACTIVITY)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { startActivity(intent) }.onFailure {
                Toast.makeText(this@EditorActivity, "Couldn't open the tools", Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            Toast.makeText(this@EditorActivity, "Couldn't prepare the photo: ${it.message}", Toast.LENGTH_LONG).show()
        }
        controller.isBusy = false
    }

    companion object {
        /** Absolute file path of the photo, so the edited copy can be saved in the same folder. */
        const val EXTRA_REAL_PATH = "com.vipervision.editor.REAL_PATH"

        /** Image Toolbox's main screen, which is part of this same app. */
        private const val IMAGE_TOOLBOX_ACTIVITY = "com.t8rin.imagetoolbox.app.presentation.AppActivity"
    }
}
