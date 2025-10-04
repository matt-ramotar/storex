package dev.mattramotar.storex.store.internal

import dev.mattramotar.storex.store.Freshness
import dev.mattramotar.storex.store.StoreKey
import dev.mattramotar.storex.store.mutation.Precondition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

//interface SourceOfTruth<K : StoreKey, V> {
//    fun reader(key: K): Flow<V?>                   // null => not present
//    suspend fun write(key: K, value: V)
//    suspend fun delete(key: K)
//    suspend fun withTransaction(block: suspend () -> Unit)
//}

interface SourceOfTruth<K : StoreKey, ReadDb, WriteDb> {
    fun reader(key: K): Flow<ReadDb?>        // emits a *projection* for key
    suspend fun write(key: K, value: WriteDb) // accepts a *batch/delta*
    suspend fun delete(key: K)
    suspend fun withTransaction(block: suspend () -> Unit)
    suspend fun rekey(old: K, new: K, reconcile: suspend (oldRead: ReadDb, serverRead: ReadDb?) -> ReadDb)
}

interface Updater<K : StoreKey, Patch, NetPatch> {
    sealed interface Outcome<out Net> {
        data class Success<Net>(val echo: Net? = null, val etag: String? = null) : Outcome<Net>
        data class Conflict(val serverVersionTag: String? = null) : Outcome<Nothing>
        data class Failure(val error: Throwable) : Outcome<Nothing>
    }
    suspend fun update(key: K, patch: Patch, body: NetPatch, precondition: Precondition?): Outcome<*>
}


sealed interface UpdateOutcome<out N> {
    data class Success<N>(val networkEcho: N?, val etag: String?) : UpdateOutcome<N>
    data class Conflict(val serverVersionTag: String?) : UpdateOutcome<Nothing>
    data class Failure(val error: Throwable, val retryAfter: Duration?) : UpdateOutcome<Nothing>
}

/**
 * A function that retrieves data from a remote source.
 *
 * Fetchers can be:
 * - One-shot: Emit a single value and complete
 * - Streaming: Emit multiple values over time (WebSocket, SSE)
 */
fun interface Fetcher<Key : StoreKey, Network : Any> {

    /**
     * Fetches data for the given key.
     *
     * @return Flow<FetcherResult<Output>> that emits results
     */
    fun fetch(key: Key, request: FetchRequest): Flow<FetcherResult<Network>>
}


data class FetchRequest(
    val conditional: ConditionalRequest? = null,
    val urgency: Urgency = Urgency.Normal // room for network QoS
)

enum class Urgency { Low, Normal, High }


/**
 * Creates a one-shot fetcher from a suspending function.
 */
fun <Key : StoreKey, Network : Any> fetcherOf(
    fetch: suspend (Key) -> Network,

): Fetcher<Key, Network> = Fetcher { key, request ->
    flow {
        try {
            // TODO: Integrate FetchRequest.conditional and FetchRequest.urgency

            val network = fetch(key)
            emit(FetcherResult.Success(network))
        } catch (e: Exception) {
            emit(FetcherResult.Error(StoreException.from(e)))
        }
    }
}

/**
 * Creates a streaming fetcher from a Flow.
 */
fun <Key : StoreKey, Network : Any> streamingFetcherOf(
    fetch: (Key) -> Flow<Network>
): Fetcher<Key, Network> = Fetcher { key, validator ->
    fetch(key).map { network ->

        // TODO: Integrate FetchRequest.conditional and FetchRequest.urgency
        FetcherResult.Success(network)
    }.catch { e -> FetcherResult.Error(StoreException.from(e)) }
}


/**
 * Inputs to decide whether a fetch is necessary and whether it can be conditional.
 */
data class FreshnessContext<K : StoreKey, DbMeta>(
    val key: K,
    val now: Instant,
    val freshness: Freshness,
    val sotMeta: DbMeta?,              // metadata decoded from SOT row (e.g., updatedAt, etag)
    val status: KeyStatus              // from Bookkeeper: lastSuccess/Failure/backoff/etag
)

/** Resulting plan for the fetch step. */
sealed interface FetchPlan {
    data object Skip : FetchPlan                         // serve local; no network
    data class Conditional(val request: ConditionalRequest) : FetchPlan
    data object Unconditional : FetchPlan                // force full fetch
}

/** Hints the Fetcher can translate to headers like If-None-Match, If-Modified-Since, etc. */
data class ConditionalRequest(
    val etag: String? = null,
    val lastModified: Instant? = null,
    val maxStale: Duration? = null
)

fun interface FreshnessValidator<K : StoreKey, DbMeta> {
    fun plan(ctx: FreshnessContext<K, DbMeta>): FetchPlan
}


/**
 * Result of a fetch operation.
 */
sealed interface FetcherResult<out T> {
    /**
     * Fetch succeeded with data.
     */
    data class Success<T>(
        val body: T,
        val etag: String? = null,
        val lastModified: Instant? = null,
        val cacheControl: String? = null
    ) : FetcherResult<T>

    data class NotModified(
        val etag: String? = null,
        val lastModified: Instant? = null
    ) : FetcherResult<Nothing>

    /**
     * Fetch failed with an error.
     */
    data class Error(val error: StoreException) : FetcherResult<Nothing>
}


interface Bookkeeper<K : StoreKey> {
    fun recordSuccess(key: K, etag: String?, at: Instant)
    fun recordFailure(key: K, error: Throwable, at: Instant)
    fun lastStatus(key: K): KeyStatus
}

data class KeyStatus(
    val lastSuccessAt: Instant?,
    val lastFailureAt: Instant?,
    val lastEtag: String?,
    val backoffUntil: Instant?
)


/**
 * Base class for all Store exceptions.
 *
 * All exceptions include an [isRetryable] property to indicate whether the operation
 * should be retried. This is used by retry policies and circuit breakers.
 */
sealed class StoreException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /**
     * Indicates whether this exception represents a transient error that may succeed on retry.
     *
     * **Retryable exceptions:**
     * - Network timeouts
     * - No network connection
     * - HTTP 5xx errors (server errors)
     * - HTTP 408 (Request Timeout)
     * - HTTP 429 (Too Many Requests)
     * - Transient persistence errors
     *
     * **Non-retryable exceptions:**
     * - HTTP 4xx errors (client errors, except 408 and 429)
     * - Validation errors
     * - Serialization errors
     * - Configuration errors
     * - Disk full
     * - Permission denied
     * - Not found
     */
    abstract val isRetryable: Boolean

    /**
     * Network-related errors (HTTP, DNS, timeout, etc.)
     */
    sealed class NetworkException(
        message: String,
        cause: Throwable? = null
    ) : StoreException(message, cause) {

        class Timeout(cause: Throwable? = null) :
            NetworkException("Network timeout", cause) {
            override val isRetryable: Boolean = true
        }

        class NoConnection(cause: Throwable? = null) :
            NetworkException("No network connection", cause) {
            override val isRetryable: Boolean = true
        }

        class HttpError(
            val statusCode: Int,
            val body: String? = null,
            cause: Throwable? = null
        ) : NetworkException("HTTP $statusCode", cause) {
            override val isRetryable: Boolean = when (statusCode) {
                // Retryable client errors
                408 -> true  // Request Timeout
                429 -> true  // Too Many Requests
                // Retryable server errors
                in 500..599 -> true  // All 5xx errors
                // Non-retryable client errors
                else -> false
            }
        }

        class DnsError(cause: Throwable? = null) :
            NetworkException("DNS resolution failed", cause) {
            override val isRetryable: Boolean = true
        }

        class SslError(cause: Throwable? = null) :
            NetworkException("SSL/TLS error", cause) {
            override val isRetryable: Boolean = false  // Certificate issues unlikely to resolve on retry
        }
    }

    /**
     * Persistence-related errors (disk full, permission denied, etc.)
     */
    sealed class PersistenceException(
        message: String,
        cause: Throwable? = null
    ) : StoreException(message, cause) {

        class ReadError(cause: Throwable? = null) :
            PersistenceException("Failed to read from persistence", cause) {
            override val isRetryable: Boolean = true  // May be transient (lock contention, etc.)
        }

        class WriteError(cause: Throwable? = null) :
            PersistenceException("Failed to write to persistence", cause) {
            override val isRetryable: Boolean = true  // May be transient
        }

        class DeleteError(cause: Throwable? = null) :
            PersistenceException("Failed to delete from persistence", cause) {
            override val isRetryable: Boolean = true  // May be transient
        }

        class DiskFull(cause: Throwable? = null) :
            PersistenceException("Disk is full", cause) {
            override val isRetryable: Boolean = false  // Won't resolve without user action
        }

        class PermissionDenied(cause: Throwable? = null) :
            PersistenceException("Permission denied", cause) {
            override val isRetryable: Boolean = false  // Won't resolve without user action
        }

        class TransactionConflict(cause: Throwable? = null) :
            PersistenceException("Transaction conflict", cause) {
            override val isRetryable: Boolean = true  // Optimistic locking conflict
        }

        class DatabaseLocked(cause: Throwable? = null) :
            PersistenceException("Database is locked", cause) {
            override val isRetryable: Boolean = true  // Temporary lock
        }
    }

    /**
     * Data validation errors
     */
    class ValidationError(
        message: String,
        cause: Throwable? = null
    ) : StoreException(message, cause) {
        override val isRetryable: Boolean = false  // Data validation won't pass on retry
    }

    /**
     * Key not found in any cache or fetcher
     */
    class NotFound(
        key: String,
        cause: Throwable? = null
    ) : StoreException("Key not found: $key", cause) {
        override val isRetryable: Boolean = false  // Entity doesn't exist
    }

    /**
     * Data deserialization errors
     */
    class SerializationError(
        message: String,
        cause: Throwable? = null
    ) : StoreException(message, cause) {
        override val isRetryable: Boolean = false  // Data format issue
    }

    /**
     * Configuration errors
     */
    class ConfigurationError(
        message: String,
        cause: Throwable? = null
    ) : StoreException(message, cause) {
        override val isRetryable: Boolean = false  // Setup issue
    }

    /**
     * Rate limiting errors
     */
    class RateLimited(
        val retryAfter: Duration? = null,
        cause: Throwable? = null
    ) : StoreException("Rate limited${retryAfter?.let { " (retry after $it)" } ?: ""}", cause) {
        override val isRetryable: Boolean = true  // Can retry after backoff
    }

    /**
     * Unknown error
     */
    class Unknown(
        message: String,
        cause: Throwable? = null
    ) : StoreException(message, cause) {
        override val isRetryable: Boolean = true  // Conservative: allow retry
    }

    companion object {
        /**
         * Converts a generic exception to a StoreException.
         *
         * This provides intelligent classification of platform-specific exceptions
         * into appropriate StoreException types with correct retryability.
         *
         * **Common mappings:**
         * - IO exceptions → NetworkException or PersistenceException
         * - Serialization exceptions → SerializationError
         * - SQL exceptions → PersistenceException
         * - Timeout exceptions → NetworkException.Timeout
         * - CancellationException → rethrown (not converted)
         *
         * @param throwable The exception to convert
         * @return Appropriate StoreException subclass
         */
        fun from(throwable: Throwable): StoreException {
            // Never wrap CancellationException
            if (throwable is kotlinx.coroutines.CancellationException) {
                throw throwable
            }

            return when (throwable) {
                // Already a StoreException
                is StoreException -> throwable

                // Serialization errors
                is kotlinx.serialization.SerializationException ->
                    SerializationError("Serialization failed: ${throwable.message}", throwable)

                // Platform-specific mappings would go here
                // Note: These are commented out because they're not available in commonMain
                // Platforms should provide their own converters or use expect/actual

                /* JVM-specific (example - would need expect/actual):
                is java.io.IOException -> when {
                    throwable.message?.contains("timeout", ignoreCase = true) == true ->
                        NetworkException.Timeout(throwable)
                    throwable.message?.contains("connection", ignoreCase = true) == true ->
                        NetworkException.NoConnection(throwable)
                    else -> PersistenceException.ReadError(throwable)
                }

                is java.net.SocketTimeoutException ->
                    NetworkException.Timeout(throwable)

                is java.net.UnknownHostException ->
                    NetworkException.DnsError(throwable)

                is javax.net.ssl.SSLException ->
                    NetworkException.SslError(throwable)

                is java.sql.SQLException -> when {
                    throwable.message?.contains("disk", ignoreCase = true) == true ->
                        PersistenceException.DiskFull(throwable)
                    throwable.message?.contains("lock", ignoreCase = true) == true ->
                        PersistenceException.DatabaseLocked(throwable)
                    throwable.message?.contains("constraint", ignoreCase = true) == true ->
                        ValidationError("Constraint violation: ${throwable.message}", throwable)
                    else -> PersistenceException.WriteError(throwable)
                }
                */

                // Generic mapping based on message content
                else -> when {
                    throwable.message?.contains("timeout", ignoreCase = true) == true ->
                        NetworkException.Timeout(throwable)

                    throwable.message?.contains("connection", ignoreCase = true) == true ->
                        NetworkException.NoConnection(throwable)

                    throwable.message?.contains("not found", ignoreCase = true) == true ->
                        NotFound(throwable.message ?: "Unknown", throwable)

                    throwable.message?.contains("permission", ignoreCase = true) == true ||
                    throwable.message?.contains("access denied", ignoreCase = true) == true ->
                        PersistenceException.PermissionDenied(throwable)

                    throwable.message?.contains("disk full", ignoreCase = true) == true ||
                    throwable.message?.contains("no space", ignoreCase = true) == true ->
                        PersistenceException.DiskFull(throwable)

                    throwable.message?.contains("rate limit", ignoreCase = true) == true ||
                    throwable.message?.contains("too many requests", ignoreCase = true) == true ->
                        RateLimited(retryAfter = null, cause = throwable)

                    throwable.message?.contains("validation", ignoreCase = true) == true ||
                    throwable.message?.contains("invalid", ignoreCase = true) == true ->
                        ValidationError(throwable.message ?: "Validation failed", throwable)

                    throwable.message?.contains("serialization", ignoreCase = true) == true ||
                    throwable.message?.contains("deserialization", ignoreCase = true) == true ||
                    throwable.message?.contains("parse", ignoreCase = true) == true ->
                        SerializationError(throwable.message ?: "Serialization error", throwable)

                    throwable.message?.contains("configuration", ignoreCase = true) == true ||
                    throwable.message?.contains("misconfigured", ignoreCase = true) == true ->
                        ConfigurationError(throwable.message ?: "Configuration error", throwable)

                    throwable.message?.contains("lock", ignoreCase = true) == true ->
                        PersistenceException.DatabaseLocked(throwable)

                    throwable.message?.contains("conflict", ignoreCase = true) == true ->
                        PersistenceException.TransactionConflict(throwable)

                    throwable.message?.contains("dns", ignoreCase = true) == true ->
                        NetworkException.DnsError(throwable)

                    throwable.message?.contains("ssl", ignoreCase = true) == true ||
                    throwable.message?.contains("tls", ignoreCase = true) == true ||
                    throwable.message?.contains("certificate", ignoreCase = true) == true ->
                        NetworkException.SslError(throwable)

                    // Default to Unknown with retry allowed (conservative)
                    else -> Unknown(throwable.message ?: "Unknown error", throwable)
                }
            }
        }

        /**
         * Creates an HttpError from an HTTP status code.
         *
         * @param statusCode HTTP status code
         * @param body Optional response body
         * @param cause Optional underlying exception
         * @return NetworkException.HttpError with appropriate retryability
         */
        fun fromHttpStatus(
            statusCode: Int,
            body: String? = null,
            cause: Throwable? = null
        ): NetworkException.HttpError {
            return NetworkException.HttpError(statusCode, body, cause)
        }

        /**
         * Creates a RateLimited exception with retry-after duration.
         *
         * @param retryAfter Duration to wait before retrying
         * @param cause Optional underlying exception
         * @return RateLimited exception
         */
        fun rateLimited(retryAfter: Duration? = null, cause: Throwable? = null): RateLimited {
            return RateLimited(retryAfter, cause)
        }
    }
}


interface MemoryCache<Key: Any, Value: Any> {
    suspend fun get(key: Key): Value?
    suspend fun put(key: Key, value: Value): Boolean
    suspend fun remove(key: Key): Boolean
    suspend fun clear()
}

/**
 * In-memory cache with LRU eviction and TTL support.
 */
internal class MemoryCacheImpl<Key : Any, Value : Any>(
    private val maxSize: Int,
    private val ttl: Duration
): MemoryCache<Key, Value> {
    private val cache = HashMap<Key, CacheEntry<Value>>() // TODO: Make this thread safe
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

data class DefaultDbMeta(
    val updatedAt: Instant,
    val etag: String? = null
)

class DefaultFreshnessValidator<K : StoreKey>(
    private val ttl: Duration,                 // e.g., 5.minutes
    private val staleIfError: Duration? = 10.minutes
) : FreshnessValidator<K, DefaultDbMeta> {

    override fun plan(ctx: FreshnessContext<K, DefaultDbMeta>): FetchPlan {
        val age = ctx.sotMeta?.let { ctx.now - it.updatedAt }
        val backoffActive = ctx.status.backoffUntil?.let { ctx.now < it } == true

        if (backoffActive) return FetchPlan.Skip

        return when (ctx.freshness) {
            Freshness.CachedOrFetch -> {
                when {
                    ctx.sotMeta == null -> unconditional()
                    age != null && age <= ttl -> FetchPlan.Skip
                    else -> conditional(ctx.sotMeta.etag, ctx.sotMeta.updatedAt)
                }
            }
            is Freshness.MinAge -> {
                val maxAge = ctx.freshness.notOlderThan
                if (age == null || age > maxAge) conditional(ctx.sotMeta?.etag, ctx.sotMeta?.updatedAt)
                else FetchPlan.Skip
            }
            Freshness.MustBeFresh -> unconditional()
            Freshness.StaleIfError -> {
                // Prefer conditional to save bytes; if network fails upstream, Store serves stale.
                conditional(ctx.sotMeta?.etag, ctx.sotMeta?.updatedAt)
            }
        }
    }

    private fun unconditional() = FetchPlan.Unconditional
    private fun conditional(etag: String?, lastModified: Instant?) =
        if (etag != null || lastModified != null)
            FetchPlan.Conditional(ConditionalRequest(etag = etag, lastModified = lastModified))
        else FetchPlan.Unconditional
}