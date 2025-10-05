enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("tooling")

    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}

rootProject.name = "storex"

// Layer 1: Foundation (Zero Dependencies)
include(":core")
include(":resilience")

// Layer 2: Write Operations
include(":mutations")

// Layer 3: Advanced Features
include(":normalization:runtime")
include(":normalization:ksp")
include(":paging")

// Layer 4: Integrations & Extensions
include(":interceptors")
include(":serialization-kotlinx")
include(":android")
include(":compose")
include(":ktor-client")

// Layer 5: Development & Observability
include(":testing")
include(":telemetry")

// Layer 6: Convenience (Meta-Packages)
include(":bom")
include(":bundle-graphql")
include(":bundle-rest")
include(":bundle-android")

// Samples & Tools
include(":sample")
