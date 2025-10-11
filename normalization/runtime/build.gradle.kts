@file:Suppress("UnstableApiUsage")

import dev.mattramotar.storex.tooling.extensions.android

group = "dev.mattramotar.storex.normalization"
version = libs.versions.storex.normalization.get()

plugins {
    id("plugin.storex.android.library")
    id("plugin.storex.kotlin.multiplatform")
    id("plugin.storex.maven.publish")
    alias(libs.plugins.mokkery)
    alias(libs.plugins.kover)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.mattramotar.storex.normalization.runtime"

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)

                // Module dependencies
                api(projects.core)
                api(projects.mutations)
            }
        }
    }
}