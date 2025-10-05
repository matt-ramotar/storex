package dev.mattramotar.storex.normalization

import dev.mattramotar.storex.normalization.keys.EntityKey
import dev.mattramotar.storex.core.StoreKey

/**
 * What the normalized SOT expects for its write path:
 * - A normalized change-set to apply atomically
 * - Optionally, an index update (for list/query roots) to keep paging roots in sync
 */
data class NormalizedWrite<K : StoreKey>(
    val changeSet: NormalizedChangeSet,
    val indexUpdate: IndexUpdate<K>? = null
)

data class IndexUpdate<K : StoreKey>(
    val requestKey: K,
    val roots: List<EntityKey>
)
