package dev.mattramotar.storex.resilience

import kotlin.time.Duration

/** To do */
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
