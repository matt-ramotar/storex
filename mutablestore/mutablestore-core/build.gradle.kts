@file:Suppress("UnstableApiUsage")

import com.vanniktech.maven.publish.SonatypeHost.Companion.CENTRAL_PORTAL
import dev.mattramotar.storex.tooling.extensions.android

plugins {
    id("plugin.storex.kotlin.android.library")
    id("plugin.storex.kotlin.multiplatform")
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "dev.mattramotar.storex.mutablestore.core"

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
                api(projects.store.storeCore)
                api(projects.storex.mutablestore.mutablestoreCoreApi)
                implementation(projects.storex.mutablestore.mutablestoreCoreImpl)
                implementation(projects.store.storeInternalHooks)
                api(projects.storexTelemetry)
                api(projects.store.storeExtensions)
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral(CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()
}

group = "dev.mattramotar.storex.mutablestore"
version = libs.versions.storex.mutablestore.get()