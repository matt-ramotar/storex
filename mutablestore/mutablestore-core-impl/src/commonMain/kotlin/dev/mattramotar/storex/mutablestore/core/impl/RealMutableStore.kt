@file:Suppress("UNCHECKED_CAST")

package dev.mattramotar.storex.mutablestore.core.impl

import dev.mattramotar.storex.mutablestore.core.api.ConflictResolutionReadStrategy
import dev.mattramotar.storex.mutablestore.core.api.MutableStore
import dev.mattramotar.storex.mutablestore.core.api.Mutation
import dev.mattramotar.storex.mutablestore.core.api.MutationOperations
import dev.mattramotar.storex.mutablestore.core.api.MutationStrategy
import dev.mattramotar.storex.mutablestore.telemetry.MutableStoreTelemetry
import dev.mattramotar.storex.mutablestore.telemetry.MutableStoreTelemetryEvent
import dev.mattramotar.storex.mutablestore.telemetry.StoreTelemetryEvent
import dev.mattramotar.storex.result.Result
import dev.mattramotar.storex.store.core.api.Store
import dev.mattramotar.storex.store.internal.hooks.MutableStoreFlowTelemetryHooks
import dev.mattramotar.storex.store.internal.hooks.StoreDataHooks
import dev.mattramotar.storex.store.internal.hooks.StoreFlowTelemetryHooks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/**
 * A concrete implementation of the [MutableStore] interface,
 * backed by a delegate [Store] (for all read paths) plus a pipeline of
 * [MutationStrategy]s (for writes).
 *
 * Read Paths:
 * - [stream] and [get] delegate to [delegateStore].
 * - Optionally run conflict resolution if configured via [conflictResolutionReadStrategy].
 *
 * Write Paths:
 * - [create], [update], [delete] each create a [Mutation], run the pipeline,
 *   and if successful, write to SOT + memory via [storeDataHooks].
 *
 * Telemetry:
 * - [MutableStoreTelemetryEvent] are emitted via a shared flow and an optional
 *   [MutableStoreTelemetry] instance.
 *
 * Notes:
 * - We encourage using decorators/middlewares for advanced read logic
 *   (e.g., fallback to local if network fails), so this class stays minimal.
 * - [conflictResolutionReadStrategy] can be used to push local changes before
 *   reading from the network.
 */
class RealMutableStore<Key : Any, Partial : Any, Value : Any, Error : Any>(
    private val delegateStore: Store<Key, Value>,
    private val mutationOperations: MutationOperations<Key, Partial, Value, Error>,
    private val strategies: List<MutationStrategy<Key, Partial, Value, Error>>,
    private val errorAdapter: (Throwable) -> Error,
    private val conflictResolutionReadStrategy: ConflictResolutionReadStrategy<Key>?,
    private val telemetry: MutableStoreTelemetry<Key, Partial, Value, Error>? = null,
    private val coroutineScope: CoroutineScope,
) : MutableStore<Key, Partial, Value, Error>,
    MutableStoreFlowTelemetryHooks<Key, Partial, Value, Error>,
    Store<Key, Value> by delegateStore {


    // Attempt to cast the delegate Store to a StoreDataHooks for writing to SOT+memory.
    private val storeDAO = delegateStore.storeDataHooks()

    // Telemetry flows from the delegate store (StoreFlowTelemetryHooks).
    private val _storeFlowTelemetryEvents = delegateStore.storeFlowTelemetryEvents().storeFlowTelemetryEvents

    // Internal mutable flow for this mutable store’s telemetry events.
    private val _mutableStoreFlowTelemetryEvents = MutableSharedFlow<MutableStoreTelemetryEvent<Key, Partial, Value, Error>>()

    // Combine the store-telemetry events + mutable-store-telemetry events into one shared flow.
    private val _combinedFlowTelemetryEvents: SharedFlow<MutableStoreTelemetryEvent<Key, Partial, Value, Error>> =
        merge(_storeFlowTelemetryEvents, _mutableStoreFlowTelemetryEvents)
            .shareIn(coroutineScope, started = SharingStarted.Eagerly)

    /**
     * Exposes read-only telemetry events for this [MutableStore].
     * Includes both basic [StoreTelemetryEvent] (e.g., fetch, memory hits)
     * and [MutableStoreTelemetryEvent] (e.g., create, update, delete, conflict resolution).
     */
    override val mutableStoreTelemetryEvents: SharedFlow<MutableStoreTelemetryEvent<Key, Partial, Value, Error>>
        get() = _combinedFlowTelemetryEvents


    /**
     * Streams data from [delegateStore], optionally runs conflict resolution
     * to push local changes before forcing a network fetch.
     */
    override fun stream(key: Key): Flow<Value> = flow {
        // 1) Emit local data + SOT from the delegate store
        emitAll(delegateStore.stream(key))

        // 2) Run conflict resolution if configured

        val canProceed = conflictResolutionReadStrategy?.let { strategy ->
            // Fire a "started" event
            coroutineScope.launch {
                onEvent(MutableStoreTelemetryEvent.ConflictResolutionStarted(key))
            }

            val succeeded = strategy.handleUnresolvedSyncBeforeReading(key)

            coroutineScope.launch {
                if (succeeded) {
                    onEvent(MutableStoreTelemetryEvent.ConflictResolutionSucceeded(key))
                } else {
                    // In a real system, we might also include an Error if we have details.
                    onEvent(MutableStoreTelemetryEvent.ConflictResolutionFailed(key))
                }
            }

            return@let { succeeded }
        } ?: {
            // If no strategy, skip conflict resolution
            coroutineScope.launch {
                onEvent(
                    MutableStoreTelemetryEvent.ConflictResolutionSkipped(
                        key,
                        reason = "No ConflictResolutionReadStrategy."
                    )
                )
            }

            true
        }

        // 3) If conflict resolution failed, we can bail out
        if (!canProceed()) return@flow

        // 4) Invalidate so the delegate store does a fresh network fetch
        delegateStore.clear(key)

        // 5) Emit updated data from the delegate store
        emitAll(delegateStore.stream(key))
    }

    /**
     * Returns a single value from [delegateStore]. If none found, optionally
     * run conflict resolution and then fetch fresh data from network.
     */
    override suspend fun get(key: Key): Value? {
        // 1) Attempt local read from the delegate store
        val localData = delegateStore.get(key)
        if (localData != null) return localData

        // 2) If we still need fresh data, run conflict resolution if present
        val canProceed = conflictResolutionReadStrategy?.handleUnresolvedSyncBeforeReading(key) ?: true
        if (!canProceed) {
            // If sync fails, return null or throw
            return null
        }

        // 3) Force a fresh fetch by invalidating the delegate store
        delegateStore.clear(key)

        // 4) Return the newly fetched data
        return delegateStore.get(key)
    }


    override suspend fun create(key: Key, partial: Partial): Result<Value, Error> {
        onEvent(MutableStoreTelemetryEvent.CreateStarted(key, partial))
        val mutation = Mutation.Create(key, partial)
        return (executeMutation(key, mutation) as Result<Value, Error>).also {
            onEvent(MutableStoreTelemetryEvent.CreateCompleted(key, partial, it))
        }
    }

    override suspend fun update(key: Key, value: Value): Result<Value, Error> {
        onEvent(MutableStoreTelemetryEvent.UpdateStarted(key, value))
        val mutation = Mutation.Update(key, value)
        return (executeMutation(key, mutation) as Result<Value, Error>).also {
            onEvent(MutableStoreTelemetryEvent.UpdateCompleted(key, value, it))
        }
    }

    override suspend fun delete(key: Key): Result<Unit, Error> {
        onEvent(MutableStoreTelemetryEvent.DeleteStarted(key))
        val mutation = Mutation.Delete(key)
        return (executeMutation(key, mutation) as Result<Unit, Error>).also {
            onEvent(MutableStoreTelemetryEvent.DeleteCompleted(key, it))
        }
    }


    /**
     * Runs the mutation pipeline (list of [MutationStrategy]s).
     * The final stage calls [mutationOperations] for a remote call.
     * If that’s successful, we write to SOT & memory via [storeDataHooks].
     */
    private suspend fun executeMutation(
        key: Key,
        mutation: Mutation<Key, Partial, Value>
    ): Result<Any, Error> {

        onEvent(MutableStoreTelemetryEvent.MutationPipelineStarted(key, mutation))

        val outcome = runMutationChain(
            mutation,
            strategies
        ) { finalMutation ->
            doTerminalMutation(
                finalMutation,
                mutationOperations,
                storeDAO,
                errorAdapter
            )
        }

        // Convert the pipeline outcome to a [Result].
        return when (outcome) {
            is MutationStrategy.Outcome.Continue -> {
                val result = Result.Success(outcome.mutation)
                onEvent(MutableStoreTelemetryEvent.MutationPipelineCompleted(key, result))
                result
            }

            is MutationStrategy.Outcome.Fail -> {
                val result = Result.Failure(outcome.error)
                onEvent(MutableStoreTelemetryEvent.MutationPipelineCompleted(key, result))
                result
            }

            is MutationStrategy.Outcome.NoOp -> {
                val result = Result.NoOp(outcome.reason)
                onEvent(MutableStoreTelemetryEvent.MutationPipelineCompleted(key, result))
                result
            }
        }
    }

    /**
     * Composes multiple [MutationStrategy] in a chain.
     * Each strategy can transform or fail the mutation.
     * The last stage does the actual network + SOT writes.
     */
    private suspend fun runMutationChain(
        mutation: Mutation<Key, Partial, Value>,
        strategies: List<MutationStrategy<Key, Partial, Value, Error>>,
        terminalStage: suspend (Mutation<Key, Partial, Value>) -> MutationStrategy.Outcome<Key, Partial, Value, Error>
    ): MutationStrategy.Outcome<Key, Partial, Value, Error> {

        var index = -1

        val chain = object : MutationStrategy.Chain<Key, Partial, Value, Error> {
            override suspend fun proceed(mutation: Mutation<Key, Partial, Value>): MutationStrategy.Outcome<Key, Partial, Value, Error> {
                index++
                return if (index < strategies.size) {
                    // Telemetry: strategy interception
                    onEvent(MutableStoreTelemetryEvent.MutationStrategyIntercepted(mutation, strategies[index], index))

                    val outcome = strategies[index].intercept(mutation, this)

                    // Telemetry: strategy outcome
                    onEvent(MutableStoreTelemetryEvent.MutationStrategyOutcome(mutation, strategies[index], index, outcome))
                    outcome
                } else {
                    // No more strategies, call terminal stage
                    terminalStage(mutation)
                }
            }
        }

        return chain.proceed(mutation)
    }

    /**
     * The terminal stage that performs the actual network request
     * via [mutationOperations], then writes to SOT+memory via [storeDataHooks].
     */
    private suspend fun doTerminalMutation(
        mutation: Mutation<Key, Partial, Value>,
        mutationOperations: MutationOperations<Key, Partial, Value, Error>,
        storeDataHooks: StoreDataHooks<Key, Value>,
        errorAdapter: (Throwable) -> Error
    ): MutationStrategy.Outcome<Key, Partial, Value, Error> {
        return try {
            // Perform the actual create, update, or delete
            when (mutation) {
                is Mutation.Create -> {
                    val networkResponse = mutationOperations.create(mutation.key, mutation.partial)
                    if (networkResponse.isSuccess) {
                        storeDataHooks.write(mutation.key, networkResponse.getOrThrow())
                        MutationStrategy.Outcome.Continue(mutation)
                    } else {
                        MutationStrategy.Outcome.Fail(networkResponse.errorOrThrow())
                    }
                }

                is Mutation.Update -> {
                    val networkResponse = mutationOperations.update(mutation.key, mutation.value)
                    if (networkResponse.isSuccess) {
                        storeDataHooks.write(mutation.key, networkResponse.getOrThrow())
                        MutationStrategy.Outcome.Continue(mutation)
                    } else {
                        MutationStrategy.Outcome.Fail(networkResponse.errorOrThrow())
                    }
                }

                is Mutation.Delete -> {
                    val networkResponse = mutationOperations.delete(mutation.key)
                    if (networkResponse.isSuccess) {
                        storeDataHooks.delete(mutation.key)
                        MutationStrategy.Outcome.Continue(mutation)
                    } else {
                        MutationStrategy.Outcome.Fail(networkResponse.errorOrThrow())
                    }
                }
            }
        } catch (t: Throwable) {
            MutationStrategy.Outcome.Fail(errorAdapter(t))
        }
    }


    /**
     * Helper to emit a [MutableStoreTelemetryEvent].
     * Also forwards the event to any user-provided [telemetry] instance.
     */
    private suspend fun onEvent(event: MutableStoreTelemetryEvent<Key, Partial, Value, Error>) {
        telemetry?.onEvent(event)
        _mutableStoreFlowTelemetryEvents.emit(event)
    }

}


/**
 * Attempts to cast a [Store] into a [StoreDataHooks] for write operations.
 * If not possible, throws an [IllegalStateException].
 */
private fun <Key : Any, Value : Any> Store<Key, Value>.storeDataHooks(): StoreDataHooks<Key, Value> =
    this as? StoreDataHooks<Key, Value>
        ?: error("The provided Store cannot be used with MutableStore. The underlying Store instance must be a RealStore.")

/**
 * Attempts to cast a [Store] into a [StoreFlowTelemetryHooks].
 * If not possible, throws an [IllegalStateException].
 */
private fun <Key : Any, Value : Any> Store<Key, Value>.storeFlowTelemetryEvents(): StoreFlowTelemetryHooks<Key, Value> =
    this as? StoreFlowTelemetryHooks<Key, Value>
        ?: error("The provided Store cannot be used with MutableStore. The underlying Store instance must be a RealStore.")