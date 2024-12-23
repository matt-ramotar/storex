package dev.mattramotar.storex.store.core.api


import kotlinx.coroutines.flow.Flow

/**
 * Core Store interface for read-only operations: streaming, one-shot [get], invalidation, and clearing.
 *
 * @param Key The key type used to identify items in the store.
 * @param Value The stored value type.
 */
interface Store<Key : Any, Value : Any> {

    /**
     * Streams the data for [key], emitting:
     * - Cached or SOT data immediately if available.
     * - Updated data after a network fetch if needed.
     * - Subsequent changes if the SOT is updated.
     */
    fun stream(key: Key): Flow<Value>

    /**
     * Returns a single [Value] by reading from the store's caches or fetching from the network if needed.
     * May return `null` if no data is found.
     */
    suspend fun get(key: Key): Value?

    /**
     * Removes all data for [key] from memory and the source of truth.
     */
    suspend fun clear(key: Key)

    /**
     * Clears all data from all caches (memory, SOT, etc.).
     */
    suspend fun clearAll()
}