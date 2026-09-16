plugins {
    alias(libs.plugins.clip.android.library.compose)
}

android {
    namespace = "com.doggy.clip_manager.core.ui"
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
