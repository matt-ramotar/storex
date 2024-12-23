package dev.mattramotar.storex.mutablestore.core.impl

import dev.mattramotar.storex.mutablestore.core.api.ConflictResolutionReadStrategy
import dev.mattramotar.storex.result.Result

/**
 * Default implementation of [ConflictResolutionReadStrategy] that checks for un-synced local data
 * and attempts to push it before a network read.
 *
 * @param syncTracker A function to detect un-synced local data for a given key.
 * @param networkPusher Pushes local changes to the server if un-synced data is detected.
 * @param errorAdapter Converts [Throwable] to the custom [Error] type.
 * @param onSyncSuccess Called upon successful sync; can be used for logging or local side effects.
 * @param onSyncFailure Called if sync fails; implement fallback behavior or error handling here.
 * @param onNoOp Called if networkPusher signals no change needed (e.g., result is [Result.NoOp]).
 */
internal class UnresolvedSyncReadStrategy<Key : Any, Response : Any, Error : Any>(
    private val syncTracker: suspend (Key) -> Boolean,
    private val networkPusher: suspend (Key) -> Result<Response, Error>,
    private val errorAdapter: (Throwable) -> Error,
    private val onSyncSuccess: suspend (Key, Response) -> Unit = { _, _ -> },
    private val onSyncFailure: suspend (Key, Error) -> Unit = { _, _ -> },
    private val onNoOp: suspend (Key) -> Unit = {}
) : ConflictResolutionReadStrategy<Key> {

    /**
     * Called before fetching from the network. If local un-synced data is found, we try to sync it.
     * Returns `true` to continue the network read, or `false` to cancel (e.g., on sync failure).
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