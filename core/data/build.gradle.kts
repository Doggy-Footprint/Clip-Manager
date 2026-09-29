plugins {
    alias(libs.plugins.clip.android.library)
    alias(libs.plugins.clip.hilt)
}

android {
    namespace = "com.doggy.clip_manager.core.data"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.database)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zip4j)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.androidx.documentfile)

    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
}
