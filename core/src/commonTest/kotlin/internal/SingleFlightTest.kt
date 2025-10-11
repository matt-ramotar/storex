package dev.mattramotar.storex.core.internal

import dev.mattramotar.storex.core.SingleFlight
import dev.mattramotar.storex.core.utils.TEST_KEY_1
import dev.mattramotar.storex.core.utils.TEST_KEY_2
import dev.mattramotar.storex.core.utils.TestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SingleFlightTest {

    @Test
    fun launch_givenSingleRequest_thenExecutesBlock() = runTest {
        // Given
        val singleFlight = SingleFlight<String, String>()
        var executions = 0

        // When
        val result = singleFlight.launch(backgroundScope, "key1") {
            executions++
            "result"
        }.await()

        // Then
        assertEquals("result", result)
        assertEquals(1, executions)
    }

    @Test
    fun launch_givenConcurrentSameKey_thenExecutesOnce() = runTest {
        // Given
        val singleFlight = SingleFlight<String, String>()
        var executions = 0
        val blockStarted = CompletableDeferred<Unit>()
        val blockCanFinish = CompletableDeferred<Unit>()

        // When - launch 100 concurrent requests for same key
        val jobs = (1..100).map {
            async {
                singleFlight.launch(backgroundScope, "key1") {
                    executions++
                    blockStarted.complete(Unit)
                    blockCanFinish.await()
                    "result"
                }.await()
            }
        }

        // Wait for block to start
        blockStarted.await()
        advanceUntilIdle()

        // Complete the block
        blockCanFinish.complete(Unit)

        // Wait for all jobs
        val results = jobs.map { it.await() }

        // Then - all requests get same result, block executed only once
        assertTrue(results.all { it == "result" })
        assertEquals(1, executions, "Block should execute only once despite 100 concurrent requests")
    }

    @Test
    fun launch_givenDifferentKeys_thenExecutesSeparately() = runTest {
        // Given
        val singleFlight = SingleFlight<String, String>()
        var key1Executions = 0
        var key2Executions = 0

        // When - concurrent requests for different keys
        val job1 = async {
            singleFlight.launch(backgroundScope, "key1") {
                key1Executions++
                "result1"
            }.await()
        }

        val job2 = async {
            singleFlight.launch(backgroundScope, "key2") {
                key2Executions++
                "result2"
            }.await()
        }

        advanceUntilIdle()

        // Then
        assertEquals("result1", job1.await())
        assertEquals("result2", job2.await())
        assertEquals(1, key1Executions)
        assertEquals(1, key2Executions)
    }

    @Test
    fun launch_givenBlockThrows_thenAllWaitersGetException() = runTest {
        // Given
        val singleFlight = SingleFlight<String, String>()
        val testException = TestException("Test failure")

        // When - multiple concurrent requests, block throws
        val jobs = (1..10).map {
            async {
                try {
                    singleFlight.launch(backgroundScope, "key1") {
                        throw testException
                    }.await()
                    null // Should not reach here
                } catch (e: TestException) {
                    e
                }
            }
        }

        advanceUntilIdle()
        val exceptions = jobs.map { it.await() }

        // Then - all waiters get the same exception
        assertEquals(10, exceptions.size)
        assertTrue(exceptions.all { it?.message == testException.message })
    }

    @Test
    fun launch_givenCancellation_thenPropagates() = runTest {
        // Given
        val singleFlight = SingleFlight<String, String>()

        // When
        assertFailsWith<CancellationException> {
            singleFlight.launch(backgroundScope, "key1") {
                throw CancellationException("Cancelled")
            }.await()
        }
    }

    @Test
    fun launch_givenCancellation_thenCancelsAllWaiters() = runTest {
        // Given
        val singleFlight = SingleFlight<String, String>()
        val blockStarted = CompletableDeferred<Unit>()
        var cancellationCount = 0

        // When - multiple waiters, block cancels
        val jobs = (1..10).map {
            async {
                try {
                    singleFlight.launch(backgroundScope, "key1") {
                        blockStarted.complete(Unit)
                        throw CancellationException("Cancelled")
                    }.await()
                    null
                } catch (e: CancellationException) {
                    cancellationCount++
                    e
                }
            }
        }

        blockStarted.await()
        advanceUntilIdle()

        val results = jobs.map { it.await() }

        // Then - all waiters cancelled
        assertTrue(results.all { it is CancellationException })
        assertEquals(10, cancellationCount)
    }

    @Test
    fun launch_afterCompletion_thenExecutesNewBlock() = runTest {
        // Given
        val singleFlight = SingleFlight<String, String>()
        var executions = 0

        // When - first request
        val result1 = singleFlight.launch(backgroundScope, "key1") {
            executions++
            "result1"
        }.await()

        // Second request (after first completes)
        val result2 = singleFlight.launch(backgroundScope, "key1") {
            executions++
            "result2"
        }.await()

        // Then - both executed
        assertEquals("result1", result1)
        assertEquals("result2", result2)
        assertEquals(2, executions)
    }

    @Test
    fun launch_cleanupAfterCompletion_thenRemovesKey() = runTest {
        // Given
        val singleFlight = SingleFlight<String, String>()

        // When - execute and complete
        singleFlight.launch(backgroundScope, "key1") { "result" }.await()
        advanceUntilIdle()

        // Execute again - should create new deferred (proves cleanup happened)
        var secondExecution = false
        singleFlight.launch(backgroundScope, "key1") {
            secondExecution = true
            "result2"
        }.await()

        // Then
        assertTrue(secondExecution, "Second execution should happen (proves key was cleaned up)")
    }

    @Test
    fun launch_cleanupAfterFailure_thenRemovesKey() = runTest {
        // Given
        val singleFlight = SingleFlight<String, String>()

        // When - execute with failure
        try {
            singleFlight.launch(backgroundScope, "key1") {
                throw TestException("Fail")
            }.await()
        } catch (e: TestException) {
            // Expected
        }
        advanceUntilIdle()

        // Execute again - should create new deferred
        var secondExecution = false
        val result = singleFlight.launch(backgroundScope, "key1") {
            secondExecution = true
            "success"
        }.await()

        // Then
        assertTrue(secondExecution, "Second execution should happen (proves key was cleaned up)")
        assertEquals("success", result)
    }

    @Test
    fun launch_stressTest_with1000ConcurrentRequests() = runTest {
        // Given
        val singleFlight = SingleFlight<String, Int>()
        var executions = 0

        // When - 1000 concurrent requests
        val jobs = (1..1000).map {
            async {
                singleFlight.launch(backgroundScope, "key1") {
                    executions++
                    delay(1) // Small delay to ensure concurrency
                    42
                }.await()
            }
        }

        advanceUntilIdle()
        val results = jobs.map { it.await() }

        // Then
        assertTrue(results.all { it == 42 })
        assertEquals(1, executions, "Despite 1000 requests, should execute only once")
    }

    @Test
    fun launch_multipleKeysConcurrently_thenIsolated() = runTest {
        // Given
        val singleFlight = SingleFlight<String, String>()
        val key1Executions = mutableListOf<String>()
        val key2Executions = mutableListOf<String>()

        // When - concurrent requests across multiple keys
        val jobs = (1..50).flatMap { i ->
            listOf(
                async {
                    singleFlight.launch(backgroundScope, "key1") {
                        key1Executions.add("execution-$i")
                        "key1-result"
                    }.await()
                },
                async {
                    singleFlight.launch(backgroundScope, "key2") {
                        key2Executions.add("execution-$i")
                        "key2-result"
                    }.await()
                }
            )
        }

        advanceUntilIdle()
        jobs.forEach { it.await() }

        // Then - each key executed only once
        assertEquals(1, key1Executions.size)
        assertEquals(1, key2Executions.size)
    }

    @Test
    fun launch_withStoreKeys_thenWorks() = runTest {
        // Given
        val singleFlight = SingleFlight<dev.mattramotar.storex.core.StoreKey, String>()
        var executions = 0

        // When - use actual StoreKey types
        val jobs = (1..10).map {
            async {
                singleFlight.launch(backgroundScope, TEST_KEY_1) {
                    executions++
                    "user-data"
                }.await()
            }
        }

        advanceUntilIdle()
        val results = jobs.map { it.await() }

        // Then
        assertTrue(results.all { it == "user-data" })
        assertEquals(1, executions)
    }

    @Test
    fun launch_withDifferentStoreKeys_thenSeparateFlights() = runTest {
        // Given
        val singleFlight = SingleFlight<dev.mattramotar.storex.core.StoreKey, String>()
        var key1Executions = 0
        var key2Executions = 0

        // When
        val job1 = async {
            singleFlight.launch(backgroundScope, TEST_KEY_1) {
                key1Executions++
                "data1"
            }.await()
        }

        val job2 = async {
            singleFlight.launch(backgroundScope, TEST_KEY_2) {
                key2Executions++
                "data2"
            }.await()
        }

        advanceUntilIdle()

        // Then
        assertEquals("data1", job1.await())
        assertEquals("data2", job2.await())
        assertEquals(1, key1Executions)
        assertEquals(1, key2Executions)
    }
}
