package com.doggy.clip_manager

import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

fun Project.addComposeDependencies() {
    dependencies {
        val bom = platform(libs.findLibrary("androidx-compose-bom").get())
        add("implementation", bom)
        add("testImplementation", bom)
        add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
    }
}
