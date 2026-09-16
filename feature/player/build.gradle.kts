plugins {
    alias(libs.plugins.clip.android.feature)
}

android {
    namespace = "com.doggy.clip_manager.feature.player"
}

dependencies {
    implementation(projects.core.player)
}
