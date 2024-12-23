package dev.mattramotar.storex.mutablestore.core.api

import dev.mattramotar.storex.result.Result
import dev.mattramotar.storex.store.core.api.Store

/**
 * Eager Local Writes (Memory + SOT) at Mutation Time
 * Whenever the user calls create, update, or delete, we immediately apply changes locally—without worrying about unresolved sync failures. This ensures the user sees updated data right away (offline-first approach).
 *
 * Handle Unresolved Sync Failures on Reads
 * When the user calls get(key) or stream(key) with the intent to fetch fresh data from the network (e.g., after an invalidation or refresh request), we:
 *
 * Check if we have unresolved local changes (via a bookkeeper or similar mechanism).
 * If so, we attempt to sync them to the server first (partial push).
 * If sync fails, we can fail the read or return stale local data, depending on the strategy.
 * If sync succeeds, we proceed with the normal network read to ensure the store is fully up-to-date.
 * Unidirectional Flow
 *
 * All data fetches (read from network) happen in one place.
 * All SOT and memory writes for read results happen in a single place (the final store logic).
 * No partial writes occur in ad-hoc strategies.
 */

// Eager local writes mean that every local mutation is final in SOT + memory. No partial conflict resolution is needed on writes.

// Reads handle unresolved sync failures only if/when they want a fresh server read. We attempt to push local changes first (networkPusher), then do a single server fetch for up-to-date data.

interface MutableStore<Key : Any, Partial : Any, Value : Any, Error : Any> : Store<Key, Value> {

    suspend fun create(key: Key, partial: Partial): Result<Value, Error>
    suspend fun update(key: Key, value: Value): Result<Value, Error>
    suspend fun delete(key: Key): Result<Unit, Error>
}




