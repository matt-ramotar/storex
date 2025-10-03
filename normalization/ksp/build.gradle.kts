@file:Suppress("UnstableApiUsage")

import com.vanniktech.maven.publish.SonatypeHost.Companion.CENTRAL_PORTAL


plugins {
    kotlin("multiplatform")
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.mokkery)
    alias(libs.plugins.kover)
}

kotlin {
    jvm()
    sourceSets {
        jvmMain {
            dependencies {
                implementation(libs.symbol.processing.api)
                implementation(libs.kotlinpoet.ksp)
                api(projects.normalization.runtime)
            }

            kotlin.srcDir("src/main/kotlin")
            resources.srcDir("src/main/resources")
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
version = libs.versions.storex.normalization.get()