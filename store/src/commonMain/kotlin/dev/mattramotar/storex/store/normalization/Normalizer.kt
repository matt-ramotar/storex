package dev.mattramotar.storex.store.normalization

import dev.mattramotar.storex.normalization.format.NormalizedRecord
import dev.mattramotar.storex.normalization.format.NormalizedValue
import dev.mattramotar.storex.normalization.keys.EntityKey
import dev.mattramotar.storex.store.normalization.backend.RootRef
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.jvm.JvmInline
import kotlin.reflect.KClass

data class NormalizedChangeSet(
    /** Entities to insert or update. */
    val upserts: Map<EntityKey, NormalizedRecord> = emptyMap(),
    /** Entities to delete (hard or soft depending on backend policy. */
    val deletes: Set<EntityKey> = emptySet(),
    /**
     * Provisional -> Canonical key migrations.
     * Used when server assigns permanent IDs after optimistic creates.
     */
    val rekeys: List<Rekey> = emptyList(),

    /**
     * Field names present in the payload per entity (PATCH safety).
     * Only masked fields are written. Others remain unchanged (PATCH semantics).
     * If empty for a key, all fields in the record are written (PUT semantics).
     */
    val fieldMasks: Map<EntityKey, Set<String>> = emptyMap(),

    /** Optional per-entity metadata used for freshness/conditional requests. */
    val meta: Map<EntityKey, EntityMeta> = emptyMap()
)

data class Rekey(
    val oldKey: EntityKey,
    val newKey: EntityKey
)

data class EntityMeta(
    /** ETag from the server for conditional requests. */
    val etag: String? = null,
    /** Timestamp when this entity was last updated. */
    val updatedAt: Instant = Clock.System.now(),
    /** Soft delete marker for anti-resurrection windows; backend may retain tombstones. */
    val tombstone: Boolean = false,
    /** Application-specific tags for garbage collection or filtering. */
    val tags: Set<String> = emptySet(),
)

@JvmInline
value class ShapeId(val value: String)

/**
 * A shape describes which outbound reference fields are traversed during recomposition
 * and may constrain breadth (e.g., edges, limits) for Strategy 2 (framework-managed).
 */
interface Shape<V : Any> {
    /** Unique identifier for this shape. */
    val id: ShapeId

    /** The output type this shape produces. */
    val outputType: KClass<V>

    /** A set of field names in the record that are known reference or reference-list edges. */
    val edgeFields: Set<String>

    /** Optional: constrain how many children of an edge to traverse (by field). */
    val edgeLimits: Map<String, Int> get() = emptyMap()

    /** Returns the maximum traversal depth for this shape. Prevents infinite recursion in cyclic graphs. */
    val maxDepth: Int get() = 10

    /**
     * Extract outbound EntityKey refs from a record according to this plan.
     * Used for batched graph traversal.
     */
    fun outboundRefs(record: NormalizedRecord): Set<EntityKey>
}


// Re-export for convenience
typealias EntityAdapter<T> = dev.mattramotar.storex.normalization.schema.EntityAdapter<T>
typealias NormalizationContext = dev.mattramotar.storex.normalization.schema.NormalizationContext
typealias DenormalizationContext = dev.mattramotar.storex.normalization.schema.DenormalizationContext
typealias SchemaRegistry = dev.mattramotar.storex.normalization.schema.SchemaRegistry


/**
 * Builder for constructing change-sets.
 */
class ChangeSetBuilder {
    private val upserts = mutableMapOf<EntityKey, NormalizedRecord>()
    private val deletes = mutableSetOf<EntityKey>()
    private val rekeys = mutableListOf<Rekey>()
    private val fieldMasks = mutableMapOf<EntityKey, Set<String>>()
    private val meta = mutableMapOf<EntityKey, EntityMeta>()

    /**
     * Adds an upsert with full field replacement (PUT semantics).
     */
    fun upsert(key: EntityKey, record: NormalizedRecord, metadata: EntityMeta? = null): ChangeSetBuilder {
        upserts[key] = record
        if (metadata != null) {
            meta[key] = metadata
        }
        return this
    }

    /**
     * Adds an upsert with partial field update (PATCH semantics).
     */
    fun patch(key: EntityKey, record: NormalizedRecord, mask: Set<String>, metadata: EntityMeta? = null): ChangeSetBuilder {
        upserts[key] = record
        fieldMasks[key] = mask
        if (metadata != null) {
            meta[key] = metadata
        }
        return this
    }

    /**
     * Adds a delete operation.
     */
    fun delete(key: EntityKey): ChangeSetBuilder {
        deletes.add(key)
        return this
    }

    /**
     * Adds a rekey operation.
     */
    fun rekey(oldKey: EntityKey, newKey: EntityKey): ChangeSetBuilder {
        rekeys.add(Rekey(oldKey, newKey))
        return this
    }

    /**
     * Builds the change-set.
     */
    fun build(): NormalizedChangeSet {
        return NormalizedChangeSet(
            upserts = upserts,
            deletes = deletes,
            rekeys = rekeys,
            fieldMasks = fieldMasks,
            meta = meta
        )
    }
}

/**
 * Manages the persistent reference index.
 */
interface ReferenceTracker {
    /**
     * Records that a root depends on a set of entities.
     * Replaces any existing dependencies for this root.
     */
    suspend fun recordDependencies(
        root: RootRef,
        entities: Set<EntityKey>
    )

    /**
     * Returns all roots that depend on any of the given entities.
     */
    suspend fun getAffectedRoots(
        entities: Set<EntityKey>
    ): Set<RootRef>

    /**
     * Removes all dependencies for a root.
     */
    suspend fun removeDependencies(root: RootRef)

    /**
     * Removes all dependencies.
     */
    suspend fun clear()
}