package dev.mattramotar.storex.resilience.internal

import dev.mattramotar.storex.resilience.CircuitBreaker
import dev.mattramotar.storex.resilience.CircuitBreakerEvent
import dev.mattramotar.storex.resilience.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A non-blocking implementation of [CircuitBreaker] implementing the [State.CLOSED]
 * -> [State.OPEN] -> [State.HALF_OPEN] state machine.
 *
 * **About Probes**: When the breaker transitions from [State.OPEN] to
 * [State.HALF_OPEN] it allows a small, bounded number of requests through to verify
 * that the downstream dependency has recovered. These requests are called probes. If every probe
 * succeeds, the breaker closes. If any probe fails, it reopens and starts another cool down period.
 *
 * @param coroutineDispatcher The [CoroutineDispatcher] for the [scope].
 * @param scope [CoroutineScope] for internal timers, defaults to [SupervisorJob] +
 * [Dispatchers.Default]. Cancel the scope to dispose the breaker.
 * @param failureThreshold Number of consecutive failures in [State.CLOSED] state
 * before opening the circuit.
 * @param openTTL Duration to remain [State.OPEN] before automatically transitioning
 * to [State.HALF_OPEN].
 * @param probeQuota Maximum probe calls allowed in [State.HALF_OPEN] before
 * returning a verdict.
 */
internal class DefaultCircuitBreaker(
    private val mutableOperationEvents: MutableOperationEvents = DefaultOperationEvents(),
    coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + coroutineDispatcher),
    private val failureThreshold: Int = 3,
    private val openTTL: Duration = 30.seconds,
    private val probeQuota: Int = 1,
    private val clock: Clock = Clock.Companion.SYSTEM
) : CircuitBreaker {

    private val _state = MutableStateFlow(CircuitBreaker.State.CLOSED)
    override val state: StateFlow<CircuitBreaker.State> = _state.asStateFlow()

    private var currentState: CircuitBreaker.State
        get() = _state.value
        set(value) {
            _state.value = value
        }

    private val mutex = Mutex()
    private var failures: Int = 0

    /** Remaining probe calls allowed in the current [State.HALF_OPEN] trial window. */
    private var probeCallsRemaining: Int = 0

    private var reopenJob: Job? = null

    override suspend fun tryAcquire(): Boolean =
        mutex.withLock {
            return when (currentState) {
                CircuitBreaker.State.OPEN -> false
                CircuitBreaker.State.CLOSED -> true
                CircuitBreaker.State.HALF_OPEN -> {
                    if (probeCallsRemaining > 0) {
                        probeCallsRemaining--
                        true
                    } else {
                        false
                    }
                }
            }
        }

    override suspend fun onSuccess() {
        var shouldClose = false
        mutex.withLock {
            when (currentState) {
                CircuitBreaker.State.CLOSED -> resetFailures()
                CircuitBreaker.State.HALF_OPEN -> shouldClose = true
                CircuitBreaker.State.OPEN -> {
                    // No op
                }
            }
        }
        if (shouldClose) {
            transitionTo(CircuitBreaker.State.CLOSED)
        }
    }

    override suspend fun onFailure() {
        var shouldTrip = false
        mutex.withLock {
            when (currentState) {
                CircuitBreaker.State.CLOSED -> {
                    failures++
                    if (failures >= failureThreshold) shouldTrip = true
                }

                CircuitBreaker.State.HALF_OPEN -> shouldTrip = true
                CircuitBreaker.State.OPEN -> {
                    // No op
                }
            }
        }
        if (shouldTrip) {
            tripOpen()
        }
    }

    private fun resetFailures() {
        failures = 0
    }

    private suspend fun tripOpen() {
        transitionTo(CircuitBreaker.State.OPEN)
        scheduleReopen()
    }

    /**
     * **Notes**:
     * - All calls to this method acquire the same [Mutex] to guarantee linearizability and avoid
     * state-machine races.
     */
    private suspend fun transitionTo(target: CircuitBreaker.State) {
        // Capture previous state and apply state change under lock
        val previous =
            mutex.withLock {
                val prev = currentState
                currentState = target

                when (target) {
                    CircuitBreaker.State.HALF_OPEN -> probeCallsRemaining = probeQuota
                    CircuitBreaker.State.CLOSED -> {
                        cancelReopenJob()
                        resetFailures()
                    }

                    CircuitBreaker.State.OPEN -> {
                        // No op
                    }
                }
                prev
            }

        mutableOperationEvents.events.emit(CircuitBreakerEvent(previous = previous, current = target))
    }

    private fun scheduleReopen() {
        cancelReopenJob()

        reopenJob =
            scope.launch {
                clock.sleep(openTTL)
                transitionTo(CircuitBreaker.State.HALF_OPEN)
            }
    }

    private fun cancelReopenJob() {
        reopenJob?.cancel()
        reopenJob = null
    }
}
