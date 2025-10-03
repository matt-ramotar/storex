package dev.mattramotar.storex.resilience.internal

import app.cash.turbine.test
import dev.mattramotar.storex.resilience.LoadState
import dev.mattramotar.storex.resilience.OperationResult
import dev.mattramotar.storex.resilience.internal.utils.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull


@OptIn(ExperimentalCoroutinesApi::class)
class PassThroughResilienceTest {

    @Test
    fun execute_givenSuccessfulOperation_whenCallCompletes_thenReturnsSuccess() = runTest {
        // Given
        val expectedValue = "test result"

        // When
        val result = PassThroughResilience.execute {
            call { expectedValue }
        }

        // Then
        assertIs<OperationResult.Success<String>>(result)
        assertEquals(expectedValue, result.value)
    }

    @Test
    fun execute_givenTimeoutCancellationException_whenCallThrows_thenReturnsError() = runTest {
        // Given
        val timeoutException = TimeoutCancellationException()

        // When
        val result = PassThroughResilience.execute<String> {
            call { throw timeoutException }
        }

        // Then
        assertIs<OperationResult.Failure.Error>(result)
        assertEquals(timeoutException, result.throwable)
    }

    @Test
    fun execute_givenCancellationException_whenCallThrows_thenReturnsCancelled() = runTest {
        // Given
        val cancellationException = CancellationException("cancelled")

        // When
        val result = PassThroughResilience.execute<String> {
            call { throw cancellationException }
        }

        // Then
        assertEquals(OperationResult.Failure.Cancelled, result)
    }

    @Test
    fun execute_givenGenericThrowable_whenCallThrows_thenReturnsError() = runTest {
        // Given
        val genericException = RuntimeException("generic error")

        // When
        val result = PassThroughResilience.execute<String> {
            call { throw genericException }
        }

        // Then
        assertIs<OperationResult.Failure.Error>(result)
        assertEquals(genericException, result.throwable)
    }

    @Test
    fun asLoadStateFlow_givenSuccess_whenOperationSucceeds_thenEmitsLoadingAndSuccess() = runTest {
        // Given
        val expectedValue = "data"

        // When
        val flow = PassThroughResilience.asLoadStateFlow<String> {
            call { expectedValue }
        }

        // Then
        flow.test {
            assertEquals(LoadState.Loading, awaitItem())
            val success = awaitItem()
            assertIs<LoadState.Success<String>>(success)
            assertEquals(expectedValue, success.data)
            awaitComplete()
        }
    }

    @Test
    fun asLoadStateFlow_givenCancellation_whenOperationCancelled_thenEmitsLoadingAndErrorWithCancellationException() =
        runTest {
            // Given
            val cancellationException = CancellationException("cancelled")

            // When
            val flow = PassThroughResilience.asLoadStateFlow<String> {
                call { throw cancellationException }
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
    fun asLoadStateFlow_givenError_whenOperationFails_thenEmitsLoadingAndErrorWithThrowable() = runTest {
        // Given
        val runtimeException = RuntimeException("failure")

        // When
        val flow = PassThroughResilience.asLoadStateFlow<String> {
            call { throw runtimeException }
        }

        // Then
        flow.test {
            assertEquals(LoadState.Loading, awaitItem())
            val error = awaitItem()
            assertIs<LoadState.Error>(error)
            assertEquals(runtimeException, error.throwable)
            awaitComplete()
        }
    }

    @Test
    fun operationEvents_givenAccess_thenReturnsNonNullOperationEvents() = runTest {
        // When
        val events = PassThroughResilience.operationEvents

        // Then
        assertNotNull(events)
        assertNotNull(events.events)
    }
}
