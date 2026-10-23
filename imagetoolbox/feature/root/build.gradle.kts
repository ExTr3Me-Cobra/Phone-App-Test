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

plugins {
    alias(libs.plugins.image.toolbox.library)
    alias(libs.plugins.image.toolbox.feature)
    alias(libs.plugins.image.toolbox.hilt)
    alias(libs.plugins.image.toolbox.compose)
}

android.namespace = "com.t8rin.imagetoolbox.feature.root"

dependencies {
    implementation(projects.feature.main)
    implementation(projects.feature.mediaPicker)
    implementation(projects.feature.crop)
    implementation(projects.feature.imagePreview)
    implementation(projects.feature.compare)
    implementation(projects.feature.singleEdit)
    implementation(projects.feature.eraseBackground)
    implementation(projects.feature.draw)
    implementation(projects.feature.filters)
    implementation(projects.feature.curves)
    implementation(projects.feature.gradientMaker)
    implementation(projects.feature.settings)
    implementation(projects.feature.easterEgg)
    implementation(projects.feature.formatConversion)
    implementation(projects.feature.textureGeneration)
    implementation(projects.feature.librariesInfo)
    implementation(projects.feature.markupLayers)
    implementation(projects.feature.meshGradients)
    implementation(projects.feature.libraryDetails)
    implementation(projects.feature.wallpapersExport)
    implementation(projects.feature.aiTools)
    implementation(projects.feature.appLogs)
    implementation(projects.feature.help)
    implementation(projects.feature.assistant)
    implementation(projects.feature.usageStatistics)
    implementation(projects.feature.multiFrameFusion)
}
