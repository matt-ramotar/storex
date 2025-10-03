@file:Suppress("UnstableApiUsage")

import com.vanniktech.maven.publish.SonatypeHost.Companion.CENTRAL_PORTAL
import dev.mattramotar.storex.tooling.extensions.android

plugins {
    id("plugin.storex.android.library")
    id("plugin.storex.kotlin.multiplatform")
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.mokkery)
    alias(libs.plugins.kover)
}

android {
    namespace = "dev.mattramotar.storex"

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

mavenPublishing {
    publishToMavenCentral(CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()
}

kover {
    reports {
        total {
            xml {
                onCheck = true
                xmlFile.set(file("${layout.buildDirectory}/reports/kover/coverage.xml"))
            }
        }
    }
}

group = "dev.mattramotar.storex"
version = libs.versions.storex.resilience.get()