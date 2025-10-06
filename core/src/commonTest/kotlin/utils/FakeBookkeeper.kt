package dev.mattramotar.storex.core.utils

import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.internal.Bookkeeper
import dev.mattramotar.storex.core.internal.KeyStatus
import kotlinx.datetime.Instant

/**
 * Fake Bookkeeper for testing.
 *
 * Records all success/failure events for assertions.
 */
class FakeBookkeeper<K : StoreKey> : Bookkeeper<K> {
    private val status = mutableMapOf<K, KeyStatus>()

    // Recording lists for test assertions
    val recordedSuccesses = mutableListOf<Triple<K, String?, Instant>>()
    val recordedFailures = mutableListOf<Triple<K, Throwable, Instant>>()

    override fun recordSuccess(key: K, etag: String?, at: Instant) {
        recordedSuccesses.add(Triple(key, etag, at))
        val current = status[key] ?: KeyStatus(null, null, null, null)
        status[key] = current.copy(lastSuccessAt = at, lastEtag = etag)
    }

    override fun recordFailure(key: K, error: Throwable, at: Instant) {
        recordedFailures.add(Triple(key, error, at))
        val current = status[key] ?: KeyStatus(null, null, null, null)
        status[key] = current.copy(lastFailureAt = at)
    }

    override fun lastStatus(key: K): KeyStatus {
        return status[key] ?: KeyStatus(null, null, null, null)
    }

    /**
     * Preset a status for a key (useful for testing scenarios).
     */
    fun setStatus(key: K, status: KeyStatus) {
        this.status[key] = status
    }

    /**
     * Clear all recorded data.
     */
    fun clear() {
        status.clear()
        recordedSuccesses.clear()
        recordedFailures.clear()
    }
}

private fun KeyStatus.copy(
    lastSuccessAt: Instant? = this.lastSuccessAt,
    lastFailureAt: Instant? = this.lastFailureAt,
    lastEtag: String? = this.lastEtag,
    backoffUntil: Instant? = this.backoffUntil
) = KeyStatus(lastSuccessAt, lastFailureAt, lastEtag, backoffUntil)
