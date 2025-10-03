package dev.mattramotar.storex.resilience

/**
 * Emitted when the circuit breaker transitions between states.
 *
 * @property previous The state before the transition.
 * @property current The state after the transition.
 */
data class CircuitBreakerEvent(
    val previous: CircuitBreaker.State,
    val current: CircuitBreaker.State,
) : OperationEvent
