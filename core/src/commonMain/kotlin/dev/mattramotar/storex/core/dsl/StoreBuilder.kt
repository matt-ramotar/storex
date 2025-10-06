package dev.mattramotar.storex.core.dsl

import dev.mattramotar.storex.core.Store
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.TimeSource
import dev.mattramotar.storex.core.dsl.internal.DefaultStoreBuilderScope
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
    timeSource: TimeSource = TimeSource.SYSTEM,
    block: StoreBuilderScope<K, V>.() -> Unit
): Store<K, V> {
    val builder = DefaultStoreBuilderScope<K, V>()
    builder.scope = scope
    builder.timeSource = timeSource
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
 * @param scope Optional coroutine scope for the store. Defaults to a new scope with SupervisorJob.
 * @param fetch Suspending function to fetch data
 * @return A configured Store instance
 */
fun <K : StoreKey, V : Any> inMemoryStore(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    timeSource: TimeSource = TimeSource.SYSTEM,
    fetch: suspend (K) -> V
): Store<K, V> = store(scope, timeSource) {
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
 * @param scope Optional coroutine scope for the store. Defaults to a new scope with SupervisorJob.
 * @param fetch Suspending function to fetch data
 * @return A configured Store instance
 */
fun <K : StoreKey, V : Any> cachedStore(
    ttl: Duration,
    maxSize: Int = 100,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    timeSource: TimeSource = TimeSource.SYSTEM,
    fetch: suspend (K) -> V
): Store<K, V> = store(scope, timeSource) {
    fetcher(fetch)
    cache {
        this.maxSize = maxSize
        this.ttl = ttl
    }
    freshness {
        this.ttl = ttl
    }
}
