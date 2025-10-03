package dev.mattramotar.storex.store.normalization.backend

import dev.mattramotar.storex.normalization.format.NormalizedRecord
import dev.mattramotar.storex.normalization.keys.EntityKey
import dev.mattramotar.storex.store.StoreKey
import dev.mattramotar.storex.store.normalization.EntityMeta
import dev.mattramotar.storex.store.normalization.NormalizedChangeSet
import dev.mattramotar.storex.store.normalization.ShapeId
import kotlinx.coroutines.flow.Flow



/**
 * Identifies a specific query/shape combination that depends on entities.
 */
data class RootRef(
    val requestKey: StoreKey,
    val shapeId: ShapeId
) {
    override fun toString(): String = "${requestKey.stableHash()}:${shapeId.value}"

    companion object {
        fun parse(str: String): RootRef {
            val parts = str.split(":", limit = 2)
            require(parts.size == 2) { "Invalid RootRef format: $str" }
            // Note: This requires StoreKey to be deserializable from hash
            // In practice, you'd store the full serialized key
            TODO("Implement StoreKey deserialization")
        }
    }
}

interface NormalizationBackend {
    /** Batched read of normalized records. Missing keys map to null. */
    suspend fun read(keys: Set<EntityKey>): Map<EntityKey, NormalizedRecord?>
    /** Optional: read metadata (etag/updatedAt/tombstone). */
    suspend fun readMeta(keys: Set<EntityKey>): Map<EntityKey, EntityMeta?>

    suspend fun readOne(key: EntityKey): NormalizedRecord? {
        return read(setOf(key))[key]
    }

    /** Atomic application of a normalized change set (upserts, deletes, rekeys). */
    suspend fun apply(changeSet: NormalizedChangeSet)

    /** Update durable dependency mapping: which entities a root graph depends on. */
    suspend fun updateRootDependencies(root: RootRef, dependsOn: Set<EntityKey>)

    /** Clears all entities. */
    suspend fun clear()

    /** Entities changed in the last write (may be coalesced). */
    val entityInvalidations: Flow<Set<EntityKey>>
    /** Roots invalidated due to entity changes (preferred for recomposition triggers). */
    val rootInvalidations: Flow<Set<RootRef>>
}