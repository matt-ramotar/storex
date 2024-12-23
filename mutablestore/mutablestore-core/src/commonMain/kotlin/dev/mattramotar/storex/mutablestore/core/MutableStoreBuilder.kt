package dev.mattramotar.storex.mutablestore.core


import dev.mattramotar.storex.mutablestore.core.api.ConflictResolutionReadStrategy
import dev.mattramotar.storex.mutablestore.core.api.MutableStore
import dev.mattramotar.storex.mutablestore.core.api.MutationOperations
import dev.mattramotar.storex.mutablestore.core.api.MutationOperationsBuilder
import dev.mattramotar.storex.mutablestore.core.api.MutationStrategy
import dev.mattramotar.storex.mutablestore.telemetry.MutableStoreTelemetry
import dev.mattramotar.storex.store.core.StoreBuilder
import dev.mattramotar.storex.store.core.api.MemoryPolicy
import dev.mattramotar.storex.store.core.api.SourceOfTruth
import dev.mattramotar.storex.store.extensions.policies.read.ReadPolicy
import dev.mattramotar.storex.store.extensions.withReadPolicies
import kotlinx.coroutines.CoroutineScope

/**
 * A builder for creating [MutableStore] instances:
 * - Optional fetcher, memory policy, source of truth, and read policies.
 * - Conflict resolution logic via [ConflictResolutionReadStrategy].
 * - Pipeline-based mutation strategies ([MutationStrategy]) for complex flows.
 * - Custom error type via [errorAdapter].
 * - Optional telemetry via [MutableStoreTelemetry].
 */
class MutableStoreBuilder<Key : Any, Partial : Any, Value : Any, Error : Any> @PublishedApi internal constructor() {

    private var fetcher: (suspend (Key) -> Value?)? = null
    private var sourceOfTruth: SourceOfTruth<Key, Value>? = null
    private var memoryPolicy: MemoryPolicy<Key, Value>? = null
    private var readPolicies: List<ReadPolicy<Key, Value>> = emptyList()

    private var conflictResolutionReadStrategy: ConflictResolutionReadStrategy<Key>? = null
    private var strategies: List<MutationStrategy<Key, Partial, Value, Error>> = emptyList()

    private var mutationOperations: MutationOperations<Key, Partial, Value, Error> =
        MutationOperationsBuilder<Key, Partial, Value, Error>().build()

    private var errorAdapter: ((Throwable) -> Error)? = null
    private var telemetry: MutableStoreTelemetry<Key, Partial, Value, Error>? = null

    private var scope: CoroutineScope? = null

    /**
     * Sets the network fetcher logic, returning a [Value] or `null`.
     */
    fun fetcher(fetcher: suspend (Key) -> Value?) = apply {
        this.fetcher = fetcher
    }

    /**
     * Configures a source of truth (SOT) to store data locally, e.g., database or disk cache.
     */
    fun sourceOfTruth(soT: SourceOfTruth<Key, Value>) = apply {
        this.sourceOfTruth = soT
    }

    /**
     * Configures an in-memory cache policy, such as max size or TTL.
     */
    fun memoryPolicy(memoryPolicy: MemoryPolicy<Key, Value>) = apply {
        this.memoryPolicy = memoryPolicy
    }

    /**
     * Adds one or more read policies that can intercept store reads.
     */
    fun addReadPolicies(vararg policies: ReadPolicy<Key, Value>) = apply {
        this.readPolicies += policies
    }

    /**
     * Defines the [CoroutineScope] under which the store operations will run.
     */
    fun scope(scope: CoroutineScope) = apply {
        this.scope = scope
    }

    /**
     * Injects a [ConflictResolutionReadStrategy] to handle un-synced local data before a network read.
     */
    fun conflictResolution(strategy: ConflictResolutionReadStrategy<Key>) = apply {
        this.conflictResolutionReadStrategy = strategy
    }

    /**
     * Appends one or more [MutationStrategy] implementations for advanced mutation handling.
     */
    fun addMutationStrategies(vararg strategies: MutationStrategy<Key, Partial, Value, Error>) = apply {
        this.strategies += strategies
    }

    /**
     * Overwrites the default [MutationOperations], which define how to create/update/delete data remotely.
     */
    fun mutationOperations(builder: MutationOperationsBuilder<Key, Partial, Value, Error>.() -> Unit) = apply {
        this.mutationOperations =
            MutationOperationsBuilder<Key, Partial, Value, Error>()
                .apply(builder)
                .build()
    }

    /**
     * Maps a thrown [Throwable] to a custom [Error] type used by this store.
     */
    fun errorAdapter(adapter: (Throwable) -> Error) = apply {
        this.errorAdapter = adapter
    }

    /**
     * Registers an optional telemetry callback for reporting store and mutation events.
     */
    fun telemetry(telemetry: MutableStoreTelemetry<Key, Partial, Value, Error>) = apply {
        this.telemetry = telemetry
    }

    /**
     * Builds the final [MutableStore] instance. Throws an [IllegalStateException] if required settings are missing.
     */
    fun build(): MutableStore<Key, Partial, Value, Error> {
        checkNotNull(fetcher) { "You must set a fetcher(...) before building." }
        checkNotNull(scope) { "You must call scope(...) before building." }

        // Default errorAdapter fallback (unsafe cast if user doesn't specify).
        if (errorAdapter == null) {
            @Suppress("UNCHECKED_CAST")
            errorAdapter = { it as Error }
        }

        // Create the base read-only store
        val readOnlyStore = StoreBuilder.from(fetcher!!)
            .scope(scope!!)
            .apply {
                memoryPolicy?.let { memoryPolicy(it) }
                sourceOfTruth?.let { sourceOfTruth(it) }
            }
            .build()
            .apply {
                // Attach read policies
                withReadPolicies(*readPolicies.toTypedArray())
            }

        // Convert the read-only store to a mutable store with the configured strategies
        return readOnlyStore.asMutableStore(
            conflictResolutionReadStrategy = conflictResolutionReadStrategy,
            strategies = strategies,
            mutationOperations = mutationOperations,
            errorAdapter = errorAdapter!!,
            telemetry = telemetry,
            coroutineScope = scope!!
        )
    }
}

