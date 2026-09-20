plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
}

// The project's pre-commit check lives in an untracked hooks directory, so a fresh clone would
// otherwise commit without it until someone remembers to install it by hand.
if (providers.gradleProperty("clip.skipHookInstall").orNull != "true") {
    providers.exec {
        commandLine("scripts/install-git-hooks.sh")
        workingDir(rootDir)
    }.result.orNull
}
