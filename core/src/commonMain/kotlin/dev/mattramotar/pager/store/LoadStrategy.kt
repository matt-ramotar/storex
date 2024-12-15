package dev.mattramotar.pager.store

import org.mobilenativefoundation.store.store5.Store

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
        store: Store<Key, Value>,
        request: StorePageLoadRequest<Key>
    ): StorePageLoadResponse<Value>
}


/**
 * A strategy that always skips the cache and fetches fresh data from the source (e.g., network).
 */
object SkipCacheStrategy : LoadStrategy {
    override suspend fun <Key : Any, Value : Any> loadPage(
        store: Store<Key, Value>,
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
        store: Store<Key, Value>,
        request: StorePageLoadRequest<Key>
    ): StorePageLoadResponse<Value> {
        val cached = store.readFromCache(request)
        return if (cached != null && cached.isNotEmpty()) {
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
        store: Store<Key, Value>,
        request: StorePageLoadRequest<Key>
    ): StorePageLoadResponse<Value> {
        return store.fetchFreshData(request)
    }
}
