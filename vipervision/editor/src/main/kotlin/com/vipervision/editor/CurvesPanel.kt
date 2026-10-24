package com.vipervision.editor

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.t8rin.curves.CurvesPresetCodec
import com.t8rin.curves.GPUImageToneCurveFilter
import com.t8rin.curves.ImageCurvesEditor
import com.t8rin.curves.ImageCurvesEditorLayout
import com.t8rin.curves.ImageCurvesEditorState
import jp.co.cyberagent.android.gpuimage.GPUImage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

/** Applies tone curves (Image Toolbox's Curves tool) to [image]. */
internal fun applyCurves(context: Context, image: Bitmap, curves: ImageCurvesEditorState?): Bitmap {
    if (curves == null || curves.isDefault()) return image
    return GPUImage(context).apply {
        setImage(image)
        setFilter(GPUImageToneCurveFilter(curves))
    }.bitmapWithFilterApplied ?: image
}

/**
 * Image Toolbox's Curves editor (RGB and per-channel curves with a histogram of the photo), plus
 * its preset import/export (.itcurve, Photoshop .acv and more), under the photo.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun CurvesPanel(controller: EditorController, histogramSource: Bitmap?) {
    val context = LocalContext.current
    val curves = controller.state.curves ?: ImageCurvesEditorState.Default

    LaunchedEffect(Unit) {
        snapshotFlow { controller.state.curves }.debounce(700).collect { controller.commit() }
    }
    // Quick preview while a curve point is being dragged; full quality once it rests.
    LaunchedEffect(Unit) {
        snapshotFlow { controller.state.curves }.debounce(250).collect { controller.isInteracting = false }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("can't read it")
            require(bytes.size <= 4 * 1024 * 1024) { "the preset is too large" }
            CurvesPresetCodec.decode(bytes)
        }.onSuccess {
            controller.change(controller.state.copy(curves = it))
        }.onFailure {
            Toast.makeText(context, "Couldn't import the curves: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(CurvesPresetCodec.encode(curves).toByteArray(Charsets.UTF_8))
            } ?: error("can't write there")
        }.onFailure {
            Toast.makeText(context, "Couldn't export the curves: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        ImageCurvesEditor(
            bitmap = histogramSource,
            state = curves,
            onStateChange = {
                controller.isInteracting = true
                controller.preview(controller.state.copy(curves = it))
            },
            imageObtainingTrigger = false,
            onImageObtained = {},
            modifier = Modifier.fillMaxWidth(),
            containerModifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            layout = ImageCurvesEditorLayout.Separate,
            showImagePreview = false,
            editorHeight = 200.dp,
            backgroundColor = MaterialTheme.colorScheme.surfaceContainer
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Reset", false) { controller.change(controller.state.copy(curves = null)) }
            Pill("Import", false) { importer.launch(arrayOf("*/*")) }
            Pill("Export", false) { exporter.launch("ViperVision_curves.itcurve") }
            Pill("Done", true, filled = true) {
                controller.commit()
                controller.curvesOpen = false
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
