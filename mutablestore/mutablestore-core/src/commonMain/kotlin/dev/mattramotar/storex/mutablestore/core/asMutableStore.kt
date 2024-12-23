package dev.mattramotar.storex.mutablestore.core

import dev.mattramotar.storex.mutablestore.core.api.ConflictResolutionReadStrategy
import dev.mattramotar.storex.mutablestore.core.api.MutableStore
import dev.mattramotar.storex.mutablestore.core.api.MutationOperations
import dev.mattramotar.storex.mutablestore.core.api.MutationOperationsBuilder
import dev.mattramotar.storex.mutablestore.core.api.MutationStrategy
import dev.mattramotar.storex.mutablestore.core.impl.RealMutableStore
import dev.mattramotar.storex.mutablestore.telemetry.MutableStoreTelemetry
import dev.mattramotar.storex.store.core.api.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.jvm.JvmName


@JvmName("asMutableStoreAllParamsWithMutationOperations")
fun <Key : Any, Partial : Any, Value : Any, Error : Any> Store<Key, Value>.asMutableStore(
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    errorAdapter: (Throwable) -> Error,
    mutationOperations: MutationOperations<Key, Partial, Value, Error>,
    conflictResolutionReadStrategy: ConflictResolutionReadStrategy<Key>? = null,
    strategies: List<MutationStrategy<Key, Partial, Value, Error>> = emptyList(),
    telemetry: MutableStoreTelemetry<Key, Partial, Value, Error>? = null,
): MutableStore<Key, Partial, Value, Error> {
    return RealMutableStore(
        delegateStore = this,
        mutationOperations = mutationOperations,
        strategies = strategies,
        errorAdapter = errorAdapter,
        conflictResolutionReadStrategy = conflictResolutionReadStrategy,
        telemetry = telemetry,
        coroutineScope = coroutineScope
    )
}


@JvmName("asMutableStoreAllParamsWithBuilder")
fun <Key : Any, Partial : Any, Value : Any, Error : Any> Store<Key, Value>.asMutableStore(
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    errorAdapter: (Throwable) -> Error,
    strategies: List<MutationStrategy<Key, Partial, Value, Error>> = emptyList(),
    telemetry: MutableStoreTelemetry<Key, Partial, Value, Error>? = null,
    conflictResolutionReadStrategy: ConflictResolutionReadStrategy<Key>? = null,
    mutationOperationsBuilder: MutationOperationsBuilder<Key, Partial, Value, Error>.() -> Unit,
): MutableStore<Key, Partial, Value, Error> = RealMutableStore(
    this,
    coroutineScope = coroutineScope,
    mutationOperations = MutationOperationsBuilder<Key, Partial, Value, Error>()
        .apply(mutationOperationsBuilder)
        .build(),
    errorAdapter = errorAdapter,
    strategies = strategies,
    telemetry = telemetry,
    conflictResolutionReadStrategy = conflictResolutionReadStrategy
)


@JvmName("asMutableStoreNoPartialWithMutationOperations")
fun <Key : Any, Value : Any> Store<Key, Value>.asMutableStore(
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    mutationOperations: MutationOperations<Key, Value, Value, Throwable> =
        MutationOperationsBuilder<Key, Value, Value, Throwable>().build(),
    strategies: List<MutationStrategy<Key, Value, Value, Throwable>> = emptyList(),
    telemetry: MutableStoreTelemetry<Key, Value, Value, Throwable>? = null,
    conflictResolutionReadStrategy: ConflictResolutionReadStrategy<Key>? = null
): MutableStore<Key, Value, Value, Throwable> = RealMutableStore(
    this,
    coroutineScope = coroutineScope,
    mutationOperations = mutationOperations,
    errorAdapter = { it },
    strategies = strategies,
    telemetry = telemetry,
    conflictResolutionReadStrategy = conflictResolutionReadStrategy
)


@JvmName("asMutableStoreNoPartialWithBuilder")
fun <Key : Any, Value : Any> Store<Key, Value>.asMutableStore(
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    strategies: List<MutationStrategy<Key, Value, Value, Throwable>> = emptyList(),
    telemetry: MutableStoreTelemetry<Key, Value, Value, Throwable>? = null,
    conflictResolutionReadStrategy: ConflictResolutionReadStrategy<Key>? = null,
    mutationOperationsBuilder: MutationOperationsBuilder<Key, Value, Value, Throwable>.() -> Unit,
): MutableStore<Key, Value, Value, Throwable> = RealMutableStore(
    this,
    coroutineScope = coroutineScope,
    mutationOperations = MutationOperationsBuilder<Key, Value, Value, Throwable>()
        .apply(mutationOperationsBuilder)
        .build(),
    errorAdapter = { it },
    strategies = strategies,
    telemetry = telemetry,
    conflictResolutionReadStrategy = conflictResolutionReadStrategy
)


@JvmName("asMutableStoreCustomErrorWithMutationOperations")
fun <Key : Any, Value : Any, Error : Any> Store<Key, Value>.asMutableStore(
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    errorAdapter: (Throwable) -> Error,
    mutationOperations: MutationOperations<Key, Value, Value, Error> =
        MutationOperationsBuilder<Key, Value, Value, Error>().build(),
    strategies: List<MutationStrategy<Key, Value, Value, Error>> = emptyList(),
    telemetry: MutableStoreTelemetry<Key, Value, Value, Error>? = null,
    conflictResolutionReadStrategy: ConflictResolutionReadStrategy<Key>? = null
): MutableStore<Key, Value, Value, Error> = RealMutableStore(
    this,
    errorAdapter = errorAdapter,
    coroutineScope = coroutineScope,
    mutationOperations = mutationOperations,
    strategies = strategies,
    telemetry = telemetry,
    conflictResolutionReadStrategy = conflictResolutionReadStrategy
)


@JvmName("asMutableStoreCustomErrorWithBuilder")
fun <Key : Any, Value : Any, Error : Any> Store<Key, Value>.asMutableStore(
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    errorAdapter: (Throwable) -> Error,
    strategies: List<MutationStrategy<Key, Value, Value, Error>> = emptyList(),
    telemetry: MutableStoreTelemetry<Key, Value, Value, Error>? = null,
    conflictResolutionReadStrategy: ConflictResolutionReadStrategy<Key>? = null,
    mutationOperationsBuilder: MutationOperationsBuilder<Key, Value, Value, Error>.() -> Unit,
): MutableStore<Key, Value, Value, Error> = RealMutableStore(
    this,
    errorAdapter = errorAdapter,
    coroutineScope = coroutineScope,
    mutationOperations = MutationOperationsBuilder<Key, Value, Value, Error>()
        .apply(mutationOperationsBuilder)
        .build(),
    strategies = strategies,
    telemetry = telemetry,
    conflictResolutionReadStrategy = conflictResolutionReadStrategy
)


@JvmName("asMutableStoreCustomPartialWithMutationOperations")
fun <Key : Any, Partial : Any, Value : Any> Store<Key, Value>.asMutableStore(
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    strategies: List<MutationStrategy<Key, Partial, Value, Throwable>> = emptyList(),
    telemetry: MutableStoreTelemetry<Key, Partial, Value, Throwable>? = null,
    conflictResolutionReadStrategy: ConflictResolutionReadStrategy<Key>? = null,
    mutationOperations: MutationOperations<Key, Partial, Value, Throwable> =
        MutationOperationsBuilder<Key, Partial, Value, Throwable>().build(),
): MutableStore<Key, Partial, Value, Throwable> = RealMutableStore(
    this,
    coroutineScope = coroutineScope,
    mutationOperations = mutationOperations,
    errorAdapter = { it },
    strategies = strategies,
    telemetry = telemetry,
    conflictResolutionReadStrategy = conflictResolutionReadStrategy
)


@JvmName("asMutableStoreCustomPartialWithBuilder")
fun <Key : Any, Partial : Any, Value : Any> Store<Key, Value>.asMutableStore(
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    strategies: List<MutationStrategy<Key, Partial, Value, Throwable>> = emptyList(),
    telemetry: MutableStoreTelemetry<Key, Partial, Value, Throwable>? = null,
    conflictResolutionReadStrategy: ConflictResolutionReadStrategy<Key>? = null,
    mutationOperationsBuilder: MutationOperationsBuilder<Key, Partial, Value, Throwable>.() -> Unit,
): MutableStore<Key, Partial, Value, Throwable> = RealMutableStore(
    this,
    errorAdapter = { it },
    coroutineScope = coroutineScope,
    mutationOperations = MutationOperationsBuilder<Key, Partial, Value, Throwable>()
        .apply(mutationOperationsBuilder)
        .build(),
    strategies = strategies,
    telemetry = telemetry,
    conflictResolutionReadStrategy = conflictResolutionReadStrategy
)

