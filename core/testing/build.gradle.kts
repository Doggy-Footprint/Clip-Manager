plugins {
    alias(libs.plugins.clip.android.library)
}

android {
    namespace = "com.doggy.clip_manager.core.testing"
}

dependencies {
    api(projects.core.data)
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
}
