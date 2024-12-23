@file:Suppress("UnstableApiUsage")

import dev.mattramotar.storex.tooling.extensions.android

plugins {
    id("plugin.storex.android.library")
    id("plugin.storex.kotlin.multiplatform")
}

android {
    namespace = "dev.mattramotar.storex.mutablestore.core.impl"

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
                api(projects.storex.mutablestore.mutablestoreCoreApi)
                implementation(projects.store.storeInternalHooks)
                api(projects.storexTelemetry)
                implementation(projects.storexResult)
            }
        }
    }
}


group = "dev.mattramotar.storex.mutablestore"
version = libs.versions.storex.mutablestore.get()