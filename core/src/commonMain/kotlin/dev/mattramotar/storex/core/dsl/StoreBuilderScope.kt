package dev.mattramotar.storex.core.dsl

import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.internal.Fetcher
import kotlinx.coroutines.CoroutineScope

/**
 * DSL scope for building a [dev.mattramotar.storex.core.Store].
 *
 * @param K The store key type
 * @param V The domain value type
 */
interface StoreBuilderScope<K : StoreKey, V : Any> {
    /**
     * The fetcher for retrieving data from the network or remote source.
     * Required.
     */
    var fetcher: Fetcher<K, *>?

    /**
     * The coroutine scope for this store's operations.
     * Default: a new scope with SupervisorJob.
     */
    var scope: CoroutineScope?

    /**
     * Optional cache configuration.
     */
    var cacheConfig: CacheConfig?

    /**
     * Optional persistence configuration.
     */
    var persistenceConfig: PersistenceConfig<K, V>?

    /**
     * Optional freshness validation configuration.
     */
    var freshnessConfig: FreshnessConfig?

    /**
     * Configure the fetcher using a simple suspend function.
     *
     * Example:
     * ```kotlin
     * fetcher { key -> api.getUser(key.id) }
     * ```
     */
    fun fetcher(fetch: suspend (K) -> V)

    /**
     * Configure in-memory caching.
     *
     * Example:
     * ```kotlin
     * cache {
     *     maxSize = 100
     *     ttl = 5.minutes
     * }
     * ```
     */
    fun cache(block: CacheConfig.() -> Unit)

    /**
     * Configure local persistence (database, file system, etc.).
     *
     * Example:
     * ```kotlin
     * persistence {
     *     reader { key -> database.getUser(key.id) }
     *     writer { key, user -> database.saveUser(user) }
     *     deleter { key -> database.deleteUser(key.id) }
     * }
     * ```
     */
    fun persistence(block: PersistenceConfig<K, V>.() -> Unit)

    /**
     * Configure freshness validation and conditional fetching.
     *
     * Example:
     * ```kotlin
     * freshness {
     *     ttl = 5.minutes
     *     staleIfError = 10.minutes
     * }
     * ```
     */
    fun freshness(block: FreshnessConfig.() -> Unit)
}
