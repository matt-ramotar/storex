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
 */
internal suspend fun <V: Any> composeFromRoot(
    root: EntityKey,
    shape: Shape<V>,
    registry: SchemaRegistry,
    backend: NormalizationBackend
): ComposeResult<V> {
    // BFS gather of records
    val seen = LinkedHashSet<EntityKey>()
    val queue = ArrayDeque<EntityKey>().apply { add(root) }
    val records = LinkedHashMap<EntityKey, NormalizedRecord?>()

    fun outboundRefs(rec: NormalizedRecord?): Set<EntityKey> {
        if (rec == null) return emptySet()
        return shape.outboundRefs(rec)
    }

    while (queue.isNotEmpty()) {
        val batch = buildList<EntityKey> {
            while (queue.isNotEmpty() && size < 256) add(queue.removeFirst())
        }.filterNot(seen::contains)

        if (batch.isEmpty()) continue
        seen += batch
        records += backend.read(batch.toSet())
        batch.forEach { key -> outboundRefs(records[key]).forEach { if (it !in seen) queue.add(it) } }
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
    val value = rootRec?.let { rootAdapter.denormalize(it, resolver) }
        ?: error("Root entity not found: $root")

    return ComposeResult(
        value = value,
        dependencies = seen,
        meta = graphMeta
    )
}
