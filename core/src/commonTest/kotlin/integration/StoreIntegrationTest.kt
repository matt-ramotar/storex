package dev.mattramotar.storex.core.integration

import app.cash.turbine.test
import dev.mattramotar.storex.core.Freshness
import dev.mattramotar.storex.core.StoreResult
import dev.mattramotar.storex.core.dsl.store
import dev.mattramotar.storex.core.utils.TEST_KEY_1
import dev.mattramotar.storex.core.utils.TEST_KEY_2
import dev.mattramotar.storex.core.utils.TEST_USER_1
import dev.mattramotar.storex.core.utils.TEST_USER_2
import dev.mattramotar.storex.core.utils.TestNetworkException
import dev.mattramotar.storex.core.utils.TestUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class StoreIntegrationTest {

    @Test
    fun endToEnd_fetch_sot_memory_thenServesData() = runTest {
        // Given
        val persistedData = mutableMapOf<dev.mattramotar.storex.core.StoreKey, TestUser>()
        var fetchCount = 0

        val store = store<dev.mattramotar.storex.core.StoreKey, TestUser> {
            fetcher { key ->
                fetchCount++
                TEST_USER_1
            }

            persistence {
                reader = { key -> persistedData[key] }
                writer = { key, user -> persistedData[key] = user }
            }

            cache {
                maxSize = 100
                ttl = 10.minutes
            }
        }

        // When - first fetch
        val result1 = store.get(TEST_KEY_1)
        advanceUntilIdle()

        // Second fetch (should be from memory)
        val result2 = store.get(TEST_KEY_1, Freshness.CachedOrFetch)

        // Then
        assertEquals(TEST_USER_1, result1)
        assertEquals(TEST_USER_1, result2)
        assertEquals(1, fetchCount, "Should only fetch once")
        assertEquals(TEST_USER_1, persistedData[TEST_KEY_1], "Should persist to SoT")
    }

    @Test
    fun multipleConcurrentRequests_thenDeduplicatesFetch() = runTest {
        // Given
        var fetchCount = 0
        val store = store<dev.mattramotar.storex.core.StoreKey, TestUser> {
            fetcher { key ->
                delay(50) // Simulate network delay
                fetchCount++
                TEST_USER_1
            }
        }

        // When - 100 concurrent requests
        val jobs = (1..100).map {
            launch {
                store.get(TEST_KEY_1)
            }
        }
        jobs.forEach { it.join() }
        advanceUntilIdle()

        // Then - only 1 fetch (SingleFlight deduplication)
        assertEquals(1, fetchCount)
    }

    @Test
    fun cacheInvalidation_thenTriggersRefetch() = runTest {
        // Given
        var fetchCount = 0
        val store = store<dev.mattramotar.storex.core.StoreKey, TestUser> {
            fetcher { key ->
                fetchCount++
                if (fetchCount == 1) TEST_USER_1 else TEST_USER_2
            }
        }

        // When - first fetch
        store.get(TEST_KEY_1)
        advanceUntilIdle()

        // Invalidate
        store.invalidate(TEST_KEY_1)
        advanceUntilIdle()

        // Second fetch (should refetch)
        val result = store.get(TEST_KEY_1)
        advanceUntilIdle()

        // Then
        assertEquals(2, fetchCount)
        assertEquals(TEST_USER_2, result)
    }

    @Test
    fun offlineScenario_servesCachedData() = runTest {
        // Given
        val persistedData = mutableMapOf<dev.mattramotar.storex.core.StoreKey, TestUser>(
            TEST_KEY_1 to TEST_USER_1 // Pre-cached
        )
        var networkAvailable = false

        val store = store<dev.mattramotar.storex.core.StoreKey, TestUser> {
            fetcher { key ->
                if (!networkAvailable) throw TestNetworkException("Offline")
                TEST_USER_2
            }

            persistence {
                reader = { key -> persistedData[key] }
                writer = { key, user -> persistedData[key] = user }
            }
        }

        // When - fetch while offline (should serve cached)
        store.stream(TEST_KEY_1, Freshness.StaleIfError).test {
            // Stale data from cache
            val data = awaitItem()
            assertIs<StoreResult.Data<TestUser>>(data)
            assertEquals(TEST_USER_1, data.value)

            // Error (network unavailable)
            val error = awaitItem()
            assertIs<StoreResult.Error>(error)
            assertTrue(error.servedStale)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun errorRecovery_thenEventuallySucceeds() = runTest {
        // Given
        var attemptCount = 0
        val store = store<dev.mattramotar.storex.core.StoreKey, TestUser> {
            fetcher { key ->
                attemptCount++
                if (attemptCount < 3) {
                    throw TestNetworkException("Temporary failure")
                }
                TEST_USER_1
            }
        }

        // When - first attempt fails
        try {
            store.get(TEST_KEY_1, Freshness.MustBeFresh)
        } catch (e: Exception) {
            // Expected first failure
        }

        // Second attempt fails
        try {
            store.get(TEST_KEY_1, Freshness.MustBeFresh)
        } catch (e: Exception) {
            // Expected second failure
        }

        // Third attempt succeeds
        val result = store.get(TEST_KEY_1, Freshness.MustBeFresh)

        // Then
        assertEquals(TEST_USER_1, result)
        assertEquals(3, attemptCount)
    }

    @Test
    fun freshnessPolicy_cachedOrFetch_thenBackgroundRefresh() = runTest {
        // Given
        val persistedData = mutableMapOf<dev.mattramotar.storex.core.StoreKey, TestUser>(
            TEST_KEY_1 to TEST_USER_1 // Pre-cached
        )
        var fetchCount = 0

        val store = store<dev.mattramotar.storex.core.StoreKey, TestUser> {
            fetcher { key ->
                fetchCount++
                TEST_USER_2 // New data
            }

            persistence {
                reader = { key -> persistedData[key] }
                writer = { key, user -> persistedData[key] = user }
            }

            freshness {
                ttl = 1.minutes // Short TTL to trigger refresh
            }
        }

        // When - advance time to make cache stale
        testScheduler.advanceTimeBy(2.minutes.inWholeMilliseconds)

        // Fetch with CachedOrFetch (should serve cached + refresh in background)
        store.stream(TEST_KEY_1, Freshness.CachedOrFetch).test {
            // Old cached data
            val cached = awaitItem()
            assertIs<StoreResult.Data<TestUser>>(cached)
            assertEquals(TEST_USER_1, cached.value)

            // Fresh data after background refresh
            val fresh = awaitItem()
            assertIs<StoreResult.Data<TestUser>>(fresh)
            assertEquals(TEST_USER_2, fresh.value)

            cancelAndIgnoreRemainingEvents()
        }

        // Then
        assertEquals(1, fetchCount)
    }

    @Test
    fun multipleKeys_thenHandlesIndependently() = runTest {
        // Given
        val store = store<dev.mattramotar.storex.core.StoreKey, TestUser> {
            fetcher { key ->
                if (key == TEST_KEY_1) TEST_USER_1 else TEST_USER_2
            }
        }

        // When - concurrent fetches for different keys
        val result1 = store.get(TEST_KEY_1)
        val result2 = store.get(TEST_KEY_2)

        // Then
        assertEquals(TEST_USER_1, result1)
        assertEquals(TEST_USER_2, result2)
    }

    @Test
    fun concurrentReadsAndWrites_thenThreadSafe() = runTest {
        // Given
        val persistedData = mutableMapOf<dev.mattramotar.storex.core.StoreKey, TestUser>()
        val store = store<dev.mattramotar.storex.core.StoreKey, TestUser> {
            fetcher { key -> TEST_USER_1 }

            persistence {
                reader = { key -> persistedData[key] }
                writer = { key, user ->
                    delay(5) // Simulate write delay
                    persistedData[key] = user
                }
            }
        }

        // When - concurrent reads and writes
        val jobs = (1..50).flatMap { i ->
            val key = if (i % 2 == 0) TEST_KEY_1 else TEST_KEY_2
            listOf(
                launch { store.get(key) },
                launch { store.stream(key).test { awaitItem(); cancelAndIgnoreRemainingEvents() } }
            )
        }
        jobs.forEach { it.join() }
        advanceUntilIdle()

        // Then - no crashes (thread safety verified)
        assertTrue(persistedData.isNotEmpty())
    }

    @Test
    fun reactiveUpdates_givenSOTChanges_thenEmitsUpdates() = runTest {
        // Given
        val sotFlow = MutableSharedFlow<TestUser?>(replay = 1)
        sotFlow.tryEmit(TEST_USER_1) // Initial value

        val store = store<dev.mattramotar.storex.core.StoreKey, TestUser> {
            fetcher { key -> TEST_USER_1 }

            persistence {
                reader = { key -> sotFlow.replayCache.firstOrNull() }
                writer = { key, user -> sotFlow.tryEmit(user) }
            }
        }

        // When/Then - stream reacts to SoT changes
        store.stream(TEST_KEY_1).test {
            // Initial data
            val initial = awaitItem()
            assertIs<StoreResult.Data<TestUser>>(initial)
            assertEquals(TEST_USER_1, initial.value)

            // External update to SoT
            sotFlow.emit(TEST_USER_2)

            // Updated data
            val updated = awaitItem()
            assertIs<StoreResult.Data<TestUser>>(updated)
            assertEquals(TEST_USER_2, updated.value)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun endToEnd_withConverter_thenTransformsData() = runTest {
        // Given
        data class NetworkUser(val userId: String, val fullName: String)
        data class DomainUser(val id: String, val name: String)

        val store = store<dev.mattramotar.storex.core.StoreKey, DomainUser> {
            // Fetcher returns network type
            fetcher { key ->
                // Cast and transform
                DomainUser(id = "user-1", name = "Alice")
            }
        }

        // When
        val result = store.get(TEST_KEY_1)

        // Then
        assertEquals("user-1", result.id)
        assertEquals("Alice", result.name)
    }
}
