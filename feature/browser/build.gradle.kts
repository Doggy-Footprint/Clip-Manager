plugins {
    alias(libs.plugins.clip.android.feature)
    alias(libs.plugins.clip.roborazzi)
}

android {
    namespace = "com.doggy.clip_manager.feature.browser"
}

dependencies {
    implementation(projects.core.data)
    implementation(libs.androidx.activity.compose)
}
