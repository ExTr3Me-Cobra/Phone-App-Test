/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/LICENSE-2.0>.
 */

package com.t8rin.imagetoolbox.feature.root.presentation.components.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import com.t8rin.imagetoolbox.core.settings.presentation.model.SettingsGroup
import com.t8rin.imagetoolbox.core.settings.presentation.model.key
import com.t8rin.imagetoolbox.core.settings.presentation.provider.LocalSettingsState
import com.t8rin.imagetoolbox.core.ui.utils.navigation.Screen
import com.t8rin.imagetoolbox.core.utils.appContext
import com.t8rin.imagetoolbox.feature.ai_tools.presentation.AiToolsContent
import com.t8rin.imagetoolbox.feature.ai_tools.presentation.screenLogic.AiToolsComponent
import com.t8rin.imagetoolbox.feature.assistant.presentation.AssistantContent
import com.t8rin.imagetoolbox.feature.assistant.presentation.screenLogic.AssistantComponent
import com.t8rin.imagetoolbox.feature.compare.presentation.CompareContent
import com.t8rin.imagetoolbox.feature.compare.presentation.screenLogic.CompareComponent
import com.t8rin.imagetoolbox.feature.crop.presentation.CropContent
import com.t8rin.imagetoolbox.feature.crop.presentation.screenLogic.CropComponent
import com.t8rin.imagetoolbox.feature.curves.presentation.CurvesContent
import com.t8rin.imagetoolbox.feature.curves.presentation.screenLogic.CurvesComponent
import com.t8rin.imagetoolbox.feature.draw.presentation.DrawContent
import com.t8rin.imagetoolbox.feature.draw.presentation.screenLogic.DrawComponent
import com.t8rin.imagetoolbox.feature.easter_egg.presentation.EasterEggContent
import com.t8rin.imagetoolbox.feature.easter_egg.presentation.screenLogic.EasterEggComponent
import com.t8rin.imagetoolbox.feature.erase_background.presentation.EraseBackgroundContent
import com.t8rin.imagetoolbox.feature.erase_background.presentation.screenLogic.EraseBackgroundComponent
import com.t8rin.imagetoolbox.feature.filters.presentation.FiltersContent
import com.t8rin.imagetoolbox.feature.filters.presentation.screenLogic.FiltersComponent
import com.t8rin.imagetoolbox.feature.format_conversion.presentation.FormatConversionContent
import com.t8rin.imagetoolbox.feature.format_conversion.presentation.screenLogic.FormatConversionComponent
import com.t8rin.imagetoolbox.feature.gradient_maker.presentation.GradientMakerContent
import com.t8rin.imagetoolbox.feature.gradient_maker.presentation.screenLogic.GradientMakerComponent
import com.t8rin.imagetoolbox.feature.help.presentation.HelpContent
import com.t8rin.imagetoolbox.feature.help.presentation.screenLogic.HelpComponent
import com.t8rin.imagetoolbox.feature.image_preview.presentation.ImagePreviewContent
import com.t8rin.imagetoolbox.feature.image_preview.presentation.screenLogic.ImagePreviewComponent
import com.t8rin.imagetoolbox.feature.libraries_info.presentation.LibrariesInfoContent
import com.t8rin.imagetoolbox.feature.libraries_info.presentation.screenLogic.LibrariesInfoComponent
import com.t8rin.imagetoolbox.feature.main.presentation.MainContent
import com.t8rin.imagetoolbox.feature.main.presentation.screenLogic.MainComponent
import com.t8rin.imagetoolbox.feature.markup_layers.presentation.MarkupLayersContent
import com.t8rin.imagetoolbox.feature.markup_layers.presentation.screenLogic.MarkupLayersComponent
import com.t8rin.imagetoolbox.feature.media_picker.presentation.components.MediaPickerRootContentEmbeddable
import com.t8rin.imagetoolbox.feature.media_picker.presentation.screenLogic.MediaPickerComponent
import com.t8rin.imagetoolbox.feature.mesh_gradients.presentation.MeshGradientsContent
import com.t8rin.imagetoolbox.feature.mesh_gradients.presentation.screenLogic.MeshGradientsComponent
import com.t8rin.imagetoolbox.feature.multi_frame_fusion.presentation.MultiFrameFusionContent
import com.t8rin.imagetoolbox.feature.multi_frame_fusion.presentation.screenLogic.MultiFrameFusionComponent
import com.t8rin.imagetoolbox.feature.settings.presentation.SettingsCategoryContent
import com.t8rin.imagetoolbox.feature.settings.presentation.SettingsContent
import com.t8rin.imagetoolbox.feature.settings.presentation.screenLogic.SettingsComponent
import com.t8rin.imagetoolbox.feature.single_edit.presentation.SingleEditContent
import com.t8rin.imagetoolbox.feature.single_edit.presentation.screenLogic.SingleEditComponent
import com.t8rin.imagetoolbox.feature.usage_statistics.presentation.UsageStatisticsContent
import com.t8rin.imagetoolbox.feature.usage_statistics.presentation.screenLogic.UsageStatisticsComponent
import com.t8rin.imagetoolbox.feature.wallpapers_export.presentation.WallpapersExportContent
import com.t8rin.imagetoolbox.feature.wallpapers_export.presentation.screenLogic.WallpapersExportComponent
import com.t8rin.imagetoolbox.library_details.presentation.LibraryDetailsContent
import com.t8rin.imagetoolbox.library_details.presentation.screenLogic.LibraryDetailsComponent
import com.t8rin.imagetoolbox.presentation.app_logs.AppLogsContent
import com.t8rin.imagetoolbox.presentation.app_logs.screenLogic.AppLogsComponent
import com.t8rin.imagetoolbox.texture_generation.presentation.TextureGenerationContent
import com.t8rin.imagetoolbox.texture_generation.presentation.screenLogic.TextureGenerationComponent


internal sealed interface NavigationChild {

    @Composable
    fun Content()

    fun assistantScreen(source: Screen?): Screen.Assistant = source.assistantScreen()


    class Compare(private val component: CompareComponent) : NavigationChild {
        override fun assistantScreen(source: Screen?) = source.assistantScreen(
            listOfNotNull(component.bitmapData?.first?.uri, component.bitmapData?.second?.uri)
        )

        @Composable
        override fun Content() = CompareContent(component)
    }

    class Crop(private val component: CropComponent) : NavigationChild {
        override fun assistantScreen(source: Screen?) = source.assistantScreen(
            listOfNotNull(component.currentUri),
            "imageFormat" to component.imageFormat.title,
            "rotation" to component.rotationState.floatValue,
            "keepExif" to component.saveExif
        )

        @Composable
        override fun Content() = CropContent(component)
    }

    class Draw(private val component: DrawComponent) : NavigationChild {
        override fun assistantScreen(source: Screen?) = source.assistantScreen(
            listOf(component.uri),
            "imageFormat" to component.imageFormat.title,
            "strokeCount" to component.paths.size,
            "keepExif" to component.saveExif
        )

        @Composable
        override fun Content() = DrawContent(component)
    }

    class EasterEgg(private val component: EasterEggComponent) : NavigationChild {
        @Composable
        override fun Content() = EasterEggContent(component)
    }

    class EraseBackground(private val component: EraseBackgroundComponent) : NavigationChild {
        override fun assistantScreen(source: Screen?) = Screen.Assistant(
            source = Screen.EraseBackground(component.uri.takeUnless { it == Uri.EMPTY }),
            parameters = mapOf(
                "imageFormat" to component.imageFormat.title,
                "restoreMode" to component.isRecoveryOn.toString(),
                "trimImage" to component.trimImage.toString(),
                "isRemovingBackground" to component.isErasingBG.toString(),
                "keepExif" to component.saveExif.toString()
            ),
            selectedUris = listOfNotNull(component.uri.takeUnless { it == Uri.EMPTY })
        )

        @Composable
        override fun Content() = EraseBackgroundContent(component)
    }

    class Filter(private val component: FiltersComponent) : NavigationChild {
        override fun assistantScreen(source: Screen?) = when (val type = component.filterType) {
            is Screen.Filter.Type.Basic -> {
                val state = component.basicFilterState
                val filterKey = state.filters.singleOrNull()?.let {
                    appContext.resources.getResourceEntryName(it.title)
                }
                Screen.Filter(Screen.Filter.Type.Basic(state.uris, filterKey)).assistantScreen(
                    state.uris,
                    "mode" to appContext.getString(type.title),
                    "filterCount" to state.filters.size,
                    "filterNames" to state.filters.take(3)
                        .joinToString { appContext.getString(it.title) },
                    "filterKey" to filterKey
                )
            }

            is Screen.Filter.Type.Masking -> Screen.Assistant(
                source = Screen.Filter(Screen.Filter.Type.Masking(component.maskingFilterState.uri)),
                parameters = mapOf(
                    "mode" to appContext.getString(type.title),
                    "filterCount" to component.maskingFilterState.masks.size.toString()
                ),
                selectedUris = listOfNotNull(component.maskingFilterState.uri)
            )

            null -> Screen.Assistant(source = source)
        }

        @Composable
        override fun Content() = FiltersContent(component)
    }

    class FormatConversion(private val component: FormatConversionComponent) : NavigationChild {
        override fun assistantScreen(source: Screen?) = source.assistantScreen(
            component.uris,
            "imageFormat" to component.imageInfo.imageFormat.title,
            "quality" to component.imageInfo.quality.qualityValue,
            "keepExif" to component.keepExif
        )

        @Composable
        override fun Content() = FormatConversionContent(component)
    }

    class Curves(private val component: CurvesComponent) : NavigationChild {
        override fun assistantScreen(source: Screen?) = source.assistantScreen(
            component.uris,
            "imageFormat" to component.imageInfo.imageFormat.title,
            "keepExif" to component.keepExif
        )

        @Composable
        override fun Content() = CurvesContent(component)
    }

    class GradientMaker(private val component: GradientMakerComponent) : NavigationChild {
        override fun assistantScreen(source: Screen?) = source.assistantScreen(
            component.uris,
            "imageFormat" to component.imageFormat.title,
            "gradientType" to component.gradientType,
            "angle" to component.angle,
            "opacity" to component.gradientAlpha
        )

        @Composable
        override fun Content() = GradientMakerContent(component)
    }

    class ImagePreview(private val component: ImagePreviewComponent) : NavigationChild {
        override fun assistantScreen(source: Screen?) = source.assistantScreen(
            component.uris
        )

        @Composable
        override fun Content() = ImagePreviewContent(component)
    }

    class MultiFrameFusion(private val component: MultiFrameFusionComponent) : NavigationChild {
        override fun assistantScreen(source: Screen?) = source.assistantScreen(
            component.uris,
            "imageFormat" to component.imageFormat.title,
            "quality" to component.quality.qualityValue,
            "canProcess" to component.canProcess
        )

        @Composable
        override fun Content() = MultiFrameFusionContent(component)
    }

    class Gallery(
        private val component: MediaPickerComponent,
        private val onGoBack: () -> Unit,
        private val onPicked: (List<Uri>) -> Unit
    ) : NavigationChild {
        @Composable
        override fun Content() = MediaPickerRootContentEmbeddable(
            component = component,
            onPicked = onPicked,
            isGalleryMode = true,
            onBack = onGoBack
        )
    }

    class Main(private val component: MainComponent) : NavigationChild {
        @Composable
        override fun Content() = MainContent(component)
    }

    class TextureGeneration(private val component: TextureGenerationComponent) : NavigationChild {
        override fun assistantScreen(source: Screen?) = source.assistantScreen(
            null,
            "imageFormat" to component.imageFormat.title,
            "quality" to component.quality.qualityValue,
            "width" to component.textureSize.width,
            "height" to component.textureSize.height
        )

        @Composable
        override fun Content() = TextureGenerationContent(component)
    }

    class Settings(private val component: SettingsComponent) : NavigationChild {
        override fun assistantScreen(source: Screen?) = Screen.Assistant(
            source = Screen.Settings(component.searchKeyword, component.targetSetting),
            settingKey = (component.filteredSettings?.singleOrNull()?.second
                ?: component.targetSetting)?.key()
        )

        @Composable
        override fun Content() {
            val category = SettingsGroup.entries.firstOrNull {
                component.targetSetting in it.settingsList
            }.takeIf { LocalSettingsState.current.useCategorizedSettings }

            if (category != null) {
                SettingsCategoryContent(component, category.id)
            } else {
                SettingsContent(component)
            }
        }
    }

    class SettingsCategory(
        private val component: SettingsComponent,
        private val categoryId: Int
    ) : NavigationChild {
        override fun assistantScreen(source: Screen?) = Screen.Assistant(
            source = Screen.Settings(component.searchKeyword, component.targetSetting),
            settingKey = (component.filteredSettings?.singleOrNull()?.second
                ?: component.targetSetting)?.key()
        )

        @Composable
        override fun Content() = SettingsCategoryContent(component, categoryId)
    }

    class Help(private val component: HelpComponent) : NavigationChild {
        @Composable
        override fun Content() = HelpContent(component)
    }

    class Assistant(private val component: AssistantComponent) : NavigationChild {
        @Composable
        override fun Content() = AssistantContent(component)
    }

    class SingleEdit(private val component: SingleEditComponent) : NavigationChild {
        override fun assistantScreen(source: Screen?) = Screen.Assistant(
            source = Screen.SingleEdit(component.uri.takeUnless { it == Uri.EMPTY }),
            parameters = mapOf(
                "imageFormat" to component.imageInfo.imageFormat.title,
                "quality" to component.imageInfo.quality.qualityValue.toString(),
                "width" to component.imageInfo.width.toString(),
                "height" to component.imageInfo.height.toString(),
                "filterCount" to component.filterList.size.toString(),
                "strokeCount" to component.drawPaths.size.toString()
            ),
            selectedUris = listOfNotNull(component.uri.takeUnless { it == Uri.EMPTY })
        )

        @Composable
        override fun Content() = SingleEditContent(component)
    }

    class LibrariesInfo(private val component: LibrariesInfoComponent) : NavigationChild {
        @Composable
        override fun Content() = LibrariesInfoContent(component)
    }

    class AppLogs(private val component: AppLogsComponent) : NavigationChild {
        @Composable
        override fun Content() = AppLogsContent(component)
    }

    class UsageStatistics(private val component: UsageStatisticsComponent) : NavigationChild {
        @Composable
        override fun Content() = UsageStatisticsContent(component)
    }

    class MarkupLayers(private val component: MarkupLayersComponent) : NavigationChild {
        override fun assistantScreen(source: Screen?) = source.assistantScreen(
            null,
            "imageFormat" to component.imageFormat.title,
            "layerCount" to component.layers.size,
            "keepExif" to component.saveExif
        )

        @Composable
        override fun Content() = MarkupLayersContent(component)
    }

    class MeshGradients(private val component: MeshGradientsComponent) : NavigationChild {
        @Composable
        override fun Content() = MeshGradientsContent(component)
    }

    class LibraryDetails(private val component: LibraryDetailsComponent) : NavigationChild {
        @Composable
        override fun Content() = LibraryDetailsContent(component)
    }

    class WallpapersExport(private val component: WallpapersExportComponent) : NavigationChild {
        @Composable
        override fun Content() = WallpapersExportContent(component)
    }

    class AiTools(private val component: AiToolsComponent) : NavigationChild {
        override fun assistantScreen(source: Screen?) = source.assistantScreen(
            component.uris,
            "model" to component.selectedModel.value?.title,
            "imageFormat" to component.imageFormat?.title
        )

        @Composable
        override fun Content() = AiToolsContent(component)
    }

}


private fun Screen?.assistantScreen(
    uris: List<Uri>? = null,
    vararg parameters: Pair<String, Any?>
): Screen.Assistant = Screen.Assistant(
    source = this,
    selectedUris = uris.orEmpty().filter { it != Uri.EMPTY }.distinct(),
    parameters = buildMap {
        this@assistantScreen?.let { put("toolKey", it.simpleName) }
        parameters.forEach { (key, value) ->
            value?.let { put(key, it.toString()) }
        }
    }
)
