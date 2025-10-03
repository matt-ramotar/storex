package dev.mattramotar.storex.resilience.internal.utils

import dev.mattramotar.storex.resilience.CircuitBreaker
import dev.mattramotar.storex.resilience.RetryPolicy
import dev.mattramotar.storex.resilience.dsl.OperationSpecScope
import kotlin.test.assertSame
import kotlin.time.Duration

internal class RecordingOperationSpecScope<T> : OperationSpecScope<T> {
    override var call: (suspend () -> T)? = null
    override var circuitBreaker: CircuitBreaker? = null
    override var timeout: Duration = Duration.ZERO
    override var retryPolicy: RetryPolicy = RetryPolicy { null }
    override var retryOn: (Throwable) -> Boolean = { false }
    override var failureThreshold: Int = 0
    override var openTTL: Duration = Duration.ZERO
    override var probeQuota: Int = 0

    override fun call(block: suspend () -> T) {
        this.call = block
    }

    suspend fun requireCall(): T {
        val current = call ?: error("call {} is required!")
        return current()
    }
}

fun <T> assertThat(actual: T) = AssertionSubject(actual)

class AssertionSubject<T>(private val actual: T) {
    fun isEqualTo(expected: T) {
        kotlin.test.assertEquals(expected, actual)
    }

    fun isSameInstanceAs(expected: T) {
        assertSame(actual, expected)
    }

    fun isNotNull() {
        kotlin.test.assertNotNull(actual)
    }
}
