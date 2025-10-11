package dev.mattramotar.storex.normalization.internal

// TODO: This file will be completed in Phase 3 (Mutations Module Migration)
// It requires RealStore and Updater which will be migrated from :store to :mutations

/*
import dev.mattramotar.storex.normalization.schema.SchemaRegistry
import dev.mattramotar.storex.core.Converter
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.internal.Bookkeeper
import dev.mattramotar.storex.core.internal.Fetcher
import dev.mattramotar.storex.core.internal.FreshnessValidator
import dev.mattramotar.storex.core.internal.MemoryCache
import dev.mattramotar.storex.core.internal.RealStore
import dev.mattramotar.storex.core.internal.SourceOfTruth
import dev.mattramotar.storex.core.internal.Updater
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
    updater: Updater<K, Patch, *>? = null,
    creator: Creator<K, Draft, Network>? = null,
    bookkeeper: Bookkeeper<K>,
    validator: FreshnessValidator<K, Any?>,
    memory: MemoryCache<K, V>
): MutationStore<K, V, Patch, Draft> {
    val sot: SourceOfTruth<K, GraphProjection<V>, NormalizedWrite<K>> =
        NormalizedEntitySot(backend, registry, shape, rootResolver)

    @Suppress("UNCHECKED_CAST")
    return RealStore<K, V, GraphProjection<V>, NormalizedWrite<K>, Network, Patch, Draft, Any?, Any?, Any?>(
        sot = sot,
        fetcher = fetcher,
        updater = updater as Updater<K, Patch, Any?>?,
        creator = creator,
        deleter = null,
        putser = null,
        converter = converter as Converter<K, V, GraphProjection<V>, Network, NormalizedWrite<K>>,
        encoder = /* your MutationEncoder<Patch, Draft, V, ...> */ error("Provide encoder"),
        bookkeeper = bookkeeper,
        validator = validator,
        memory = memory
    )
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
    updater: Updater<K, Patch, *>? = null,
    creator: Creator<K, Draft, Network>? = null,
    bookkeeper: Bookkeeper<K>,
    validator: FreshnessValidator<K, Any?>,
    memory: MemoryCache<K, List<V>>
): MutationStore<K, List<V>, Patch, Draft> {
    val sot: SourceOfTruth<K, GraphProjection<List<V>>, NormalizedWrite<K>> =
        NormalizedListSot(backend, index, registry, itemShape)

    @Suppress("UNCHECKED_CAST")
    return RealStore<K, List<V>, GraphProjection<List<V>>, NormalizedWrite<K>, Network, Patch, Draft, Any?, Any?, Any?>(
        sot = sot,
        fetcher = fetcher,
        updater = updater as Updater<K, Patch, Any?>?,
        creator = creator,
        deleter = null,
        putser = null,
        converter = converter as Converter<K, List<V>, GraphProjection<List<V>>, Network, NormalizedWrite<K>>,
        encoder = /* your MutationEncoder */ error("Provide encoder"),
        bookkeeper = bookkeeper,
        validator = validator,
        memory = memory
    )
}
*/
