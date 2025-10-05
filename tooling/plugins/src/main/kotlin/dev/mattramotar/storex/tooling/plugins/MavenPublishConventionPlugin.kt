package dev.mattramotar.storex.tooling.plugins

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class MavenPublishConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("com.vanniktech.maven.publish")
        }

        extensions.configure<MavenPublishBaseExtension> {
            publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
            signAllPublications()

            pom {
                name.set(provider { "StoreX ${project.name.capitalize()}" })
                description.set(provider {
                    when (project.name) {
                        "core" -> "Minimal reactive caching library with source of truth pattern"
                        "resilience" -> "Fault tolerance patterns for distributed systems"
                        "mutations" -> "CRUD write operations for StoreX with optimistic updates"
                        "normalization-runtime" -> "GraphQL-style graph normalization for StoreX"
                        "normalization-ksp" -> "KSP code generation for @Normalizable types"
                        "paging" -> "Pagination and infinite scroll support for StoreX"
                        "interceptors" -> "Middleware pipeline for StoreX operations"
                        "serialization-kotlinx" -> "Automatic JSON serialization for StoreX with kotlinx.serialization"
                        "testing" -> "Test utilities and fake implementations for StoreX"
                        "telemetry" -> "Observability and metrics for StoreX"
                        "android" -> "Android platform integration for StoreX"
                        "compose" -> "Jetpack Compose and Compose Multiplatform helpers for StoreX"
                        "ktor-client" -> "Ktor HTTP client integration for StoreX"
                        "bundle-graphql" -> "GraphQL use case bundle (core + mutations + normalization + interceptors)"
                        "bundle-rest" -> "REST API use case bundle (core + mutations + resilience + serialization)"
                        "bundle-android" -> "Android app bundle (core + mutations + android + compose)"
                        "bom" -> "Bill of Materials for StoreX - version alignment across all modules"
                        else -> "StoreX module: ${project.name}"
                    }
                })
                url.set("https://github.com/matt-ramotar/storex")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("matt-ramotar")
                        name.set("Matthew Ramotar")
                    }
                }

                scm {
                    url.set("https://github.com/matt-ramotar/storex")
                    connection.set("scm:git:git://github.com/matt-ramotar/storex.git")
                    developerConnection.set("scm:git:ssh://git@github.com/matt-ramotar/storex.git")
                }
            }
        }
    }
}

private fun String.capitalize(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
