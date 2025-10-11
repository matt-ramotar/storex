plugins {
    `kotlin-dsl`
}

group = "dev.mattramotar.storex.tooling"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17

    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.dokka.gradle.plugin)
    compileOnly(libs.maven.publish.plugin)
    implementation(libs.kover.gradle.plugin)
}

gradlePlugin {
    plugins {

        register("androidLibraryPlugin") {
            id = "plugin.storex.android.library"
            implementationClass = "dev.mattramotar.storex.tooling.plugins.AndroidLibraryConventionPlugin"
        }

        register("kotlinAndroidLibraryPlugin") {
            id = "plugin.storex.kotlin.android.library"
            implementationClass = "dev.mattramotar.storex.tooling.plugins.KotlinAndroidLibraryConventionPlugin"
        }

        register("kotlinMultiplatformPlugin") {
            id = "plugin.storex.kotlin.multiplatform"
            implementationClass = "dev.mattramotar.storex.tooling.plugins.KotlinMultiplatformConventionPlugin"
        }

        register("mavenPublishPlugin") {
            id = "plugin.storex.maven.publish"
            implementationClass = "dev.mattramotar.storex.tooling.plugins.MavenPublishConventionPlugin"
        }
    }
}