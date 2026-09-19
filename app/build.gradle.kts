import java.util.Properties

plugins {
    alias(libs.plugins.clip.android.application)
    alias(libs.plugins.clip.android.application.compose)
    alias(libs.plugins.clip.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.doggy.clip_manager"

    compileOptions.isCoreLibraryDesugaringEnabled = true
    defaultConfig {
        applicationId = "com.doggy.clip_manager"
        versionCode = 1
        versionName = "1.0"

        ndk {
            // core:player ships FFmpeg libs only for these ABIs.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    val keystoreProps = rootProject.file("keystore.properties")
        .takeIf { it.exists() }
        ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (keystoreProps != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = false
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.feature.browser)
    implementation(projects.feature.player)

    // Overlay debug harness (app/src/debug): these modules are otherwise behind `implementation`
    // in the feature modules, so the debug screen cannot see their types without them.
    debugImplementation(projects.core.data)
    debugImplementation(projects.core.editor)
    debugImplementation(projects.core.model)
    debugImplementation(libs.media3.common)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.serialization.json)
}
