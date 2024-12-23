@file:Suppress("UnstableApiUsage")

import dev.mattramotar.storex.tooling.extensions.android

plugins {
    id("plugin.storex.android.library")
    id("plugin.storex.kotlin.multiplatform")
}

android {
    namespace = "dev.mattramotar.storex.result"

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}