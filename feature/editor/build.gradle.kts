plugins {
    alias(libs.plugins.clip.android.feature)
}

android {
    namespace = "com.doggy.clip_manager.feature.editor"
}

dependencies {
    implementation(projects.core.editor)
    implementation(libs.media3.transformer)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}
