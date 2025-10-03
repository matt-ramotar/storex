package dev.mattramotar.storex.resilience.internal

import dev.mattramotar.storex.resilience.CircuitBreaker
import dev.mattramotar.storex.resilience.CircuitBreakerEvent
import dev.mattramotar.storex.resilience.Clock
import dev.mattramotar.storex.resilience.OperationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultCircuitBreakerTest {

    @Test
    fun tryAcquire_givenClosedState_thenReturnsTrue() = runTest {
        // Given
        val breaker = createBreaker()

        // When
        val result = breaker.tryAcquire()

        // Then
        assertTrue(result)
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state.value)
    }

    @Test
    fun tryAcquire_givenOpenState_thenReturnsFalse() = runTest {
        // Given
        val breaker = createBreaker(failureThreshold = 1)
        tripToOpen(breaker)
        advanceUntilIdle()

        // When
        val result = breaker.tryAcquire()

        // Then
        assertFalse(result)
        assertEquals(CircuitBreaker.State.OPEN, breaker.state.value)
    }

    @Test
    fun tryAcquire_givenHalfOpenWithProbes_thenReturnsTrueAndDecrements() = runTest {
        // Given
        val breaker = createBreaker(
            failureThreshold = 1,
            openTTL = 10.milliseconds,
            probeQuota = 2
        )
        tripToHalfOpen(breaker, 10.milliseconds)

        // When
        val first = breaker.tryAcquire()
        val second = breaker.tryAcquire()
        val third = breaker.tryAcquire()

        // Then
        assertTrue(first)
        assertTrue(second)
        assertFalse(third)
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.state.value)
    }

    @Test
    fun tryAcquire_givenHalfOpenWithoutProbes_thenReturnsFalse() = runTest {
        // Given
        val breaker = createBreaker(
            failureThreshold = 1,
            openTTL = 10.milliseconds,
            probeQuota = 1
        )
        tripToHalfOpen(breaker, 10.milliseconds)
        breaker.tryAcquire() // Consume the one probe

        // When
        val result = breaker.tryAcquire()

        // Then
        assertFalse(result)


    }

    @Test
    fun onSuccess_givenClosedState_thenResetsFailureCount() = runTest {
        // Given
        val breaker = createBreaker(failureThreshold = 3)
        breaker.tryAcquire()
        breaker.onFailure()
        breaker.tryAcquire()
        breaker.onFailure()
        advanceUntilIdle()

        // When
        breaker.onSuccess()
        advanceUntilIdle()

        // Then - Can fail 2 more times without tripping (proves reset)
        breaker.tryAcquire()
        breaker.onFailure()
        breaker.tryAcquire()
        breaker.onFailure()
        advanceUntilIdle()
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state.value)
    }

    @Test
    fun onSuccess_givenHalfOpenState_thenTransitionsToClosedAndEmitsEvent() = runTest {
        // Given
        val events = FakeOperationEvents()
        val breaker = createBreaker(
            events = events,
            failureThreshold = 1,
            openTTL = 10.milliseconds
        )
        tripToHalfOpen(breaker, 10.milliseconds)

        // When
        breaker.onSuccess()
        advanceUntilIdle()

        // Then
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state.value)
        assertTrue(
            events.captured.contains(
                CircuitBreakerEvent(
                    previous = CircuitBreaker.State.HALF_OPEN,
                    current = CircuitBreaker.State.CLOSED
                )
            )
        )
    }

    @Test
    fun onSuccess_givenOpenState_thenNoOp() = runTest {
        // Given
        val breaker = createBreaker(failureThreshold = 1)
        tripToOpen(breaker)
        advanceUntilIdle()

        // When
        breaker.onSuccess()
        advanceUntilIdle()

        // Then
        assertEquals(CircuitBreaker.State.OPEN, breaker.state.value)
    }

    @Test
    fun onFailure_givenClosedUnderThreshold_thenIncrementsFailures() = runTest {
        // Given
        val breaker = createBreaker(failureThreshold = 3)

        // When
        breaker.tryAcquire()
        breaker.onFailure()
        advanceUntilIdle()

        // Then
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state.value)
    }

    @Test
    fun onFailure_givenClosedAtThreshold_thenTransitionsToOpenAndSchedules() = runTest {
        // Given
        val events = FakeOperationEvents()
        val clock = SuspendingRecordingTestClock()
        val breaker = createBreaker(
            events = events,
            failureThreshold = 2,
            openTTL = 50.milliseconds,
            clock = clock
        )

        // When
        breaker.tryAcquire()
        breaker.onFailure()
        breaker.tryAcquire()
        breaker.onFailure()
        runCurrent() // Process the transition to OPEN
        advanceUntilIdle() // Let the background scope launch the timer coroutine

        // Then
        assertEquals(CircuitBreaker.State.OPEN, breaker.state.value)
        assertTrue(
            events.captured.contains(
                CircuitBreakerEvent(
                    previous = CircuitBreaker.State.CLOSED,
                    current = CircuitBreaker.State.OPEN
                )
            )
        )

        // Verify timer was scheduled with correct duration
        assertEquals(1, clock.sleeps.size)
        assertEquals(50.milliseconds, clock.sleeps.first())
    }

    @Test
    fun onFailure_givenHalfOpenState_thenTransitionsToOpen() = runTest {
        // Given
        val events = FakeOperationEvents()
        val breaker = createBreaker(
            events = events,
            failureThreshold = 1,
            openTTL = 10.milliseconds
        )
        tripToHalfOpen(breaker, 10.milliseconds)

        // When
        breaker.onFailure()
        advanceUntilIdle()

        // Then
        assertEquals(CircuitBreaker.State.OPEN, breaker.state.value)
        assertTrue(
            events.captured.contains(
                CircuitBreakerEvent(
                    previous = CircuitBreaker.State.HALF_OPEN,
                    current = CircuitBreaker.State.OPEN
                )
            )
        )
    }

    @Test
    fun onFailure_givenOpenState_thenNoOp() = runTest {
        // Given
        val breaker = createBreaker(failureThreshold = 1)
        tripToOpen(breaker)
        advanceUntilIdle()

        // When
        breaker.onFailure()
        advanceUntilIdle()

        // Then
        assertEquals(CircuitBreaker.State.OPEN, breaker.state.value)
    }

    @Test
    fun openTimer_whenTTLExpires_thenTransitionsToHalfOpen() = runTest {
        // Given
        val events = FakeOperationEvents()
        val breaker = createBreaker(
            events = events,
            failureThreshold = 1,
            openTTL = 100.milliseconds
        )
        tripToOpen(breaker)
        runCurrent() // Start the scheduleReopen coroutine
        advanceUntilIdle() // Wait for any immediate work
        assertEquals(CircuitBreaker.State.OPEN, breaker.state.value)

        // When
        advanceTimeBy(100) // Advance past the openTTL delay
        runCurrent() // Process tasks that are now due
        advanceUntilIdle() // Complete the transition

        // Then
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.state.value)
        assertTrue(
            events.captured.contains(
                CircuitBreakerEvent(
                    previous = CircuitBreaker.State.OPEN,
                    current = CircuitBreaker.State.HALF_OPEN
                )
            )
        )
    }

    @Test
    fun closedTransition_givenOpenTimer_thenCancelsTimer() = runTest {
        // Given
        val breaker = createBreaker(
            failureThreshold = 1,
            openTTL = 100.milliseconds,
            probeQuota = 1
        )
        tripToHalfOpen(breaker, 100.milliseconds)

        // When
        breaker.tryAcquire()
        breaker.onSuccess()
        advanceUntilIdle()

        // Then - Timer cancelled, stays CLOSED
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state.value)
        advanceTimeBy(200)
        advanceUntilIdle()
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state.value)
    }

    @Test
    fun probeQuota_whenHalfOpen_thenSetsCorrectCount() = runTest {
        // Given
        val breaker = createBreaker(
            failureThreshold = 1,
            openTTL = 10.milliseconds,
            probeQuota = 3
        )

        // When
        tripToHalfOpen(breaker, 10.milliseconds)

        // Then - Can acquire exactly 3 times
        assertTrue(breaker.tryAcquire())
        assertTrue(breaker.tryAcquire())
        assertTrue(breaker.tryAcquire())
        assertFalse(breaker.tryAcquire())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun TestScope.createBreaker(
        events: MutableOperationEvents = FakeOperationEvents(),
        failureThreshold: Int = 3,
        openTTL: Duration = 30.milliseconds,
        probeQuota: Int = 1,
        clock: Clock = Clock.SYSTEM
    ) = DefaultCircuitBreaker(
        mutableOperationEvents = events,
        scope = backgroundScope,
        failureThreshold = failureThreshold,
        openTTL = openTTL,
        probeQuota = probeQuota,
        clock = clock
    )

    private suspend fun tripToOpen(breaker: DefaultCircuitBreaker) {
        breaker.tryAcquire()
        breaker.onFailure()
    }

    /**
     * Trips the breaker to OPEN, then advances virtual time to transition to HALF_OPEN.
     * Handles all the timing coordination needed for virtual time testing.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun TestScope.tripToHalfOpen(
        breaker: DefaultCircuitBreaker,
        openTTL: Duration
    ) {
        tripToOpen(breaker)
        runCurrent() // Start the scheduleReopen coroutine
        advanceUntilIdle() // Wait for any immediate work
        advanceTimeBy(openTTL.inWholeMilliseconds) // Advance past the openTTL delay
        runCurrent() // Process tasks that are now due
        advanceUntilIdle() // Complete the transition
    }

    /**
     * Fake MutableOperationEvents that captures emitted events for assertions.
     * Not mockable per constraints, so using a builder-backed fake.
     */
    private class FakeOperationEvents : MutableOperationEvents {
        override val events = MutableSharedFlow<OperationEvent>(
            extraBufferCapacity = 64
        )

        val captured = mutableListOf<OperationEvent>()

        init {
            CoroutineScope(Dispatchers.Unconfined).launch {
                events.collect { event ->
                    captured.add(event)
                }
            }
        }
    }
}
