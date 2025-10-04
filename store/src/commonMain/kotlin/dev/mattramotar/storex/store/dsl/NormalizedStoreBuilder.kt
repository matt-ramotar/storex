package dev.mattramotar.storex.store.dsl

import dev.mattramotar.storex.normalization.schema.SchemaRegistry
import dev.mattramotar.storex.store.StoreKey
import dev.mattramotar.storex.store.internal.Fetcher
import dev.mattramotar.storex.store.mutation.MutationStore
import dev.mattramotar.storex.store.normalization.NormalizedWrite
import dev.mattramotar.storex.store.normalization.Shape
import dev.mattramotar.storex.store.normalization.backend.NormalizationBackend
import dev.mattramotar.storex.store.normalization.internal.NormalizationConverter
import dev.mattramotar.storex.store.normalization.internal.RootResolver
import dev.mattramotar.storex.store.normalization.internal.buildNormalizedEntityStore

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
    /**
     * The normalization backend (e.g., SQLDelight, Room, etc.)
     */
    var backend: NormalizationBackend?

    /**
     * The schema registry containing entity definitions.
     */
    var schema: SchemaRegistry?

    /**
     * The shape definition for type V, describing how to traverse and normalize it.
     */
    var shape: Shape<V>?

    /**
     * The root resolver for finding the root entity of a query.
     */
    var rootResolver: RootResolver<K>?

    /**
     * The fetcher for retrieving data from the network.
     */
    var fetcher: Fetcher<K, Network>?

    /**
     * Function to convert network responses to normalized writes.
     */
    var normalizer: (suspend (K, Network) -> NormalizedWrite<K>)?

    /**
     * Configure how to normalize network responses into the graph.
     *
     * Example:
     * ```kotlin
     * normalizer { key, networkUser ->
     *     normalize(networkUser) {
     *         // Normalization logic
     *     }
     * }
     * ```
     */
    fun normalizer(normalize: suspend (K, Network) -> NormalizedWrite<K>)
}

/**
 * Implementation of normalized store builder scope.
 */
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

        // Use the existing normalized store builder
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

/**
 * Creates a normalized entity store using a type-safe DSL.
 *
 * Normalized stores are useful when you need to maintain relational integrity
 * across entities and handle partial updates efficiently.
 *
 * Example:
 * ```kotlin
 * val userStore = normalizedStore<UserKey, User, NetworkUser> {
 *     backend = sqlDelightBackend
 *     schema = schemaRegistry
 *     shape = UserShape
 *     rootResolver = UserRootResolver()
 *
 *     fetcher { key -> api.getUser(key.id) }
 *
 *     normalizer { key, networkUser ->
 *         NormalizedWrite(
 *             entities = normalizeUser(networkUser),
 *             indexUpdate = null
 *         )
 *     }
 * }
 * ```
 *
 * @param K The store key type
 * @param V The domain value type
 * @param Network The network response type
 * @param block Configuration block
 * @return A configured normalized MutationStore instance
 */
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


