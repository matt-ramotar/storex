package dev.mattramotar.storex.resilience

import dev.mattramotar.storex.resilience.internal.DefaultCircuitBreaker
import kotlinx.coroutines.flow.StateFlow

/**
 * Non-blocking circuit breaker for a single downstream dependency. Protects against cascading
 * failures by cycling through the [State.CLOSED] -> [State.OPEN] -> [State.HALF_OPEN] states.
 *
 * #### Usage
 * 1. Call [tryAcquire] before invoking the dependency.
 * 2. If it returns `false`, fast fail. Otherwise, proceed.
 * 3. After invocation, call either [onSuccess] or [onFailure].
 *
 * #### Observability Monitor state changes via the [state] flow.
 */
interface CircuitBreaker {

  /**
   * Reserve a slot for a single call.
   *
   * @return `true` if the call is allowed ([State.CLOSED] or [State.HALF_OPEN]), `false` if the
   * circuit is [State.OPEN].
   */
  suspend fun tryAcquire(): Boolean

  /**
   * Record a successful call.
   *
   * - In [State.CLOSED]: Resets failure count.
   * - In [State.HALF_OPEN]: Transitions to [State.CLOSED].
   * - In [State.OPEN]: No op.
   */
  suspend fun onSuccess()

  /**
   * Record a failed call.
   *
   * - In [State.CLOSED]: Increments failures and may open the circuit.
   * - In [State.HALF_OPEN]: Transitions immediately back to [State.OPEN].
   * - In [State.OPEN]: No op.
   */
  suspend fun onFailure()

  /** Stream of the current circuit [State]. Use for telemetry. */
  val state: StateFlow<State>

  /** Circuit breaker states. */
  enum class State {
    /** All calls allowed. Failures accumulate and can open the circuit. */
    CLOSED,

    /** All calls blocked. Transitions to [State.HALF_OPEN] after a timeout. */
    OPEN,

    /**
     * Limited calls allowed to test recovery.
     * - One success -> [State.CLOSED].
     * - Any failure -> [State.OPEN].
     */
    HALF_OPEN
  }

    companion object {

        /** Creates a new circuit breaker with default settings. */
        val Default: CircuitBreaker
            get() = DefaultCircuitBreaker()
    }
}
