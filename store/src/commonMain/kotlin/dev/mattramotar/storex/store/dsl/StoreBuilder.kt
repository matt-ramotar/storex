package dev.mattramotar.storex.store.dsl

import dev.mattramotar.storex.store.Store
import dev.mattramotar.storex.store.StoreKey
import dev.mattramotar.storex.store.dsl.internal.DefaultMutationStoreBuilderScope
import dev.mattramotar.storex.store.dsl.internal.DefaultStoreBuilderScope
import dev.mattramotar.storex.store.internal.fetcherOf
import dev.mattramotar.storex.store.mutation.MutationStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Duration

/**
 * Creates a read-only [Store] using a type-safe DSL.
 *
 * Example:
 * ```kotlin
 * val userStore = store<UserKey, User> {
 *     fetcher { key -> api.getUser(key.id) }
 *
 *     cache {
 *         maxSize = 100
 *         ttl = 5.minutes
 *     }
 *
 *     persistence {
 *         reader { key -> database.getUser(key.id) }
 *         writer { key, user -> database.saveUser(user) }
 *     }
 *
 *     freshness {
 *         ttl = 5.minutes
 *         staleIfError = 10.minutes
 *     }
 * }
 * ```
 *
 * @param K The store key type
 * @param V The domain value type
 * @param scope Optional coroutine scope for the store. Defaults to a new scope with SupervisorJob.
 * @param block Configuration block
 * @return A configured Store instance
 */
fun <K : StoreKey, V : Any> store(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    block: StoreBuilderScope<K, V>.() -> Unit
): Store<K, V> {
    val builder = DefaultStoreBuilderScope<K, V>()
    builder.scope = scope
    builder.block()
    return builder.build()
}

/**
 * Creates a [MutationStore] with support for updates, creates, deletes, and other mutations.
 *
 * Example:
 * ```kotlin
 * val userStore = mutationStore<UserKey, User, UserPatch, UserDraft> {
 *     fetcher { key -> api.getUser(key.id) }
 *
 *     cache {
 *         maxSize = 100
 *         ttl = 5.minutes
 *     }
 *
 *     persistence {
 *         reader { key -> database.getUser(key.id) }
 *         writer { key, user -> database.saveUser(user) }
 *     }
 *
 *     mutations {
 *         update { key, patch ->
 *             val response = api.updateUser(key.id, patch)
 *             UpdateOutcome.Success(response, response.etag)
 *         }
 *
 *         create { draft ->
 *             val response = api.createUser(draft)
 *             CreateOutcome.Success(UserKey(response.id), response)
 *         }
 *
 *         delete { key ->
 *             api.deleteUser(key.id)
 *             DeleteOutcome.Success(alreadyDeleted = false)
 *         }
 *     }
 * }
 * ```
 *
 * @param K The store key type
 * @param V The domain value type
 * @param P The patch/update type
 * @param D The draft/create type
 * @param scope Optional coroutine scope for the store. Defaults to a new scope with SupervisorJob.
 * @param block Configuration block
 * @return A configured MutationStore instance
 */
fun <K : StoreKey, V : Any, P, D> mutationStore(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    block: MutationStoreBuilderScope<K, V, P, D>.() -> Unit
): MutationStore<K, V, P, D> {
    val builder = DefaultMutationStoreBuilderScope<K, V, P, D>()
    builder.scope = scope
    builder.block()
    return builder.build()
}

/**
 * Creates an in-memory only store with no persistence or caching.
 * Useful for simple cases or testing.
 *
 * Example:
 * ```kotlin
 * val userStore = inMemoryStore<UserKey, User> { key ->
 *     api.getUser(key.id)
 * }
 * ```
 *
 * @param K The store key type
 * @param V The domain value type
 * @param fetch Suspending function to fetch data
 * @return A configured Store instance
 */
fun <K : StoreKey, V : Any> inMemoryStore(
    fetch: suspend (K) -> V
): Store<K, V> = store {
    fetcher(fetch)
}

/**
 * Creates a store with in-memory caching and a configured TTL.
 *
 * Example:
 * ```kotlin
 * val userStore = cachedStore<UserKey, User>(
 *     ttl = 5.minutes,
 *     maxSize = 100
 * ) { key ->
 *     api.getUser(key.id)
 * }
 * ```
 *
 * @param K The store key type
 * @param V The domain value type
 * @param ttl Time-to-live for cached items
 * @param maxSize Maximum number of items to cache (default: 100)
 * @param fetch Suspending function to fetch data
 * @return A configured Store instance
 */
fun <K : StoreKey, V : Any> cachedStore(
    ttl: Duration,
    maxSize: Int = 100,
    fetch: suspend (K) -> V
): Store<K, V> = store {
    fetcher(fetch)
    cache {
        this.maxSize = maxSize
        this.ttl = ttl
    }
    freshness {
        this.ttl = ttl
    }
}
