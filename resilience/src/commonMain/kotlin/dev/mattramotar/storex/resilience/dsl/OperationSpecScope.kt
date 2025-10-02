package dev.mattramotar.storex.resilience.dsl

import dev.mattramotar.storex.resilience.CircuitBreaker
import dev.mattramotar.storex.resilience.RetryPolicy
import kotlin.time.Duration

/**
 * Builder DSL for configuring resilient calls and flows.
 *
 * @param T The type of the operation result.
 */
interface OperationSpecScope<T> {

  /** The suspending work to execute under resilience policies. This is required. */
  var call: (suspend () -> T)?

  /** Enforces failure thresholds and backoff timing. */
  var circuitBreaker: CircuitBreaker?

  /** The timeout duration, defaults to 10 seconds. */
  var timeout: Duration

  /** Controls if and when to retry, defaults to exponential jitter. */
  var retryPolicy: RetryPolicy

  /** Predicate to decide which exceptions should be retried. */
  var retryOn: (Throwable) -> Boolean

  /** Consecutive failures in [CircuitBreaker.State.CLOSED] state required to open the circuit. */
  var failureThreshold: Int

  /**
   * How long to stay [CircuitBreaker.State.OPEN] before trying [CircuitBreaker.State.HALF_OPEN]
   * probes.
   */
  var openTTL: Duration

  /** Probe calls allowed in [CircuitBreaker.State.HALF_OPEN] before a verdict. */
  var probeQuota: Int

  /** Ergonomic method. */
  fun call(block: suspend () -> T) {
    this.call = block
  }
}
