package dev.mattramotar.storex.resilience.dsl

import dev.mattramotar.storex.resilience.*
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Execute a suspending [block] under resilience policies with optional overrides.
 *
 * @param T The type returned by [block].
 * @param timeout Maximum duration before timing out.
 * @param retryPolicy Retry strategy for failures.
 * @param circuitBreaker Optional circuit breaker to use.
 * @param retryOn Predicate determining errors that should be retried.
 * @param block Suspending work to execute.
 * @return A [OperationResult] indicating success or type of failure.
 */
suspend inline fun <reified T> Resilience.call(
    timeout: Duration = 10.seconds,
    retryPolicy: RetryPolicy = RetryPolicy.exponentialJitter(),
    circuitBreaker: CircuitBreaker? = CircuitBreaker.Default,
    noinline retryOn: (Throwable) -> Boolean = RetryDefaults::retryOn,
    crossinline block: suspend () -> T,
): OperationResult<T> = execute {
    this.timeout = timeout
    this.retryPolicy = retryPolicy
    this.circuitBreaker = circuitBreaker
    this.retryOn = retryOn
    call { block() }
}

/**
 * Execute a suspending [block] under resilience policies and return a cold [Flow] emitting
 * [LoadState.Loading] followed by [LoadState.Success] or [LoadState.Error].
 *
 * @param T The type returned by [block].
 * @param timeout Maximum duration before timing out.
 * @param retryPolicy Retry strategy for failures.
 * @param circuitBreaker Optional circuit breaker to use.
 * @param retryOn Predicate determining errors that should be retried.
 * @param block Suspending work to execute.
 * @return A [Flow] of [LoadState].
 */
inline fun <reified T> Resilience.flow(
    timeout: Duration = 10.seconds,
    retryPolicy: RetryPolicy = RetryPolicy.exponentialJitter(),
    circuitBreaker: CircuitBreaker? = CircuitBreaker.Default,
    noinline retryOn: (Throwable) -> Boolean = RetryDefaults::retryOn,
    crossinline block: suspend () -> T,
): Flow<LoadState<T>> = asLoadStateFlow {
    this.timeout = timeout
    this.retryPolicy = retryPolicy
    this.circuitBreaker = circuitBreaker
    this.retryOn = retryOn
    call { block() }
}