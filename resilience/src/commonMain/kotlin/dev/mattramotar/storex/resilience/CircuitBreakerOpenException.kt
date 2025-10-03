package dev.mattramotar.storex.resilience

/** Signals that the circuit breaker is open and the request has been denied. */
class CircuitBreakerOpenException : IllegalStateException("Circuit is open. Request denied.")
