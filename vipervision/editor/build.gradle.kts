// Viper Vision's own photo editor (the screen Gallery's Edit button opens). Built with Image
// Toolbox's build conventions so it matches the rest of the app's Android/Compose setup.

plugins {
    alias(libs.plugins.image.toolbox.library)
    alias(libs.plugins.image.toolbox.compose)
}

android {
    namespace = "com.vipervision.editor"
}

dependencies {
    // Image Toolbox's icon set.
    implementation(projects.core.resources)
    implementation(libs.activityCompose)
}
