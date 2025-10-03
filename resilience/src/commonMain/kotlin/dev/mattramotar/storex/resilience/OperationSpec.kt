package dev.mattramotar.storex.resilience

import dev.mattramotar.storex.resilience.dsl.OperationSpecScope
import kotlin.time.Duration

/**
 * Immutable configuration snapshot for a resilient operation.
 *
 * This data class captures all parameters needed to execute a suspending call with timeout,
 * retry, and circuit breaker protection. Users configure these via the [OperationSpecScope]
 * DSL. This class is the validated, immutable result built internally.
 *
 * @param T The type of value returned by [call] upon success.
 * @property call The suspending work to execute under resilience policies.
 * @property circuitBreaker Optional circuit breaker instance to enforce failure thresholds and state sharing across calls.
 * @property timeout Maximum duration for [call] to complete. If exceeded, a [kotlinx.coroutines.TimeoutCancellationException] is thrown and retries are attempted according to [retryPolicy].
 * @property retryPolicy Determines the delay (if any) before each retry attempt.
 * @property retryOn Predicate invoked on each exception to decide if retries should be attempted.
 * @property failureThreshold Number of consecutive failures in [CircuitBreaker.State.CLOSED] required to trip the circuit to [CircuitBreaker.State.OPEN]. Only used when [circuitBreaker] is `null`.
 * @property openTTL Duration the circuit remains [CircuitBreaker.State.OPEN] before transitioning to [CircuitBreaker.State.HALF_OPEN] for probe attempts. Only used when [circuitBreaker] is `null`.
 * @property probeQuota Maximum probe calls allowed in [CircuitBreaker.State.HALF_OPEN] before making a verdict. All probes must succeed to close the circuit, any failure reopens it. Only used when [circuitBreaker] is `null`.
 *
 * @see OperationSpecScope The DSL builder for configuring operations.
 * @see Resilience.execute Entry point for executing resilient operations.
 * @see Resilience.asLoadStateFlow Reactive Flow-based execution.
 * @see OperationResult Result type capturing success or various failure modes.
 * @see CircuitBreaker Circuit breaker implementation for failure threshold enforcement.
 */
data class OperationSpec<T>(
    /** The suspending work to execute under resilience policies. This is required. */
    val call: suspend () -> T,

    /** Enforces failure thresholds and backoff timing. */
    val circuitBreaker: CircuitBreaker?,

    /** The timeout duration, defaults to 10 seconds. */
    val timeout: Duration,

    /** Controls if and when to retry, defaults to exponential jitter. */
    val retryPolicy: RetryPolicy,

    /** Predicate to decide which exceptions should be retried. */
    val retryOn: (Throwable) -> Boolean,

    /** Consecutive failures in [CircuitBreaker.State.CLOSED] state required to open the circuit. */
    val failureThreshold: Int,

    /**
     * How long to stay [CircuitBreaker.State.OPEN] before trying [CircuitBreaker.State.HALF_OPEN]
     * probes.
     */
    val openTTL: Duration,

    /** Probe calls allowed in [CircuitBreaker.State.HALF_OPEN] before a verdict. */
    val probeQuota: Int,
)
