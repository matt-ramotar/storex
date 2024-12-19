package dev.mattramotar.storex.repository.runtime

enum class DataSource {
    CACHE,
    DISK,
    REMOTE
}

data class DataSources(
    val dataSources: List<DataSource>
)