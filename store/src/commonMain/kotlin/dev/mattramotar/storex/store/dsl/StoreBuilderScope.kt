package dev.mattramotar.storex.store.dsl

import dev.mattramotar.storex.store.StoreKey
import dev.mattramotar.storex.store.internal.Fetcher
import kotlinx.coroutines.CoroutineScope

/**
 * DSL scope for building a [dev.mattramotar.storex.store.Store].
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

/**
 * DSL scope for building a [dev.mattramotar.storex.store.mutation.MutationStore].
 *
 * Extends [StoreBuilderScope] with mutation-specific configuration.
 *
 * @param K The store key type
 * @param V The domain value type
 * @param P The patch/update type
 * @param D The draft/create type
 */
interface MutationStoreBuilderScope<K : StoreKey, V : Any, P, D> : StoreBuilderScope<K, V> {
    /**
     * Configuration for mutation operations (update, create, delete, upsert, replace).
     */
    var mutationsConfig: MutationsConfig<K, V, P, D>?

    /**
     * Configure mutation operations.
     *
     * Example:
     * ```kotlin
     * mutations {
     *     update { key, patch ->
     *         val response = api.updateUser(key.id, patch)
     *         UpdateOutcome.Success(response, response.etag)
     *     }
     *
     *     create { draft ->
     *         val response = api.createUser(draft)
     *         CreateOutcome.Success(UserKey(response.id), response)
     *     }
     *
     *     delete { key ->
     *         api.deleteUser(key.id)
     *         DeleteOutcome.Success(alreadyDeleted = false)
     *     }
     * }
     * ```
     */
    fun mutations(block: MutationsConfig<K, V, P, D>.() -> Unit)
}
