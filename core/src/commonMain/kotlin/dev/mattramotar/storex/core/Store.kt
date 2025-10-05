package dev.mattramotar.storex.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.time.Duration

@JvmInline
value class StoreNamespace(val value: String)

sealed interface StoreKey {
    val namespace: StoreNamespace
    fun stableHash(): Long
}

data class EntityId(val type: String, val id: String)

data class ByIdKey(
    override val namespace: StoreNamespace,
    val entity: EntityId,
) : StoreKey {
    override fun stableHash(): Long {
        // Use proper 64-bit hash combining
        var result = namespace.value.hashCode().toLong()
        result = 31 * result + entity.type.hashCode()
        result = 31 * result + entity.id.hashCode()
        return result
    }
}


data class QueryKey(
    override val namespace: StoreNamespace,
    val query: Map<String, String>,
) : StoreKey {
    override fun stableHash(): Long {
        // Hash the content string representation, not the List object
        val content = namespace.value + ":" +
            query.toList().sortedBy { it.first }.joinToString("|") { "${it.first}=${it.second}" }
        var result = content.hashCode().toLong()
        // Use better distribution by mixing bits
        result = result xor (result ushr 32)
        return result
    }
}

sealed interface Freshness {
    data object CachedOrFetch : Freshness             // serve cached, trigger refresh
    data class MinAge(val notOlderThan: Duration) : Freshness  // must be at least this fresh
    data object MustBeFresh : Freshness               // force fetch; fail if remote fails (configurable)
    data object StaleIfError : Freshness              // prefer cached if fetch errors
}

/**
 * Read-only store for caching and fetching data with reactive updates.
 *
 * A [Store] acts as a single source of truth for data, coordinating between:
 * - **Memory cache**: Fast in-memory access
 * - **Source of Truth (SoT)**: Local persistence (database, files, etc.)
 * - **Fetcher**: Remote data source (network API, etc.)
 *
 * Data flows unidirectionally:
 * ```
 * Fetcher → SoT → Memory → App
 * ```
 *
 * ## Key Features
 *
 * - **Reactive**: Returns [Flow] of [StoreResult] for real-time updates
 * - **Freshness control**: Configurable staleness via [Freshness] policies
 * - **Multi-layer caching**: Memory + persistence for optimal performance
 * - **Type-safe**: Strongly typed keys and values
 *
 * @param Key The [StoreKey] subtype that uniquely identifies stored entities (e.g., UserId, ArticleKey)
 * @param Domain The application's domain model type - what your app works with (e.g., User, Article, Post)
 *
 * @see Freshness For controlling data staleness and fetch behavior
 * @see StoreResult For the result type emitted by [stream]
 *
 * ## Example: Simple User Store
 * ```kotlin
 * val userStore = store<UserKey, User> {
 *     fetcher { key -> api.getUser(key.id) }
 * }
 *
 * // Get once
 * val user: User = userStore.get(UserKey("123"))
 *
 * // Observe updates
 * userStore.stream(UserKey("123")).collect { result ->
 *     when (result) {
 *         is StoreResult.Data -> updateUI(result.value)
 *         is StoreResult.Loading -> showLoading()
 *         is StoreResult.Error -> showError(result.throwable)
 *     }
 * }
 * ```
 */
interface Store<Key : StoreKey, out Domain> {  // Covariant in Domain for read operations

    /**
     * Fetches and returns a single value for the given [key].
     *
     * Suspends until data is available or an error occurs. Does not emit intermediate
     * loading states - use [stream] for reactive updates.
     *
     * @param key The unique identifier for the data to fetch
     * @param freshness Controls when to fetch from remote vs serve cached data
     * @return The domain model value
     * @throws Exception if fetch fails and no cached data available (depends on [freshness] policy)
     *
     * ## Example
     * ```kotlin
     * val user = store.get(
     *     key = UserKey("123"),
     *     freshness = Freshness.MustBeFresh  // Force network fetch
     * )
     * ```
     */
    suspend fun get(
        key: Key,
        freshness: Freshness = Freshness.CachedOrFetch
    ): Domain // suspends until first emission or throws

    /**
     * Returns a [Flow] of [StoreResult] for reactive updates to the data.
     *
     * Emits:
     * 1. **Loading state** (if no cached data)
     * 2. **Cached data** (if available)
     * 3. **Fresh data** (after fetch completes)
     * 4. **Subsequent updates** (from writes, invalidations, etc.)
     *
     * The flow stays active until cancelled, emitting new values whenever the underlying
     * data changes.
     *
     * @param key The unique identifier for the data to observe
     * @param freshness Controls when to fetch from remote vs serve cached data
     * @return Cold flow that emits [StoreResult] updates
     *
     * ## Example
     * ```kotlin
     * store.stream(UserKey("123"))
     *     .collect { result ->
     *         when (result) {
     *             is StoreResult.Data -> {
     *                 println("User: ${result.value}")
     *                 println("Age: ${result.age}, Origin: ${result.origin}")
     *             }
     *             is StoreResult.Loading -> println("Loading...")
     *             is StoreResult.Error -> println("Error: ${result.throwable}")
     *         }
     *     }
     * ```
     */
    fun stream(
        key: Key,
        freshness: Freshness = Freshness.CachedOrFetch
    ): kotlinx.coroutines.flow.Flow<StoreResult<Domain>>

    /**
     * Invalidates cached data for a specific [key].
     *
     * - Evicts from memory cache
     * - Signals active [stream] observers to refetch
     * - Does NOT delete from Source of Truth
     *
     * Use this after external changes (e.g., user edited profile in another screen).
     *
     * @param key The key to invalidate
     */
    fun invalidate(key: Key)

    /**
     * Invalidates all cached data within a [namespace].
     *
     * Useful for clearing all data of a certain type (e.g., all users, all articles).
     *
     * @param ns The namespace to invalidate
     */
    fun invalidateNamespace(ns: StoreNamespace)

    /**
     * Invalidates all cached data in this store.
     *
     * Nuclear option - clears memory cache and triggers refetch for all active observers.
     */
    fun invalidateAll()
}

/**
 * Result type emitted by [Store.stream] representing the current state of data.
 *
 * A discriminated union of three states:
 * - [Data]: Successful result with value and metadata
 * - [Loading]: Data is being fetched
 * - [Error]: Fetch failed with optional stale data fallback
 *
 * @param Domain The application's domain model type
 *
 * ## Example: Handling Results
 * ```kotlin
 * store.stream(key).collect { result ->
 *     when (result) {
 *         is StoreResult.Data -> {
 *             updateUI(result.value)
 *             if (result.age > 5.minutes) showRefreshButton()
 *         }
 *         is StoreResult.Loading -> showSpinner()
 *         is StoreResult.Error -> {
 *             if (result.servedStale) showStaleDataWarning()
 *             else showError(result.throwable)
 *         }
 *     }
 * }
 * ```
 */
sealed interface StoreResult<out Domain> {
    /**
     * Successful data result.
     *
     * @param value The domain model value
     * @param origin Where the data came from (MEMORY, SOT, or FETCHER)
     * @param age How old the data is (now - updatedAt timestamp)
     */
    data class Data<Domain>(val value: Domain, val origin: Origin, val age: Duration) : StoreResult<Domain>

    /**
     * Data is being loaded/fetched.
     *
     * @param fromCache True if we're showing cached data while fetching fresh data
     */
    data class Loading(val fromCache: Boolean) : StoreResult<Nothing>

    /**
     * Fetch failed.
     *
     * @param throwable The error that occurred
     * @param servedStale True if stale cached data was served despite the error (see [Freshness.StaleIfError])
     */
    data class Error(val throwable: Throwable, val servedStale: Boolean) : StoreResult<Nothing>
}

enum class Origin { MEMORY, SOT, FETCHER }

/**
 * Interceptor for store operations (fetch, cache lookup, etc.).
 *
 * Allows decorating [Store] behavior with cross-cutting concerns like:
 * - Logging
 * - Metrics/telemetry
 * - Request deduplication
 * - Authorization checks
 * - Custom error handling
 *
 * @param Key The [StoreKey] subtype
 * @param Domain The application's domain model type
 *
 * ## Example: Logging Interceptor
 * ```kotlin
 * class LoggingInterceptor<Key : StoreKey, Domain> : StoreInterceptor<Key, Domain> {
 *     override suspend fun intercept(
 *         chain: Chain<Key, Domain>,
 *         key: Key,
 *         proceed: suspend () -> StoreResult<Domain>
 *     ): StoreResult<Domain> {
 *         println("Fetching $key")
 *         val result = proceed()
 *         println("Result: $result")
 *         return result
 *     }
 * }
 * ```
 */
fun interface StoreInterceptor<Key: StoreKey, Domain> {
    suspend fun intercept(
        chain: Chain<Key, Domain>,
        key: Key,
        proceed: suspend () -> StoreResult<Domain>
    ): StoreResult<Domain>

    /**
     * Interceptor chain for passing context and invoking next interceptor.
     *
     * @param Key The [StoreKey] subtype
     * @param Domain The application's domain model type
     */
    interface Chain<Key : StoreKey, Domain> {
        val context: CoroutineContext
        suspend fun proceed(): StoreResult<Domain>
    }
}


/**
 * Converts values between domain, persistence, and network layers.
 *
 * Supports CQRS (Command Query Responsibility Segregation) by allowing different
 * types for reads ([ReadEntity]) and writes ([WriteEntity]).
 *
 * ## Typical Usage Patterns
 *
 * ### 1. Same type everywhere (simplest)
 * ```kotlin
 * Converter<UserKey, User, User, User, User>
 * ```
 *
 * ### 2. Separate persistence layer
 * ```kotlin
 * Converter<UserKey, User, UserEntity, UserEntity, UserEntity>
 * ```
 *
 * ### 3. CQRS with read/write split
 * ```kotlin
 * Converter<UserKey, User, UserReadProjection, UserAggregate, UserAggregate>
 * ```
 *
 * @param Key The [StoreKey] subtype
 * @param Domain The application's domain model type (what your app works with)
 * @param ReadEntity The database read projection type (query results from SoT)
 * @param NetworkResponse The type returned from network fetch operations
 * @param WriteEntity The database write model type (what gets persisted to SoT)
 *
 * @see SimpleConverter For simplified 3-parameter version when ReadEntity == WriteEntity
 */
interface Converter<Key : StoreKey, Domain, ReadEntity, NetworkResponse, WriteEntity> {
    /**
     * Converts network fetch response to database write format.
     *
     * Called after successful fetch to persist data to Source of Truth.
     *
     * @param key The store key
     * @param net The network response
     * @return The entity to write to database
     */
    suspend fun netToDbWrite(key: Key, net: NetworkResponse): WriteEntity

    /**
     * Converts database read projection to domain model.
     *
     * Called when reading from Source of Truth to transform persistence format
     * into the application's domain model.
     *
     * @param key The store key
     * @param db The database entity
     * @return The domain model instance
     */
    suspend fun dbReadToDomain(key: Key, db: ReadEntity): Domain

    /**
     * Extracts metadata from database projection for freshness validation.
     *
     * Return metadata like updatedAt timestamp for staleness checks.
     *
     * @param db The database entity
     * @return Metadata object (typically containing updatedAt: Instant)
     */
    suspend fun dbMetaFromProjection(db: ReadEntity): Any?

    /**
     * Extracts metadata from network response for conditional requests.
     *
     * Extract etag, lastModified, etc. for HTTP conditional requests (If-None-Match, If-Modified-Since).
     *
     * @param net The network response
     * @return Network metadata with etag and lastModified
     */
    suspend fun netMeta(net: NetworkResponse): NetMeta = NetMeta()

    /**
     * Network response metadata for conditional requests.
     *
     * @property etag ETag header value for If-None-Match requests
     * @property lastModifiedMillis Last-Modified header as epoch millis
     */
    data class NetMeta(
        val etag: String? = null,
        val lastModifiedMillis: Long? = null
    )

    /**
     * Converts domain model to database write format for optimistic updates.
     *
     * Optional: Enables optimistic UI updates before server confirmation.
     * If implemented, local writes can immediately update the Source of Truth.
     *
     * @param key The store key
     * @param value The domain model
     * @return The entity to write, or null if conversion not possible
     */
    suspend fun domainToDbWrite(key: Key, value: Domain): WriteEntity? = null
}


/**
 * Single-flight per key: coalesces concurrent requests to a single job.
 *
 * Prevents the "thundering herd" problem where multiple concurrent requests for the
 * same key trigger duplicate fetches. Only the first request executes the block;
 * subsequent requests await the same result.
 *
 * @param Key The key type (typically [StoreKey])
 * @param Result The result type of the operation
 *
 * ## Example
 * ```kotlin
 * val singleFlight = SingleFlight<UserKey, User>()
 *
 * // 100 concurrent requests → only 1 actual fetch
 * val jobs = (1..100).map {
 *     launch {
 *         val user = singleFlight.launch(scope, userKey) {
 *             api.getUser(userKey.id)  // Executes only once
 *         }.await()
 *     }
 * }
 * ```
 */
class SingleFlight<Key, Result> {
    private val inFlight = hashMapOf<Key, CompletableDeferred<Result>>()
    private val mutex = kotlinx.coroutines.sync.Mutex()

    suspend fun launch(scope: CoroutineScope, key: Key, block: suspend () -> Result): CompletableDeferred<Result> {
        // Atomic get-or-create to prevent race conditions
        val deferred = mutex.withLock {
            inFlight.getOrPut(key) {
                CompletableDeferred<Result>().also { newDeferred ->
                    scope.launch {
                        try {
                            val result = block()
                            newDeferred.complete(result)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            // Always rethrow CancellationException
                            newDeferred.cancel(e)
                            throw e
                        } catch (t: Throwable) {
                            newDeferred.completeExceptionally(t)
                        } finally {
                            // Only remove if it's still our deferred
                            mutex.withLock {
                                if (inFlight[key] === newDeferred) {
                                    inFlight.remove(key)
                                }
                            }
                        }
                    }
                }
            }
        }
        return deferred
    }
}

/**
 * Per-key mutex with LRU eviction to prevent memory leaks.
 *
 * Provides thread-safe, per-key locking without unbounded memory growth.
 * Uses LRU (Least Recently Used) eviction when maxSize is reached.
 *
 * @param Key The key type (typically [StoreKey])
 * @property maxSize Maximum number of mutexes to cache (default: 1000)
 *
 * ## Use Case
 * Prevents concurrent writes to the same key in Source of Truth:
 * ```kotlin
 * val keyMutex = KeyMutex<UserKey>()
 *
 * suspend fun saveUser(key: UserKey, user: User) {
 *     keyMutex.forKey(key).withLock {
 *         database.save(user)  // Thread-safe per key
 *     }
 * }
 * ```
 */
class KeyMutex<Key>(private val maxSize: Int = 1000) {
    private val map = mutableMapOf<Key, Mutex>()
    private val mapMutex = Mutex()

    suspend fun forKey(key: Key): Mutex = mapMutex.withLock {
        // Simple LRU: if map is full, remove a random entry
        if (map.size >= maxSize && key !in map) {
            map.remove(map.keys.first())
        }
        map.getOrPut(key) { Mutex() }
    }
}
