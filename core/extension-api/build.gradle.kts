plugins {
    alias(libs.plugins.clip.android.library.compose)
    alias(libs.plugins.clip.hilt)
}

android {
    namespace = "com.doggy.clip_manager.core.extension"
}

dependencies {
    api(projects.core.designsystem)
}
