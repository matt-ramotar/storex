package dev.mattramotar.storex.core.seams

import dev.mattramotar.storex.core.StoreKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

fun interface Fetcher<Key : StoreKey, Network : Any> {
    fun fetch(key: Key, request: FetchRequest): Flow<FetcherResult<Network>>
}

data class FetchRequest(
    val conditional: ConditionalRequest? = null,
    val urgency: Urgency = Urgency.Normal
)

enum class Urgency { Low, Normal, High }

data class ConditionalRequest(
    val etag: String? = null,
    val lastModified: Instant? = null,
    val maxStale: kotlin.time.Duration? = null
)

sealed interface FetcherResult<out T> {
    data class Success<T>(
        val body: T,
        val etag: String? = null,
        val lastModified: Instant? = null,
        val cacheControl: String? = null
    ) : FetcherResult<T>

    data class NotModified(
        val etag: String? = null,
        val lastModified: Instant? = null
    ) : FetcherResult<Nothing>

    data class Error(val error: StoreException) : FetcherResult<Nothing>
}

fun <Key : StoreKey, Network : Any> fetcherOf(
    fetch: suspend (Key) -> Network
): Fetcher<Key, Network> = Fetcher { key, _ ->
    flow {
        try {
            emit(FetcherResult.Success(fetch(key)))
        } catch (e: Exception) {
            emit(FetcherResult.Error(StoreException.from(e)))
        }
    }
}

fun <Key : StoreKey, Network : Any> streamingFetcherOf(
    fetch: (Key) -> Flow<Network>
): Fetcher<Key, Network> = Fetcher { key, _ ->
    fetch(key)
        .map<Network, FetcherResult<Network>> { network -> FetcherResult.Success(network) }
        .catch { e -> emit(FetcherResult.Error(StoreException.from(e))) }
}
