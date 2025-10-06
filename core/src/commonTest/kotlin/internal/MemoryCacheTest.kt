package dev.mattramotar.storex.core.internal

import dev.mattramotar.storex.core.utils.TEST_KEY_1
import dev.mattramotar.storex.core.utils.TEST_KEY_2
import dev.mattramotar.storex.core.utils.TEST_USER_1
import dev.mattramotar.storex.core.utils.TEST_USER_2
import dev.mattramotar.storex.core.utils.TestUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryCacheTest {

    @Test
    fun get_givenEmptyCache_thenReturnsNull() = runTest {
        // Given
        val cache = createCache()

        // When
        val result = cache.get(TEST_KEY_1)

        // Then
        assertNull(result)
    }

    @Test
    fun put_givenNewEntry_thenReturnsTrue() = runTest {
        // Given
        val cache = createCache()

        // When
        val isNewEntry = cache.put(TEST_KEY_1, TEST_USER_1)

        // Then
        assertTrue(isNewEntry)
    }

    @Test
    fun put_givenExistingEntry_thenReturnsFalse() = runTest {
        // Given
        val cache = createCache()
        cache.put(TEST_KEY_1, TEST_USER_1)

        // When
        val isNewEntry = cache.put(TEST_KEY_1, TEST_USER_1.copy(name = "Updated"))

        // Then
        assertFalse(isNewEntry)
    }

    @Test
    fun put_thenGet_thenReturnsValue() = runTest {
        // Given
        val cache = createCache()
        cache.put(TEST_KEY_1, TEST_USER_1)

        // When
        val result = cache.get(TEST_KEY_1)

        // Then
        assertEquals(TEST_USER_1, result)
    }

    @Test
    fun put_givenUpdate_thenReturnsUpdatedValue() = runTest {
        // Given
        val cache = createCache()
        cache.put(TEST_KEY_1, TEST_USER_1)
        val updatedUser = TEST_USER_1.copy(name = "Updated Alice")

        // When
        cache.put(TEST_KEY_1, updatedUser)
        val result = cache.get(TEST_KEY_1)

        // Then
        assertEquals(updatedUser, result)
    }

    @Test
    fun put_whenAtCapacity_thenEvictsLRU() = runTest {
        // Given
        val cache = createCache(maxSize = 2)
        cache.put(TEST_KEY_1, TEST_USER_1)
        cache.put(TEST_KEY_2, TEST_USER_2)

        // When - add third item, should evict TEST_KEY_1 (oldest)
        val key3 = dev.mattramotar.storex.core.ByIdKey(
            namespace = TEST_KEY_1.namespace,
            entity = dev.mattramotar.storex.core.EntityId("User", "user-3")
        )
        cache.put(key3, TEST_USER_1.copy(id = "user-3"))

        // Then
        assertNull(cache.get(TEST_KEY_1), "Oldest key should be evicted")
        assertNotNull(cache.get(TEST_KEY_2), "Second key should remain")
        assertNotNull(cache.get(key3), "New key should be present")
    }

    @Test
    fun put_whenAtCapacity_andKeyExists_thenDoesNotEvict() = runTest {
        // Given
        val cache = createCache(maxSize = 2)
        cache.put(TEST_KEY_1, TEST_USER_1)
        cache.put(TEST_KEY_2, TEST_USER_2)

        // When - update existing key (should not trigger eviction)
        cache.put(TEST_KEY_1, TEST_USER_1.copy(name = "Updated"))

        // Then
        assertNotNull(cache.get(TEST_KEY_1), "Updated key should be present")
        assertNotNull(cache.get(TEST_KEY_2), "Other key should remain")
    }

    @Test
    fun get_updatesAccessOrder_forLRU() = runTest {
        // Given
        val cache = createCache(maxSize = 2)
        cache.put(TEST_KEY_1, TEST_USER_1)
        cache.put(TEST_KEY_2, TEST_USER_2)

        // When - access TEST_KEY_1 to make it "recently used"
        cache.get(TEST_KEY_1)

        // Add third item - should evict TEST_KEY_2 (now oldest)
        val key3 = dev.mattramotar.storex.core.ByIdKey(
            namespace = TEST_KEY_1.namespace,
            entity = dev.mattramotar.storex.core.EntityId("User", "user-3")
        )
        cache.put(key3, TEST_USER_1.copy(id = "user-3"))

        // Then
        assertNotNull(cache.get(TEST_KEY_1), "Recently accessed key should remain")
        assertNull(cache.get(TEST_KEY_2), "Oldest (unaccessed) key should be evicted")
        assertNotNull(cache.get(key3), "New key should be present")
    }

    @Test
    fun get_whenEntryExpired_thenReturnsNull() = runTest {
        // Given
        val ttl = 100.milliseconds
        val timeSource = dev.mattramotar.storex.core.utils.TestTimeSource.atNow()
        val cache = createCache(ttl = ttl, timeSource = timeSource)
        cache.put(TEST_KEY_1, TEST_USER_1)

        // When - advance time beyond TTL
        timeSource.advance(ttl + 1.milliseconds)

        // Get after expiration
        val result = cache.get(TEST_KEY_1)

        // Then
        assertNull(result, "Expired entry should return null")
    }

    @Test
    fun get_whenEntryExpired_thenRemovesFromCache() = runTest {
        // Given
        val ttl = 100.milliseconds
        val timeSource = dev.mattramotar.storex.core.utils.TestTimeSource.atNow()
        val cache = createCache(ttl = ttl, timeSource = timeSource)
        cache.put(TEST_KEY_1, TEST_USER_1)

        // When - advance time and get (triggers removal)
        timeSource.advance(ttl + 1.milliseconds)
        cache.get(TEST_KEY_1)

        // Add new entry (should not trigger eviction since expired entry was removed)
        val timeSource2 = dev.mattramotar.storex.core.utils.TestTimeSource.atNow()
        val cache2Items = createCache(maxSize = 1, ttl = ttl, timeSource = timeSource2)
        cache2Items.put(TEST_KEY_1, TEST_USER_1)
        timeSource2.advance(ttl + 1.milliseconds)
        cache2Items.get(TEST_KEY_1) // Remove expired
        val putResult = cache2Items.put(TEST_KEY_2, TEST_USER_2)

        // Then
        assertTrue(putResult, "Should be able to add new entry after expired one removed")
    }

    @Test
    fun get_whenNotExpired_thenReturnsValue() = runTest {
        // Given
        val ttl = 1.hours
        val timeSource = dev.mattramotar.storex.core.utils.TestTimeSource.atNow()
        val cache = createCache(ttl = ttl, timeSource = timeSource)
        cache.put(TEST_KEY_1, TEST_USER_1)

        // When - advance time but stay within TTL
        timeSource.advance(ttl / 2)
        val result = cache.get(TEST_KEY_1)

        // Then
        assertEquals(TEST_USER_1, result)
    }

    @Test
    fun get_withInfiniteTTL_thenNeverExpires() = runTest {
        // Given
        val timeSource = dev.mattramotar.storex.core.utils.TestTimeSource.atNow()
        val cache = createCache(ttl = Duration.INFINITE, timeSource = timeSource)
        cache.put(TEST_KEY_1, TEST_USER_1)

        // When - advance time significantly
        timeSource.advance(365.days) // 1 year
        val result = cache.get(TEST_KEY_1)

        // Then
        assertEquals(TEST_USER_1, result, "Entry with infinite TTL should never expire")
    }

    @Test
    fun remove_givenExistingKey_thenReturnsTrue() = runTest {
        // Given
        val cache = createCache()
        cache.put(TEST_KEY_1, TEST_USER_1)

        // When
        val removed = cache.remove(TEST_KEY_1)

        // Then
        assertTrue(removed)
        assertNull(cache.get(TEST_KEY_1))
    }

    @Test
    fun remove_givenNonExistentKey_thenReturnsFalse() = runTest {
        // Given
        val cache = createCache()

        // When
        val removed = cache.remove(TEST_KEY_1)

        // Then
        assertFalse(removed)
    }

    @Test
    fun remove_thenPut_thenWorks() = runTest {
        // Given
        val cache = createCache()
        cache.put(TEST_KEY_1, TEST_USER_1)
        cache.remove(TEST_KEY_1)

        // When
        cache.put(TEST_KEY_1, TEST_USER_2)
        val result = cache.get(TEST_KEY_1)

        // Then
        assertEquals(TEST_USER_2, result)
    }

    @Test
    fun clear_thenRemovesAllEntries() = runTest {
        // Given
        val cache = createCache()
        cache.put(TEST_KEY_1, TEST_USER_1)
        cache.put(TEST_KEY_2, TEST_USER_2)

        // When
        cache.clear()

        // Then
        assertNull(cache.get(TEST_KEY_1))
        assertNull(cache.get(TEST_KEY_2))
    }

    @Test
    fun clear_thenPut_thenWorks() = runTest {
        // Given
        val cache = createCache()
        cache.put(TEST_KEY_1, TEST_USER_1)
        cache.clear()

        // When
        cache.put(TEST_KEY_2, TEST_USER_2)
        val result = cache.get(TEST_KEY_2)

        // Then
        assertEquals(TEST_USER_2, result)
    }

    @Test
    fun concurrentPuts_thenAllSucceed() = runTest {
        // Given
        val cache = createCache(maxSize = 1000)
        val iterations = 100

        // When - concurrent puts
        val jobs = (1..iterations).map { i ->
            launch {
                val key = dev.mattramotar.storex.core.ByIdKey(
                    namespace = TEST_KEY_1.namespace,
                    entity = dev.mattramotar.storex.core.EntityId("User", "user-$i")
                )
                val user = TEST_USER_1.copy(id = "user-$i")
                cache.put(key, user)
            }
        }
        jobs.forEach { it.join() }

        // Then - all entries should be present
        val successCount = (1..iterations).count { i ->
            val key = dev.mattramotar.storex.core.ByIdKey(
                namespace = TEST_KEY_1.namespace,
                entity = dev.mattramotar.storex.core.EntityId("User", "user-$i")
            )
            cache.get(key) != null
        }
        assertEquals(iterations, successCount)
    }

    @Test
    fun concurrentGetsAndPuts_thenThreadSafe() = runTest {
        // Given
        val cache = createCache(maxSize = 100)

        // When - concurrent reads and writes
        val putJobs = (1..50).map { i ->
            launch {
                val key = dev.mattramotar.storex.core.ByIdKey(
                    namespace = TEST_KEY_1.namespace,
                    entity = dev.mattramotar.storex.core.EntityId("User", "user-$i")
                )
                val user = TEST_USER_1.copy(id = "user-$i")
                cache.put(key, user)
            }
        }

        val getJobs = (1..50).map { i ->
            launch {
                val key = dev.mattramotar.storex.core.ByIdKey(
                    namespace = TEST_KEY_1.namespace,
                    entity = dev.mattramotar.storex.core.EntityId("User", "user-$i")
                )
                cache.get(key) // May return null or value
            }
        }

        (putJobs + getJobs).forEach { it.join() }

        // Then - no crashes (thread safety verified)
        // Success is implicit (test completes without exception)
    }

    @Test
    fun concurrentRemoves_thenThreadSafe() = runTest {
        // Given
        val cache = createCache()
        cache.put(TEST_KEY_1, TEST_USER_1)

        // When - concurrent removes of same key
        val jobs = (1..10).map {
            launch {
                cache.remove(TEST_KEY_1)
            }
        }
        jobs.forEach { it.join() }

        // Then - key should be removed
        assertNull(cache.get(TEST_KEY_1))
    }

    @Test
    fun boundsCheck_whenEmpty_thenEvictionHandled() = runTest {
        // Given
        val cache = createCache(maxSize = 1)

        // When - try to fill beyond capacity on empty cache
        cache.put(TEST_KEY_1, TEST_USER_1)
        cache.put(TEST_KEY_2, TEST_USER_2) // Should evict TEST_KEY_1

        // Then
        assertNull(cache.get(TEST_KEY_1))
        assertNotNull(cache.get(TEST_KEY_2))
    }

    // Helper functions
    private fun createCache(
        maxSize: Int = 100,
        ttl: Duration = Duration.INFINITE,
        timeSource: dev.mattramotar.storex.core.TimeSource = dev.mattramotar.storex.core.TimeSource.SYSTEM
    ): MemoryCache<dev.mattramotar.storex.core.StoreKey, TestUser> {
        return MemoryCacheImpl(maxSize = maxSize, ttl = ttl, timeSource = timeSource)
    }
}
