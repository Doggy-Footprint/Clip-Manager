plugins {
    `kotlin-dsl`
}

group = "com.doggy.clip_manager.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    compileOnly(libs.roborazzi.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "clip.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "clip.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "clip.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "clip.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidLibraryNdk") {
            id = "clip.android.library.ndk"
            implementationClass = "AndroidLibraryNdkConventionPlugin"
        }
        register("androidFeature") {
            id = "clip.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidRoom") {
            id = "clip.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("hilt") {
            id = "clip.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "clip.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("roborazzi") {
            id = "clip.roborazzi"
            implementationClass = "RoborazziConventionPlugin"
        }
    }
}
