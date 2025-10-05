package dev.mattramotar.storex.interceptors

import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.StoreResult

/**
 * Interceptor for Store operations.
 *
 * Interceptors can modify requests/responses, add logging, collect metrics,
 * handle authentication, implement caching policies, and more.
 *
 * **Planned Features** (to be implemented):
 * - Request/response transformation
 * - Logging and metrics collection
 * - Authentication and authorization
 * - Caching strategies (HTTP cache headers, conditional requests)
 * - Error handling and retry logic
 * - Request/response validation
 *
 * Example usage (future):
 * ```kotlin
 * val loggingInterceptor = object : Interceptor<MyKey, MyValue> {
 *     override suspend fun intercept(
 *         key: MyKey,
 *         chain: InterceptorChain<MyKey, MyValue>
 *     ): StoreResult<MyValue> {
 *         println("Fetching: $key")
 *         val result = chain.proceed(key)
 *         println("Result: $result")
 *         return result
 *     }
 * }
 * ```
 *
 * @param K The store key type
 * @param V The domain value type
 */
interface Interceptor<K : StoreKey, V> {
    /**
     * Intercepts a Store operation.
     *
     * @param key The key being fetched
     * @param chain The interceptor chain to continue the operation
     * @return The result of the operation
     */
    suspend fun intercept(key: K, chain: InterceptorChain<K, V>): StoreResult<V>
}

/**
 * Chain of interceptors for processing Store operations.
 *
 * @param K The store key type
 * @param V The domain value type
 */
interface InterceptorChain<K : StoreKey, V> {
    /**
     * Proceeds with the operation, passing it to the next interceptor in the chain.
     *
     * @param key The key being fetched
     * @return The result of the operation
     */
    suspend fun proceed(key: K): StoreResult<V>
}

// TODO: Implement the following interceptors in future phases:
// - LoggingInterceptor: Logs all Store operations
// - MetricsInterceptor: Collects performance metrics
// - AuthInterceptor: Adds authentication headers/tokens
// - CachingInterceptor: Implements HTTP cache header support
// - RetryInterceptor: Automatic retry with backoff
// - ValidationInterceptor: Validates requests/responses
