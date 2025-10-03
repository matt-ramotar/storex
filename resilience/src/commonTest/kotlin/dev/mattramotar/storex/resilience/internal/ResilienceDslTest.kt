package dev.mattramotar.storex.resilience.internal

import app.cash.turbine.test
import dev.mattramotar.storex.resilience.CircuitBreaker
import dev.mattramotar.storex.resilience.LoadState
import dev.mattramotar.storex.resilience.OperationEvent
import dev.mattramotar.storex.resilience.OperationEvents
import dev.mattramotar.storex.resilience.OperationResult
import dev.mattramotar.storex.resilience.Resilience
import dev.mattramotar.storex.resilience.RetryDefaults
import dev.mattramotar.storex.resilience.RetryPolicy
import dev.mattramotar.storex.resilience.dsl.OperationSpecScope
import dev.mattramotar.storex.resilience.dsl.call
import dev.mattramotar.storex.resilience.dsl.flow
import dev.mattramotar.storex.resilience.internal.utils.RecordingOperationSpecScope
import dev.mattramotar.storex.resilience.internal.utils.assertThat
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ResilienceDslTest {

    @Test
    fun call_givenDefaults_whenExecuted_thenAppliesLibraryDefaults() = runTest {
        // Given
        val resilience = RecordingResilience()
        val sampleError = IllegalStateException("boom")
        var invocationCount = 0

        // When
        val result = resilience.call {
            invocationCount++
            "value"
        }

        // Then
        val scope = resilience.lastScopeTyped<String>()
        val success = result.requireSuccess()
        assertThat(invocationCount).isEqualTo(1)
        assertThat(success.value).isEqualTo("value")
        assertThat(scope.timeout).isEqualTo(10.seconds)
        assertThat(scope.retryPolicy).isNotSameInstanceAs(RecordingOperationSpecScope.INITIAL_RETRY_POLICY)
        assertThat(scope.retryPolicy.nextDelay(0)).isNotNull()
        assertThat(scope.circuitBreaker).isNotNull()
        assertThat(scope.retryOn(sampleError)).isEqualTo(RetryDefaults.retryOn(sampleError))
        assertThat(scope.retryOn).isNotSameInstanceAs(RecordingOperationSpecScope.INITIAL_RETRY_ON)
        assertThat(scope.call).isNotNull()
    }

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
        val success = result.requireSuccess()
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
    fun flow_givenDefaults_whenCollected_thenAppliesLibraryDefaults() = runTest {
        // Given
        val resilience = RecordingResilience()
        val sampleError = IllegalStateException("boom")
        var invocationCount = 0

        // When
        val flow = resilience.flow {
            invocationCount++
            "value"
        }

        // Then
        flow.test {
            assertThat(awaitItem()).isEqualTo(LoadState.Loading)
            val next = awaitItem()
            val success = next as? LoadState.Success<String>
                ?: error("Expected LoadState.Success but was $next")
            assertThat(success.data).isEqualTo("value")
            awaitComplete()
        }
        val scope = resilience.lastScopeTyped<String>()
        assertThat(invocationCount).isEqualTo(1)
        assertThat(scope.timeout).isEqualTo(10.seconds)
        assertThat(scope.retryPolicy).isNotSameInstanceAs(RecordingOperationSpecScope.INITIAL_RETRY_POLICY)
        assertThat(scope.retryPolicy.nextDelay(0)).isNotNull()
        assertThat(scope.circuitBreaker).isNotNull()
        assertThat(scope.retryOn(sampleError)).isEqualTo(RetryDefaults.retryOn(sampleError))
        assertThat(scope.retryOn).isNotSameInstanceAs(RecordingOperationSpecScope.INITIAL_RETRY_ON)
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

    private fun <T> OperationResult<T>.requireSuccess(): OperationResult.Success<T> {
        return this as? OperationResult.Success<T>
            ?: error("Expected OperationResult.Success but was $this")
    }
}

private class RecordingResilience : Resilience {

    override val operationEvents: OperationEvents = object : OperationEvents {
        override val events: MutableSharedFlow<OperationEvent> = MutableSharedFlow()
    }

    var lastScope: RecordingOperationSpecScope<*>? = null
        private set

    override suspend fun <T> execute(configure: OperationSpecScope<T>.() -> Unit): OperationResult<T> {
        val scope = RecordingOperationSpecScope<T>().apply(configure)
        lastScope = scope
        val value = scope.requireCall()
        return OperationResult.Success(value)
    }

    override fun <T> asLoadStateFlow(configure: OperationSpecScope<T>.() -> Unit): Flow<LoadState<T>> {
        return kotlinx.coroutines.flow.flow {
            val scope = RecordingOperationSpecScope<T>().apply(configure)
            lastScope = scope
            emit(LoadState.Loading)
            val value = scope.requireCall()
            emit(LoadState.Success(value))
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> lastScopeTyped(): RecordingOperationSpecScope<T> {
        val scope = lastScope as? RecordingOperationSpecScope<T>
        return scope ?: error("Expected RecordingOperationSpecScope<${T::class}> but was $lastScope")
    }
}

