plugins {
    alias(libs.plugins.clip.android.library.compose)
    alias(libs.plugins.clip.roborazzi)
}

android {
    namespace = "com.doggy.clip_manager.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material3.navigationSuite)
    api(libs.androidx.compose.material.iconsExtended)
    api(libs.androidx.compose.ui)
}
