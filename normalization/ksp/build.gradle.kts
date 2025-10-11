@file:Suppress("UnstableApiUsage")

group = "dev.mattramotar.storex.normalization"
version = libs.versions.storex.normalization.get()

plugins {
    kotlin("multiplatform")
    id("plugin.storex.maven.publish")
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