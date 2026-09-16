plugins {
    alias(libs.plugins.clip.android.library)
    alias(libs.plugins.clip.android.room)
    alias(libs.plugins.clip.hilt)
}

android {
    namespace = "com.doggy.clip_manager.core.database"
}

dependencies {
    api(projects.core.model)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
