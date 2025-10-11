package dev.mattramotar.storex.normalization.internal


import dev.mattramotar.storex.normalization.keys.EntityKey
import dev.mattramotar.storex.normalization.schema.SchemaRegistry
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.internal.SourceOfTruth
import dev.mattramotar.storex.normalization.GraphMeta
import dev.mattramotar.storex.normalization.GraphProjection
import dev.mattramotar.storex.normalization.IndexManager
import dev.mattramotar.storex.normalization.NormalizedWrite
import dev.mattramotar.storex.normalization.Shape
import dev.mattramotar.storex.normalization.backend.NormalizationBackend
import dev.mattramotar.storex.normalization.backend.RootRef
import dev.mattramotar.storex.normalization.internal.composeFromRoot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.Clock

/**
 * SOT for *lists* backed by normalized storage. Roots come from an IndexManager.
 * ReadDb = GraphProjection<List<V>>
 * WriteDb = NormalizedWrite<K>
 */
class NormalizedListSot<K : StoreKey, V: Any>(
    private val backend: NormalizationBackend,
    private val index: IndexManager,
    private val registry: SchemaRegistry,
    private val itemShape: Shape<V> // shape used to compose each list item
) : SourceOfTruth<K, GraphProjection<List<V>>, NormalizedWrite<K>> {

    override fun reader(key: K): Flow<GraphProjection<List<V>>?> {
        val rootRef = RootRef(key, itemShape.id)
        val rootsFlow = index.streamIndex(key)

        val invalidations = backend.rootInvalidations
            .filter { roots -> roots.isEmpty() || roots.contains(rootRef) }
            .onStart { emit(emptySet()) } // initial compose
            .conflate()  // Drop intermediate invalidations

        return combine(rootsFlow, invalidations) { roots, _ -> roots }
            .conflate()  // Drop intermediate combinations
            .flatMapLatest { roots: List<EntityKey>? ->
                flow {
                    if (roots == null) {
                        emit(GraphProjection(emptyList(), meta = defaultEmptyMeta()))
                        return@flow
                    }
                    val composed = roots.map { root ->
                        composeFromRoot(root, itemShape, registry, backend)
                    }
                    val deps = composed.flatMapTo(LinkedHashSet()) { it.dependencies }
                    // aggregate meta over the list
                    val updatedAtMin = composed.minOfOrNull { it.meta.updatedAt }
                        ?: Clock.System.now()
                    val etagFingerprint = composed.mapNotNull { it.meta.etagFingerprint }
                        .sorted().joinToString("|").let { if (it.isEmpty()) null else it }
                    backend.updateRootDependencies(rootRef, deps)
                    emit(
                        GraphProjection(
                            value = composed.map { it.value },
                            meta = GraphMeta(
                                updatedAt = updatedAtMin,
                                etagFingerprint = etagFingerprint
                            )
                        )
                    )
                }
            }
    }

    override suspend fun write(key: K, value: NormalizedWrite<K>) {
        backend.apply(value.changeSet)
        value.indexUpdate?.let { upd ->
            // Keep the root index current for the list key.
            // (If you prefer, IndexManager can be injected and updated here instead of backend)
            // This demo keeps IndexManager outside the backend; update it via the builder (see samples).
        }
    }

    override suspend fun delete(key: K) {
        // same rationale as entity SOT; do nothing by default
    }

    override suspend fun withTransaction(block: suspend () -> Unit) = block()

    private fun defaultEmptyMeta() =
        GraphMeta(
            updatedAt = Clock.System.now(), etagFingerprint = null
        )

    override suspend fun rekey(
        old: K,
        new: K,
        reconcile: suspend (GraphProjection<List<V>>, GraphProjection<List<V>>?) -> GraphProjection<List<V>>
    ) { /* no-op */ }
}
