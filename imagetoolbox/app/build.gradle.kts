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

import com.android.build.api.artifact.SingleArtifact
import com.t8rin.imagetoolbox.OptimizeReleaseApksTask
import java.util.Properties

// Viper Vision: the combined app's identity.
val vipervision = Properties().apply {
    rootDir.resolve("../vipervision/vipervision.properties").inputStream().use(::load)
}

plugins {
    alias(libs.plugins.image.toolbox.application)
    alias(libs.plugins.image.toolbox.hilt)
    id("androidx.baselineprofile")
}

android {
    // Viper Vision: which processor types to build for (ABIS in vipervision.properties). Each one
    // adds a slow compression step to release builds, so only arm64 is built by default.
    val supportedAbi = vipervision["ABIS"].toString().split(",").map(String::trim).toTypedArray()

    namespace = "com.t8rin.imagetoolbox"

    defaultConfig {
        vectorDrawables.useSupportLibrary = true

        // Viper Vision: the app's name, ID and version come from ../vipervision/vipervision.properties.
        applicationId = vipervision["APP_ID"].toString()
        // Gallery needs Android 8.0 (API 26) or newer.
        minSdk = 26

        versionCode = vipervision["VERSION_CODE"].toString().toInt()
        versionName = vipervision["VERSION_NAME"].toString()

        ndk {
            abiFilters.clear()
            //noinspection ChromeOsAbiSupport
            abiFilters += supportedAbi.toSet()
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    flavorDimensions += "app"

    productFlavors {
        create("foss") {
            dimension = "app"
            versionNameSuffix = "-foss"
            extra.set("gmsEnabled", false)
        }
        create("market") {
            dimension = "app"
            extra.set("gmsEnabled", true)
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            // Viper Vision: own names, so it never clashes with an installed Image Toolbox.
            resValue("string", "app_launcher_name", "${vipervision["APP_NAME"]} DEBUG")
            resValue("string", "app_name", "${vipervision["APP_NAME"]} DEBUG")
            resValue("string", "file_provider", "${vipervision["APP_ID"]}.fileprovider.debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Viper Vision: own names, so it never clashes with an installed Image Toolbox.
            resValue("string", "app_launcher_name", vipervision["APP_NAME"].toString())
            resValue("string", "app_name", vipervision["APP_NAME"].toString())
            resValue("string", "file_provider", "${vipervision["APP_ID"]}.fileprovider")
        }
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    splits {
        abi {
            // Detect app bundle and conditionally disable split abis
            // This is needed due to a "Sequence contains more than one matching element" error
            // present since AGP 8.9.0, for more info see:
            // https://issuetracker.google.com/issues/402800800

            // AppBundle tasks usually contain "bundle" in their name
            //noinspection WrongGradleMethod
            val isBuildingBundle =
                gradle.startParameter.taskNames.any { it.lowercase().contains("bundle") }

            // Disable split abis when building appBundle
            // Viper Vision: and when only one ABI is built (one plain APK, no splits).
            isEnable = !isBuildingBundle && supportedAbi.size > 1
            reset()
            //noinspection ChromeOsAbiSupport
            include(*supportedAbi)
            isUniversalApk = supportedAbi.size > 1
        }
    }

    lint {
        disable += "Instantiatable"
    }

    packaging {
        jniLibs {
            keepDebugSymbols.add("**/*.so")
            pickFirsts.add("lib/*/libcoder.so")
            pickFirsts.add("**/libdatstore_shared_counter.so")
            useLegacyPackaging = true
        }
        resources {
            excludes += "META-INF/"
            excludes += "META-INF/LICENSE.md"
            excludes += "kotlin/"
            excludes += "org/"
            excludes += ".properties"
            excludes += ".bin"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }

    buildFeatures {
        resValues = true
    }

    // Viper Vision: app-level resources (the app icon) from ../vipervision/res.
    sourceSets {
        getByName("main") {
            res.directories.add(rootDir.resolve("../vipervision/res").path)
        }
    }
}

base {
    archivesName = "vipervision-${android.defaultConfig.versionName}"
}

aboutLibraries {
    export.excludeFields.addAll("generated")
}

dependencies {
    baselineProfile(project(":benchmark"))

    // Viper Vision: Fossify Gallery (photo browsing), opened from the launcher icon.
    implementation(projects.vipervisionGallery)
    // Viper Vision: the Samsung-style photo editor Gallery opens for Edit.
    implementation(projects.vipervisionEditor)

    implementation(projects.feature.root)
    implementation(projects.feature.mediaPicker)
    implementation(projects.feature.quickTiles)

    implementation(projects.lib.opencvTools)
    implementation(projects.lib.neuralTools)

    implementation(libs.bouncycastle.pkix)
    implementation(libs.bouncycastle.provider)
    implementation(libs.pdfbox)

    "marketImplementation"(libs.quickie.bundled)
    "fossImplementation"(libs.quickie.foss)
}

baselineProfile {
    automaticGenerationDuringBuild = false
    dexLayoutOptimization = true
    mergeIntoMain = true
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.packaging.jniLibs.keepDebugSymbols.set(emptySet())
        // Viper Vision: the extra recompression below takes ~10 minutes per APK to save ~4% of
        // the download size, so it's off unless OPTIMIZE_APK=true in vipervision.properties.
        if (!vipervision["OPTIMIZE_APK"].toString().toBoolean()) return@onVariants
        val buildToolsVersion = android.buildToolsVersion
        val executable = if (System.getProperty("os.name").startsWith("Windows")) {
            "zipalign.exe"
        } else {
            "zipalign"
        }

        val optimizeApks = tasks.register<OptimizeReleaseApksTask>(
            "optimize${variant.name.replaceFirstChar(Char::uppercase)}Apks"
        ) {
            description = "Compresses and aligns unsigned ${variant.name} APKs before signing."
            zipalign.set(sdkComponents.sdkDirectory.map { sdk ->
                sdk.file("build-tools/$buildToolsVersion/$executable")
            })
            outputDirectory.set(layout.buildDirectory.dir("outputs/apk/${variant.flavorName}/release"))
        }
        val request = variant.artifacts.use(optimizeApks)
            .wiredWithDirectories(
                OptimizeReleaseApksTask::inputDirectory,
                OptimizeReleaseApksTask::outputDirectory
            )
            .toTransformMany(SingleArtifact.APK)
        optimizeApks.configure { transformationRequest.set(request) }
    }

    beforeVariants(selector().all()) { variantBuilder ->
        val flavorName = variantBuilder.productFlavors.firstOrNull()?.second.orEmpty()
        val flavorCap = flavorName.replaceFirstChar(Char::uppercase)

        val gmsEnabled = android.productFlavors
            .findByName(flavorName)
            ?.extra
            ?.get("gmsEnabled") == true

        tasks.configureEach {
            val isTargetTask = listOf("GoogleServices", "Crashlytics").any { marker ->
                name.contains(marker)
            } && name.contains(flavorCap)

            if (isTargetTask) {
                enabled = gmsEnabled
            }
        }
    }
}
