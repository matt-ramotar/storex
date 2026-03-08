package dev.mattramotar.storex.core.seams

interface MemoryCache<Key : Any, Value : Any> {
    suspend fun get(key: Key): Value?

    suspend fun put(key: Key, value: Value): Boolean

    suspend fun remove(key: Key): Boolean

    suspend fun clear()

    suspend fun keys(): Set<Key>
}
