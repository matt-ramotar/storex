package dev.mattramotar.storex.normalization.dsl

// TODO: This file will be completed in Phase 3 (Mutations Module Migration)
// It requires RealStore and Updater which will be migrated from :store to :mutations

/*
import dev.mattramotar.storex.normalization.schema.SchemaRegistry
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.internal.Fetcher
import dev.mattramotar.storex.mutations.MutationStore
import dev.mattramotar.storex.normalization.NormalizedWrite
import dev.mattramotar.storex.normalization.Shape
import dev.mattramotar.storex.normalization.backend.NormalizationBackend
import dev.mattramotar.storex.normalization.internal.NormalizationConverter
import dev.mattramotar.storex.normalization.internal.RootResolver
import dev.mattramotar.storex.normalization.internal.buildNormalizedEntityStore

/**
 * DSL scope for building a normalized entity store.
 *
 * Normalized stores use a graph-based persistence model where entities are stored
 * in normalized form and assembled on read.
 *
 * @param K The store key type
 * @param V The domain value type
 * @param Network The network response type
 */
interface NormalizedStoreBuilderScope<K : StoreKey, V : Any, Network : Any> {
    var backend: NormalizationBackend?
    var schema: SchemaRegistry?
    var shape: Shape<V>?
    var rootResolver: RootResolver<K>?
    var fetcher: Fetcher<K, Network>?
    var normalizer: (suspend (K, Network) -> NormalizedWrite<K>)?
    fun normalizer(normalize: suspend (K, Network) -> NormalizedWrite<K>)
}

private class DefaultNormalizedStoreBuilderScope<K : StoreKey, V : Any, Network : Any> :
    NormalizedStoreBuilderScope<K, V, Network> {
    override var backend: NormalizationBackend? = null
    override var schema: SchemaRegistry? = null
    override var shape: Shape<V>? = null
    override var rootResolver: RootResolver<K>? = null
    override var fetcher: Fetcher<K, Network>? = null
    override var normalizer: (suspend (K, Network) -> NormalizedWrite<K>)? = null

    override fun normalizer(normalize: suspend (K, Network) -> NormalizedWrite<K>) {
        normalizer = normalize
    }

    fun build(): MutationStore<K, V, Nothing?, Nothing?> {
        val actualBackend = requireNotNull(backend) { "backend is required for normalized stores" }
        val actualSchema = requireNotNull(schema) { "schema is required for normalized stores" }
        val actualShape = requireNotNull(shape) { "shape is required for normalized stores" }
        val actualRootResolver = requireNotNull(rootResolver) { "rootResolver is required for normalized stores" }
        val actualFetcher = requireNotNull(fetcher) { "fetcher is required for normalized stores" }
        val actualNormalizer = requireNotNull(normalizer) { "normalizer is required for normalized stores" }

        val converter = NormalizationConverter<K, V, Network>(actualNormalizer)

        return buildNormalizedEntityStore(
            backend = actualBackend,
            registry = actualSchema,
            shape = actualShape,
            rootResolver = actualRootResolver,
            fetcher = actualFetcher,
            converter = converter,
            updater = null,
            creator = null,
            bookkeeper = TODO("Provide bookkeeper"),
            validator = TODO("Provide validator"),
            memory = TODO("Provide memory cache")
        )
    }
}

fun <K : StoreKey, V : Any, Network : Any> normalizedStore(
    block: NormalizedStoreBuilderScope<K, V, Network>.() -> Unit
): MutationStore<K, V, Nothing?, Nothing?> {
    val builder = DefaultNormalizedStoreBuilderScope<K, V, Network>()
    builder.block()
    return builder.build()
}


data class Item(
    val name: String
)

data class NetworkItem(
    val name: String
)
*/
