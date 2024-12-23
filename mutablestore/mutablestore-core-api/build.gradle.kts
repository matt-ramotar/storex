@file:Suppress("UnstableApiUsage")

import dev.mattramotar.storex.tooling.extensions.android

plugins {
    id("plugin.storex.kotlin.android.library")
    id("plugin.storex.kotlin.multiplatform")
}

android {
    namespace = "dev.mattramotar.storex.mutablestore.core.api"

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
                api(projects.store.storeCoreApi)
                api(projects.storexResult)
            }
        }
    }
}