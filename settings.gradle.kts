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
include(":core:extension-api")
include(":core:model")
include(":core:player")
include(":core:testing")
include(":core:ui")
include(":feature:browser")
include(":feature:editor")
include(":feature:player")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

run {
    val localProperties = java.util.Properties()
    val localPropertiesFile = rootDir.resolve("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }
    val extensionModulesDirPath = localProperties.getProperty("extensionModules.dir")
    val extensionModulesDir = extensionModulesDirPath?.let { java.io.File(it) }
    if (extensionModulesDir != null && extensionModulesDir.exists()) {
        val moduleDirs = extensionModulesDir.listFiles { file -> file.isDirectory }
            ?.filter { it.resolve("build.gradle.kts").exists() }
            .orEmpty()
        if (moduleDirs.isNotEmpty()) {
            // ":ext" is an implicit grouping project with no build script of its own; Gradle still
            // requires its projectDir to exist, so point it at the configured extensions directory.
            include(":ext")
            project(":ext").projectDir = extensionModulesDir
            moduleDirs.forEach { moduleDir ->
                val moduleName = moduleDir.name
                include(":ext:$moduleName")
                project(":ext:$moduleName").projectDir = moduleDir
            }
        }
    }
}
