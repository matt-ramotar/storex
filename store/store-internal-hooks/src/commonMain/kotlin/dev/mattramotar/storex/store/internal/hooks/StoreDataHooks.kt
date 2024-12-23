package dev.mattramotar.storex.store.internal.hooks

import kotlinx.coroutines.flow.Flow

interface StoreDataHooks<Key : Any, Value : Any> {
    /**
     * Internal function to write domain data to the SOT and memory cache in one step.
     */
    suspend fun write(key: Key, value: Value)

    /**
     * Internal function to delete data for a [key] from SOT and memory cache.
     */
    suspend fun delete(key: Key)

    fun readFromSOT(key: Key): Flow<Value?>
}








