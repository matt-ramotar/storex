plugins {
    id("plugin.storex.android.library")
    kotlin("multiplatform")
    alias(libs.plugins.ksp)
}

kotlin {
    applyDefaultHierarchyTemplate()

    androidTarget()

    jvm()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    js {
        browser()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.normalization.runtime)
            }
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", projects.normalization.ksp)
}

kotlin.targets.configureEach {
    compilations.configureEach {
        compileTaskProvider.configure {
            dependsOn("kspCommonMainKotlinMetadata")
        }
    }
}

ksp {
    arg("storex.normalization.package", "dev.mattramotar.storex.sample")
}

android {
    namespace = "dev.mattramotar.storex.sample"
}