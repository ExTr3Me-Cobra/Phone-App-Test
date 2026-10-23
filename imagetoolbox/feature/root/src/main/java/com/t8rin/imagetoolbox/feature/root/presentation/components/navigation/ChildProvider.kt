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

import com.arkivanov.decompose.ComponentContext
import com.t8rin.imagetoolbox.core.ui.utils.navigation.Screen
import com.t8rin.imagetoolbox.feature.ai_tools.presentation.screenLogic.AiToolsComponent
import com.t8rin.imagetoolbox.feature.compare.presentation.screenLogic.CompareComponent
import com.t8rin.imagetoolbox.feature.crop.presentation.screenLogic.CropComponent
import com.t8rin.imagetoolbox.feature.curves.presentation.screenLogic.CurvesComponent
import com.t8rin.imagetoolbox.feature.draw.presentation.screenLogic.DrawComponent
import com.t8rin.imagetoolbox.feature.erase_background.presentation.screenLogic.EraseBackgroundComponent
import com.t8rin.imagetoolbox.feature.filters.presentation.screenLogic.FiltersComponent
import com.t8rin.imagetoolbox.feature.format_conversion.presentation.screenLogic.FormatConversionComponent
import com.t8rin.imagetoolbox.feature.gradient_maker.presentation.screenLogic.GradientMakerComponent
import com.t8rin.imagetoolbox.feature.image_preview.presentation.screenLogic.ImagePreviewComponent
import com.t8rin.imagetoolbox.feature.libraries_info.presentation.screenLogic.LibrariesInfoComponent
import com.t8rin.imagetoolbox.feature.main.presentation.screenLogic.MainComponent
import com.t8rin.imagetoolbox.feature.markup_layers.presentation.screenLogic.MarkupLayersComponent
import com.t8rin.imagetoolbox.feature.media_picker.presentation.screenLogic.MediaPickerComponent
import com.t8rin.imagetoolbox.feature.mesh_gradients.presentation.screenLogic.MeshGradientsComponent
import com.t8rin.imagetoolbox.feature.multi_frame_fusion.presentation.screenLogic.MultiFrameFusionComponent
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.AiTools
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.AppLogs
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.Compare
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.Crop
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.Curves
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.Draw
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.EasterEgg
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.EraseBackground
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.Filter
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.FormatConversion
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.Gallery
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.GradientMaker
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.Help
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.ImagePreview
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.LibrariesInfo
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.LibraryDetails
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.Main
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.MarkupLayers
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.MeshGradients
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.MultiFrameFusion
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.Settings
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.SettingsCategory
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.SingleEdit
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.TextureGeneration
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.UsageStatistics
import com.t8rin.imagetoolbox.feature.root.presentation.components.navigation.NavigationChild.WallpapersExport
import com.t8rin.imagetoolbox.feature.root.presentation.screenLogic.RootComponent
import com.t8rin.imagetoolbox.feature.settings.presentation.screenLogic.SettingsComponent
import com.t8rin.imagetoolbox.feature.single_edit.presentation.screenLogic.SingleEditComponent
import com.t8rin.imagetoolbox.feature.usage_statistics.presentation.screenLogic.UsageStatisticsComponent
import com.t8rin.imagetoolbox.feature.wallpapers_export.presentation.screenLogic.WallpapersExportComponent
import com.t8rin.imagetoolbox.library_details.presentation.screenLogic.LibraryDetailsComponent
import com.t8rin.imagetoolbox.presentation.app_logs.screenLogic.AppLogsComponent
import com.t8rin.imagetoolbox.texture_generation.presentation.screenLogic.TextureGenerationComponent
import javax.inject.Inject
import com.t8rin.imagetoolbox.feature.assistant.presentation.screenLogic.AssistantComponent
import com.t8rin.imagetoolbox.feature.easter_egg.presentation.screenLogic.EasterEggComponent
import com.t8rin.imagetoolbox.feature.help.presentation.screenLogic.HelpComponent

internal class ChildProvider @Inject constructor(
    private val compareComponentFactory: CompareComponent.Factory,
    private val cropComponentFactory: CropComponent.Factory,
    private val curvesComponentFactory: CurvesComponent.Factory,
    private val drawComponentFactory: DrawComponent.Factory,
    private val eraseBackgroundComponentFactory: EraseBackgroundComponent.Factory,
    private val filtersComponentFactory: FiltersComponent.Factory,
    private val formatConversionComponentFactory: FormatConversionComponent.Factory,
    private val gradientMakerComponentFactory: GradientMakerComponent.Factory,
    private val imagePreviewComponentFactory: ImagePreviewComponent.Factory,
    private val multiFrameFusionComponentFactory: MultiFrameFusionComponent.Factory,
    private val textureGenerationComponentFactory: TextureGenerationComponent.Factory,
    private val settingsComponentFactory: SettingsComponent.Factory,
    private val singleEditComponentFactory: SingleEditComponent.Factory,
    private val librariesInfoComponentFactory: LibrariesInfoComponent.Factory,
    private val appLogsComponentFactory: AppLogsComponent.Factory,
    private val usageStatisticsComponentFactory: UsageStatisticsComponent.Factory,
    private val mainComponentFactory: MainComponent.Factory,
    private val mediaPickerComponentFactory: MediaPickerComponent.Factory,
    private val markupLayersComponentFactory: MarkupLayersComponent.Factory,
    private val meshGradientsComponentFactory: MeshGradientsComponent.Factory,
    private val libraryDetailsComponentFactory: LibraryDetailsComponent.Factory,
    private val wallpapersExportComponentFactory: WallpapersExportComponent.Factory,
    private val aiToolsComponentFactory: AiToolsComponent.Factory,
    private val easterEggComponentFactory: EasterEggComponent.Factory,
    private val helpComponentFactory: HelpComponent.Factory,
    private val assistantComponentFactory: AssistantComponent.Factory,
) {
    fun RootComponent.createChild(
        config: Screen,
        componentContext: ComponentContext
    ): NavigationChild = when (config) {
        Screen.EasterEgg -> EasterEgg(
            easterEggComponentFactory(
                componentContext = componentContext,
                onGoBack = ::navigateBack
            )
        )

        Screen.Main -> Main(
            mainComponentFactory(
                componentContext = componentContext,
                onTryGetUpdate = ::tryGetUpdate,
                onGetClipList = ::updateUris,
                onNavigate = ::navigateToNew,
                isUpdateAvailable = isUpdateAvailable
            )
        )

        is Screen.Compare -> Compare(
            compareComponentFactory(
                componentContext = componentContext,
                initialComparableUris = config.uris
                    ?.takeIf { it.size == 2 }
                    ?.let { it[0] to it[1] },
                onGoBack = ::navigateBack
            )
        )

        is Screen.Crop -> Crop(
            cropComponentFactory(
                componentContext = componentContext,
                initialUri = config.uri,
                onGoBack = ::navigateBack,
                onNavigate = ::navigateTo
            )
        )

        is Screen.Draw -> Draw(
            drawComponentFactory(
                componentContext = componentContext,
                initialUri = config.uri,
                onGoBack = ::navigateBack,
                onNavigate = ::navigateTo
            )
        )

        is Screen.EraseBackground -> EraseBackground(
            eraseBackgroundComponentFactory(
                componentContext = componentContext,
                initialUri = config.uri,
                onGoBack = ::navigateBack,
                onNavigate = ::navigateTo
            )
        )

        is Screen.Filter -> Filter(
            filtersComponentFactory(
                componentContext = componentContext,
                initialType = config.type,
                onGoBack = ::navigateBack,
                onNavigate = ::navigateTo
            )
        )

        is Screen.FormatConversion -> FormatConversion(
            formatConversionComponentFactory(
                componentContext = componentContext,
                initialUris = config.uris,
                onGoBack = ::navigateBack,
                onNavigate = ::navigateTo
            )
        )

        is Screen.Curves -> Curves(
            curvesComponentFactory(
                componentContext = componentContext,
                initialUris = config.uris,
                onGoBack = ::navigateBack,
                onNavigate = ::navigateTo
            )
        )

        is Screen.GradientMaker -> GradientMaker(
            gradientMakerComponentFactory(
                componentContext = componentContext,
                initialUris = config.uris,
                onGoBack = ::navigateBack,
                onNavigate = ::navigateTo
            )
        )

        is Screen.ImagePreview -> ImagePreview(
            imagePreviewComponentFactory(
                componentContext = componentContext,
                initialUris = config.uris,
                onGoBack = ::navigateBack,
                onNavigate = ::navigateTo
            )
        )

        is Screen.MultiFrameFusion -> MultiFrameFusion(
            multiFrameFusionComponentFactory(
                componentContext = componentContext,
                initialUris = config.uris,
                onGoBack = ::navigateBack,
                onNavigate = ::navigateTo
            )
        )

        Screen.Gallery -> Gallery(
            component = mediaPickerComponentFactory(componentContext),
            onGoBack = ::navigateBack,
            onPicked = ::updateUris
        )

        Screen.TextureGeneration -> TextureGeneration(
            textureGenerationComponentFactory(
                componentContext = componentContext,
                onGoBack = ::navigateBack,
                onNavigate = ::navigateTo,
            )
        )

        is Screen.SettingsCategory -> SettingsCategory(
            component = settingsComponentFactory(
                componentContext = componentContext,
                onTryGetUpdate = ::tryGetUpdate,
                onNavigate = ::navigateToNew,
                isUpdateAvailable = isUpdateAvailable,
                onGoBack = ::navigateBack,
                initialSearchQuery = "",
                targetSetting = null
            ),
            categoryId = config.categoryId
        )

        is Screen.Settings -> Settings(
            settingsComponentFactory(
                componentContext = componentContext,
                onTryGetUpdate = ::tryGetUpdate,
                onNavigate = ::navigateToNew,
                isUpdateAvailable = isUpdateAvailable,
                onGoBack = ::navigateBack,
                initialSearchQuery = config.searchQuery,
                targetSetting = config.targetSetting
            )
        )

        is Screen.Help -> Help(
            helpComponentFactory(
                componentContext = componentContext,
                initialCategory = config.categoryName,
                initialTipId = config.tipId,
                showButtonHint = config.showButtonHint,
                onGoBack = ::navigateBack,
                onNavigate = ::navigateTo
            )
        )

        is Screen.Assistant -> NavigationChild.Assistant(
            assistantComponentFactory(
                componentContext = componentContext,
                route = config,
                onGoBack = ::navigateBack,
                onNavigate = ::navigateTo
            )
        )

        is Screen.SingleEdit -> SingleEdit(
            singleEditComponentFactory(
                componentContext = componentContext,
                initialUri = config.uri,
                onNavigate = ::navigateTo,
                onGoBack = ::navigateBack
            )
        )

        Screen.LibrariesInfo -> LibrariesInfo(
            librariesInfoComponentFactory(
                componentContext = componentContext,
                onGoBack = ::navigateBack,
                onNavigate = ::navigateTo
            )
        )

        Screen.AppLogs -> AppLogs(
            appLogsComponentFactory(
                componentContext = componentContext,
                onGoBack = ::navigateBack
            )
        )

        Screen.UsageStatistics -> UsageStatistics(
            usageStatisticsComponentFactory(
                componentContext = componentContext,
                onGoBack = ::navigateBack,
                onNavigate = ::navigateTo
            )
        )

        is Screen.MarkupLayers -> MarkupLayers(
            markupLayersComponentFactory(
                componentContext = componentContext,
                initialUri = config.uri,
                onGoBack = ::navigateBack,
                onNavigate = ::navigateTo
            )
        )

        is Screen.MeshGradients -> MeshGradients(
            meshGradientsComponentFactory(
                componentContext = componentContext,
                onGoBack = ::navigateBack,
                onNavigate = ::navigateTo
            )
        )

        is Screen.LibraryDetails -> LibraryDetails(
            libraryDetailsComponentFactory(
                componentContext = componentContext,
                onGoBack = ::navigateBack,
                libraryName = config.name,
                libraryDescription = config.htmlDescription,
                libraryLink = config.link
            )
        )

        is Screen.WallpapersExport -> WallpapersExport(
            wallpapersExportComponentFactory(
                componentContext = componentContext,
                onGoBack = ::navigateBack,
                onNavigate = ::navigateTo
            )
        )

        is Screen.AiTools -> AiTools(
            aiToolsComponentFactory(
                componentContext = componentContext,
                initialUris = config.uris,
                onGoBack = ::navigateBack,
                onNavigate = ::navigateTo
            )
        )

        // Viper Vision: tools removed from the app open the home screen instead.
        else -> Main(
            mainComponentFactory(
                componentContext = componentContext,
                onTryGetUpdate = ::tryGetUpdate,
                onGetClipList = ::updateUris,
                onNavigate = ::navigateToNew,
                isUpdateAvailable = isUpdateAvailable
            )
        )
    }
}
