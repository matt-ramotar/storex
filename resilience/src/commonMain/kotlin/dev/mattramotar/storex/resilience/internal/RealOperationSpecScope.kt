package dev.mattramotar.storex.resilience.internal

import dev.mattramotar.storex.resilience.CircuitBreaker
import dev.mattramotar.storex.resilience.OperationSpec
import dev.mattramotar.storex.resilience.RetryDefaults
import dev.mattramotar.storex.resilience.RetryPolicy
import dev.mattramotar.storex.resilience.dsl.OperationSpecScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Builder DSL for configuring resilient calls and flows.
 *
 * @param T The type of the operation result.
 */
internal class RealOperationSpecScope<T> : OperationSpecScope<T> {

    /** The suspending work to call under resilience policies. This is required. */
    override var call: (suspend () -> T)? = null

    /** Enforces failure thresholds and backoff timing. */
    override var circuitBreaker: CircuitBreaker? = null

    /** The timeout duration, defaults to 10 seconds. */
    override var timeout: Duration = 10.seconds

    /** Controls if and when to retry, defaults to exponential jitter. */
    override var retryPolicy: RetryPolicy = RetryPolicy.Companion.exponentialJitter()

    /** Predicate to decide which exceptions should be retried. */
    override var retryOn: (Throwable) -> Boolean = RetryDefaults::retryOn

    /** Consecutive failures in [CircuitBreaker.State.CLOSED] state required to open the circuit. */
    override var failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD

    /**
     * How long to stay [CircuitBreaker.State.OPEN] before trying [CircuitBreaker.State.HALF_OPEN]
     * probes.
     */
    override var openTTL: Duration = 30.seconds

    /** Probe calls allowed in [CircuitBreaker.State.HALF_OPEN] before a verdict. */
    override var probeQuota: Int = 1

    /** Ergonomic method. */
    override fun call(block: suspend () -> T) {
        this.call = block
    }

    internal fun build(): OperationSpec<T> {
        return with(call) {
            if (this == null) {
                error("call {} is required!")
            }

            OperationSpec(
                call = this,
                circuitBreaker = circuitBreaker,
                timeout = timeout,
                retryPolicy = retryPolicy,
                retryOn = retryOn,
                failureThreshold = failureThreshold,
                openTTL = openTTL,
                probeQuota = probeQuota
            )
        }
    }

    companion object {
        internal const val DEFAULT_FAILURE_THRESHOLD = 3
    }
}
