package dev.mattramotar.storex.store.normalization.internal


import dev.mattramotar.storex.normalization.keys.EntityKey
import dev.mattramotar.storex.normalization.schema.SchemaRegistry
import dev.mattramotar.storex.store.StoreKey
import dev.mattramotar.storex.store.internal.SourceOfTruth
import dev.mattramotar.storex.store.normalization.GraphProjection
import dev.mattramotar.storex.store.normalization.NormalizedWrite
import dev.mattramotar.storex.store.normalization.Shape
import dev.mattramotar.storex.store.normalization.backend.NormalizationBackend
import dev.mattramotar.storex.store.normalization.backend.RootRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart

/**
 * Resolves a StoreKey to a normalized root EntityKey.
 */
fun interface RootResolver<K : StoreKey> {
    fun resolve(key: K): EntityKey
}

/**
 * Source-of-Truth for a *single entity* view backed by normalized storage.
 * ReadDb = GraphProjection<V>   (domain value + graph meta)
 * WriteDb = NormalizedWrite<K>  (change-set + optional index update)
 */
class NormalizedEntitySot<K : StoreKey, V: Any>(
    private val backend: NormalizationBackend,
    private val registry: SchemaRegistry,
    private val shape: Shape<V>,
    private val resolver: RootResolver<K>
) : SourceOfTruth<K, GraphProjection<V>, NormalizedWrite<K>> {

    override fun reader(key: K): Flow<GraphProjection<V>?> {
        val rootRef = RootRef(key, shape.id)
        return backend.rootInvalidations
            .filter { roots -> roots.isEmpty() || roots.contains(rootRef) }
            .onStart { emit(emptySet()) } // initial compose
            .conflate()  // Drop intermediate invalidations to prevent overwhelming downstream
            .flatMapLatest {
                flow {
                    val root = resolver.resolve(key)
                    val result = composeFromRoot(root, shape, registry, backend)
                    // Update durable dependency mapping for precise invalidations
                    backend.updateRootDependencies(rootRef, result.dependencies)
                    emit(GraphProjection(value = result.value, meta = result.meta))
                }
            }
            .buffer(capacity = 1)  // Additional buffering for bursty updates
    }

    override suspend fun write(key: K, value: NormalizedWrite<K>) {
        backend.apply(value.changeSet)
        value.indexUpdate?.let { upd ->
            // Typically for list roots; entity SOT writes rarely update indexes, but allowed.
            backend.updateRootDependencies(RootRef(upd.requestKey, shape.id), emptySet())
        }
    }

    override suspend fun delete(key: K) {
        // Deleting the *root projection* isn't a single write; callers should send a change-set with deletes.
        // We no-op to avoid accidental destructive behavior.
    }

    override suspend fun withTransaction(block: suspend () -> Unit) = block()

    override suspend fun rekey(
        old: K,
        new: K,
        reconcile: suspend (GraphProjection<V>, GraphProjection<V>?) -> GraphProjection<V>
    ) {
        // Key-level rekeying is handled by normalized change-sets (entity-level rekeys).
        // Store-level K->K rekey is not applicable for normalized entity SOT.
        // No-op.
    }
}
