package dev.mattramotar.storex.resilience.internal

import dev.mattramotar.storex.resilience.CircuitBreaker
import dev.mattramotar.storex.resilience.RetryPolicy
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds



class RealOperationSpecScopeTest {

    @Test
    fun build_givenCallNotSet_whenBuildInvoked_thenThrowsIllegalStateException() {
        // Given
        val scope = RealOperationSpecScope<String>()

        // When/Then
        val exception = assertFailsWith<IllegalStateException> {
            scope.build()
        }
        assertEquals("call {} is required!", exception.message)
    }

    @Test
    fun build_givenCallSet_whenBuildInvoked_thenReturnsOperationSpecWithAllProperties() {
        // Given
        val scope = RealOperationSpecScope<String>()
        val testCall: suspend () -> String = { "test" }
        val testCircuitBreaker = mock<CircuitBreaker>()
        val testRetryPolicy = RetryPolicy.NONE
        val testRetryOn: (Throwable) -> Boolean = { false }

        scope.call = testCall
        scope.circuitBreaker = testCircuitBreaker
        scope.timeout = 5.seconds
        scope.retryPolicy = testRetryPolicy
        scope.retryOn = testRetryOn
        scope.failureThreshold = 5
        scope.openTTL = 60.seconds
        scope.probeQuota = 3

        // When
        val spec = scope.build()

        // Then
        assertEquals(testCall, spec.call)
        assertEquals(testCircuitBreaker, spec.circuitBreaker)
        assertEquals(5.seconds, spec.timeout)
        assertEquals(testRetryPolicy, spec.retryPolicy)
        assertEquals(testRetryOn, spec.retryOn)
        assertEquals(5, spec.failureThreshold)
        assertEquals(60.seconds, spec.openTTL)
        assertEquals(3, spec.probeQuota)
    }

    @Test
    fun call_givenBlock_whenInvoked_thenSetsCallProperty() {
        // Given
        val scope = RealOperationSpecScope<String>()
        val testBlock: suspend () -> String = { "result" }

        // When
        scope.call(testBlock)

        // Then
        assertEquals(testBlock, scope.call)
    }

    @Test
    fun properties_givenDefaults_whenInitialized_thenHaveCorrectDefaultValues() {
        // When
        val scope = RealOperationSpecScope<String>()

        // Then
        assertNull(scope.call)
        assertNull(scope.circuitBreaker)
        assertEquals(10.seconds, scope.timeout)
        assertNotNull(scope.retryPolicy)
        assertNotNull(scope.retryOn)
        assertEquals(3, scope.failureThreshold)
        assertEquals(30.seconds, scope.openTTL)
        assertEquals(1, scope.probeQuota)
    }

    @Test
    fun properties_givenCustomValues_whenSet_thenCanBeRetrieved() {
        // Given
        val scope = RealOperationSpecScope<Int>()
        val testCall: suspend () -> Int = { 42 }
        val testCircuitBreaker = mock<CircuitBreaker>()
        val testRetryPolicy = RetryPolicy.NONE
        val testRetryOn: (Throwable) -> Boolean = { true }

        // When
        scope.call = testCall
        scope.circuitBreaker = testCircuitBreaker
        scope.timeout = 100.milliseconds
        scope.retryPolicy = testRetryPolicy
        scope.retryOn = testRetryOn
        scope.failureThreshold = 10
        scope.openTTL = 120.seconds
        scope.probeQuota = 5

        // Then
        assertEquals(testCall, scope.call)
        assertEquals(testCircuitBreaker, scope.circuitBreaker)
        assertEquals(100.milliseconds, scope.timeout)
        assertEquals(testRetryPolicy, scope.retryPolicy)
        assertEquals(testRetryOn, scope.retryOn)
        assertEquals(10, scope.failureThreshold)
        assertEquals(120.seconds, scope.openTTL)
        assertEquals(5, scope.probeQuota)
    }
}
