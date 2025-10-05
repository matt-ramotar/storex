package dev.mattramotar.storex.core.internal

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * In-memory cache interface for Store.
 *
 * Provides fast, ephemeral storage for frequently accessed data.
 * Data stored here does not survive app restarts.
 *
 * @param Key The key type (typically [StoreKey])
 * @param Value The cached value type (typically the domain model)
 */
interface MemoryCache<Key: Any, Value: Any> {
    /**
     * Retrieves a value from the cache.
     *
     * @param key The key to lookup
     * @return The cached value, or null if not found or expired
     */
    suspend fun get(key: Key): Value?

    /**
     * Stores a value in the cache.
     *
     * @param key The key to store under
     * @param value The value to cache
     * @return true if this was a new entry, false if it replaced an existing entry
     */
    suspend fun put(key: Key, value: Value): Boolean

    /**
     * Removes a value from the cache.
     *
     * @param key The key to remove
     * @return true if a value was removed, false if key didn't exist
     */
    suspend fun remove(key: Key): Boolean

    /**
     * Clears all entries from the cache.
     */
    suspend fun clear()
}

/**
 * In-memory cache with LRU eviction and TTL support.
 *
 * Features:
 * - **LRU eviction**: Least recently used entries are evicted when capacity is reached
 * - **TTL expiration**: Entries expire after a configurable time-to-live
 * - **Thread-safe**: Uses mutex for concurrent access
 *
 * @param Key The key type
 * @param Value The value type
 * @property maxSize Maximum number of entries (default: 100)
 * @property ttl Time-to-live for entries (default: infinite, no expiration)
 *
 * ## Example
 * ```kotlin
 * val cache = MemoryCacheImpl<UserKey, User>(
 *     maxSize = 500,
 *     ttl = 5.minutes
 * )
 * ```
 */
internal class MemoryCacheImpl<Key : Any, Value : Any>(
    private val maxSize: Int,
    private val ttl: Duration
): MemoryCache<Key, Value> {
    private val cache = HashMap<Key, CacheEntry<Value>>()
    private val accessOrder = LinkedHashSet<Key>()
    private val mutex = Mutex()

    override suspend fun get(key: Key): Value? = mutex.withLock {
        val entry = cache[key] ?: return null

        if (isExpired(entry)) {
            cache.remove(key)
            accessOrder.remove(key)
            return null
        }

        // Update access order for LRU
        accessOrder.remove(key)
        accessOrder.add(key)

        entry.value
    }

    override suspend fun put(key: Key, value: Value): Boolean = mutex.withLock {
        // Evict if at capacity
        if (cache.size >= maxSize && key !in cache) {
            // Bounds check before accessing first()
            if (accessOrder.isNotEmpty()) {
                val oldest = accessOrder.first()
                cache.remove(oldest)
                accessOrder.remove(oldest)
            }
        }

        val previous = cache.put(key, CacheEntry(
            value = value,
            timestamp = Clock.System.now()
        ))
        accessOrder.remove(key)
        accessOrder.add(key)

        // Return true if this was a new entry
        return@withLock previous == null
    }

    override suspend fun remove(key: Key): Boolean = mutex.withLock {
        cache.remove(key)
        accessOrder.remove(key)
    }

    override suspend fun clear() = mutex.withLock {
        cache.clear()
        accessOrder.clear()
    }

    private fun isExpired(entry: CacheEntry<Value>): Boolean {
        if (ttl.isInfinite()) return false
        return Clock.System.now() - entry.timestamp > ttl
    }

    private data class CacheEntry<V>(
        val value: V,
        val timestamp: Instant
    )
}
