package dev.mattramotar.storex.store.core.api

interface Cache<Key : Any, Value : Any> {
    fun getIfPresent(key: Key): Value?
    fun getOrPut(
        key: Key,
        valueProducer: () -> Value,
    ): Value

    fun getAllPresent(keys: List<*>): Map<Key, Value>

    fun getAllPresent(): Map<Key, Value> = throw NotImplementedError()

    fun put(
        key: Key,
        value: Value,
    )

    fun putAll(map: Map<Key, Value>)

    fun invalidate(key: Key)

    fun invalidateAll(keys: List<Key>)

    fun invalidateAll()
    fun size(): Long
}