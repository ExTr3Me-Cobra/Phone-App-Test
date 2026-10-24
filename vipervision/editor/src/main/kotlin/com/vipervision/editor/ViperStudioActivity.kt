package com.vipervision.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.IntentCompat
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.retainedComponent
import com.t8rin.imagetoolbox.core.settings.presentation.model.toUiState
import com.t8rin.imagetoolbox.core.ui.utils.ComposeActivity
import com.t8rin.imagetoolbox.core.ui.utils.navigation.Screen
import com.t8rin.imagetoolbox.core.ui.utils.provider.ImageToolboxCompositionLocals
import com.t8rin.imagetoolbox.core.ui.utils.provider.LocalViperStyle
import com.t8rin.imagetoolbox.feature.compare.presentation.CompareContent
import com.t8rin.imagetoolbox.feature.compare.presentation.screenLogic.CompareComponent
import com.t8rin.imagetoolbox.feature.format_conversion.presentation.FormatConversionContent
import com.t8rin.imagetoolbox.feature.format_conversion.presentation.screenLogic.FormatConversionComponent
import com.t8rin.imagetoolbox.feature.gradient_maker.presentation.GradientMakerContent
import com.t8rin.imagetoolbox.feature.gradient_maker.presentation.screenLogic.GradientMakerComponent
import com.t8rin.imagetoolbox.feature.mesh_gradients.presentation.MeshGradientsContent
import com.t8rin.imagetoolbox.feature.mesh_gradients.presentation.screenLogic.MeshGradientsComponent
import com.t8rin.imagetoolbox.feature.multi_frame_fusion.presentation.MultiFrameFusionContent
import com.t8rin.imagetoolbox.feature.multi_frame_fusion.presentation.screenLogic.MultiFrameFusionComponent
import com.t8rin.imagetoolbox.feature.wallpapers_export.presentation.WallpapersExportContent
import com.t8rin.imagetoolbox.feature.wallpapers_export.presentation.screenLogic.WallpapersExportComponent
import com.t8rin.imagetoolbox.texture_generation.presentation.TextureGenerationContent
import com.t8rin.imagetoolbox.texture_generation.presentation.screenLogic.TextureGenerationComponent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Viper Vision's Studio: Image Toolbox's tools that work on several photos or make new pictures,
 * each fully featured and shown in Viper Vision's layout ([LocalViperStyle]). Opened from Gallery
 * (the Create menu, and the selection menu for Compare, Fuse frames and Convert).
 */
@AndroidEntryPoint
class ViperStudioActivity : ComposeActivity() {

    /** The Studio's tools; [needsPhotos] tells Gallery how many photos to pass. */
    enum class Tool(val needsPhotos: IntRange?) {
        Compare(2..2),
        Fusion(2..Int.MAX_VALUE),
        Convert(1..Int.MAX_VALUE),
        Gradient(null),
        MeshGradients(null),
        Texture(null),
        Wallpapers(null)
    }

    @Inject lateinit var compareFactory: CompareComponent.Factory
    @Inject lateinit var fusionFactory: MultiFrameFusionComponent.Factory
    @Inject lateinit var convertFactory: FormatConversionComponent.Factory
    @Inject lateinit var gradientFactory: GradientMakerComponent.Factory
    @Inject lateinit var meshFactory: MeshGradientsComponent.Factory
    @Inject lateinit var textureFactory: TextureGenerationComponent.Factory
    @Inject lateinit var wallpapersFactory: WallpapersExportComponent.Factory

    private lateinit var tool: Tool
    private var uris: List<Uri> = emptyList()

    /** The tool's screen logic, kept across rotation. */
    private val screen: Any by lazy {
        retainedComponent { context: ComponentContext -> createComponent(context) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        tool = runCatching { Tool.valueOf(intent.getStringExtra(EXTRA_TOOL).orEmpty()) }.getOrDefault(Tool.Gradient)
        uris = IntentCompat.getParcelableArrayListExtra(intent, EXTRA_URIS, Uri::class.java).orEmpty()
        super.onCreate(savedInstanceState)
    }

    private fun createComponent(context: ComponentContext): Any = when (tool) {
        Tool.Compare -> compareFactory(context, uris.takeIf { it.size >= 2 }?.let { it[0] to it[1] }, ::finish)
        Tool.Fusion -> fusionFactory(context, uris.ifEmpty { null }, ::finish, ::navigate)
        Tool.Convert -> convertFactory(context, uris.ifEmpty { null }, ::finish, ::navigate)
        Tool.Gradient -> gradientFactory(context, uris.ifEmpty { null }, ::finish, ::navigate)
        Tool.MeshGradients -> meshFactory(context, ::finish, ::navigate)
        Tool.Texture -> textureFactory(context, ::finish, ::navigate)
        Tool.Wallpapers -> wallpapersFactory(context, ::finish, ::navigate)
    }

    @Composable
    override fun Content() {
        ImageToolboxCompositionLocals(settingsState = settingsState.forEditor().toUiState()) {
            CompositionLocalProvider(LocalViperStyle provides true) {
                when (val component = screen) {
                    is CompareComponent -> CompareContent(component)
                    is MultiFrameFusionComponent -> MultiFrameFusionContent(component)
                    is FormatConversionComponent -> FormatConversionContent(component)
                    is GradientMakerComponent -> GradientMakerContent(component)
                    is MeshGradientsComponent -> MeshGradientsContent(component)
                    is TextureGenerationComponent -> TextureGenerationContent(component)
                    is WallpapersExportComponent -> WallpapersExportContent(component)
                }
            }
        }
    }

    /**
     * A tool's "edit with…" choice: single-photo tools open Viper Vision's editor, Studio tools
     * open here.
     */
    private fun navigate(target: Screen) {
        val studio: Pair<Tool, List<Uri>>? = when (target) {
            is Screen.Compare -> Tool.Compare to target.uris.orEmpty()
            is Screen.MultiFrameFusion -> Tool.Fusion to target.uris.orEmpty()
            is Screen.FormatConversion -> Tool.Convert to target.uris.orEmpty()
            is Screen.GradientMaker -> Tool.Gradient to target.uris.orEmpty()
            else -> null
        }
        if (studio != null) {
            startActivity(intent(this, studio.first, studio.second))
            return
        }
        val photo: Uri? = when (target) {
            is Screen.SingleEdit -> target.uri
            is Screen.Crop -> target.uri
            is Screen.Draw -> target.uri
            is Screen.EraseBackground -> target.uri
            is Screen.MarkupLayers -> target.uri
            is Screen.AiTools -> target.uris?.firstOrNull()
            is Screen.Curves -> target.uris?.firstOrNull()
            is Screen.ImagePreview -> target.uris?.firstOrNull()
            is Screen.Filter -> (target.type as? Screen.Filter.Type.Basic)?.uris?.firstOrNull()
            else -> null
        }
        if (photo != null) {
            startActivity(
                Intent(this, EditorActivity::class.java)
                    .setData(photo)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } else {
            Toast.makeText(this, "Open a photo in Viper Vision's editor to use that tool", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_TOOL = "com.vipervision.editor.STUDIO_TOOL"
        const val EXTRA_URIS = "com.vipervision.editor.STUDIO_URIS"

        fun intent(context: Context, tool: Tool, uris: List<Uri> = emptyList()): Intent =
            Intent(context, ViperStudioActivity::class.java)
                .putExtra(EXTRA_TOOL, tool.name)
                .putParcelableArrayListExtra(EXTRA_URIS, ArrayList(uris))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
