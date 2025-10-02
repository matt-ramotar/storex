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

include(":pager:pager-core")
include(":pager:pager-compose")
include(":repository:repository-runtime")
include(":repository:repository-compiler:ksp")
include(":store:store-core")
include(":store:store-core-api")
include(":store:store-core-impl")
include(":storex-result")
include(":store:store-extensions")
include(":store:store-internal-hooks")
include(":storex-telemetry")
include(":mutablestore:mutablestore-core")
include(":mutablestore:mutablestore-core-api")
include(":mutablestore:mutablestore-core-impl")
include(":storex-coroutines")
include(":sample")
include(":resilience")