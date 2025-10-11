@file:Suppress("UnstableApiUsage")

import dev.mattramotar.storex.tooling.extensions.android

group = "dev.mattramotar.storex"
version = libs.versions.storex.mutations.get()

plugins {
    id("plugin.storex.android.library")
    id("plugin.storex.kotlin.multiplatform")
    id("plugin.storex.maven.publish")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.mokkery)
    alias(libs.plugins.kover)
}

android {
    namespace = "dev.mattramotar.storex.mutations"
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.core)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
