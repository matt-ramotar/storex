package dev.mattramotar.storex.mutablestore.core.api

/**
 * A strategy that runs before fetching fresh data from the network, allowing unresolved local changes
 * to be pushed first. This ensures that local changes are not overwritten by network responses.
 *
 * @param Key the type of the key used to identify data entries.
 */
interface ConflictResolutionReadStrategy<Key: Any> {

    /**
     * Called immediately before a network read. Return `true` to proceed with the network fetch,
     * or `false` to abort (e.g., if sync fails).
     */
    suspend fun handleUnresolvedSyncBeforeReading(key: Key): Boolean
}