package dev.mattramotar.storex.mutations.dsl

import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.dsl.CacheConfig
import dev.mattramotar.storex.core.dsl.FreshnessConfig
import dev.mattramotar.storex.core.dsl.PersistenceConfig
import dev.mattramotar.storex.core.internal.Fetcher
import kotlinx.coroutines.CoroutineScope

/**
 * Builder scope for configuring a mutation store.
 *
 * @param K The store key type
 * @param V The domain value type
 * @param P The patch/update type
 * @param D The draft/create type
 */
interface MutationStoreBuilderScope<K : StoreKey, V : Any, P, D> {
    /**
     * The fetcher for reading data from remote source.
     */
    var fetcher: Fetcher<K, *>?

    /**
     * Optional coroutine scope for the store.
     */
    var scope: CoroutineScope?

    /**
     * Cache configuration.
     */
    var cacheConfig: CacheConfig?

    /**
     * Persistence configuration.
     */
    var persistenceConfig: PersistenceConfig<K, V>?

    /**
     * Freshness configuration.
     */
    var freshnessConfig: FreshnessConfig?

    /**
     * Mutations configuration.
     */
    var mutationsConfig: MutationsConfig<K, V, P, D>?

    /**
     * Configure the fetcher for reading data.
     *
     * Example:
     * ```kotlin
     * fetcher { key -> api.getUser(key.id) }
     * ```
     */
    fun fetcher(fetch: suspend (K) -> V)

    /**
     * Configure cache settings.
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
     * Configure persistence layer.
     *
     * Example:
     * ```kotlin
     * persistence {
     *     reader { key -> database.getUser(key.id) }
     *     writer { key, user -> database.saveUser(user) }
     * }
     * ```
     */
    fun persistence(block: PersistenceConfig<K, V>.() -> Unit)

    /**
     * Configure freshness validation.
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

    /**
     * Configure mutation operations (create, update, delete, etc.).
     *
     * Example:
     * ```kotlin
     * mutations {
     *     update { key, patch ->
     *         val response = api.updateUser(key.id, patch)
     *         PatchClient.Response.Success(response, response.etag)
     *     }
     *
     *     create { draft ->
     *         val response = api.createUser(draft)
     *         PostClient.Response.Success(UserKey(response.id), response, response.etag)
     *     }
     *
     *     delete { key ->
     *         api.deleteUser(key.id)
     *         DeleteClient.Response.Success(alreadyDeleted = false)
     *     }
     * }
     * ```
     */
    fun mutations(block: MutationsConfig<K, V, P, D>.() -> Unit)
}
