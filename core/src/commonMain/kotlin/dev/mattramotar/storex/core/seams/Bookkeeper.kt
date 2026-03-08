package dev.mattramotar.storex.core.seams

import dev.mattramotar.storex.core.StoreKey
import kotlinx.datetime.Instant

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
