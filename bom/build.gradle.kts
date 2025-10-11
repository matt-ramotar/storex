plugins {
    id("java-platform")
    alias(libs.plugins.maven.publish)
}

val storexVersion = "1.0.0"

group = "dev.mattramotar.storex"
version = storexVersion

dependencies {
    constraints {
        api("dev.mattramotar.storex:core:$storexVersion")
        api("dev.mattramotar.storex:resilience:$storexVersion")
        api("dev.mattramotar.storex:mutations:$storexVersion")
        api("dev.mattramotar.storex:normalization-runtime:$storexVersion")
        api("dev.mattramotar.storex:normalization-ksp:$storexVersion")
        api("dev.mattramotar.storex:paging:$storexVersion")
        api("dev.mattramotar.storex:interceptors:$storexVersion")
        api("dev.mattramotar.storex:serialization-kotlinx:$storexVersion")
        api("dev.mattramotar.storex:android:$storexVersion")
        api("dev.mattramotar.storex:compose:$storexVersion")
        api("dev.mattramotar.storex:ktor-client:$storexVersion")
        api("dev.mattramotar.storex:testing:$storexVersion")
        api("dev.mattramotar.storex:telemetry:$storexVersion")
        api("dev.mattramotar.storex:bundle-graphql:$storexVersion")
        api("dev.mattramotar.storex:bundle-rest:$storexVersion")
        api("dev.mattramotar.storex:bundle-android:$storexVersion")
    }
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.Companion.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()

    coordinates("dev.mattramotar.storex", "bom", storexVersion)

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
