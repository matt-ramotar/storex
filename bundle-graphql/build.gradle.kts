@file:Suppress("UnstableApiUsage")

import dev.mattramotar.storex.tooling.extensions.android

plugins {
    id("plugin.storex.android.library")
    id("plugin.storex.kotlin.multiplatform")
    id("plugin.storex.maven.publish")
}

group = "dev.mattramotar.storex"
version = "1.0.0"

android {
    namespace = "dev.mattramotar.storex.bundle.graphql"
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.core)
                api(projects.mutations)
                api(projects.normalization.runtime)
                api(projects.interceptors)
            }
        }
    }
}
