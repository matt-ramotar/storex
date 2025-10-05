@file:Suppress("UnstableApiUsage")

import dev.mattramotar.storex.tooling.extensions.android

plugins {
    id("plugin.storex.android.library")
    id("plugin.storex.kotlin.multiplatform")
    id("plugin.storex.maven.publish")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.mokkery)
}

group = "dev.mattramotar.storex"
version = "1.0.0"

android {
    namespace = "dev.mattramotar.storex.core"
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}
