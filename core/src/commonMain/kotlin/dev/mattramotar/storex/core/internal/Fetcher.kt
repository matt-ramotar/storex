package dev.mattramotar.storex.core.internal

import dev.mattramotar.storex.core.StoreKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

/**
 * Fetcher - Remote data source for Store.
 *
 * A fetcher retrieves data from a remote source (network API, WebSocket, etc.).
 * It can be:
 * - **One-shot**: Emit a single value and complete (typical HTTP GET)
 * - **Streaming**: Emit multiple values over time (WebSocket, SSE, gRPC stream)
 *
 * Fetchers should:
 * - Return [FetcherResult] to distinguish success/error/not-modified states
 * - Support conditional requests (If-None-Match, If-Modified-Since) when possible
 * - Handle retries at the network layer (not in the fetcher itself)
 *
 * @param Key The [StoreKey] subtype identifying what to fetch
 * @param Network The network response type (e.g., UserDto, UserJson)
 *
 * @see fetcherOf For creating one-shot fetchers from suspend functions
 * @see streamingFetcherOf For creating streaming fetchers from Flows
 *
 * ## Example: Simple HTTP Fetcher
 * ```kotlin
 * val userFetcher = fetcherOf<UserKey, UserJson> { key ->
 *     httpClient.get("/users/${key.id}")
 * }
 * ```
 *
 * ## Example: Conditional Request Fetcher
 * ```kotlin
 * val articleFetcher = Fetcher<ArticleKey, ArticleJson> { key, request ->
 *     flow {
 *         val headers = buildMap {
 *             request.conditional?.etag?.let { put("If-None-Match", it) }
 *             request.conditional?.lastModified?.let {
 *                 put("If-Modified-Since", it.toString())
 *             }
 *         }
 *
 *         val response = httpClient.get("/articles/${key.id}") {
 *             headers.forEach { (k, v) -> header(k, v) }
 *         }
 *
 *         when (response.status) {
 *             304 -> emit(FetcherResult.NotModified(
 *                 etag = response.headers["ETag"],
 *                 lastModified = response.headers["Last-Modified"]?.let { Instant.parse(it) }
 *             ))
 *             200 -> emit(FetcherResult.Success(
 *                 body = response.body(),
 *                 etag = response.headers["ETag"],
 *                 lastModified = response.headers["Last-Modified"]?.let { Instant.parse(it) }
 *             ))
 *             else -> emit(FetcherResult.Error(
 *                 StoreException.fromHttpStatus(response.status)
 *             ))
 *         }
 *     }
 * }
 * ```
 *
 * ## Example: Streaming WebSocket Fetcher
 * ```kotlin
 * val pricesFetcher = streamingFetcherOf<TickerKey, PriceUpdate> { key ->
 *     webSocket.connect("/prices/${key.symbol}")
 *         .map { message -> json.decodeFromString<PriceUpdate>(message) }
 * }
 * ```
 */
fun interface Fetcher<Key : StoreKey, Network : Any> {

    /**
     * Fetches data for the given key.
     *
     * Returns a Flow that emits [FetcherResult] values:
     * - [FetcherResult.Success]: Fetch succeeded with data
     * - [FetcherResult.NotModified]: Conditional request returned 304 (data unchanged)
     * - [FetcherResult.Error]: Fetch failed with exception
     *
     * The Flow should:
     * - Emit at least one result (or throw)
     * - Complete after emitting (for one-shot fetchers)
     * - Stay active and emit multiple results (for streaming fetchers)
     *
     * @param key The key identifying what to fetch
     * @param request Fetch parameters (conditional headers, urgency, etc.)
     * @return Flow emitting fetch results
     */
    fun fetch(key: Key, request: FetchRequest): Flow<FetcherResult<Network>>
}

/**
 * Parameters for a fetch request.
 *
 * @property conditional Optional conditional request headers (If-None-Match, If-Modified-Since)
 * @property urgency Network priority hint (for QoS, rate limiting, etc.)
 */
data class FetchRequest(
    val conditional: ConditionalRequest? = null,
    val urgency: Urgency = Urgency.Normal
)

/**
 * Network priority for quality-of-service hints.
 *
 * - **Low**: Background fetch, can be delayed/throttled
 * - **Normal**: Standard user-initiated fetch
 * - **High**: Critical user action, prioritize this request
 */
enum class Urgency { Low, Normal, High }

/**
 * Conditional request parameters for HTTP 304 Not Modified support.
 *
 * Used to avoid re-fetching unchanged data. The fetcher should translate these
 * to appropriate HTTP headers:
 * - [etag] → If-None-Match header
 * - [lastModified] → If-Modified-Since header
 * - [maxStale] → Cache-Control: max-stale header
 *
 * @property etag ETag from previous response (for If-None-Match)
 * @property lastModified Last-Modified from previous response (for If-Modified-Since)
 * @property maxStale Maximum staleness acceptable (for Cache-Control)
 */
data class ConditionalRequest(
    val etag: String? = null,
    val lastModified: Instant? = null,
    val maxStale: kotlin.time.Duration? = null
)

/**
 * Result of a fetch operation.
 *
 * @param T The network response type
 */
sealed interface FetcherResult<out T> {
    /**
     * Fetch succeeded with data.
     *
     * @property body The response data
     * @property etag Optional ETag header for conditional requests
     * @property lastModified Optional Last-Modified header
     * @property cacheControl Optional Cache-Control header
     */
    data class Success<T>(
        val body: T,
        val etag: String? = null,
        val lastModified: Instant? = null,
        val cacheControl: String? = null
    ) : FetcherResult<T>

    /**
     * Conditional request returned 304 Not Modified.
     *
     * Server confirmed cached data is still valid.
     *
     * @property etag Updated ETag header (may differ from request)
     * @property lastModified Updated Last-Modified header
     */
    data class NotModified(
        val etag: String? = null,
        val lastModified: Instant? = null
    ) : FetcherResult<Nothing>

    /**
     * Fetch failed with an error.
     *
     * @property error The exception that occurred
     */
    data class Error(val error: StoreException) : FetcherResult<Nothing>
}

/**
 * Creates a one-shot fetcher from a suspending function.
 *
 * Use this for simple HTTP GET requests or any suspend function that returns data.
 *
 * @param Key The [StoreKey] subtype
 * @param Network The network response type
 * @param fetch Suspending function that fetches data
 * @return Fetcher that emits a single result
 *
 * ## Example
 * ```kotlin
 * val fetcher = fetcherOf<UserKey, User> { key ->
 *     httpClient.get("/users/${key.id}")
 * }
 * ```
 */
fun <Key : StoreKey, Network : Any> fetcherOf(
    fetch: suspend (Key) -> Network
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
 *
 * Use this for WebSocket connections, Server-Sent Events, or any long-lived
 * connection that emits multiple values over time.
 *
 * @param Key The [StoreKey] subtype
 * @param Network The network response type
 * @param fetch Function that returns a Flow of network responses
 * @return Fetcher that emits multiple results over time
 *
 * ## Example
 * ```kotlin
 * val fetcher = streamingFetcherOf<TickerKey, PriceUpdate> { key ->
 *     webSocket.connect("/prices/${key.symbol}")
 *         .map { json.decodeFromString<PriceUpdate>(it) }
 * }
 * ```
 */
fun <Key : StoreKey, Network : Any> streamingFetcherOf(
    fetch: (Key) -> Flow<Network>
): Fetcher<Key, Network> = Fetcher { key, validator ->
    fetch(key).map { network ->
        // TODO: Integrate FetchRequest.conditional and FetchRequest.urgency
        FetcherResult.Success(network)
    }.catch { e -> FetcherResult.Error(StoreException.from(e)) }
}
