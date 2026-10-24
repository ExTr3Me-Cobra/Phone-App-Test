// Viper Vision's own photo editor (the screen Gallery's Edit button opens). Built with Image
// Toolbox's build conventions so it matches the rest of the app's Android/Compose/Hilt setup.

plugins {
    alias(libs.plugins.image.toolbox.library)
    alias(libs.plugins.image.toolbox.compose)
    alias(libs.plugins.image.toolbox.hilt)
    // Image Toolbox's core modules (UI kit, settings, theme), so its tools can live in the editor.
    alias(libs.plugins.image.toolbox.feature)
}

android {
    namespace = "com.vipervision.editor"
}

dependencies {
    // Image Toolbox's icon set.
    implementation(projects.core.resources)
    // Image Toolbox's AI engine and on-device models (the AI tab).
    implementation(projects.feature.aiTools)
    implementation(projects.core.domain)
    // Image Toolbox's filter catalogue, pickers and settings cards (the Effects tab).
    implementation(projects.core.filters)
    // Image Toolbox's Background Remover and drawing engine (Cut out).
    implementation(projects.feature.eraseBackground)
    implementation(projects.feature.draw)
    // Image Toolbox's tone curves editor (Curves bubble).
    implementation(projects.lib.curves)
    // Image Toolbox's heal engines: OpenCV content-aware fill and the LaMa AI model.
    implementation(projects.lib.opencvTools)
    implementation(projects.lib.neuralTools)
    // The image libraries Image Toolbox's filters use (the Adjust tab).
    implementation(libs.aire)
    implementation(libs.toolbox.gpuimage)
    implementation(libs.activityCompose)
}
