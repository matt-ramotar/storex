package dev.mattramotar.storex.store.concurrency

import dev.mattramotar.storex.store.*
import dev.mattramotar.storex.store.internal.MemoryCacheImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for TASK-002: Thread safety fix in MemoryCacheImpl
 *
 * Validates that:
 * - Concurrent operations don't cause ConcurrentModificationException
 * - Bounds checking prevents accessOrder.first() crashes
 * - LRU eviction works correctly under concurrent access
 * - Return types correctly indicate new vs existing entries
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoryCacheThreadSafetyTest {

    private val testNamespace = StoreNamespace("test")

    @Test
    fun put_whenConcurrentPutsToSameKey_thenNoException() = runTest {
        // Given
        val cache = MemoryCacheImpl<ByIdKey, String>(maxSize = 100)
        val key = ByIdKey(testNamespace, EntityId("User", "123"))

        // When - 100 concurrent puts to the same key
        val jobs = (1..100).map { iteration ->
            launch(Dispatchers.Default) {
                cache.put(key, "value-$iteration")
            }
        }

        // Then - All should complete without exception
        jobs.joinAll()

        // Value should be one of the concurrent writes
        val value = cache.get(key)
        assertNotNull(value)
        assertTrue(value.startsWith("value-"))
    }

    @Test
    fun put_whenConcurrentPutsToDifferentKeys_thenNoException() = runTest {
        // Given
        val cache = MemoryCacheImpl<ByIdKey, String>(maxSize = 1000)

        // When - 1000 concurrent puts to different keys
        val jobs = (1..1000).map { iteration ->
            launch(Dispatchers.Default) {
                val key = ByIdKey(testNamespace, EntityId("User", "$iteration"))
                cache.put(key, "value-$iteration")
            }
        }

        // Then - All should complete without exception
        jobs.joinAll()

        // Verify all values are present
        val allPresent = (1..1000).all { iteration ->
            val key = ByIdKey(testNamespace, EntityId("User", "$iteration"))
            cache.get(key) == "value-$iteration"
        }
        assertTrue(allPresent)
    }

    @Test
    fun put_whenConcurrentPutsExceedMaxSize_thenLRUEvictionWorks() = runTest {
        // Given
        val maxSize = 10
        val cache = MemoryCacheImpl<ByIdKey, String>(maxSize = maxSize)

        // When - Put more items than maxSize concurrently
        val jobs = (1..50).map { iteration ->
            launch(Dispatchers.Default) {
                val key = ByIdKey(testNamespace, EntityId("User", "$iteration"))
                cache.put(key, "value-$iteration")
                delay(1.milliseconds) // Small delay to ensure ordering
            }
        }

        jobs.joinAll()

        // Then - Cache size should not exceed maxSize
        // Count how many items are in cache
        var count = 0
        for (i in 1..50) {
            val key = ByIdKey(testNamespace, EntityId("User", "$i"))
            if (cache.get(key) != null) count++
        }

        assertTrue(count <= maxSize, "Cache size $count exceeds maxSize $maxSize")

        // The most recently added items should still be present
        val recentKeys = (41..50).map { ByIdKey(testNamespace, EntityId("User", "$it")) }
        val recentPresent = recentKeys.count { cache.get(it) != null }
        assertTrue(recentPresent > 0, "Most recent items should be in cache")
    }

    @Test
    fun put_whenNewEntry_thenReturnsTrue() = runTest {
        // Given
        val cache = MemoryCacheImpl<ByIdKey, String>(maxSize = 100)
        val key = ByIdKey(testNamespace, EntityId("User", "123"))

        // When
        val isNew = cache.put(key, "value")

        // Then
        assertTrue(isNew, "First put should return true for new entry")
    }

    @Test
    fun put_whenExistingEntry_thenReturnsFalse() = runTest {
        // Given
        val cache = MemoryCacheImpl<ByIdKey, String>(maxSize = 100)
        val key = ByIdKey(testNamespace, EntityId("User", "123"))
        cache.put(key, "value1")

        // When
        val isNew = cache.put(key, "value2")

        // Then
        assertFalse(isNew, "Second put should return false for existing entry")
    }

    @Test
    fun put_whenEvictionOccurs_thenNoBoundsException() = runTest {
        // Given - Cache with size 1
        val cache = MemoryCacheImpl<ByIdKey, String>(maxSize = 1)

        // When - Put 2 items
        val key1 = ByIdKey(testNamespace, EntityId("User", "1"))
        val key2 = ByIdKey(testNamespace, EntityId("User", "2"))

        cache.put(key1, "value1")
        cache.put(key2, "value2") // Should evict key1

        // Then - No exception and key1 should be evicted
        assertNull(cache.get(key1))
        assertNotNull(cache.get(key2))
    }

    @Test
    fun concurrent_putGetRemove_thenNoException() = runTest {
        // Given
        val cache = MemoryCacheImpl<ByIdKey, String>(maxSize = 100)
        val keys = (1..50).map { ByIdKey(testNamespace, EntityId("User", "$it")) }

        // When - Concurrent mix of put, get, remove operations
        val jobs = mutableListOf<Job>()

        // Putters
        repeat(10) {
            jobs += launch(Dispatchers.Default) {
                repeat(100) {
                    val key = keys.random()
                    cache.put(key, "value-$it")
                    delay(1.milliseconds)
                }
            }
        }

        // Getters
        repeat(10) {
            jobs += launch(Dispatchers.Default) {
                repeat(100) {
                    val key = keys.random()
                    cache.get(key)
                    delay(1.milliseconds)
                }
            }
        }

        // Removers
        repeat(5) {
            jobs += launch(Dispatchers.Default) {
                repeat(50) {
                    val key = keys.random()
                    cache.remove(key)
                    delay(2.milliseconds)
                }
            }
        }

        // Then - All operations should complete without exception
        jobs.joinAll()
    }

    @Test
    fun clear_whenConcurrentOperations_thenNoException() = runTest {
        // Given
        val cache = MemoryCacheImpl<ByIdKey, String>(maxSize = 100)
        val keys = (1..20).map { ByIdKey(testNamespace, EntityId("User", "$it")) }

        // Pre-populate
        keys.forEach { cache.put(it, "value") }

        // When - Concurrent operations with clear
        val jobs = mutableListOf<Job>()

        // Clearers
        repeat(5) {
            jobs += launch(Dispatchers.Default) {
                delay(10.milliseconds)
                cache.clear()
            }
        }

        // Putters during clear
        repeat(10) {
            jobs += launch(Dispatchers.Default) {
                repeat(20) {
                    val key = keys.random()
                    cache.put(key, "new-value-$it")
                    delay(1.milliseconds)
                }
            }
        }

        // Then - All should complete without exception
        jobs.joinAll()
    }

    @Test
    fun accessOrder_whenConcurrentAccess_thenLRUOrderMaintained() = runTest {
        // Given
        val maxSize = 5
        val cache = MemoryCacheImpl<ByIdKey, String>(maxSize = maxSize)
        val keys = (1..5).map { ByIdKey(testNamespace, EntityId("User", "$it")) }

        // Put initial items
        keys.forEach { cache.put(it, "value") }

        // When - Access key1 multiple times (should make it most recently used)
        repeat(10) {
            cache.get(keys[0])
            delay(5.milliseconds)
        }

        // Add a new item (should evict the least recently used, which is not key1)
        val newKey = ByIdKey(testNamespace, EntityId("User", "6"))
        cache.put(newKey, "new-value")

        // Then - key1 should still be present (it was recently accessed)
        assertNotNull(cache.get(keys[0]), "Most recently accessed key should remain in cache")
        assertNotNull(cache.get(newKey), "New key should be in cache")

        // At least one of the other original keys should be evicted
        val originalKeysPresent = keys.count { cache.get(it) != null }
        assertTrue(originalKeysPresent < keys.size, "Some original keys should be evicted")
    }

    @Test
    fun stressTest_highConcurrency_thenNoException() = runTest(timeout = 30.seconds) {
        // Given
        val cache = MemoryCacheImpl<ByIdKey, String>(maxSize = 100)
        val keys = (1..200).map { ByIdKey(testNamespace, EntityId("User", "$it")) }

        // When - High concurrency stress test
        val jobs = (1..100).map { workerId ->
            launch(Dispatchers.Default) {
                repeat(500) { iteration ->
                    val key = keys.random()
                    when (iteration % 4) {
                        0 -> cache.put(key, "worker-$workerId-$iteration")
                        1 -> cache.get(key)
                        2 -> cache.remove(key)
                        3 -> if (iteration % 20 == 0) cache.clear()
                    }
                }
            }
        }

        // Then - All operations should complete without exception
        withTimeout(20.seconds) {
            jobs.joinAll()
        }
    }
}
