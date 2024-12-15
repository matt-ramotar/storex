package dev.mattramotar.clerk.tooling.plugins

import com.android.build.gradle.LibraryExtension
import dev.mattramotar.clerk.tooling.extensions.Versions
import dev.mattramotar.clerk.tooling.extensions.configureKotlin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class KotlinAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
            }

            configureKotlin()

            extensions.configure<LibraryExtension> {
                compileSdk = Versions.COMPILE_SDK
            }
        }
    }
}