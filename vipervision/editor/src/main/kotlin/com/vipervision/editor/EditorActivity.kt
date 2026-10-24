package com.vipervision.editor

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.retainedComponent
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.t8rin.cropper.model.OutlineType
import com.t8rin.imagetoolbox.feature.crop.presentation.screenLogic.CropComponent
import com.t8rin.imagetoolbox.feature.draw.presentation.screenLogic.DrawComponent
import com.t8rin.imagetoolbox.feature.markup_layers.presentation.screenLogic.MarkupLayersComponent
import com.t8rin.imagetoolbox.feature.erase_background.presentation.screenLogic.EraseBackgroundComponent
import com.t8rin.neural_tools.bgremover.MobileSAM
import com.t8rin.imagetoolbox.core.domain.image.ImageCompressor
import com.t8rin.imagetoolbox.core.domain.image.ImageTransformer
import com.t8rin.imagetoolbox.core.domain.model.ImageModel
import com.t8rin.imagetoolbox.core.filters.domain.FilterProvider
import com.t8rin.imagetoolbox.core.filters.presentation.widget.FilterTemplateCreationSheetComponent
import com.t8rin.imagetoolbox.core.filters.presentation.widget.addFilters.AddFiltersSheetComponent
import com.t8rin.imagetoolbox.core.settings.presentation.model.toUiState
import com.t8rin.imagetoolbox.core.ui.utils.ComposeActivity
import com.t8rin.imagetoolbox.core.ui.utils.provider.ImageToolboxCompositionLocals
import com.t8rin.imagetoolbox.feature.ai_tools.domain.AiProgressListener
import com.t8rin.imagetoolbox.feature.ai_tools.domain.AiToolsRepository
import com.t8rin.imagetoolbox.feature.ai_tools.presentation.screenLogic.AiToolsComponent
import com.t8rin.neural_tools.inpaint.LaMaProcessor
import com.t8rin.opencv_tools.spot_heal.SpotHealer
import com.t8rin.opencv_tools.spot_heal.model.HealType
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
class EditorActivity : ComposeActivity() {

    private val controller = EditorController()

    @Inject
    lateinit var filterProvider: FilterProvider<Bitmap>

    @Inject
    lateinit var imageTransformer: ImageTransformer<Bitmap>

    @Inject
    lateinit var addFiltersFactory: AddFiltersSheetComponent.Factory

    @Inject
    lateinit var filterTemplatesFactory: FilterTemplateCreationSheetComponent.Factory

    /** Image Toolbox's Background Remover, driven by the Cut out tool. */
    @Inject
    lateinit var eraseBackgroundFactory: EraseBackgroundComponent.Factory

    /** Image Toolbox's Draw screen logic, driven by the Draw tool. */
    @Inject
    lateinit var drawFactory: DrawComponent.Factory

    /** Image Toolbox's Markup Layers screen logic, driven by "Text & layers". */
    @Inject
    lateinit var markupFactory: MarkupLayersComponent.Factory

    /** Image Toolbox's image encoder (every format it supports), for "Save as…". */
    @Inject
    lateinit var imageCompressor: ImageCompressor<Bitmap>

    /** Image Toolbox's Crop screen logic, driven by Advanced crop. */
    @Inject
    lateinit var cropFactory: CropComponent.Factory

    /** Image Toolbox's AI Tools screen logic, driven by "All AI models". */
    @Inject
    lateinit var aiToolsFactory: AiToolsComponent.Factory

    /** Set while asking whether to download MobileSAM for Cut out's Smart select. */
    private var pendingSamDownload by mutableStateOf(false)

    /** Adjustments and effects, shared by the preview and saving. */
    private val finisher by lazy { EditFinisher(filterProvider, imageTransformer) }

    /** Image Toolbox's pickers and sheets that the editor reuses. */
    private val toolbox: ToolboxComponents by lazy {
        retainedComponent { ToolboxComponents(it, addFiltersFactory, filterTemplatesFactory) }
    }

    private lateinit var source: Uri
    private var realPath: String? = null

    /** Image Toolbox's AI engine (the same one its AI Tools screen uses). */
    @Inject
    lateinit var aiRepository: AiToolsRepository<Bitmap>

    private val aiRunner by lazy { AiRunner(aiRepository) }

    /** AI tool waiting for the user to agree to its first-time model download. */
    private var pendingDownload by mutableStateOf<AiTool?>(null)

    /** Set while asking whether to download the Object eraser's LaMa model. */
    private var pendingLamaDownload by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val data = intent.data
        if (data == null) {
            super.onCreate(savedInstanceState)
            Toast.makeText(this, "No photo to edit", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        source = data
        realPath = intent.getStringExtra(EXTRA_REAL_PATH)
        super.onCreate(savedInstanceState)
    }

    /**
     * The editor, inside Image Toolbox's theme and settings (forced dark) so its pickers, sheets
     * and controls work here as they do in its own screens.
     */
    @Composable
    override fun Content() {
        if (!::source.isInitialized) return
        val source = source
        val realPath = realPath
        val thumbnail = remember(controller.workingPreview) { controller.workingPreview?.thumbnail() }
        ImageToolboxCompositionLocals(
            settingsState = settingsState.forEditor().toUiState(),
            filterPreviewModel = thumbnail?.let(::ImageModel),
            canSetDynamicFilterPreview = thumbnail != null
        ) {
            run {
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
                val lamaReady by LaMaProcessor.isDownloaded.collectAsState()

                EditorScreen(
                    controller = controller,
                    toolbox = toolbox,
                    finisher = finisher,
                    thumbnail = thumbnail,
                    aiReady = { tool -> downloaded.let { aiRunner.isReady(tool) } },
                    onRunAi = { tool ->
                        if (aiRunner.isReady(tool)) scope.runAi(source, tool) else pendingDownload = tool
                    },
                    lamaReady = lamaReady,
                    onStartObjectEraser = ::startObjectEraser,
                    onStartCutOut = { startCutOut(source) },
                    onApplyCutOut = { scope.applyCutOut() },
                    onCancelCutOut = ::closeCutOut,
                    onNeedSamDownload = { pendingSamDownload = true },
                    onStartAiStudio = { startAiStudio(source) },
                    onRunAiStudio = { scope.runAiStudio(source) },
                    onApplyAiStudio = { scope.applyAiStudio() },
                    onCancelAiStudio = ::closeAiStudio,
                    onStartDraw = { startDraw(source) },
                    onApplyDraw = { scope.applyDraw() },
                    onCancelDraw = ::closeDraw,
                    onStartMarkup = { startMarkup(source) },
                    onApplyMarkup = { scope.applyMarkup() },
                    onCancelMarkup = ::closeMarkup,
                    onStartCropStudio = { startCropStudio(source) },
                    onApplyCropStudio = { scope.applyCropStudio() },
                    onCancelCropStudio = ::closeCropStudio,
                    onApplyPaint = { scope.applyPaint(source) },
                    onSave = { scope.save(source, realPath) },
                    onSaveAs = { openSaveAs(source) },
                    onSaveWithOptions = {
                        val options = controller.saveAs?.options
                        controller.saveAs = null
                        scope.save(source, realPath, options)
                    },
                    onOpenMoreTools = { scope.openMoreTools(source) },
                    onClose = ::finish
                )

                if (pendingSamDownload) {
                    AlertDialog(
                        onDismissRequest = {
                            pendingSamDownload = false
                            controller.cutOut?.tool = CutTool.Auto
                        },
                        title = { Text("Download Smart select?") },
                        text = {
                            Text(
                                "Smart select uses the MobileSAM (Segment Anything) AI model: roughly paint over " +
                                        "what to keep and it finds the exact edge.\n\nIt needs a one-time download of " +
                                        "about 45 MB. After that it works offline."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                pendingSamDownload = false
                                scope.downloadSam()
                            }) { Text("Download") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                pendingSamDownload = false
                                controller.cutOut?.tool = CutTool.Auto
                            }) { Text("Cancel") }
                        }
                    )
                }

                if (pendingLamaDownload) {
                    AlertDialog(
                        onDismissRequest = { pendingLamaDownload = false },
                        title = { Text("Download Object eraser?") },
                        text = {
                            Text(
                                "Paint over something and the LaMa AI model fills in what was behind it.\n\n" +
                                        "It needs a one-time download of about 200 MB. After that it works offline."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                pendingLamaDownload = false
                                scope.downloadLama()
                            }) { Text("Download") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingLamaDownload = false }) { Text("Cancel") }
                        }
                    )
                }

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

    /** Opens "Save as…", working out the saved photo's size from the edits. */
    private fun openSaveAs(source: Uri) {
        val input = controller.workingFile?.let(Uri::fromFile) ?: source
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { contentResolver.openInputStream(input)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) } }
        val geometry = controller.state.geometry
        val upright = if (controller.workingFile != null) bounds.outWidth to bounds.outHeight else {
            // Photos from the camera are often stored sideways with an EXIF rotation.
            val preview = controller.basePreview
            if (preview != null && (preview.width > preview.height) != (bounds.outWidth > bounds.outHeight)) {
                bounds.outHeight to bounds.outWidth
            } else bounds.outWidth to bounds.outHeight
        }
        val (turnedWidth, turnedHeight) = geometry.turnedSize(upright.first.coerceAtLeast(1), upright.second.coerceAtLeast(1))
        controller.saveAs = SaveAsRequest(
            options = SaveOptions(),
            source = source,
            width = (turnedWidth * geometry.crop.width).toInt().coerceAtLeast(1),
            height = (turnedHeight * geometry.crop.height).toInt().coerceAtLeast(1)
        )
    }

    private fun CoroutineScope.save(source: Uri, realPath: String?, options: SaveOptions? = null) = launch {
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
                    finish = finishFor(state),
                    renderFrom = renderFrom,
                    forcePng = forcePng,
                    encode = options?.encoder(imageCompressor),
                    keepMetadata = options?.keepMetadata ?: true,
                    metadata = options?.metadataTags()
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
        val steps = controller.state.pixelSteps + PixelStep.Ai(tool)
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
            storeResult(output)
        }.onSuccess { result ->
            controller.addPixelStep(steps, result)
        }.onFailure {
            Toast.makeText(this@EditorActivity, "${tool.label} failed: ${it.message}", Toast.LENGTH_LONG).show()
        }
        controller.busyMessage = null
        controller.isBusy = false
    }

    /** Keeps a pixel step's full-size result on disk, plus a screen-sized copy for the preview. */
    private suspend fun storeResult(output: Bitmap): AiResult = withContext(Dispatchers.IO) {
        val folder = File(cacheDir, "vv_ai").apply { mkdirs() }
        val file = File(folder, "step_${System.nanoTime()}.png")
        file.outputStream().use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val metrics = resources.displayMetrics
        val side = min(2048, max(metrics.widthPixels, metrics.heightPixels))
        AiResult(file, output.scaledToFit(side))
    }

    /** Opens the Object eraser's painting mode, downloading the LaMa model first if needed (after asking). */
    private fun startObjectEraser() {
        if (LaMaProcessor.isDownloaded.value) {
            controller.paint = PaintSession(PaintPurpose.Erase)
        } else {
            pendingLamaDownload = true
        }
    }

    private fun CoroutineScope.downloadLama() = launch {
        if (controller.isBusy) return@launch
        controller.isBusy = true
        controller.busyMessage = "Downloading Object eraser…"
        runCatching {
            LaMaProcessor.startDownload().collect {
                controller.busyMessage = "Downloading Object eraser… ${(it.currentPercent * 100).toInt()}%"
            }
            check(LaMaProcessor.isDownloaded.value) { "the download didn't finish" }
        }.onSuccess {
            controller.paint = PaintSession(PaintPurpose.Erase)
        }.onFailure {
            Toast.makeText(this@EditorActivity, "Couldn't download the Object eraser: ${it.message}", Toast.LENGTH_LONG).show()
        }
        controller.busyMessage = null
        controller.isBusy = false
    }

    /**
     * Fills in what the user painted over, on the full-size photo: with Image Toolbox's
     * content-aware fill (Heal, OpenCV) or its LaMa AI model (Object eraser), exactly as its Draw
     * tool's Heal Spot mode does. The result becomes an undoable step.
     */
    private fun CoroutineScope.applyPaint(source: Uri) = launch {
        val session = controller.paint ?: return@launch
        val working = controller.workingPreview ?: return@launch
        if (controller.isBusy || session.strokes.isEmpty()) return@launch
        controller.isBusy = true
        val label = if (session.generative) "Object eraser" else "Heal"
        val strokes = controller.state.geometry.toSourceStrokes(
            strokes = session.strokes.toList(),
            shownWidthPx = session.shownWidthPx,
            sourceWidth = working.width,
            sourceHeight = working.height
        )
        controller.paint = null
        if (session.purpose == PaintPurpose.EffectMask) {
            // Painting where an effect applies: just remember the area (on the upright photo).
            controller.updateEffect(session.effectId ?: return@launch) { it.copy(mask = it.mask.orEmpty() + strokes) }
            controller.commit()
            controller.isBusy = false
            return@launch
        }
        val steps = controller.state.pixelSteps + PixelStep.Heal(strokes, session.generative)
        controller.busyMessage = "$label…"
        runCatching {
            val input = controller.workingFile?.let(Uri::fromFile) ?: source
            val full = withContext(Dispatchers.IO) { decodeImage(this@EditorActivity, input, maxSafeSide(this@EditorActivity, input)) }
            val output = withContext(Dispatchers.Default) {
                val mask = renderMask(strokes, full.width, full.height)
                if (session.generative) {
                    LaMaProcessor.inpaint(image = full, mask = mask) ?: error("the AI model couldn't process this photo")
                } else {
                    SpotHealer.heal(image = full, mask = mask, radius = 3f, type = HealType.TELEA)
                }
            }
            controller.busyMessage = "Finishing…"
            storeResult(output)
        }.onSuccess { result ->
            controller.addPixelStep(steps, result)
        }.onFailure {
            Toast.makeText(this@EditorActivity, "$label failed: ${it.message}", Toast.LENGTH_LONG).show()
        }
        controller.busyMessage = null
        controller.isBusy = false
    }

    private fun startDraw(source: Uri) {
        if (controller.isBusy || controller.draw != null) return
        val input = controller.workingFile?.let(Uri::fromFile) ?: source
        val lifecycle = LifecycleRegistry()
        val component = drawFactory(
            componentContext = DefaultComponentContext(lifecycle),
            initialUri = input,
            onGoBack = ::closeDraw,
            onNavigate = {}
        )
        lifecycle.resume()
        controller.draw = DrawSession(
            component = component,
            lifecycle = lifecycle,
            defaultColor = androidx.compose.ui.graphics.Color(settingsState.defaultDrawColor.colorInt),
            defaultWidth = settingsState.defaultDrawLineWidth
        )
    }

    private fun closeDraw() {
        controller.draw?.close()
        controller.draw = null
    }

    private fun CoroutineScope.applyDraw() = launch {
        val session = controller.draw ?: return@launch
        if (controller.isBusy) return@launch
        if (!session.component.havePaths) {
            closeDraw()
            return@launch
        }
        controller.isBusy = true
        controller.busyMessage = "Drawing…"
        runCatching {
            val output = session.component.getDrawingBitmap() ?: error("nothing was drawn")
            storeResult(output)
        }.onSuccess { result ->
            controller.addPixelStep(controller.state.pixelSteps + PixelStep.Drawing(), result)
            closeDraw()
        }.onFailure {
            Toast.makeText(this@EditorActivity, "Couldn't apply the drawing: ${it.message}", Toast.LENGTH_LONG).show()
        }
        controller.busyMessage = null
        controller.isBusy = false
    }

    private fun startMarkup(source: Uri) {
        if (controller.isBusy || controller.markup != null) return
        val input = controller.workingFile?.let(Uri::fromFile) ?: source
        val lifecycle = LifecycleRegistry()
        val component = markupFactory(
            componentContext = DefaultComponentContext(lifecycle),
            initialUri = input,
            onGoBack = ::closeMarkup,
            onNavigate = {}
        )
        lifecycle.resume()
        controller.markup = MarkupSession(component, lifecycle)
    }

    private fun closeMarkup() {
        controller.markup?.close()
        controller.markup = null
    }

    private fun CoroutineScope.applyMarkup() = launch {
        val session = controller.markup ?: return@launch
        if (controller.isBusy) return@launch
        if (session.component.layers.none { !it.isBackground }) {
            closeMarkup()
            return@launch
        }
        controller.isBusy = true
        controller.busyMessage = "Adding layers…"
        runCatching {
            val output = session.component.renderLayers(resources.configuration.fontScale) ?: error("nothing to add")
            storeResult(output)
        }.onSuccess { result ->
            controller.addPixelStep(controller.state.pixelSteps + PixelStep.Drawing(), result)
            closeMarkup()
        }.onFailure {
            Toast.makeText(this@EditorActivity, "Couldn't add the layers: ${it.message}", Toast.LENGTH_LONG).show()
        }
        controller.busyMessage = null
        controller.isBusy = false
    }

    private fun startCropStudio(source: Uri) {
        if (controller.isBusy || controller.cropStudio != null) return
        val input = controller.workingFile?.let(Uri::fromFile) ?: source
        val lifecycle = LifecycleRegistry()
        val component = cropFactory(
            componentContext = DefaultComponentContext(lifecycle),
            initialUri = input,
            onGoBack = ::closeCropStudio,
            onNavigate = {}
        )
        lifecycle.resume()
        controller.cropStudio = CropStudioSession(component, lifecycle)
    }

    private fun closeCropStudio() {
        controller.cropStudio?.close()
        controller.cropStudio = null
    }

    private fun CoroutineScope.applyCropStudio() = launch {
        val session = controller.cropStudio ?: return@launch
        val cropped = session.component.currentUri ?: return@launch
        if (controller.isBusy || !session.component.isBitmapChanged) return@launch
        controller.isBusy = true
        controller.busyMessage = "Cropping…"
        runCatching {
            val output = withContext(Dispatchers.IO) {
                decodeImage(this@EditorActivity, cropped, maxSafeSide(this@EditorActivity, cropped))
            }
            storeResult(output)
        }.onSuccess { result ->
            // Shaped crops leave transparent corners, so keep PNG for them.
            val shaped = session.component.cropProperties.cropOutlineProperty.outlineType != OutlineType.Rect
            controller.addPixelStep(controller.state.pixelSteps + PixelStep.Crop(alpha = shaped), result)
            closeCropStudio()
        }.onFailure {
            Toast.makeText(this@EditorActivity, "Couldn't crop: ${it.message}", Toast.LENGTH_LONG).show()
        }
        controller.busyMessage = null
        controller.isBusy = false
    }

    private fun startAiStudio(source: Uri) {
        if (controller.isBusy || controller.aiStudio != null) return
        val input = controller.workingFile?.let(Uri::fromFile) ?: source
        val lifecycle = LifecycleRegistry()
        val component = aiToolsFactory(
            componentContext = DefaultComponentContext(lifecycle),
            initialUris = listOf(input),
            onGoBack = ::closeAiStudio,
            onNavigate = {}
        )
        lifecycle.resume()
        controller.aiStudio = AiStudioSession(component, lifecycle)
    }

    private fun closeAiStudio() {
        controller.aiStudio?.close()
        controller.aiStudio = null
        aiRepository.cleanup()
    }

    /** Runs the selected model (with its settings) on the full-size photo, for preview. */
    private fun CoroutineScope.runAiStudio(source: Uri) = launch {
        val session = controller.aiStudio ?: return@launch
        val model = session.component.selectedModel.value ?: return@launch
        if (session.busyMessage != null) return@launch
        session.clearResult()
        session.busyMessage = "${model.title}…"
        val listener = object : AiProgressListener {
            override fun onError(error: String) {
                runOnUiThread { Toast.makeText(this@EditorActivity, error, Toast.LENGTH_LONG).show() }
            }

            override fun onProgress(currentChunkIndex: Int, totalChunks: Int) {
                if (totalChunks > 1) session.busyMessage = "${model.title}… ${min(currentChunkIndex + 1, totalChunks)} of $totalChunks"
            }
        }
        runCatching {
            val input = controller.workingFile?.let(Uri::fromFile) ?: source
            val full = withContext(Dispatchers.IO) { decodeImage(this@EditorActivity, input, maxSafeSide(this@EditorActivity, input)) }
            if (model.isAiDetector) {
                session.detection = aiRepository.detectAiImage(full, listener) ?: error("the model gave no answer")
            } else {
                val params = session.component.params
                check(!model.isStyleTransfer || params.auxiliaryImage != null) { "pick a style image first" }
                val output = aiRepository.processImage(full, listener, params) ?: error("the model gave no result")
                val metrics = resources.displayMetrics
                session.resultModelName = model.name
                session.resultNeedsAlpha = session.component.requiresAlphaOutput
                session.result = output
                session.resultPreview = output.scaledToFit(min(2048, max(metrics.widthPixels, metrics.heightPixels)))
            }
        }.onFailure {
            Toast.makeText(this@EditorActivity, "${model.title} failed: ${it.message}", Toast.LENGTH_LONG).show()
        }
        aiRepository.cleanup()
        session.busyMessage = null
    }

    private fun CoroutineScope.applyAiStudio() = launch {
        val session = controller.aiStudio ?: return@launch
        val output = session.result ?: return@launch
        if (controller.isBusy) return@launch
        controller.isBusy = true
        controller.busyMessage = "Finishing…"
        runCatching { storeResult(output) }
            .onSuccess { result ->
                controller.addPixelStep(
                    controller.state.pixelSteps + PixelStep.AiModel(session.resultModelName, session.resultNeedsAlpha),
                    result
                )
                closeAiStudio()
            }.onFailure {
                Toast.makeText(this@EditorActivity, "Couldn't keep the result: ${it.message}", Toast.LENGTH_LONG).show()
            }
        controller.busyMessage = null
        controller.isBusy = false
    }

    /** Opens Cut out on the upright photo, including earlier AI/heal steps. */
    private fun startCutOut(source: Uri) {
        if (controller.isBusy || controller.cutOut != null) return
        val input = controller.workingFile?.let(Uri::fromFile) ?: source
        val lifecycle = LifecycleRegistry()
        val component = eraseBackgroundFactory(
            componentContext = DefaultComponentContext(lifecycle),
            initialUri = input,
            onGoBack = ::closeCutOut,
            onNavigate = {}
        )
        lifecycle.resume()
        controller.cutOut = CutOutSession(component, lifecycle)
    }

    private fun closeCutOut() {
        controller.cutOut?.close()
        controller.cutOut = null
    }

    private fun CoroutineScope.applyCutOut() = launch {
        val session = controller.cutOut ?: return@launch
        if (controller.isBusy) return@launch
        controller.isBusy = true
        controller.busyMessage = "Cutting out…"
        runCatching {
            val output = session.component.getErasedBitmap(session.component.trimImage) ?: error("nothing to save")
            storeResult(output)
        }.onSuccess { result ->
            controller.addPixelStep(controller.state.pixelSteps + PixelStep.CutOut(), result)
            closeCutOut()
        }.onFailure {
            Toast.makeText(this@EditorActivity, "Cut out failed: ${it.message}", Toast.LENGTH_LONG).show()
        }
        controller.busyMessage = null
        controller.isBusy = false
    }

    private fun CoroutineScope.downloadSam() = launch {
        val session = controller.cutOut ?: return@launch
        session.busyMessage = "Downloading Smart select…"
        runCatching {
            MobileSAM.download { session.busyMessage = "Downloading Smart select… ${(it * 100).toInt()}%" }
            check(MobileSAM.isDownloaded()) { "the download didn't finish" }
        }.onSuccess {
            session.busyMessage = null
            // Re-run Smart select's setup now that the model is here.
            session.tool = CutTool.Auto
            session.tool = CutTool.Smart
        }.onFailure {
            session.busyMessage = null
            session.tool = CutTool.Auto
            Toast.makeText(this@EditorActivity, "Couldn't download Smart select: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun finishFor(state: EditState): FinishEdits = { turned, width, height ->
        finisher.finish(this, turned, state, width, height)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            File(cacheDir, "vv_ai").deleteRecursively()
            controller.cutOut?.close()
            controller.aiStudio?.close()
            controller.draw?.close()
            controller.markup?.close()
            controller.cropStudio?.close()
        }
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
                    saveForHandoff(this@EditorActivity, source, state.geometry, finishFor(state), renderFrom, forcePng)
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
