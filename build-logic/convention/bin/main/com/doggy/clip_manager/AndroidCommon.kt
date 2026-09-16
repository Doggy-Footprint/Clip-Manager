package com.doggy.clip_manager

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion

internal fun CommonExtension.configureAndroidCommon() {
    compileSdk = 37
    defaultConfig.minSdk = 24
    compileOptions.sourceCompatibility = JavaVersion.VERSION_11
    compileOptions.targetCompatibility = JavaVersion.VERSION_11
    testOptions.unitTests.isIncludeAndroidResources = true
}

internal fun CommonExtension.configureCompose() {
    buildFeatures.compose = true
}
