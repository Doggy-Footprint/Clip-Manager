plugins {
    alias(libs.plugins.clip.android.library.ndk)
}

android {
    namespace = "com.doggy.clip_manager.core.player"
}

dependencies {
    api(projects.core.model)
}
