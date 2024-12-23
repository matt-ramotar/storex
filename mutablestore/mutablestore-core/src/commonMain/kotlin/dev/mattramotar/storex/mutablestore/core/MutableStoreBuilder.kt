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
 * A single builder that can build a MutableStore with any combination of:
 * - Key type
 * - Partial mutation type
 * - Value type
 * - Custom error type
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

    fun fetcher(fetcher: suspend (Key) -> Value?) = apply {
        this.fetcher = fetcher
    }

    fun sourceOfTruth(soT: SourceOfTruth<Key, Value>) = apply {
        this.sourceOfTruth = soT
    }

    fun memoryPolicy(memoryPolicy: MemoryPolicy<Key, Value>) = apply {
        this.memoryPolicy = memoryPolicy
    }

    fun addReadPolicies(vararg policies: ReadPolicy<Key, Value>) = apply {
        this.readPolicies = this.readPolicies + policies
    }

    fun scope(scope: CoroutineScope) = apply {
        this.scope = scope
    }

    fun conflictResolution(strategy: ConflictResolutionReadStrategy<Key>) = apply {
        this.conflictResolutionReadStrategy = strategy
    }

    fun addMutationStrategies(vararg strategies: MutationStrategy<Key, Partial, Value, Error>) = apply {
        this.strategies = this.strategies + strategies
    }

    fun mutationOperations(builder: MutationOperationsBuilder<Key, Partial, Value, Error>.() -> Unit) = apply {
        this.mutationOperations =
            MutationOperationsBuilder<Key, Partial, Value, Error>()
                .apply(builder)
                .build()
    }

    fun errorAdapter(adapter: (Throwable) -> Error) = apply {
        this.errorAdapter = adapter
    }

    fun telemetry(telemetry: MutableStoreTelemetry<Key, Partial, Value, Error>) = apply {
        this.telemetry = telemetry
    }

    fun build(): MutableStore<Key, Partial, Value, Error> {
        checkNotNull(fetcher) { "You must set a fetcher(...) before building." }
        checkNotNull(scope) { "You must call scope(...) before building." }

        if (errorAdapter == null) {
            @Suppress("UNCHECKED_CAST")
            errorAdapter = { it as Error }
        }

        val readOnlyStore = StoreBuilder.from(fetcher!!)
            .scope(scope!!)
            .apply {
                memoryPolicy?.let { memoryPolicy(it) }
                sourceOfTruth?.let { sourceOfTruth(it) }
            }
            .build()
            .apply {
                withReadPolicies(*readPolicies.toTypedArray())
            }

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

