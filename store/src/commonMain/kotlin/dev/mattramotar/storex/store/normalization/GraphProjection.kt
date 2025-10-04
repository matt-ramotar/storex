package dev.mattramotar.storex.store.normalization

import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * Aggregate freshness over an entire object graph.
 * - updatedAtMin: min(updatedAt) across all participating entities
 * - etagFingerprint: stable fingerprint of entity etags (optional)
 */
data class GraphMeta(
    val updatedAt: Instant,
    val etagFingerprint: String? = null
) {
    fun age(now: Instant): Duration = now - updatedAt
}

/** SOT read value + attached graph metadata (used by the FreshnessValidator). */
data class GraphProjection<V>(
    val value: V,
    val meta: GraphMeta
)