package dev.mattramotar.storex.core.internal

import dev.mattramotar.storex.core.StoreKey
import kotlinx.datetime.Instant

/**
 * Bookkeeper tracks fetch success/failure status per key.
 *
 * Used for:
 * - Conditional requests (providing etag for If-None-Match)
 * - Backoff/retry logic (preventing repeated failures)
 * - Telemetry (tracking error rates)
 */
interface Bookkeeper<K : StoreKey> {
    /**
     * Records a successful fetch.
     *
     * @param key The key that was fetched
     * @param etag Optional ETag from the response
     * @param at Timestamp of the success
     */
    fun recordSuccess(key: K, etag: String?, at: Instant)

    /**
     * Records a failed fetch.
     *
     * @param key The key that failed
     * @param error The error that occurred
     * @param at Timestamp of the failure
     */
    fun recordFailure(key: K, error: Throwable, at: Instant)

    /**
     * Gets the last known status for a key.
     *
     * @param key The key to query
     * @return Status including last success/failure times and etag
     */
    fun lastStatus(key: K): KeyStatus
}

/**
 * Status of a key's fetch history.
 *
 * @property lastSuccessAt Timestamp of last successful fetch
 * @property lastFailureAt Timestamp of last failed fetch
 * @property lastEtag ETag from last successful fetch (for conditional requests)
 * @property backoffUntil Timestamp until which fetches should be skipped (for rate limiting)
 */
data class KeyStatus(
    val lastSuccessAt: Instant?,
    val lastFailureAt: Instant?,
    val lastEtag: String?,
    val backoffUntil: Instant?
)
