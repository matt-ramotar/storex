package dev.mattramotar.storex.store.core.impl

import dev.mattramotar.storex.store.core.api.Cache

class DefaultMemoryCache<K : Any, V : Any> : Cache<K, CacheEntry<V>> {

    override fun getIfPresent(key: K): CacheEntry<V>? {
        TODO("Not yet implemented")
    }

    override fun getOrPut(key: K, valueProducer: () -> CacheEntry<V>): CacheEntry<V> {
        TODO("Not yet implemented")
    }

    override fun getAllPresent(keys: List<*>): Map<K, CacheEntry<V>> {
        TODO("Not yet implemented")
    }

    override fun invalidateAll() {
        TODO("Not yet implemented")
    }

    override fun size(): Long {
        TODO("Not yet implemented")
    }

    override fun invalidateAll(keys: List<K>) {
        TODO("Not yet implemented")
    }

    override fun invalidate(key: K) {
        TODO("Not yet implemented")
    }

    override fun putAll(map: Map<K, CacheEntry<V>>) {
        TODO("Not yet implemented")
    }

    override fun put(key: K, value: CacheEntry<V>) {
        TODO("Not yet implemented")
    }
}
