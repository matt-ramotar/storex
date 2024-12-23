package dev.mattramotar.storex.mutablestore.core.impl

import dev.mattramotar.storex.mutablestore.core.api.ConflictResolutionReadStrategy
import dev.mattramotar.storex.result.Result

/**
 * syncTracker(key): e.g., a local DB check to see if we have unsynced local data for this key.
 * networkPusher(key): if we do, push it to the server.
 * If pushing fails, we can fail the read or do something else (like keep showing stale data). This logic is up to you.
 */
internal class UnresolvedSyncReadStrategy<Key : Any, Response : Any, Error : Any>(
    private val syncTracker: suspend (Key) -> Boolean,            // checks if there’s unsynced local changes
    private val networkPusher: suspend (Key) -> Result<Response, Error>, // pushes local changes to network
    private val errorAdapter: (Throwable) -> Error,
    private val onSyncSuccess: suspend (Key, Response) -> Unit = { _, _ -> },
    private val onSyncFailure: suspend (Key, Error) -> Unit = { _, _ -> },
    private val onNoOp: suspend (Key) -> Unit = {}
) : ConflictResolutionReadStrategy<Key> {

    /**
     * This function is called right before we fetch from the network.
     * If there's an unresolved local state, we try to push it.
     * Return `true` if the read can proceed, `false` if we should abort or fail.
     */
    override suspend fun handleUnresolvedSyncBeforeReading(key: Key): Boolean {
        val hasUnresolvedSync = syncTracker(key)
        if (!hasUnresolvedSync) return true

        return try {
            when (val result = networkPusher(key)) {
                is Result.Success -> {
                    onSyncSuccess(key, result.value)
                    true
                }

                is Result.Failure -> {
                    onSyncFailure(key, result.error)
                    false
                }

                is Result.NoOp -> {
                    onNoOp(key)
                    false
                }
            }
        } catch (t: Throwable) {
            val err = errorAdapter(t)
            onSyncFailure(key, err)
            false
        }
    }
}