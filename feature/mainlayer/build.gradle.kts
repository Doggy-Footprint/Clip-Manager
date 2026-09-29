plugins {
    alias(libs.plugins.clip.android.feature)
    alias(libs.plugins.clip.roborazzi)
}

android {
    namespace = "com.doggy.clip_manager.feature.mainlayer"
}

dependencies {
    implementation(projects.core.player)
    implementation(projects.core.data)
    testImplementation(libs.androidx.activity.compose)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
}
