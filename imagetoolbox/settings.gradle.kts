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

@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        includeBuild("build-logic")
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven("https://jitpack.io") { name = "JitPack" }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven("https://androidx.dev/storage/compose-compiler/repository") {
            name = "Compose Compiler Snapshots"
            content { includeGroup("androidx.compose.compiler") }
        }
        mavenCentral()
        maven("https://jitpack.io") { name = "JitPack" }
        maven("https://oss.sonatype.org/content/repositories/snapshots/") {
            name = "Sonatype Snapshots"
            mavenContent {
                snapshotsOnly()
            }
        }
    }
}
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "ImageToolbox"

// Viper Vision: 38 tools and the collages library were removed (see
// core/ui/.../navigation/ViperVisionRemovedTools.kt), and later Image Toolbox's own main screen
// (root, main, settings screens, help, single edit, preview, quick tiles...). What's left is used
// by Viper Vision's editor and Studio behind the scenes.

include(":app")

include(":feature:pick-color")
include(":core:filters")
include(":feature:filters")
include(":feature:draw")
include(":feature:erase-background")
include(":feature:compare")
include(":feature:crop")
include(":feature:archive-tools")
include(":feature:media-picker")
include(":feature:settings")
include(":feature:markup-layers")
include(":feature:ai-tools")
include(":feature:multi-frame-fusion")

include(":core:settings")
include(":core:resources")
include(":core:data")
include(":core:domain")
include(":core:ui")
include(":core:di")
include(":core:crash")
include(":core:ksp")
include(":core:utils")

include(":lib:neural-tools")
include(":lib:ascii")
include(":lib:opencv-tools")
include(":lib:documentscanner")
include(":lib:snowfall")
include(":lib:curves")
include(":lib:dynamic-theme")
include(":lib:palette")
include(":lib:modalsheet")
include(":lib:qrose")
include(":lib:cropper")
include(":lib:colors")
include(":lib:gesture")
include(":lib:image")
include(":lib:zoomable")
include(":lib:archive")

include(":benchmark")
// Viper Vision: Image Toolbox's on-device test suite is left out; it covers removed tools.
// include(":instrumentation-tests")

// Viper Vision: Fossify Gallery and Fossify Commons, built from their own folders next to this one.
// The build files for these two live in ../vipervision; their source stays untouched in place.
dependencyResolutionManagement {
    versionCatalogs {
        create("galleryLibs") { from(files("../gallery/gradle/libs.versions.toml")) }
        create("commonsLibs") { from(files("../fossify-commons/gradle/libs.versions.toml")) }
    }
}
include(":vipervision-gallery")
project(":vipervision-gallery").projectDir = file("../vipervision/gallery")
include(":fossify-commons")
project(":fossify-commons").projectDir = file("../vipervision/commons")
// Viper Vision's own photo editor (what Gallery's Edit button opens).
include(":vipervision-editor")
project(":vipervision-editor").projectDir = file("../vipervision/editor")
