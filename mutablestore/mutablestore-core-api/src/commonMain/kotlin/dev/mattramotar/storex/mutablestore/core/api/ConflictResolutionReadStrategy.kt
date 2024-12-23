package dev.mattramotar.storex.mutablestore.core.api

/**
 * Introduce a “read strategy” that runs before we do a network refresh.
 * For example, this strategy can for example check if local data is unsynced. If so, it attempts to push it to the server first.
 *
 */
interface ConflictResolutionReadStrategy<Key: Any> {
    suspend fun handleUnresolvedSyncBeforeReading(key: Key): Boolean
}