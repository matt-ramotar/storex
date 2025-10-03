package dev.mattramotar.storex.resilience.internal

import app.cash.turbine.test
import dev.mattramotar.storex.resilience.CircuitBreaker
import dev.mattramotar.storex.resilience.LoadState
import dev.mattramotar.storex.resilience.OperationResult
import dev.mattramotar.storex.resilience.RetryPolicy
import dev.mattramotar.storex.resilience.dsl.call
import dev.mattramotar.storex.resilience.dsl.flow
import dev.mattramotar.storex.resilience.internal.utils.RecordingResilience
import dev.mattramotar.storex.resilience.internal.utils.assertThat
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ResilienceDslTest {

    @Test
    fun call_givenCustomOverrides_whenExecuted_thenConfiguresScope() = runTest {
        // Given
        val resilience = RecordingResilience()
        val circuitBreaker = mock<CircuitBreaker>()
        val timeout = 3.seconds
        val retryPolicy = RetryPolicy { attempt -> if (attempt == 0) 1.seconds else null }
        val retryPredicate: (Throwable) -> Boolean = { it is IllegalArgumentException }
        var invocationCount = 0

        // When
        val result = resilience.call(
            timeout = timeout,
            retryPolicy = retryPolicy,
            circuitBreaker = circuitBreaker,
            retryOn = retryPredicate,
        ) {
            invocationCount++
            "value"
        }

        // Then
        val scope = resilience.lastScopeTyped<String>()
        val success = result as? OperationResult.Success<String>
            ?: error("Expected OperationResult.Success but was $result")
        assertThat(invocationCount).isEqualTo(1)
        assertThat(success.value).isEqualTo("value")
        assertThat(scope.timeout).isEqualTo(timeout)
        assertThat(scope.retryPolicy).isSameInstanceAs(retryPolicy)
        assertThat(scope.circuitBreaker).isSameInstanceAs(circuitBreaker)
        assertThat(scope.retryOn(IllegalArgumentException())).isEqualTo(true)
        assertThat(scope.retryOn(RuntimeException())).isEqualTo(false)
        assertThat(scope.call).isNotNull()
    }

    @Test
    fun flow_givenCustomOverrides_whenCollected_thenEmitsLoadingAndSuccess() = runTest {
        // Given
        val resilience = RecordingResilience()
        val circuitBreaker = mock<CircuitBreaker>()
        val timeout = 7.seconds
        val retryPolicy = RetryPolicy { attempt -> if (attempt < 2) 2.seconds else null }
        val retryPredicate: (Throwable) -> Boolean = { it is IllegalArgumentException }
        var invocationCount = 0

        // When
        val flow = resilience.flow(
            timeout = timeout,
            retryPolicy = retryPolicy,
            circuitBreaker = circuitBreaker,
            retryOn = retryPredicate,
        ) {
            invocationCount++
            "payload"
        }

        // Then
        flow.test {
            assertThat(awaitItem()).isEqualTo(LoadState.Loading)
            val next = awaitItem()
            val success = next as? LoadState.Success<String>
                ?: error("Expected LoadState.Success but was $next")
            assertThat(success.data).isEqualTo("payload")
            awaitComplete()
        }
        val scope = resilience.lastScopeTyped<String>()
        assertThat(invocationCount).isEqualTo(1)
        assertThat(scope.timeout).isEqualTo(timeout)
        assertThat(scope.retryPolicy).isSameInstanceAs(retryPolicy)
        assertThat(scope.circuitBreaker).isSameInstanceAs(circuitBreaker)
        assertThat(scope.retryOn(IllegalArgumentException())).isEqualTo(true)
        assertThat(scope.retryOn(RuntimeException())).isEqualTo(false)
        assertThat(scope.call).isNotNull()
    }
}


