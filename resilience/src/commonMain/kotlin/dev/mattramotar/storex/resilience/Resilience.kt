package dev.mattramotar.storex.resilience

import dev.mattramotar.storex.resilience.dsl.OperationSpecScope
import dev.mattramotar.storex.resilience.internal.DefaultResilience
import kotlinx.coroutines.flow.Flow

/** Executes work with built-in circuit breaker, timeout, and retry support. */
interface Resilience {
    /**
     * Executes a one-shot operation under the provided resilience configuration.
     *
     * @param T The type of the operation result.
     * @param configure A DSL to configure call parameters.
     * @return A [OperationResult].
     */
    suspend fun <T> execute(configure: OperationSpecScope<T>.() -> Unit): OperationResult<T>

    /**
     * Wraps a single suspending call in a cold [Flow] that emits:
     * 1. [LoadState.Loading] immediately.
     * 2. [LoadState.Success] with the result on success, or
     * 3. [LoadState.Error] with the exception on failure.
     *
     * Execution is protected by timeout, retries, and a circuit breaker. Each collector triggers a
     * fresh invocation, and downstream cancellation aborts in-flight retries.
     *
     * @param T The type of the operation result.
     * @param configure A DSL to configure call parameters.
     * @return A cold [Flow] of [LoadState].
     */
    fun <T> asLoadStateFlow(configure: OperationSpecScope<T>.() -> Unit): Flow<LoadState<T>>

    /** Read-only bus for telemetry. */
    val operationEvents: OperationEvents

    companion object {
        /** Provides a [Resilience] instance. */
        val Default: Resilience = DefaultResilience()
    }
}