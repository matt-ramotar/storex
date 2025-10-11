@file:Suppress("UnstableApiUsage")

import dev.mattramotar.storex.tooling.extensions.android

group = "dev.mattramotar.storex"
version = libs.versions.storex.paging.get()

plugins {
    id("plugin.storex.android.library")
    id("plugin.storex.kotlin.multiplatform")
    id("plugin.storex.maven.publish")
}

android {
    namespace = "dev.mattramotar.storex.paging"
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.core)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.turbine)
            }
        }
    }
}
