package dev.mattramotar.storex.core.utils

import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.internal.Fetcher
import dev.mattramotar.storex.core.internal.FetchRequest
import dev.mattramotar.storex.core.internal.FetcherResult
import dev.mattramotar.storex.core.internal.StoreException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/**
 * Fake Fetcher for testing.
 *
 * Allows configuring responses per key for flexible test scenarios.
 */
class FakeFetcher<K : StoreKey, N : Any> : Fetcher<K, N> {

    // Recording list for test assertions
    val fetchedKeys = mutableListOf<K>()
    val fetchRequests = mutableListOf<Pair<K, FetchRequest>>()

    // Configuration: key -> response
    private val responses = mutableMapOf<K, FetcherResult<N>>()
    private val defaultResponse: ((K, FetchRequest) -> FetcherResult<N>)? = null

    // Configuration: simulate delay
    var simulateDelay: Long = 0

    override fun fetch(key: K, request: FetchRequest): Flow<FetcherResult<N>> = flow {
        fetchedKeys.add(key)
        fetchRequests.add(key to request)

        if (simulateDelay > 0) {
            kotlinx.coroutines.delay(simulateDelay)
        }

        val result = responses[key]
            ?: defaultResponse?.invoke(key, request)
            ?: FetcherResult.Error(
                StoreException.from(TestException("No configured response for key: $key"))
            )

        emit(result)
    }

    /**
     * Configure a success response for a specific key.
     */
    fun respondWith(key: K, body: N, etag: String? = null, lastModified: Instant? = null) {
        responses[key] = FetcherResult.Success(body, etag, lastModified)
    }

    /**
     * Configure a NotModified response for a specific key.
     */
    fun respondWithNotModified(key: K, etag: String? = null, lastModified: Instant? = null) {
        responses[key] = FetcherResult.NotModified(etag, lastModified)
    }

    /**
     * Configure an error response for a specific key.
     */
    fun respondWithError(key: K, error: Throwable) {
        responses[key] = FetcherResult.Error(StoreException.from(error))
    }

    /**
     * Configure a success response for all keys (default).
     */
    fun respondWithSuccess(body: N, etag: String? = null) {
        // No-op helper - configure responses per key instead
    }

    /**
     * Clear all responses and recordings.
     */
    fun clear() {
        responses.clear()
        fetchedKeys.clear()
        fetchRequests.clear()
        simulateDelay = 0
    }

    /**
     * Check if a key was fetched.
     */
    fun wasFetched(key: K): Boolean = fetchedKeys.contains(key)

    /**
     * Get the number of times a key was fetched.
     */
    fun fetchCount(key: K): Int = fetchedKeys.count { it == key }

    /**
     * Get the total number of fetches.
     */
    fun totalFetchCount(): Int = fetchedKeys.size
}
