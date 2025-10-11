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
                val moduleName = deriveModuleName(project.group.toString(), project.name)
                name.set(provider { moduleName })

                description.set(provider {
                    when (project.name) {
                        "core" -> "Reactive caching with Store pattern"
                        "resilience" -> "Fault tolerance patterns for distributed systems"
                        "mutations" -> "Write operations with optimistic updates"
                        "runtime" -> "Graph normalization for relational data"
                        "ksp" -> "Code generation for normalized types"
                        "paging" -> "Pagination and infinite scroll support"
                        else -> "StoreX is an extension library built on top of MobileNativeFoundation/Store"
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

    private fun deriveModuleName(group: String, name: String): String {
        val basePackage = "dev.mattramotar.storex"

        val modulePath = when {
            group.startsWith("$basePackage.") -> group.removePrefix("$basePackage.")
            group == basePackage -> ""
            else -> group
        }

        val fullModulePath = if (modulePath.isEmpty()) {
            name
        } else {
            "$modulePath.$name"
        }

        val parts = fullModulePath.split(".")
        val titleParts = parts.map { it.toTitleCase() }

        return "StoreX ${titleParts.joinToString(" ")}"
    }


    private fun String.toTitleCase(): String {
        return when (this.lowercase()) {
            "ksp" -> "KSP"
            "api" -> "API"
            "bom" -> "BOM"
            "dsl" -> "DSL"
            "ui" -> "UI"
            else -> this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}