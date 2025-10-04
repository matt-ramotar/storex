package dev.mattramotar.storex.store.concurrency

import dev.mattramotar.storex.store.ByIdKey
import dev.mattramotar.storex.store.EntityId
import dev.mattramotar.storex.store.SingleFlight
import dev.mattramotar.storex.store.StoreNamespace
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for TASK-003: SingleFlight double-check lock fix
 *
 * Validates that:
 * - Concurrent requests to the same key are coalesced
 * - Atomic get-or-create prevents race conditions
 * - Identity check in finally block works correctly
 * - CancellationException is properly propagated
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SingleFlightTest {

    private val testNamespace = StoreNamespace("test")
    private val testKey = ByIdKey(testNamespace, EntityId("User", "123"))

    @Test
    fun launch_whenConcurrentRequests_thenOnlyOneExecution() = runTest {
        // Given
        var executionCount = 0
        val singleFlight = SingleFlight<ByIdKey, String>()

        // When - Launch 100 concurrent requests for the same key
        val jobs = (1..100).map {
            async {
                singleFlight.launch(this, testKey) {
                    executionCount++
                    delay(50.milliseconds)
                    "result"
                }.await()
            }
        }

        val results = jobs.awaitAll()

        // Then - Only one execution, all get same result
        assertEquals(1, executionCount, "Block should execute exactly once")
        assertEquals(100, results.size)
        assertTrue(results.all { it == "result" })
    }

    @Test
    fun launch_whenDifferentKeys_thenIndependentExecution() = runTest {
        // Given
        var executionCount = 0
        val singleFlight = SingleFlight<ByIdKey, String>()
        val keys = (1..10).map { ByIdKey(testNamespace, EntityId("User", "$it")) }

        // When - Launch concurrent requests for different keys
        val jobs = keys.map { key ->
            async {
                singleFlight.launch(this, key) {
                    executionCount++
                    delay(10.milliseconds)
                    "result-$key"
                }.await()
            }
        }

        val results = jobs.awaitAll()

        // Then - Each key executes independently
        assertEquals(10, executionCount, "Each key should execute once")
        assertEquals(10, results.size)
        assertEquals(10, results.toSet().size, "Each result should be unique")
    }

    @Test
    fun launch_whenBlockThrowsException_thenAllWaitersGetException() = runTest {
        // Given
        val singleFlight = SingleFlight<ByIdKey, String>()
        val testException = IllegalStateException("Test error")

        // When - Launch concurrent requests that will fail
        val jobs = (1..50).map {
            async {
                try {
                    singleFlight.launch(this, testKey) {
                        delay(50.milliseconds)
                        throw testException
                    }.await()
                    null
                } catch (e: Exception) {
                    e
                }
            }
        }

        val results = jobs.awaitAll()

        // Then - All should receive the same exception
        assertEquals(50, results.size)
        assertTrue(results.all { it === testException || it?.cause === testException })
    }

    @Test
    fun launch_whenCancelled_thenCancellationPropagates() = runTest {
        // Given
        val singleFlight = SingleFlight<ByIdKey, String>()
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()

        // When - Launch a long-running operation and cancel it
        val job = launch {
            try {
                singleFlight.launch(this, testKey) {
                    started.complete(Unit)
                    delay(10.seconds)
                    "never-completes"
                }.await()
            } catch (e: CancellationException) {
                cancelled.complete(Unit)
                throw e
            }
        }

        started.await()
        job.cancel()
        job.join()

        // Then - Cancellation should propagate
        assertTrue(cancelled.isCompleted)
    }

    @Test
    fun launch_whenSequentialRequests_thenEachExecutes() = runTest {
        // Given
        var executionCount = 0
        val singleFlight = SingleFlight<ByIdKey, Int>()

        // When - Sequential requests (not concurrent)
        val result1 = singleFlight.launch(this, testKey) {
            executionCount++
            delay(10.milliseconds)
            executionCount
        }.await()

        val result2 = singleFlight.launch(this, testKey) {
            executionCount++
            delay(10.milliseconds)
            executionCount
        }.await()

        // Then - Each request executes separately
        assertEquals(1, result1)
        assertEquals(2, result2)
        assertEquals(2, executionCount)
    }

    @Test
    fun launch_whenRapidSuccessiveRequests_thenCorrectCoalescing() = runTest {
        // Given
        var executionCount = 0
        val singleFlight = SingleFlight<ByIdKey, Int>()

        // When - 3 waves of concurrent requests with delays between
        val wave1 = (1..30).map {
            async {
                singleFlight.launch(this, testKey) {
                    executionCount++
                    delay(100.milliseconds)
                    1
                }.await()
            }
        }

        delay(50.milliseconds) // Start wave2 while wave1 is still running

        val wave2 = (1..30).map {
            async {
                singleFlight.launch(this, testKey) {
                    executionCount++
                    delay(100.milliseconds)
                    2
                }.await()
            }
        }

        wave1.awaitAll()
        wave2.awaitAll()

        delay(150.milliseconds) // Wait for wave2 to complete

        val wave3 = (1..30).map {
            async {
                singleFlight.launch(this, testKey) {
                    executionCount++
                    delay(100.milliseconds)
                    3
                }.await()
            }
        }

        wave3.awaitAll()

        // Then - Should have 3 executions (one per wave)
        // Wave1 and Wave2 might coalesce into 1 or 2 executions depending on timing
        // Wave3 should be separate
        assertTrue(executionCount in 2..3, "Expected 2-3 executions, got $executionCount")
    }

    @Test
    fun launch_whenIdentityCheckInFinally_thenNoPreemptiveRemoval() = runTest {
        // Given
        var execution1Started = false
        var execution2Started = false
        val singleFlight = SingleFlight<ByIdKey, String>()
        val execution1Ready = CompletableDeferred<Unit>()
        val continueExecution1 = CompletableDeferred<Unit>()

        // When - Start first execution
        val job1 = async {
            singleFlight.launch(this, testKey) {
                execution1Started = true
                execution1Ready.complete(Unit)
                continueExecution1.await()
                "result1"
            }.await()
        }

        execution1Ready.await()

        // Try to start second execution before first completes
        val job2 = async {
            delay(10.milliseconds) // Small delay to ensure job1 has the deferred
            singleFlight.launch(this, testKey) {
                execution2Started = true
                "result2"
            }.await()
        }

        delay(50.milliseconds)
        continueExecution1.complete(Unit)

        val result1 = job1.await()
        val result2 = job2.await()

        // Then - Both should get result1 (job2 should wait for job1)
        assertTrue(execution1Started)
        assertFalse(execution2Started, "Second execution should not start; should reuse first")
        assertEquals("result1", result1)
        assertEquals("result1", result2)
    }

    @Test
    fun launch_stressTest_thenNoRaceConditions() = runTest(timeout = 30.seconds) {
        // Given
        val singleFlight = SingleFlight<ByIdKey, String>()
        val keys = (1..50).map { ByIdKey(testNamespace, EntityId("User", "$it")) }
        val executionCounts = mutableMapOf<ByIdKey, Int>()
        val mutex = kotlinx.coroutines.sync.Mutex()

        // When - High concurrency stress test
        val jobs = (1..500).map { workerId ->
            async(Dispatchers.Default) {
                val key = keys.random()
                singleFlight.launch(this, key) {
                    mutex.withLock {
                        executionCounts[key] = (executionCounts[key] ?: 0) + 1
                    }
                    delay((1..10).random().milliseconds)
                    "worker-$workerId-key-$key"
                }.await()
            }
        }

        jobs.awaitAll()

        // Then - Each key should have reasonable execution count
        // With perfect coalescing, each key would execute once
        // With timing variations, some keys might execute a few times
        val totalExecutions = executionCounts.values.sum()
        assertTrue(totalExecutions < 200, "Total executions $totalExecutions suggests poor coalescing")
    }

    @Test
    fun launch_whenMixedSuccessAndFailure_thenCorrectBehavior() = runTest {
        // Given
        val singleFlight = SingleFlight<ByIdKey, String>()
        var attempt = 0

        // When - First attempt fails, second succeeds
        val result1 = try {
            singleFlight.launch(this, testKey) {
                attempt++
                throw IllegalStateException("First attempt fails")
            }.await()
        } catch (e: Exception) {
            "failed"
        }

        val result2 = singleFlight.launch(this, testKey) {
            attempt++
            "success"
        }.await()

        // Then - Both attempts should execute (failure doesn't block future requests)
        assertEquals("failed", result1)
        assertEquals("success", result2)
        assertEquals(2, attempt)
    }

    @Test
    fun launch_whenCancellationException_thenPropagatesCorrectly() = runTest {
        // Given
        val singleFlight = SingleFlight<ByIdKey, String>()

        // When - Block throws CancellationException
        assertFailsWith<CancellationException> {
            singleFlight.launch(this, testKey) {
                throw CancellationException("Explicit cancellation")
            }.await()
        }

        // Then - Subsequent request should work normally
        val result = singleFlight.launch(this, testKey) {
            "recovered"
        }.await()

        assertEquals("recovered", result)
    }
}
