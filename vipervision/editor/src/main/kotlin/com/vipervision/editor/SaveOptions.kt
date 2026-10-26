package com.vipervision.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t8rin.imagetoolbox.core.domain.image.ImageCompressor
import com.t8rin.imagetoolbox.core.domain.image.Metadata
import com.t8rin.imagetoolbox.core.domain.image.clearAllAttributes
import com.t8rin.imagetoolbox.core.domain.image.clearAttribute
import com.t8rin.imagetoolbox.core.domain.image.model.ImageFormat
import com.t8rin.imagetoolbox.core.domain.image.model.ImageInfo
import com.t8rin.imagetoolbox.core.domain.image.model.ImageScaleMode
import com.t8rin.imagetoolbox.core.domain.image.model.MetadataTag
import com.t8rin.imagetoolbox.core.domain.image.model.Quality
import com.t8rin.imagetoolbox.core.domain.image.model.ResizeType
import com.t8rin.imagetoolbox.core.domain.model.IntegerSize
import com.t8rin.imagetoolbox.core.ui.utils.provider.LocalMetadataProvider
import com.t8rin.imagetoolbox.core.ui.widget.controls.ResizeImageField
import com.t8rin.imagetoolbox.core.ui.widget.controls.resize_group.ResizeTypeSelector
import com.t8rin.imagetoolbox.core.ui.widget.controls.selection.ImageFormatSelector
import com.t8rin.imagetoolbox.core.ui.widget.controls.selection.QualitySelector
import com.t8rin.imagetoolbox.core.ui.widget.controls.selection.ScaleModeSelector
import com.t8rin.imagetoolbox.core.ui.widget.sheets.EditExifSheet
import kotlin.math.roundToInt

/**
 * "Save as…": Image Toolbox's export options for the edited photo — any of its formats (JPEG, PNG
 * and its optimised variants, WebP, AVIF, HEIC, JXL, BMP, TIFF, QOI, ICO and more) with their
 * quality settings, resizing with its resize types and scaling algorithms, and the photo details
 * (keep, edit or remove).
 */
class SaveOptions {
    var format by mutableStateOf<ImageFormat?>(null)
    var quality by mutableStateOf<Quality>(Quality.Base(95))
    var resize by mutableStateOf(false)
    var width by mutableStateOf(0)
    var height by mutableStateOf(0)
    var resizeType by mutableStateOf<ResizeType>(ResizeType.Explicit)
    var scaleMode by mutableStateOf<ImageScaleMode>(ImageScaleMode.Default)
    var keepMetadata by mutableStateOf(true)

    /** Edited photo details, or null to keep the original's as they are. */
    var metadata by mutableStateOf<Metadata?>(null)
    var metadataEdited by mutableStateOf(false)

    /** Photo details to write after saving: every tag, null where removed. */
    fun metadataTags(): Map<String, String?>? = metadata?.takeIf { metadataEdited }?.let { m ->
        MetadataTag.entries.associate { it.key to m.getAttribute(it) }
    }

    /** Encodes the rendered photo with these options, using Image Toolbox's compressor. */
    fun encoder(compressor: ImageCompressor<Bitmap>): suspend (Bitmap, Boolean) -> EncodedPhoto = { image, isPng ->
        val format = format ?: if (isPng) ImageFormat.Png.Lossless else ImageFormat.Jpg
        val info = ImageInfo(
            width = if (resize && width > 0) width else image.width,
            height = if (resize && height > 0) height else image.height,
            quality = quality.coerceIn(format),
            imageFormat = format,
            resizeType = if (resize) resizeType else ResizeType.Explicit,
            imageScaleMode = scaleMode
        )
        val bytes = compressor.compressAndTransform(image = image, imageInfo = info)
        EncodedPhoto(bytes, format.extension, format.mimeType.entry)
    }
}

/** "Save as…" open for a photo of [width] x [height] (after the edits), from [source]. */
class SaveAsRequest(val options: SaveOptions, val source: Uri, val width: Int, val height: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SaveOptionsSheet(
    options: SaveOptions,
    source: Uri,
    photoWidth: Int,
    photoHeight: Int,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val metadataProvider = LocalMetadataProvider.current
    var showExif by remember { mutableStateOf(false) }

    LaunchedEffect(source) {
        if (options.metadata == null) options.metadata = runCatching { metadataProvider.readMetadata(source.toString()) }.getOrNull()
    }
    LaunchedEffect(photoWidth, photoHeight) {
        if (options.width == 0 || options.height == 0) {
            options.width = photoWidth
            options.height = photoHeight
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Save as…", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)

            ImageFormatSelector(
                value = options.format ?: ImageFormat.Jpg,
                quality = options.quality,
                onValueChange = {
                    options.format = it
                    options.quality = options.quality.coerceIn(it)
                }
            )
            val format = options.format ?: ImageFormat.Jpg
            if (format.canChangeCompressionValue) {
                QualitySelector(
                    imageFormat = format,
                    quality = options.quality,
                    onQualityChange = { options.quality = it }
                )
            }

            OptionSwitch("Resize", options.resize) { options.resize = it }
            if (options.resize) {
                ResizeImageField(
                    imageInfo = ImageInfo(width = options.width, height = options.height),
                    originalSize = IntegerSize(photoWidth, photoHeight),
                    onWidthChange = { w ->
                        options.width = w
                        if (options.resizeType == ResizeType.Explicit && photoWidth > 0) {
                            options.height = (w * photoHeight / photoWidth.toFloat()).roundToInt()
                        }
                    },
                    onHeightChange = { h ->
                        options.height = h
                        if (options.resizeType == ResizeType.Explicit && photoHeight > 0) {
                            options.width = (h * photoWidth / photoHeight.toFloat()).roundToInt()
                        }
                    }
                )
                ResizeTypeSelector(
                    enabled = true,
                    value = options.resizeType,
                    onValueChange = { options.resizeType = it }
                )
                ScaleModeSelector(
                    value = options.scaleMode,
                    onValueChange = { options.scaleMode = it }
                )
            }

            OptionSwitch("Keep photo details (date, camera, location)", options.keepMetadata) { options.keepMetadata = it }
            Pill(if (options.metadataEdited) "Photo details edited ✓" else "Edit photo details", options.metadataEdited) {
                showExif = true
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
            ) {
                Pill("Cancel", false, onClick = onDismiss)
                Pill("Save", true, filled = true, onClick = onSave)
            }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }

    EditExifSheet(
        visible = showExif,
        onDismiss = { showExif = false },
        exif = options.metadata,
        onClearExif = {
            options.metadata = options.metadata?.clearAllAttributes()
            options.metadataEdited = true
        },
        onUpdateTag = { tag, value ->
            options.metadata = options.metadata?.setAttribute(tag, value)
            options.metadataEdited = true
        },
        onRemoveTag = { tag ->
            options.metadata = options.metadata?.clearAttribute(tag)
            options.metadataEdited = true
        }
    )
}

@Composable
private fun OptionSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.Black)
        )
    }
}
