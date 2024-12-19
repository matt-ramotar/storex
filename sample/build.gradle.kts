
plugins {
    kotlin("jvm")
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(projects.storex.repository.repositoryRuntime)
    ksp(projects.storex.repository.repositoryCompiler.ksp)
}


ksp {
    arg("repositoryPackageName", "dev.mattramotar.storex.sample")
}