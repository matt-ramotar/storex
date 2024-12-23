@file:Suppress("UnstableApiUsage")

import com.vanniktech.maven.publish.SonatypeHost.Companion.CENTRAL_PORTAL
import dev.mattramotar.storex.tooling.extensions.android

plugins {
    id("plugin.storex.android.library")
    id("plugin.storex.kotlin.multiplatform")
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "dev.mattramotar.storex.result"

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

mavenPublishing {
    publishToMavenCentral(CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()
}

group = "dev.mattramotar.storex.result"
version = libs.versions.storex.result.get()