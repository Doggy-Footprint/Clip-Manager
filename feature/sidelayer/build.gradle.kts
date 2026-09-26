plugins {
    alias(libs.plugins.clip.android.feature)
    alias(libs.plugins.clip.roborazzi)
}

android {
    namespace = "com.doggy.clip_manager.feature.sidelayer"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.model)
    implementation(libs.androidx.activity.compose)
}
