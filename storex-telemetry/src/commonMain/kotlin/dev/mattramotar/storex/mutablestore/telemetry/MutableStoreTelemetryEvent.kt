package dev.mattramotar.storex.mutablestore.telemetry

import dev.mattramotar.storex.mutablestore.core.api.Mutation
import dev.mattramotar.storex.mutablestore.core.api.MutationStrategy
import dev.mattramotar.storex.result.Result


/**
 * A sealed class of telemetry events specific to mutable-store operations:
 * create, update, delete, conflict resolution, and mutation pipeline steps.
 *
 * The goal is to give you (and downstream consumers) full visibility
 * into what the RealMutableStore is doing at runtime.
 *
 * Each event variant may carry information (like the input key, partial data,
 * success/failure, or the specific MutationStrategy that intercepted the mutation).
 *
 * You can expand or trim these events to suit your production needs.
 */
sealed class MutableStoreTelemetryEvent<out Key : Any, out Partial : Any, out Value : Any, out Error : Any> {


    /**
     * Emitted when RealMutableStore is about to run conflict-resolution
     * prior to a read (stream/get) that requires fresh data from network.
     */
    data class ConflictResolutionStarted<Key : Any, Partial : Any, Value : Any, Error : Any>(
        val key: Key
    ) : MutableStoreTelemetryEvent<Key, Partial, Value, Error>()

    /**
     * Emitted when conflict-resolution was skipped (e.g., because no unresolved
     * local changes exist or some condition short-circuited the process).
     */
    data class ConflictResolutionSkipped<Key : Any, Partial : Any, Value : Any, Error : Any>(
        val key: Key,
        val reason: String
    ) : MutableStoreTelemetryEvent<Key, Partial, Value, Error>()

    /**
     * Emitted when conflict-resolution fails (e.g., partial sync with server
     * encountered an error).
     */
    data class ConflictResolutionFailed<Key : Any, Partial : Any, Value : Any, Error : Any>(
        val key: Key,
        val error: Error? = null
    ) : MutableStoreTelemetryEvent<Key, Partial, Value, Error>()

    /**
     * Emitted when conflict-resolution completes successfully.
     */
    data class ConflictResolutionSucceeded<Key : Any, Partial : Any, Value : Any, Error : Any>(
        val key: Key
    ) : MutableStoreTelemetryEvent<Key, Partial, Value, Error>()


    /**
     * Emitted when a new mutation pipeline is started (create, update, or delete).
     */
    data class MutationPipelineStarted<Key : Any, Partial : Any, Value : Any, Error : Any>(
        val key: Key,
        val mutation: Mutation<Key, Partial, Value>
    ) : MutableStoreTelemetryEvent<Key, Partial, Value, Error>()

    /**
     * Emitted when the mutation pipeline has finished executing
     * (after running through all MutationStrategies and the final stage).
     */
    data class MutationPipelineCompleted<Key : Any, Partial : Any, Value : Any, Error : Any>(
        val key: Key,
        val result: Result<Any, Error>
    ) : MutableStoreTelemetryEvent<Key, Partial, Value, Error>()

    /**
     * Emitted each time a MutationStrategy intercepts (examines or transforms)
     * the mutation in the chain.
     */
    data class MutationStrategyIntercepted<Key : Any, Partial : Any, Value : Any, Error : Any>(
        val mutation: Mutation<Key, Partial, Value>,
        val strategy: MutationStrategy<Key, Partial, Value, Error>,
        val index: Int
    ) : MutableStoreTelemetryEvent<Key, Partial, Value, Error>()

    /**
     * Emitted after a MutationStrategy returns an outcome (Continue, Fail, NoOp).
     */
    data class MutationStrategyOutcome<Key : Any, Partial : Any, Value : Any, Error : Any>(
        val mutation: Mutation<Key, Partial, Value>,
        val strategy: MutationStrategy<Key, Partial, Value, Error>,
        val index: Int,
        val outcome: MutationStrategy.Outcome<Key, Partial, Value, Error>
    ) : MutableStoreTelemetryEvent<Key, Partial, Value, Error>()


    data class CreateStarted<Key : Any, Partial : Any, Value : Any, Error : Any>(
        val key: Key,
        val partial: Partial
    ) : MutableStoreTelemetryEvent<Key, Partial, Value, Error>()

    data class CreateCompleted<Key : Any, Partial : Any, Value : Any, Error : Any>(
        val key: Key,
        val partial: Partial,
        val result: Result<Value, Error>
    ) : MutableStoreTelemetryEvent<Key, Partial, Value, Error>()


    data class UpdateStarted<Key : Any, Partial : Any, Value : Any, Error : Any>(
        val key: Key,
        val value: Value
    ) : MutableStoreTelemetryEvent<Key, Partial, Value, Error>()

    data class UpdateCompleted<Key : Any, Partial : Any, Value : Any, Error : Any>(
        val key: Key,
        val value: Value,
        val result: Result<Value, Error>
    ) : MutableStoreTelemetryEvent<Key, Partial, Value, Error>()


    data class DeleteStarted<Key : Any, Partial : Any, Value : Any, Error : Any>(
        val key: Key
    ) : MutableStoreTelemetryEvent<Key, Partial, Value, Error>()

    data class DeleteCompleted<Key : Any, Partial : Any, Value : Any, Error : Any>(
        val key: Key,
        val result: Result<Unit, Error>
    ) : MutableStoreTelemetryEvent<Key, Partial, Value, Error>()

}
