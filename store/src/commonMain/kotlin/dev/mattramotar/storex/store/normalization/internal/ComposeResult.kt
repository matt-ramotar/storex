package dev.mattramotar.storex.store.normalization.internal

import dev.mattramotar.storex.normalization.format.NormalizedRecord
import dev.mattramotar.storex.normalization.keys.EntityKey
import dev.mattramotar.storex.normalization.schema.DenormalizationContext
import dev.mattramotar.storex.normalization.schema.EntityAdapter
import dev.mattramotar.storex.normalization.schema.SchemaRegistry
import dev.mattramotar.storex.store.normalization.EntityMeta
import dev.mattramotar.storex.store.normalization.GraphMeta
import dev.mattramotar.storex.store.normalization.Shape
import dev.mattramotar.storex.store.normalization.backend.NormalizationBackend
import kotlinx.datetime.Clock
import kotlin.math.abs

internal data class ComposeResult<V: Any>(
    val value: V,
    val dependencies: Set<EntityKey>,
    val meta: GraphMeta
)

/**
 * Compose a domain value V from a root EntityKey using BFS over the normalized graph.
 * - Batches reads via NormalizationBackend.read(...)
 * - Tracks dependencies (entity keys visited)
 * - Aggregates GraphMeta using EntityMeta
 * - Handles errors gracefully, tracking failed entities
 */
internal suspend fun <V: Any> composeFromRoot(
    root: EntityKey,
    shape: Shape<V>,
    registry: SchemaRegistry,
    backend: NormalizationBackend
): ComposeResult<V> {
    // BFS gather of records with depth tracking
    val seen = LinkedHashSet<EntityKey>()
    val queue = ArrayDeque<Pair<EntityKey, Int>>().apply { add(root to 0) }  // Track (key, depth)
    val records = LinkedHashMap<EntityKey, NormalizedRecord?>()
    val errors = mutableMapOf<EntityKey, Throwable>()
    var maxDepthReached = false

    fun outboundRefs(rec: NormalizedRecord?): Set<EntityKey> {
        if (rec == null) return emptySet()
        return shape.outboundRefs(rec)
    }

    while (queue.isNotEmpty()) {
        val batch = buildList<Pair<EntityKey, Int>> {
            while (queue.isNotEmpty() && size < 256) {
                val (key, depth) = queue.removeFirst()

                // Check depth limit
                if (depth >= shape.maxDepth) {
                    maxDepthReached = true
                    continue
                }

                // Cycle detection - skip if already seen
                if (key in seen) continue

                add(key to depth)
            }
        }

        if (batch.isEmpty()) continue

        val keys = batch.map { it.first }
        seen += keys

        // Handle backend read errors gracefully
        try {
            records += backend.read(keys.toSet())
        } catch (e: Exception) {
            // Log error and mark entities as failed, but continue with partial data
            keys.forEach { key ->
                errors[key] = e
                records[key] = null  // Mark as null to stop traversal for this branch
            }
            // Continue to next batch instead of failing completely
            continue
        }

        batch.forEach { (key, depth) ->
            outboundRefs(records[key]).forEach { childKey ->
                if (childKey !in seen) {
                    queue.add(childKey to depth + 1)  // Increment depth
                }
            }
        }
    }

    // Meta aggregation
    val metas: Map<EntityKey, EntityMeta?> = backend.readMeta(seen)
    val updatedAtMin = metas.values
        .filterNotNull()
        .minOfOrNull { it.updatedAt } ?: Clock.System.now()

    val etagConcat = metas.values.filterNotNull().mapNotNull { it.etag }.sorted().joinToString("|")
    val etagFp = if (etagConcat.isEmpty()) null else {
        // tiny non-crypto fp; replace with a real hash if you prefer
        abs(etagConcat.hashCode()).toString(16)
    }

    val graphMeta = GraphMeta(updatedAt = updatedAtMin, etagFingerprint = etagFp)

    // Denormalize with a request-scoped resolver backed by 'records'
    val resolver = object : DenormalizationContext {
        override suspend fun resolveReference(key: EntityKey): Any? {
            val rec = records[key] ?: backend.read(setOf(key))[key]
            @Suppress("UNCHECKED_CAST")
            val adapter: EntityAdapter<Any> = registry.getAdapter(key.typeName)
            return rec?.let { adapter.denormalize(it, this) }
        }
    }

    val rootRec = records[root]
    @Suppress("UNCHECKED_CAST")
    val rootAdapter: EntityAdapter<V> = registry.getAdapter(root.typeName)

    val value = if (rootRec != null) {
        try {
            rootAdapter.denormalize(rootRec, resolver)
        } catch (e: Exception) {
            throw dev.mattramotar.storex.store.normalization.GraphCompositionException(
                message = "Failed to denormalize root entity: $root",
                rootKey = root,
                shapeId = shape.id,
                partialRecords = records.size,
                totalExpected = seen.size,
                failedEntities = errors,
                maxDepthReached = maxDepthReached,
                cause = e
            )
        }
    } else {
        throw dev.mattramotar.storex.store.normalization.GraphCompositionException(
            message = "Root entity not found: $root",
            rootKey = root,
            shapeId = shape.id,
            partialRecords = records.size,
            totalExpected = seen.size,
            failedEntities = errors,
            maxDepthReached = maxDepthReached
        )
    }

    return ComposeResult(
        value = value,
        dependencies = seen,
        meta = graphMeta
    )
}
