package dev.mattramotar.storex.ktor

import dev.mattramotar.storex.core.StoreKey

/**
 * Ktor HTTP Client integration for StoreX.
 *
 * Provides automatic Fetcher creation from Ktor HttpClient, with support
 * for authentication, conditional requests, and Ktor plugins.
 *
 * **Planned Features** (to be implemented):
 * - Automatic Fetcher from Ktor HttpClient
 * - Authentication plugin integration
 * - Conditional request support (ETag, If-Modified-Since)
 * - Request/response interceptors
 * - Automatic retry with Ktor's retry plugin
 * - Content negotiation integration
 *
 * Example usage (future):
 * ```kotlin
 * val httpClient = HttpClient {
 *     install(ContentNegotiation) {
 *         json()
 *     }
 *     install(Auth) {
 *         bearer {
 *             loadTokens { /* ... */ }
 *         }
 *     }
 * }
 *
 * val store = store<UserKey, User> {
 *     fetcher = httpClient.asFetcher { key ->
 *         get("/api/users/${key.id}")
 *     }
 *     // ... other configuration
 * }
 *
 * // With conditional requests
 * val store = store<ArticleKey, Article> {
 *     fetcher = httpClient.asFetcher(
 *         conditionalRequests = true
 *     ) { key ->
 *         get("/api/articles/${key.id}") {
 *             header("If-None-Match", etag)
 *         }
 *     }
 * }
 * ```
 */

/**
 * Configuration for Ktor-based fetchers.
 */
data class KtorFetcherConfig(
    /**
     * Whether to use conditional requests (ETag, If-Modified-Since).
     */
    val conditionalRequests: Boolean = false,

    /**
     * Whether to automatically retry failed requests.
     */
    val autoRetry: Boolean = true,

    /**
     * Maximum number of retry attempts.
     */
    val maxRetries: Int = 3
)

/**
 * Response wrapper for Ktor HTTP responses.
 *
 * @param K The store key type
 * @param V The response body type
 */
interface KtorResponse<K : StoreKey, V> {
    /**
     * The response body.
     */
    val body: V

    /**
     * The ETag header, if present.
     */
    val etag: String?

    /**
     * The Last-Modified header, if present.
     */
    val lastModified: String?

    /**
     * The HTTP status code.
     */
    val statusCode: Int

    /**
     * Whether the response was a 304 Not Modified.
     */
    val notModified: Boolean
}

// TODO: Implement the following in future phases:
// - HttpClient.asFetcher(): Extension to create Fetcher from HttpClient
// - ConditionalRequestHandler: Handler for ETag/If-Modified-Since
// - KtorAuthInterceptor: Authentication integration
// - KtorRetryPolicy: Retry policy using Ktor's retry plugin
// - KtorContentNegotiator: Content negotiation helper
// - streamingFetcher(): Support for streaming responses
// - multipartFetcher(): Support for multipart uploads
