plugins {
    id("java-platform")
    alias(libs.plugins.maven.publish)
}

group = "dev.mattramotar.storex"
version = "1.0.0"

dependencies {
    constraints {
        api("dev.mattramotar.storex:core:1.0.0")
        api("dev.mattramotar.storex:resilience:1.0.0")
        api("dev.mattramotar.storex:mutations:1.0.0")
        api("dev.mattramotar.storex:normalization-runtime:1.0.0")
        api("dev.mattramotar.storex:normalization-ksp:1.0.0")
        api("dev.mattramotar.storex:paging:1.0.0")
        api("dev.mattramotar.storex:interceptors:1.0.0")
        api("dev.mattramotar.storex:serialization-kotlinx:1.0.0")
        api("dev.mattramotar.storex:android:1.0.0")
        api("dev.mattramotar.storex:compose:1.0.0")
        api("dev.mattramotar.storex:ktor-client:1.0.0")
        api("dev.mattramotar.storex:testing:1.0.0")
        api("dev.mattramotar.storex:telemetry:1.0.0")
        api("dev.mattramotar.storex:bundle-graphql:1.0.0")
        api("dev.mattramotar.storex:bundle-rest:1.0.0")
        api("dev.mattramotar.storex:bundle-android:1.0.0")
    }
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.Companion.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()

    coordinates("dev.mattramotar.storex", "bom", "1.0.0")

    pom {
        name.set("StoreX BOM")
        description.set("Bill of Materials for StoreX - version alignment across all modules")
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
