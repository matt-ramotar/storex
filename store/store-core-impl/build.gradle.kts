@file:Suppress("UnstableApiUsage")

import dev.mattramotar.storex.tooling.extensions.android

plugins {
    id("plugin.storex.android.library")
    id("plugin.storex.kotlin.multiplatform")
}

android {
    namespace = "dev.mattramotar.storex.store.core.impl"

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
                implementation(libs.store5.cache)
                implementation(projects.store.storeInternalHooks)
                api(libs.kotlinx.datetime)
                implementation(projects.storexTelemetry)
                implementation(projects.store.storeCoreApi)
            }
        }
    }
}