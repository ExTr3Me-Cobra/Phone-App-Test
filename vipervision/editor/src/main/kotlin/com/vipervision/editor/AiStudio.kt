package com.vipervision.editor

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.t8rin.imagetoolbox.core.resources.Icons
import com.t8rin.imagetoolbox.core.resources.icons.Close
import com.t8rin.imagetoolbox.core.resources.icons.Done
import com.t8rin.imagetoolbox.feature.ai_tools.domain.model.AiDetectionResult
import com.t8rin.imagetoolbox.feature.ai_tools.presentation.components.AiToolsControls
import com.t8rin.imagetoolbox.feature.ai_tools.presentation.screenLogic.AiToolsComponent
import kotlin.math.roundToInt

/**
 * "All AI models": Image Toolbox's whole AI Tools catalogue (upscalers, restoration, denoise,
 * de-JPEG, colorize, anime, scans, style transfer, depth effects, AI-image detection, background
 * removal, imported models) with every setting, run on the photo and kept as an undoable step.
 */
class AiStudioSession internal constructor(
    val component: AiToolsComponent,
    private val lifecycle: LifecycleRegistry
) {
    /** Result of the last run: full size, and a screen-sized copy. */
    var result by mutableStateOf<Bitmap?>(null)
        internal set
    var resultPreview by mutableStateOf<Bitmap?>(null)
        internal set

    /** Whether the result needs transparency (background removal, depth cut-outs). */
    var resultNeedsAlpha = false
        internal set
    var resultModelName: String = ""
        internal set

    var detection by mutableStateOf<AiDetectionResult?>(null)
        internal set
    var busyMessage by mutableStateOf<String?>(null)
        internal set

    fun clearResult() {
        result = null
        resultPreview = null
        detection = null
    }

    fun close() = lifecycle.destroy()
}

@Composable
internal fun AiStudioScreen(
    session: AiStudioSession,
    photo: Bitmap?,
    onCancel: () -> Unit,
    onRun: () -> Unit,
    onApply: () -> Unit
) {
    val component = session.component
    val selected by component.selectedModel.collectAsState()
    val downloaded by component.downloadedModels.collectAsState()
    var showOriginal by remember { mutableStateOf(false) }

    BackHandler(onBack = onCancel)

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Cancel", tint = Color.White) }
            Text("All AI models", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onApply, enabled = session.result != null) {
                Icon(Icons.Rounded.Done, "Apply", tint = if (session.result != null) Accent else Color.Gray)
            }
        }

        // The photo, or the model's result (hold to compare with the photo).
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        showOriginal = true
                        tryAwaitRelease()
                        showOriginal = false
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            val shown = if (showOriginal) photo else session.resultPreview ?: photo
            if (shown != null) {
                if (session.resultNeedsAlpha && !showOriginal && session.resultPreview != null) {
                    Checkerboard(Modifier.fillMaxSize(), shown.width, shown.height)
                }
                Image(shown.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            session.detection?.let { result ->
                val percent = (result.aiProbability * 100).roundToInt()
                Text(
                    when (result.verdict) {
                        AiDetectionResult.Verdict.LikelyAi -> "Likely AI-generated ($percent%)"
                        AiDetectionResult.Verdict.LikelyHuman -> "Likely a real photo ($percent% AI)"
                        else -> "Uncertain ($percent% AI)"
                    },
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (showOriginal && session.resultPreview != null) {
                Text(
                    "Before",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            session.busyMessage?.let {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(20.dp)
                ) {
                    CircularProgressIndicator(color = Accent)
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        }

        // Image Toolbox's model picker and settings.
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            AiToolsControls(component, showImageFormat = false)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
        ) {
            val model = selected
            val ready = model != null && downloaded.any { it.name == model.name }
            Pill(
                text = when {
                    model == null -> "Pick a model above"
                    !ready -> "Download ${model.title} above first"
                    model.isAiDetector -> "Check if AI-made"
                    else -> "Run ${model.title}"
                },
                selected = ready && session.busyMessage == null,
                filled = true
            ) { if (ready && session.busyMessage == null) onRun() }
        }
    }
}
