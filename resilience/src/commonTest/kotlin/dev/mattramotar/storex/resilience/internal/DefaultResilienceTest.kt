package dev.mattramotar.storex.resilience.internal

import app.cash.turbine.test
import dev.mattramotar.storex.resilience.*
import dev.mattramotar.storex.resilience.internal.utils.RecordingTestClock
import dev.mattramotar.storex.resilience.internal.utils.TimeoutCancellationException
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds


@OptIn(ExperimentalCoroutinesApi::class)
class DefaultResilienceTest {

    @Test
    fun execute_givenCircuitBreakerOpen_whenTryAcquireFails_thenReturnsCircuitOpen() = runTest {
        // Given
        val circuitBreaker = mock<CircuitBreaker>()
        everySuspend { circuitBreaker.tryAcquire() } returns false
        val resilience = DefaultResilience()

        // When
        val result = resilience.execute {
            call { "success" }
            this.circuitBreaker = circuitBreaker
        }

        // Then
        assertEquals(OperationResult.Failure.CircuitOpen, result)
        verifySuspend(mode = VerifyMode.exactly(1)) { circuitBreaker.tryAcquire() }
        verifySuspend(mode = VerifyMode.exactly(0)) { circuitBreaker.onSuccess() }
        verifySuspend(mode = VerifyMode.exactly(0)) { circuitBreaker.onFailure() }
    }

    @Test
    fun execute_givenSuccessfulOperation_whenCallCompletes_thenReturnsSuccess() = runTest {
        // Given
        val circuitBreaker = mock<CircuitBreaker>()
        everySuspend { circuitBreaker.tryAcquire() } returns true
        everySuspend { circuitBreaker.onSuccess() } returns Unit
        val resilience = DefaultResilience()

        // When
        val result = resilience.execute {
            call { "success" }
            this.circuitBreaker = circuitBreaker
        }

        // Then
        assertIs<OperationResult.Success<String>>(result)
        assertEquals("success", result.value)
        verifySuspend(mode = VerifyMode.exactly(1)) { circuitBreaker.tryAcquire() }
        verifySuspend(mode = VerifyMode.exactly(1)) { circuitBreaker.onSuccess() }
        verifySuspend(mode = VerifyMode.exactly(0)) { circuitBreaker.onFailure() }
    }

    @Test
    fun execute_givenTimeout_whenNoRetries_thenReturnsTimedOut() = runTest {
        // Given
        val circuitBreaker = mock<CircuitBreaker>()
        everySuspend { circuitBreaker.tryAcquire() } returns true
        everySuspend { circuitBreaker.onFailure() } returns Unit
        val resilience = DefaultResilience()
        val timeoutException = TimeoutCancellationException()

        // When
        val result = resilience.execute {
            call {
                throw timeoutException
            }
            timeout = 10.milliseconds
            retryPolicy = RetryPolicy.NONE
            this.circuitBreaker = circuitBreaker
        }
        advanceUntilIdle()

        // Then
        assertIs<OperationResult.Failure.TimedOut>(result)
        assertEquals(1, result.attemptCount)
        assertEquals(timeoutException::class, result.cause::class)
        assertEquals(timeoutException.message, result.cause.message)
        verifySuspend(mode = VerifyMode.exactly(1)) { circuitBreaker.tryAcquire() }
        verifySuspend(mode = VerifyMode.exactly(1)) { circuitBreaker.onFailure() }
    }

    @Test
    fun execute_givenTimeout_whenRetrySucceeds_thenReturnsSuccess() = runTest {
        // Given
        val circuitBreaker = mock<CircuitBreaker>()
        everySuspend { circuitBreaker.tryAcquire() } returns true
        everySuspend { circuitBreaker.onSuccess() } returns Unit
        everySuspend { circuitBreaker.onFailure() } returns Unit
        val clock = RecordingTestClock()
        val fakeEvents = FakeOperationEvents()
        val resilience = DefaultResilience(
            clock = clock,
            operationEventsFactory = { fakeEvents }
        )
        var attemptCount = 0

        // When
        val result = resilience.execute {
            call {
                attemptCount++
                if (attemptCount == 1) {
                    delay(2000)
                    "never reached"
                } else {
                    "success"
                }
            }
            timeout = 10.milliseconds
            retryPolicy = RetryPolicy.exponentialJitter(maxRetries = 1)
            this.circuitBreaker = circuitBreaker
        }
        advanceUntilIdle()

        // Then
        assertIs<OperationResult.Success<String>>(result)
        assertEquals("success", result.value)
        assertEquals(1, clock.sleeps.size)
        assertTrue(fakeEvents.captured.any { it is RetryScheduled })
        verifySuspend(mode = VerifyMode.exactly(1)) { circuitBreaker.onFailure() }
        verifySuspend(mode = VerifyMode.exactly(1)) { circuitBreaker.onSuccess() }
    }

    @Test
    fun execute_givenCancellation_whenOperationCancelled_thenReturnsCancelled() = runTest {
        // Given
        val circuitBreaker = mock<CircuitBreaker>()
        everySuspend { circuitBreaker.tryAcquire() } returns true
        val resilience = DefaultResilience()

        // When
        val result = resilience.execute<String> {
            call { throw CancellationException("test cancellation") }
            this.circuitBreaker = circuitBreaker
        }

        // Then
        assertEquals(OperationResult.Failure.Cancelled, result)
        verifySuspend(mode = VerifyMode.exactly(1)) { circuitBreaker.tryAcquire() }
        verifySuspend(mode = VerifyMode.exactly(0)) { circuitBreaker.onFailure() }
    }

    @Test
    fun execute_givenNonRetryableError_whenRetryOnReturnsFalse_thenReturnsError() = runTest {
        // Given
        val circuitBreaker = mock<CircuitBreaker>()
        everySuspend { circuitBreaker.tryAcquire() } returns true
        everySuspend { circuitBreaker.onFailure() } returns Unit
        val resilience = DefaultResilience()
        val testException = RuntimeException("test error")

        // When
        val result = resilience.execute<String> {
            call { throw testException }
            retryOn = { false }
            this.circuitBreaker = circuitBreaker
        }

        // Then
        assertIs<OperationResult.Failure.Error>(result)
        assertEquals(testException::class, result.throwable::class)
        assertEquals(testException.message, result.throwable.message)
        verifySuspend(mode = VerifyMode.exactly(1)) { circuitBreaker.tryAcquire() }
        verifySuspend(mode = VerifyMode.exactly(1)) { circuitBreaker.onFailure() }
    }

    @Test
    fun execute_givenRetryableError_whenNoMoreRetries_thenReturnsError() = runTest {
        // Given
        val circuitBreaker = mock<CircuitBreaker>()
        everySuspend { circuitBreaker.tryAcquire() } returns true
        everySuspend { circuitBreaker.onFailure() } returns Unit
        val resilience = DefaultResilience()
        val testException = RuntimeException("test error")

        // When
        val result = resilience.execute<String> {
            call { throw testException }
            retryOn = { true }
            retryPolicy = RetryPolicy.NONE
            this.circuitBreaker = circuitBreaker
        }

        // Then
        assertIs<OperationResult.Failure.Error>(result)
        assertEquals(testException::class, result.throwable::class)
        assertEquals(testException.message, result.throwable.message)
        verifySuspend(mode = VerifyMode.exactly(1)) { circuitBreaker.tryAcquire() }
        verifySuspend(mode = VerifyMode.exactly(1)) { circuitBreaker.onFailure() }
    }

    @Test
    fun execute_givenRetryableError_whenRetrySucceeds_thenReturnsSuccess() = runTest {
        // Given
        val circuitBreaker = mock<CircuitBreaker>()
        everySuspend { circuitBreaker.tryAcquire() } returns true
        everySuspend { circuitBreaker.onSuccess() } returns Unit
        everySuspend { circuitBreaker.onFailure() } returns Unit
        val clock = RecordingTestClock()
        val fakeEvents = FakeOperationEvents()
        val resilience = DefaultResilience(
            clock = clock,
            operationEventsFactory = { fakeEvents }
        )
        var attemptCount = 0

        // When
        val result = resilience.execute {
            call {
                attemptCount++
                if (attemptCount == 1) {
                    throw RuntimeException("first attempt fails")
                } else {
                    "success"
                }
            }
            retryOn = { true }
            retryPolicy = RetryPolicy.exponentialJitter(maxRetries = 1)
            this.circuitBreaker = circuitBreaker
        }
        advanceUntilIdle()

        // Then
        assertIs<OperationResult.Success<String>>(result)
        assertEquals("success", result.value)
        assertEquals(1, clock.sleeps.size)
        assertTrue(fakeEvents.captured.any { it is RetryScheduled })
        verifySuspend(mode = VerifyMode.exactly(1)) { circuitBreaker.onFailure() }
        verifySuspend(mode = VerifyMode.exactly(1)) { circuitBreaker.onSuccess() }
    }

    @Test
    fun execute_givenDefaultCircuitBreaker_whenNotProvided_thenCreatesAndUsesDefault() = runTest {
        // Given
        val clock = RecordingTestClock()
        val resilience = DefaultResilience(clock = clock)

        // When
        val result = resilience.execute {
            call { "success" }
            failureThreshold = 5
            openTTL = 60.milliseconds
            probeQuota = 2
        }

        // Then
        assertIs<OperationResult.Success<String>>(result)
        assertEquals("success", result.value)
    }

    @Test
    fun execute_givenMultipleTimeouts_whenRetriesExhausted_thenReturnsTimedOut() = runTest {
        // Given
        val circuitBreaker = mock<CircuitBreaker>()
        everySuspend { circuitBreaker.tryAcquire() } returns true
        everySuspend { circuitBreaker.onFailure() } returns Unit
        val clock = RecordingTestClock()
        val fakeEvents = FakeOperationEvents()
        val resilience = DefaultResilience(
            clock = clock,
            operationEventsFactory = { fakeEvents }
        )

        // When
        val result = resilience.execute {
            call {
                delay(2000)
                "never reached"
            }
            timeout = 10.milliseconds
            retryPolicy = RetryPolicy.exponentialJitter(maxRetries = 2)
            this.circuitBreaker = circuitBreaker
        }
        advanceUntilIdle()

        // Then
        assertIs<OperationResult.Failure.TimedOut>(result)
        assertEquals(3, result.attemptCount)
        assertEquals(2, clock.sleeps.size)
        assertEquals(2, fakeEvents.captured.filterIsInstance<RetryScheduled>().size)
        verifySuspend(mode = VerifyMode.exactly(3)) { circuitBreaker.onFailure() }
    }

    @Test
    fun asLoadStateFlow_givenSuccess_whenOperationSucceeds_thenEmitsLoadingAndSuccess() = runTest {
        // Given
        val circuitBreaker = mock<CircuitBreaker>()
        everySuspend { circuitBreaker.tryAcquire() } returns true
        everySuspend { circuitBreaker.onSuccess() } returns Unit
        val resilience = DefaultResilience()

        // When
        val flow = resilience.asLoadStateFlow {
            call { "data" }
            this.circuitBreaker = circuitBreaker
        }

        // Then
        flow.test {
            assertEquals(LoadState.Loading, awaitItem())
            val success = awaitItem()
            assertIs<LoadState.Success<String>>(success)
            assertEquals("data", success.data)
            awaitComplete()
        }
    }

    @Test
    fun asLoadStateFlow_givenError_whenOperationFails_thenEmitsLoadingAndError() = runTest {
        // Given
        val circuitBreaker = mock<CircuitBreaker>()
        everySuspend { circuitBreaker.tryAcquire() } returns true
        everySuspend { circuitBreaker.onFailure() } returns Unit
        val resilience = DefaultResilience()
        val testException = RuntimeException("test")

        // When
        val flow = resilience.asLoadStateFlow<String> {
            call { throw testException }
            retryOn = { false }
            this.circuitBreaker = circuitBreaker
        }

        // Then
        flow.test {
            assertEquals(LoadState.Loading, awaitItem())
            val error = awaitItem()
            assertIs<LoadState.Error>(error)
            assertEquals(testException::class, error.throwable::class)
            assertEquals(testException.message, error.throwable.message)
            awaitComplete()
        }
    }

    @Test
    fun asLoadStateFlow_givenTimeout_whenOperationTimesOut_thenEmitsLoadingAndError() = runTest {
        // Given
        val circuitBreaker = mock<CircuitBreaker>()
        everySuspend { circuitBreaker.tryAcquire() } returns true
        everySuspend { circuitBreaker.onFailure() } returns Unit
        val resilience = DefaultResilience()

        // When
        val flow = resilience.asLoadStateFlow {
            call {
                delay(2000)
                "never"
            }
            timeout = 10.milliseconds
            retryPolicy = RetryPolicy.NONE
            this.circuitBreaker = circuitBreaker
        }

        // Then
        flow.test {
            assertEquals(LoadState.Loading, awaitItem())
            advanceUntilIdle()
            val error = awaitItem()
            assertIs<LoadState.Error>(error)
            assertIs<TimeoutCancellationException>(error.throwable)
            awaitComplete()
        }
    }

    @Test
    fun asLoadStateFlow_givenCircuitOpen_whenCircuitBreakerOpen_thenEmitsLoadingAndErrorWithCircuitBreakerException() =
        runTest {
            // Given
            val circuitBreaker = mock<CircuitBreaker>()
            everySuspend { circuitBreaker.tryAcquire() } returns false
            val resilience = DefaultResilience()

            // When
            val flow = resilience.asLoadStateFlow {
                call { "never" }
                this.circuitBreaker = circuitBreaker
            }

            // Then
            flow.test {
                assertEquals(LoadState.Loading, awaitItem())
                val error = awaitItem()
                assertIs<LoadState.Error>(error)
                assertIs<CircuitBreakerOpenException>(error.throwable)
                awaitComplete()
            }
        }

    @Test
    fun asLoadStateFlow_givenCancellation_whenOperationCancelled_thenEmitsLoadingAndErrorWithCancellationException() =
        runTest {
            // Given
            val circuitBreaker = mock<CircuitBreaker>()
            everySuspend { circuitBreaker.tryAcquire() } returns true
            val resilience = DefaultResilience()

            // When
            val flow = resilience.asLoadStateFlow<String> {
                call { throw CancellationException("test") }
                this.circuitBreaker = circuitBreaker
            }

            // Then
            flow.test {
                assertEquals(LoadState.Loading, awaitItem())
                val error = awaitItem()
                assertIs<LoadState.Error>(error)
                assertIs<CancellationException>(error.throwable)
                awaitComplete()
            }
        }

    @Test
    fun operationEvents_givenInitialization_thenExposesOperationEvents() = runTest {
        // Given
        val fakeEvents = FakeOperationEvents()
        val resilience = DefaultResilience(operationEventsFactory = { fakeEvents })

        // When
        val events = resilience.operationEvents

        // Then
        assertEquals(fakeEvents, events)
    }

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
