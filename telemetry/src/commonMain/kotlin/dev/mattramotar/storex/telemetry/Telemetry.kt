package dev.mattramotar.storex.telemetry

import dev.mattramotar.storex.core.StoreKey
import kotlin.time.Duration

/**
 * Telemetry and observability for Store operations.
 *
 * Provides OpenTelemetry integration, metrics collection, distributed tracing,
 * and performance monitoring for Store operations.
 *
 * **Planned Features** (to be implemented):
 * - OpenTelemetry integration for traces and metrics
 * - Performance metrics (cache hit rate, fetch latency, error rate)
 * - Distributed tracing support
 * - Custom metric collectors
 * - Performance monitoring hooks
 * - Structured logging integration
 *
 * Example usage (future):
 * ```kotlin
 * val telemetry = StoreTelemetry {
 *     exporters {
 *         jaeger("localhost:14268")
 *         prometheus(port = 9090)
 *     }
 *     metrics {
 *         cacheHitRate()
 *         fetchLatency()
 *         errorRate()
 *     }
 * }
 *
 * val store = store<UserKey, User> {
 *     telemetry(telemetry)
 *     // ... other configuration
 * }
 * ```
 */
interface StoreTelemetry {
    /**
     * Records a cache hit event.
     *
     * @param key The key that was found in cache
     */
    fun recordCacheHit(key: StoreKey)

    /**
     * Records a cache miss event.
     *
     * @param key The key that was not found in cache
     */
    fun recordCacheMiss(key: StoreKey)

    /**
     * Records a fetch operation.
     *
     * @param key The key being fetched
     * @param duration The time taken to fetch
     * @param success Whether the fetch succeeded
     */
    fun recordFetch(key: StoreKey, duration: Duration, success: Boolean)

    /**
     * Records an error event.
     *
     * @param key The key that caused the error
     * @param error The error that occurred
     */
    fun recordError(key: StoreKey, error: Throwable)

    /**
     * Starts a distributed trace span.
     *
     * @param operation The operation name
     * @param key The key being operated on
     * @return A span that can be closed when the operation completes
     */
    fun startSpan(operation: String, key: StoreKey): Span
}

/**
 * A telemetry span representing a Store operation.
 */
interface Span {
    /**
     * Adds an attribute to the span.
     *
     * @param key The attribute key
     * @param value The attribute value
     */
    fun setAttribute(key: String, value: String)

    /**
     * Records an exception in the span.
     *
     * @param exception The exception that occurred
     */
    fun recordException(exception: Throwable)

    /**
     * Ends the span.
     */
    fun end()
}

// TODO: Implement the following in future phases:
// - OpenTelemetryTelemetry: Integration with OpenTelemetry SDK
// - PrometheusTelemetry: Prometheus metrics exporter
// - MetricsCollector: Built-in metrics collection
// - TracingInterceptor: Interceptor for automatic tracing
// - PerformanceMonitor: Performance monitoring and alerts
// - StructuredLogger: Structured logging integration
