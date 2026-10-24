// Viper Vision's own photo editor (the screen Gallery's Edit button opens). Built with Image
// Toolbox's build conventions so it matches the rest of the app's Android/Compose/Hilt setup.

plugins {
    alias(libs.plugins.image.toolbox.library)
    alias(libs.plugins.image.toolbox.compose)
    alias(libs.plugins.image.toolbox.hilt)
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
    // The image libraries Image Toolbox's filters use (the Adjust tab).
    implementation(libs.aire)
    implementation(libs.toolbox.gpuimage)
    implementation(libs.activityCompose)
}
