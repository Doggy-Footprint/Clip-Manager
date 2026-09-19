plugins {
    alias(libs.plugins.clip.android.feature)
    alias(libs.plugins.clip.roborazzi)
}

android {
    namespace = "com.doggy.clip_manager.feature.player"
}

dependencies {
    implementation(projects.core.player)
    testImplementation(libs.androidx.activity.compose)
}
