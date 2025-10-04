package dev.mattramotar.storex.store.normalization.internal

import dev.mattramotar.storex.normalization.schema.SchemaRegistry
import dev.mattramotar.storex.store.Converter
import dev.mattramotar.storex.store.StoreKey
import dev.mattramotar.storex.store.internal.Bookkeeper
import dev.mattramotar.storex.store.internal.Fetcher
import dev.mattramotar.storex.store.internal.FreshnessValidator
import dev.mattramotar.storex.store.internal.MemoryCache
import dev.mattramotar.storex.store.internal.RealStore
import dev.mattramotar.storex.store.internal.SourceOfTruth
import dev.mattramotar.storex.store.internal.Updater
import dev.mattramotar.storex.store.mutation.Creator
import dev.mattramotar.storex.store.mutation.MutationStore
import dev.mattramotar.storex.store.normalization.GraphProjection
import dev.mattramotar.storex.store.normalization.IndexManager
import dev.mattramotar.storex.store.normalization.NormalizedWrite
import dev.mattramotar.storex.store.normalization.Shape
import dev.mattramotar.storex.store.normalization.backend.NormalizationBackend


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
