package dev.mattramotar.storex.store.core.api

import kotlinx.coroutines.flow.Flow


interface Store<Key : Any, Value : Any> {
    /**
     * Returns a flow that:
     * - Immediately emits cached data if available (memory or SOT).
     * - If data is not available or invalidated, fetches from the fetcher.
     * - Emits updated data when SOT changes.
     */
    fun stream(key: Key): Flow<Value>

    /**
     * Returns a single value, fetching from SOT or network if needed.
     * Can return null if fetcher returns null and no data is cached.
     */
    suspend fun get(key: Key): Value?

    /**
     * Invalidate cached data for a key, causing future requests to fetch fresh data.
     */
    suspend fun invalidate(key: Key)

    /**
     * Clear a single key from all caches (memory & SOT).
     */
    suspend fun clear(key: Key)

    /**
     * Clear all data from all caches.
     */
    suspend fun clearAll()
}




