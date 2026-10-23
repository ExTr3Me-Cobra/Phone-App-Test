package com.vipervision.editor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
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
class EditorActivity : ComponentActivity() {

    private val controller = EditorController()

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

                EditorScreen(
                    controller = controller,
                    onSave = { scope.save(source, realPath) },
                    onOpenMoreTools = { scope.openMoreTools(source) },
                    onClose = ::finish
                )
            }
        }
    }

    private fun CoroutineScope.save(source: Uri, realPath: String?) = launch {
        if (controller.isBusy) return@launch
        controller.isBusy = true
        val state = controller.state
        runCatching {
            withContext(Dispatchers.IO) { saveEditedCopy(this@EditorActivity, source, realPath, state.geometry, state.adjustments) }
        }.onSuccess {
            Toast.makeText(this@EditorActivity, "Saved as a new photo", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK, Intent().setData(it))
            finish()
        }.onFailure {
            Toast.makeText(this@EditorActivity, "Couldn't save: ${it.message}", Toast.LENGTH_LONG).show()
        }
        controller.isBusy = false
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
                withContext(Dispatchers.IO) { saveForHandoff(this@EditorActivity, source, state.geometry, state.adjustments) }
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
