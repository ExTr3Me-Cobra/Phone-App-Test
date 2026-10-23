// Builds Fossify Gallery (../../gallery) as a library inside the PhotoBox app.
// Gallery's own files are used in place; only the build setup lives here.

import java.util.Properties

plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
}

val galleryDir = rootDir.resolve("../gallery/app")
val photobox = Properties().apply {
    rootDir.resolve("../photobox/photobox.properties").inputStream().use(::load)
}

android {
    namespace = "org.fossify.gallery"
    compileSdk = galleryLibs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        minSdk = galleryLibs.versions.app.build.minimumSDK.get().toInt()
        consumerProguardFiles(galleryDir.resolve("proguard-rules.pro"))

        // Gallery uses these for its file sharing provider and About screen. They must match the
        // real installed app, not Fossify's.
        buildConfigField("String", "APPLICATION_ID", "\"${photobox["APP_ID"]}\"")
        buildConfigField("String", "VERSION_NAME", "\"${photobox["VERSION_NAME"]}\"")
        buildConfigField("int", "VERSION_CODE", "${photobox["VERSION_CODE"]}")
    }

    buildTypes {
        debug {
            // Debug builds of the app get ".debug" added to their ID.
            buildConfigField("String", "APPLICATION_ID", "\"${photobox["APP_ID"]}.debug\"")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile(galleryDir.resolve("src/main/AndroidManifest.xml"))
            kotlin.directories.add(galleryDir.resolve("src/main/kotlin").path)
            res.directories.add(galleryDir.resolve("src/main/res").path)
            // Gallery's "foss" flavor resources.
            res.directories.add(galleryDir.resolve("src/foss/res").path)
            // PhotoBox glue code (startup), kept here rather than in Gallery's folder.
            kotlin.directories.add("src/main/kotlin")
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

ksp {
    arg("room.schemaLocation", layout.buildDirectory.dir("schemas").get().asFile.path)
}

dependencies {
    implementation(project(":fossify-commons"))
    implementation(commonsLibs.androidx.lifecycle.process)
    implementation(galleryLibs.androidx.print)
    implementation(galleryLibs.android.image.cropper)
    implementation(galleryLibs.exif)
    implementation(galleryLibs.android.gif.drawable)
    implementation(galleryLibs.androidx.lifecycle.runtime)
    implementation(galleryLibs.androidx.constraintlayout)
    implementation(galleryLibs.androidx.documentfile)
    implementation(galleryLibs.androidx.media3.exoplayer)
    implementation(galleryLibs.sanselan)
    // androidphotofilters is left out: only Fossify's removed editor used it.
    implementation(galleryLibs.androidsvg.aar)
    implementation(galleryLibs.gestureviews)
    implementation(galleryLibs.subsamplingscaleimageview)
    implementation(galleryLibs.androidx.swiperefreshlayout)
    implementation(galleryLibs.awebp)
    implementation(galleryLibs.apng)
    implementation(galleryLibs.avif)
    implementation(galleryLibs.avif.integration)
    implementation(galleryLibs.jxl.integration)
    implementation(galleryLibs.okio)
    implementation(galleryLibs.picasso) {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    compileOnly(galleryLibs.okhttp)

    ksp(galleryLibs.glide.compiler)
    implementation(galleryLibs.zjupure.webpdecoder)

    implementation(galleryLibs.bundles.room)
    ksp(galleryLibs.androidx.room.compiler)
}
