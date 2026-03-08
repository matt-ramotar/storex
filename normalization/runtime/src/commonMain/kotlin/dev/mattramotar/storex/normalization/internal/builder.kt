package dev.mattramotar.storex.normalization.internal

// TODO: This file will be completed in Phase 4 once the mutation/runtime seams are fully
// available for normalized builders without depending on legacy concrete runtime types.

/*
import dev.mattramotar.storex.normalization.schema.SchemaRegistry
import dev.mattramotar.storex.core.Converter
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.seams.Bookkeeper
import dev.mattramotar.storex.core.seams.Fetcher
import dev.mattramotar.storex.core.seams.FreshnessValidator
import dev.mattramotar.storex.core.seams.MemoryCache
import dev.mattramotar.storex.core.seams.SourceOfTruth
import dev.mattramotar.storex.mutations.Creator
import dev.mattramotar.storex.mutations.MutationStore
import dev.mattramotar.storex.normalization.GraphProjection
import dev.mattramotar.storex.normalization.IndexManager
import dev.mattramotar.storex.normalization.NormalizedWrite
import dev.mattramotar.storex.normalization.Shape
import dev.mattramotar.storex.normalization.backend.NormalizationBackend


/**
 * Build a normalized *entity* store K -> V
 */
fun <K : StoreKey, V: Any, Network: Any, Patch, Draft> buildNormalizedEntityStore(
    backend: NormalizationBackend,
    registry: SchemaRegistry,
    shape: Shape<V>,
    rootResolver: RootResolver<K>,
    fetcher: Fetcher<K, Network>,
    converter: NormalizationConverter<K, V, Network>,
    updater: Any? = null,
    creator: Creator<K, Draft, Network>? = null,
    bookkeeper: Bookkeeper<K>,
    validator: FreshnessValidator<K, Any?>,
    memory: MemoryCache<K, V>
): MutationStore<K, V, Patch, Draft> {
    val sot: SourceOfTruth<K, GraphProjection<V>, NormalizedWrite<K>> =
        NormalizedEntitySot(backend, registry, shape, rootResolver)

    @Suppress("UNCHECKED_CAST")
    return error("Phase 4 normalized mutation builder not yet implemented after seam extraction")
}

/**
 * Build a normalized *list* store K -> List<V>
 * Note: to keep IndexManager updated, set NormalizedWrite.indexUpdate in your converter's toWrite lambda.
 */
fun <K : StoreKey, V: Any, Network: Any, Patch, Draft> buildNormalizedListStore(
    backend: NormalizationBackend,
    index: IndexManager,
    registry: SchemaRegistry,
    itemShape: Shape<V>,
    fetcher: Fetcher<K, Network>,
    converter: NormalizationConverter<K, List<V>, Network>,
    updater: Any? = null,
    creator: Creator<K, Draft, Network>? = null,
    bookkeeper: Bookkeeper<K>,
    validator: FreshnessValidator<K, Any?>,
    memory: MemoryCache<K, List<V>>
): MutationStore<K, List<V>, Patch, Draft> {
    val sot: SourceOfTruth<K, GraphProjection<List<V>>, NormalizedWrite<K>> =
        NormalizedListSot(backend, index, registry, itemShape)

    @Suppress("UNCHECKED_CAST")
    return error("Phase 4 normalized list builder not yet implemented after seam extraction")
}
*/
