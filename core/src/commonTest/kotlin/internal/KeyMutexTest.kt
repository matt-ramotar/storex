package dev.mattramotar.storex.core.internal

import dev.mattramotar.storex.core.KeyMutex
import dev.mattramotar.storex.core.utils.TEST_KEY_1
import dev.mattramotar.storex.core.utils.TEST_KEY_2
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class KeyMutexTest {

    @Test
    fun forKey_givenNewKey_thenCreatesMutex() = runTest {
        // Given
        val keyMutex = KeyMutex<String>()

        // When
        val mutex = keyMutex.forKey("key1")

        // Then
        assertTrue(mutex.tryLock(), "Newly created mutex should be unlocked")
        mutex.unlock()
    }

    @Test
    fun forKey_givenSameKey_thenReturnsSameMutex() = runTest {
        // Given
        val keyMutex = KeyMutex<String>()

        // When
        val mutex1 = keyMutex.forKey("key1")
        val mutex2 = keyMutex.forKey("key1")

        // Then - both are same mutex (locking one locks the other)
        mutex1.lock()
        val tryLock = mutex2.tryLock()
        mutex1.unlock()

        assertEquals(false, tryLock, "Should be same mutex (already locked)")
    }

    @Test
    fun forKey_givenDifferentKeys_thenCreatesSeparateMutexes() = runTest {
        // Given
        val keyMutex = KeyMutex<String>()

        // When
        val mutex1 = keyMutex.forKey("key1")
        val mutex2 = keyMutex.forKey("key2")

        // Then - independent mutexes
        mutex1.lock()
        val canLock2 = mutex2.tryLock()
        mutex1.unlock()
        if (canLock2) mutex2.unlock()

        assertTrue(canLock2, "Different keys should have independent mutexes")
    }

    @Test
    fun forKey_givenPerKeyLocking_thenSerializesAccessPerKey() = runTest {
        // Given
        val keyMutex = KeyMutex<String>()
        val key1Operations = mutableListOf<Int>()
        val key2Operations = mutableListOf<Int>()
        val key1CanStart = CompletableDeferred<Unit>()
        val key2CanStart = CompletableDeferred<Unit>()

        // When - concurrent operations on different keys
        val job1 = async {
            keyMutex.forKey("key1").withLock {
                key1CanStart.complete(Unit)
                delay(10)
                key1Operations.add(1)
            }
        }

        val job2 = async {
            keyMutex.forKey("key2").withLock {
                key2CanStart.complete(Unit)
                delay(10)
                key2Operations.add(2)
            }
        }

        // Wait for both to start (proves they run concurrently)
        key1CanStart.await()
        key2CanStart.await()

        advanceUntilIdle()
        job1.await()
        job2.await()

        // Then - both completed (different keys don't block each other)
        assertEquals(1, key1Operations.size)
        assertEquals(1, key2Operations.size)
    }

    @Test
    fun forKey_givenSameKey_thenSerializesAccess() = runTest {
        // Given
        val keyMutex = KeyMutex<String>()
        val operations = mutableListOf<Int>()
        val firstStarted = CompletableDeferred<Unit>()
        val firstCanFinish = CompletableDeferred<Unit>()

        // When - concurrent operations on same key
        val job1 = async {
            keyMutex.forKey("key1").withLock {
                operations.add(1)
                firstStarted.complete(Unit)
                firstCanFinish.await()
            }
        }

        firstStarted.await()

        val job2 = async {
            keyMutex.forKey("key1").withLock {
                operations.add(2)
            }
        }

        // Let first job complete
        firstCanFinish.complete(Unit)
        advanceUntilIdle()

        job1.await()
        job2.await()

        // Then - operations serialized (1 before 2)
        assertEquals(listOf(1, 2), operations)
    }

    @Test
    fun forKey_whenAtMaxSize_thenEvictsLRU() = runTest {
        // Given
        val keyMutex = KeyMutex<String>(maxSize = 2)

        // When - create mutexes for 3 keys
        val mutex1 = keyMutex.forKey("key1")
        val mutex2 = keyMutex.forKey("key2")
        val mutex3 = keyMutex.forKey("key3") // Should evict key1

        // Get key1 again (should create new mutex since evicted)
        val mutex1Again = keyMutex.forKey("key1")

        // Then - mutex1Again is a different instance (proved by independent locking)
        mutex1.lock()
        val canLock = mutex1Again.tryLock()
        mutex1.unlock()
        if (canLock) mutex1Again.unlock()

        assertTrue(canLock, "Should be different mutex after eviction")
    }

    @Test
    fun forKey_whenAtMaxSizeWithExistingKey_thenDoesNotEvict() = runTest {
        // Given
        val keyMutex = KeyMutex<String>(maxSize = 2)
        keyMutex.forKey("key1")
        keyMutex.forKey("key2")

        // When - access existing key (should not trigger eviction)
        val mutex1 = keyMutex.forKey("key1")
        val mutex1Again = keyMutex.forKey("key1")

        // Then - same mutex returned
        mutex1.lock()
        val tryLock = mutex1Again.tryLock()
        mutex1.unlock()

        assertEquals(false, tryLock, "Should be same mutex (no eviction)")
    }

    @Test
    fun forKey_stressTest_with1000Keys() = runTest {
        // Given
        val keyMutex = KeyMutex<String>(maxSize = 1000)
        val successCount = mutableListOf<Int>()

        // When - 1000 keys, each with concurrent operations
        val jobs = (1..1000).flatMap { i ->
            listOf(
                async {
                    keyMutex.forKey("key$i").withLock {
                        delay(1)
                        successCount.add(i)
                    }
                },
                async {
                    keyMutex.forKey("key$i").withLock {
                        delay(1)
                        successCount.add(i)
                    }
                }
            )
        }

        advanceUntilIdle()
        jobs.forEach { it.await() }

        // Then - all operations completed
        assertEquals(2000, successCount.size)
    }

    @Test
    fun forKey_concurrentAccess_thenThreadSafe() = runTest {
        // Given
        val keyMutex = KeyMutex<String>()
        val sharedCounter = mutableListOf<Int>()

        // When - concurrent increments protected by same mutex
        val jobs = (1..100).map { i ->
            async {
                keyMutex.forKey("counter").withLock {
                    val current = sharedCounter.size
                    delay(1) // Simulate work
                    sharedCounter.add(current + 1)
                }
            }
        }

        advanceUntilIdle()
        jobs.forEach { it.await() }

        // Then - all increments happened safely
        assertEquals(100, sharedCounter.size)
    }

    @Test
    fun forKey_withStoreKeys_thenWorks() = runTest {
        // Given
        val keyMutex = KeyMutex<dev.mattramotar.storex.core.StoreKey>()
        val operations = mutableListOf<String>()

        // When - use actual StoreKey types
        val job1 = async {
            keyMutex.forKey(TEST_KEY_1).withLock {
                operations.add("op1")
            }
        }

        val job2 = async {
            keyMutex.forKey(TEST_KEY_2).withLock {
                operations.add("op2")
            }
        }

        advanceUntilIdle()
        job1.await()
        job2.await()

        // Then
        assertEquals(2, operations.size)
    }

    @Test
    fun forKey_withSameStoreKey_thenSerializes() = runTest {
        // Given
        val keyMutex = KeyMutex<dev.mattramotar.storex.core.StoreKey>()
        val operations = mutableListOf<Int>()
        val firstStarted = CompletableDeferred<Unit>()
        val firstCanFinish = CompletableDeferred<Unit>()

        // When - concurrent operations on same StoreKey
        val job1 = async {
            keyMutex.forKey(TEST_KEY_1).withLock {
                operations.add(1)
                firstStarted.complete(Unit)
                firstCanFinish.await()
            }
        }

        firstStarted.await()

        val job2 = async {
            keyMutex.forKey(TEST_KEY_1).withLock {
                operations.add(2)
            }
        }

        firstCanFinish.complete(Unit)
        advanceUntilIdle()

        job1.await()
        job2.await()

        // Then - serialized
        assertEquals(listOf(1, 2), operations)
    }
}
