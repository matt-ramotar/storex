package dev.mattramotar.pager.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse

/**
 * A LoadStrategy defines how a page should be loaded when the Pager requests it.
 *
 * Implementations can define custom logic for retrieving data, for example:
 * - SkipCache: Always hit the network (old enum behavior)
 * - CacheThenNetwork: Try the cache first, then fallback to network
 * - StaleWhileRevalidate: Serve stale data from cache immediately, then attempt to refresh in background
 * - NetworkOnly: Ignore cache and always load from the network
 *
 * Developers can create their own implementations and pass them to the Pager
 * to customize loading behavior without changing the library.
 */
interface LoadStrategy {
    /**
     * Load a page given the request parameters. Implementing classes determine how to fetch the data
     * (e.g., from cache, network, or a combination).
     *
     * @param store The [Store] that can provide data from cache and/or network.
     * @param request The request containing the key, pageSize, and direction.
     *
     * @return A [StorePageLoadResponse] that contains either the successfully loaded items or an error.
     */
    suspend fun <Key : Any, Value : Any> loadPage(
        store: Store<Key, List<Value>>,
        request: StorePageLoadRequest<Key>
    ): StorePageLoadResponse<Value>
}


/**
 * A strategy that always skips the cache and fetches fresh data from the source (e.g., network).
 */
object SkipCacheStrategy : LoadStrategy {
    override suspend fun <Key : Any, Value : Any> loadPage(
        store: Store<Key, List<Value>>,
        request: StorePageLoadRequest<Key>
    ): StorePageLoadResponse<Value> {
        // For "SkipCache", just ignore cache and load fresh data.
        // The store decides what "fresh data" means (e.g., network call).
        return store.fetchFreshData(request)
    }
}

/**
 * A strategy that tries to load from cache first, and if not available or empty, then tries the network.
 */
object CacheThenNetworkStrategy : LoadStrategy {
    override suspend fun <Key : Any, Value : Any> loadPage(
        store: Store<Key, List<Value>>,
        request: StorePageLoadRequest<Key>
    ): StorePageLoadResponse<Value> {
        val cached = store.readFromCache(request)
        return if (!cached.isNullOrEmpty()) {
            StorePageLoadResponse.Success(cached)
        } else {
            store.fetchFreshData(request)
        }
    }
}

/**
 * Example of another strategy: NetworkOnly
 */
object NetworkOnlyStrategy : LoadStrategy {
    override suspend fun <Key : Any, Value : Any> loadPage(
        store: Store<Key, List<Value>>,
        request: StorePageLoadRequest<Key>
    ): StorePageLoadResponse<Value> {
        return store.fetchFreshData(request)
    }
}


/**
 * Extension function: Fetch fresh data from the store, ignoring caches.
 */
private suspend fun <Key : Any, Value : Any> Store<Key, List<Value>>.fetchFreshData(
    request: StorePageLoadRequest<Key>
): StorePageLoadResponse<Value> {
    val response = stream(StoreReadRequest.fresh(request.key)).firstLoaded()
    return response.toPageLoadResponse()
}

/**
 * Extension function: Attempt to read data from cache. If no cached data, return null.
 */
private suspend fun <Key : Any, Value : Any> Store<Key, List<Value>>.readFromCache(
    request: StorePageLoadRequest<Key>
): List<Value>? {
    return when (val response = stream(StoreReadRequest.cached(request.key, refresh = false)).firstLoaded()) {
        is StoreReadResponse.Data -> response.value

        StoreReadResponse.Initial,
        is StoreReadResponse.Loading,
        is StoreReadResponse.NoNewData,
        is StoreReadResponse.Error.Exception,
        is StoreReadResponse.Error.Message,
        is StoreReadResponse.Error.Custom<*> -> null
    }
}

/**
 * Helper to skip loading states and get the first non-loading emission from the store.
 */
private suspend fun <T> Flow<StoreReadResponse<T>>.firstLoaded(): StoreReadResponse<T> {
    return this.filterNot {
        it is StoreReadResponse.Initial ||
        it is StoreReadResponse.Loading }.first()
}

/**
 * Convert StoreReadResponse to our StorePageLoadResponse.
 */
private fun <Value> StoreReadResponse<List<Value>>.toPageLoadResponse(): StorePageLoadResponse<Value> {
    return when (this) {
        is StoreReadResponse.Data -> StorePageLoadResponse.Success(value)
        is StoreReadResponse.Error.Exception -> StorePageLoadResponse.Failure(error)
        is StoreReadResponse.Error.Message -> StorePageLoadResponse.Failure(Throwable(message))

        is StoreReadResponse.NoNewData -> StorePageLoadResponse.Success(emptyList())
        is StoreReadResponse.Error.Custom<*> -> StorePageLoadResponse.Failure(Throwable(this.error.toString()))

        is StoreReadResponse.Loading,
            StoreReadResponse.Initial -> {
            // Ideally, we never hit this due to firstLoaded(), but handle just in case:
            StorePageLoadResponse.Failure(Throwable("Unexpected loading state"))
        }
    }
}
