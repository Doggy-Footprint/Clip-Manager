pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "clip-manager"
include(":app")
include(":core:data")
include(":core:database")
include(":core:designsystem")
include(":core:editor")
include(":core:model")
include(":core:player")
include(":core:testing")
include(":core:ui")
include(":feature:browser")
include(":feature:editor")
include(":feature:player")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
