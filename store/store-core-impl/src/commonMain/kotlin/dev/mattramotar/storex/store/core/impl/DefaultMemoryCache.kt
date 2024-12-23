package dev.mattramotar.storex.store.core.impl

import dev.mattramotar.storex.store.core.api.Cache
import org.mobilenativefoundation.store.cache5.CacheBuilder

class DefaultMemoryCache<K : Any, V : Any> : Cache<K, CacheEntry<V>> {

    private val delegate = CacheBuilder<K, CacheEntry<V>>().build()

    override fun getIfPresent(key: K): CacheEntry<V>? {
        return delegate.getIfPresent(key)
    }

    override fun getOrPut(key: K, valueProducer: () -> CacheEntry<V>): CacheEntry<V> {
        return delegate.getOrPut(key, valueProducer)
    }

    override fun getAllPresent(keys: List<*>): Map<K, CacheEntry<V>> {
        return delegate.getAllPresent()
    }

    override fun invalidateAll() {
        return delegate.invalidateAll()
    }

    override fun size(): Long {
        return delegate.size()
    }

    override fun invalidateAll(keys: List<K>) {
        return delegate.invalidateAll()
    }

    override fun invalidate(key: K) {
        return delegate.invalidate(key)
    }

    override fun putAll(map: Map<K, CacheEntry<V>>) {
        return delegate.putAll(map)
    }

    override fun put(key: K, value: CacheEntry<V>) {
        return delegate.put(key, value)
    }
}
