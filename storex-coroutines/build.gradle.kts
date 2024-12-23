@file:Suppress("UnstableApiUsage")

import dev.mattramotar.storex.tooling.extensions.android

plugins {
    id("plugin.storex.android.library")
    id("plugin.storex.kotlin.multiplatform")
}

android {
    namespace = "dev.mattramotar.storex.coroutines"

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
            }
        }
    }
}