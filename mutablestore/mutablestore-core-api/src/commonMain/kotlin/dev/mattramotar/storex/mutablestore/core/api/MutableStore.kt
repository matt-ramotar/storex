package dev.mattramotar.storex.mutablestore.core.api

import dev.mattramotar.storex.result.Result
import dev.mattramotar.storex.store.core.api.Store

/**
 * A [Store] that supports mutation operations (create, update, delete).
 */
interface MutableStore<Key : Any, Partial : Any, Value : Any, Error : Any> : Store<Key, Value> {

    /**
     * Creates a new entry identified by [key], using the given [partial] data to build a full [Value].
     * Applies local changes immediately, then attempts network sync if configured.
     *
     * @return a [Result] encapsulating the created [Value] or an [Error].
     */
    suspend fun create(key: Key, partial: Partial): Result<Value, Error>

    /**
     * Updates the entry at [key] with a new [value]. Writes locally first.
     *
     * @return a [Result] indicating success ([Value]) or failure ([Error]).
     */
    suspend fun update(key: Key, value: Value): Result<Value, Error>

    /**
     * Deletes the entry at [key] from local caches and SOT, optionally syncing the change to the server.
     *
     * @return a [Result] indicating success ([Unit]) or failure ([Error]).
     */
    suspend fun delete(key: Key): Result<Unit, Error>
}




