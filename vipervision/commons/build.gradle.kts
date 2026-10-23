// Builds Fossify Commons (../../fossify-commons) as a library inside the Viper Vision app.
// Commons' own files are used in place; only the build setup lives here.

plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-parcelize")
}

val commonsDir = rootDir.resolve("../fossify-commons/commons")

android {
    namespace = "org.fossify.commons"
    compileSdk = commonsLibs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        minSdk = commonsLibs.versions.app.build.minimumSDK.get().toInt()
        vectorDrawables.useSupportLibrary = true
        consumerProguardFiles(commonsDir.resolve("consumer-rules.pro"))
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile(commonsDir.resolve("src/main/AndroidManifest.xml"))
            kotlin.directories.add(commonsDir.resolve("src/main/kotlin").path)
            res.directories.add(commonsDir.resolve("src/main/res").path)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "kotlin.RequiresOptIn",
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material.ExperimentalMaterialApi",
            "androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }
}

ksp {
    arg("room.schemaLocation", layout.buildDirectory.dir("schemas").get().asFile.path)
}

dependencies {
    implementation(commonsLibs.kotlinx.serialization.json)
    api(commonsLibs.kotlin.immutable.collections)
    implementation(commonsLibs.androidx.constraintlayout)
    implementation(commonsLibs.androidx.documentfile)
    implementation(commonsLibs.androidx.swiperefreshlayout)
    implementation(commonsLibs.androidx.exifinterface)
    implementation(commonsLibs.androidx.biometric.ktx)
    implementation(commonsLibs.androidx.lifecycle.process)
    implementation(commonsLibs.androidx.startup)
    implementation(commonsLibs.ez.vcard)

    implementation(commonsLibs.bundles.lifecycle)
    implementation(commonsLibs.bundles.compose)
    implementation(commonsLibs.compose.view.binding)

    api(commonsLibs.joda.time)
    api(commonsLibs.recyclerView.fastScroller)
    api(commonsLibs.reprint)
    api(commonsLibs.rtl.viewpager)
    api(commonsLibs.patternLockView)
    api(commonsLibs.androidx.core.ktx)
    api(commonsLibs.androidx.appcompat)
    api(commonsLibs.material)
    api(commonsLibs.gson)

    api(commonsLibs.glide)
    ksp(commonsLibs.glide.compiler)

    api(commonsLibs.bundles.room)
    ksp(commonsLibs.androidx.room.compiler)
}
