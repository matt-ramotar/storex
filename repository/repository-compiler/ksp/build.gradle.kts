import com.vanniktech.maven.publish.SonatypeHost.Companion.CENTRAL_PORTAL

plugins {
    kotlin("jvm")
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(libs.symbol.processing.api)
    implementation(projects.storex.repository.repositoryRuntime)
}

mavenPublishing {
    publishToMavenCentral(CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()
}

group = "dev.mattramotar.storex.repository"
version = libs.versions.storex.repository.get()