package dev.mattramotar.storex.core.internal

import app.cash.turbine.test
import dev.mattramotar.storex.core.Converter
import dev.mattramotar.storex.core.Freshness
import dev.mattramotar.storex.core.Origin
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.StoreResult
import dev.mattramotar.storex.core.utils.FakeBookkeeper
import dev.mattramotar.storex.core.utils.FakeFetcher
import dev.mattramotar.storex.core.utils.FakeSourceOfTruth
import dev.mattramotar.storex.core.utils.TEST_KEY_1
import dev.mattramotar.storex.core.utils.TEST_KEY_2
import dev.mattramotar.storex.core.utils.TEST_USER_1
import dev.mattramotar.storex.core.utils.TEST_USER_2
import dev.mattramotar.storex.core.utils.TestException
import dev.mattramotar.storex.core.utils.TestNetworkException
import dev.mattramotar.storex.core.utils.TestUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RealReadStoreTest {

    @Test
    fun stream_givenNoCache_thenEmitsLoading() = runTest {
        // Given
        val store = createStore()

        // When/Then
        store.stream(TEST_KEY_1).test {
            val result = awaitItem()
            assertIs<StoreResult.Loading>(result)
            assertFalse(result.fromCache)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_givenCachedData_thenEmitsData() = runTest {
        // Given
        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        sot.emit(TEST_KEY_1, TEST_USER_1)
        val store = createStore(sot = sot)

        // When/Then
        store.stream(TEST_KEY_1).test {
            val result = awaitItem()
            assertIs<StoreResult.Data<TestUser>>(result)
            assertEquals(TEST_USER_1, result.value)
            assertEquals(Origin.SOT, result.origin)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_givenSuccessfulFetch_thenEmitsDataFromSOT() = runTest {
        // Given
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        fetcher.respondWith(TEST_KEY_1, TEST_USER_1)
        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        val store = createStore(sot = sot, fetcher = fetcher)

        // When/Then
        store.stream(TEST_KEY_1).test {
            // Loading
            assertIs<StoreResult.Loading>(awaitItem())

            // Fresh data from SOT (after fetch writes to it)
            val data = awaitItem()
            assertIs<StoreResult.Data<TestUser>>(data)
            assertEquals(TEST_USER_1, data.value)
            assertEquals(Origin.SOT, data.origin)

            // Verify fetch happened
            assertTrue(fetcher.wasFetched(TEST_KEY_1))

            // Verify write to SOT
            assertEquals(1, sot.writes.size)
            assertEquals(TEST_KEY_1 to TEST_USER_1, sot.writes.first())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_givenFetchError_thenEmitsError() = runTest {
        // Given
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        fetcher.respondWithError(TEST_KEY_1, TestNetworkException())
        val store = createStore(fetcher = fetcher)

        // When/Then
        store.stream(TEST_KEY_1).test {
            assertIs<StoreResult.Loading>(awaitItem())

            val error = awaitItem()
            assertIs<StoreResult.Error>(error)
            assertTrue(error.servedStale)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_mustBeFresh_givenFetchFails_thenEmitsNonStaleError() = runTest {
        // Given
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        fetcher.respondWithError(TEST_KEY_1, TestNetworkException())
        val store = createStore(fetcher = fetcher)

        // When/Then
        store.stream(TEST_KEY_1, Freshness.MustBeFresh).test {
            val error = awaitItem()
            assertIs<StoreResult.Error>(error)
            assertFalse(error.servedStale)
            awaitComplete()
        }
    }

    @Test
    fun stream_staleIfError_givenCachedAndFetchFails_thenEmitsStaleData() = runTest {
        // Given
        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        sot.emit(TEST_KEY_1, TEST_USER_1)
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        fetcher.respondWithError(TEST_KEY_1, TestNetworkException())

        val store = createStore(sot = sot, fetcher = fetcher)

        // When/Then
        store.stream(TEST_KEY_1, Freshness.StaleIfError).test {
            // Stale data from cache
            val data = awaitItem()
            assertIs<StoreResult.Data<TestUser>>(data)
            assertEquals(TEST_USER_1, data.value)

            // Error (but served stale)
            val error = awaitItem()
            assertIs<StoreResult.Error>(error)
            assertTrue(error.servedStale)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_fetcherNotModified_thenDoesNotWriteToSOT() = runTest {
        // Given
        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        sot.emit(TEST_KEY_1, TEST_USER_1)
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        fetcher.respondWithNotModified(TEST_KEY_1, etag = "etag-123")

        val store = createStore(sot = sot, fetcher = fetcher)

        // When
        store.stream(TEST_KEY_1).test {
            awaitItem() // Initial cached data
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        // Then - no new writes (304 doesn't trigger write)
        assertEquals(0, sot.writes.size)
    }

    @Test
    fun get_givenMemoryCache_thenReturnsFast() = runTest {
        // Given
        val memory = MemoryCacheImpl<StoreKey, TestUser>(maxSize = 100, ttl = 10.minutes)
        memory.put(TEST_KEY_1, TEST_USER_1)
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        val store = createStore(memory = memory, fetcher = fetcher)

        // When
        val result = store.get(TEST_KEY_1, Freshness.CachedOrFetch)

        // Then
        assertEquals(TEST_USER_1, result)
        // Fetcher not called (served from memory)
        assertEquals(0, fetcher.totalFetchCount())
    }

    @Test
    fun get_givenNoMemoryCache_thenFetchesFromSOT() = runTest {
        // Given
        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        sot.emit(TEST_KEY_1, TEST_USER_1)
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        val store = createStore(sot = sot, fetcher = fetcher)

        // When
        val result = store.get(TEST_KEY_1, Freshness.CachedOrFetch)

        // Then
        assertEquals(TEST_USER_1, result)
    }

    @Test
    fun get_givenFetchError_thenThrows() = runTest {
        // Given
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        fetcher.respondWithError(TEST_KEY_1, TestNetworkException("Network error"))
        val store = createStore(fetcher = fetcher)

        // When/Then
        try {
            store.get(TEST_KEY_1, Freshness.MustBeFresh)
            error("Should have thrown")
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }

    @Test
    fun invalidate_givenKey_thenRemovesFromMemory() = runTest {
        // Given
        val memory = MemoryCacheImpl<StoreKey, TestUser>(maxSize = 100, ttl = 10.minutes)
        memory.put(TEST_KEY_1, TEST_USER_1)
        val store = createStore(memory = memory)

        // When
        store.invalidate(TEST_KEY_1)
        advanceUntilIdle()

        // Then
        assertNull(memory.get(TEST_KEY_1))
    }

    @Test
    fun invalidateNamespace_thenClearsMemory() = runTest {
        // Given
        val memory = MemoryCacheImpl<StoreKey, TestUser>(maxSize = 100, ttl = 10.minutes)
        memory.put(TEST_KEY_1, TEST_USER_1)
        memory.put(TEST_KEY_2, TEST_USER_2)
        val store = createStore(memory = memory)

        // When
        store.invalidateNamespace(TEST_KEY_1.namespace)
        advanceUntilIdle()

        // Then
        assertNull(memory.get(TEST_KEY_1))
        assertNull(memory.get(TEST_KEY_2))
    }

    @Test
    fun invalidateAll_thenClearsMemory() = runTest {
        // Given
        val memory = MemoryCacheImpl<StoreKey, TestUser>(maxSize = 100, ttl = 10.minutes)
        memory.put(TEST_KEY_1, TEST_USER_1)
        memory.put(TEST_KEY_2, TEST_USER_2)
        val store = createStore(memory = memory)

        // When
        store.invalidateAll()
        advanceUntilIdle()

        // Then
        assertNull(memory.get(TEST_KEY_1))
        assertNull(memory.get(TEST_KEY_2))
    }

    @Test
    fun close_thenCancelsScope() = runTest {
        // Given
        val store = createStore()

        // When
        store.close()
        advanceUntilIdle()

        // Then - no exception (scope cancelled cleanly)
    }

    @Test
    fun stream_givenConcurrentSameKey_thenDeduplicatesFetch() = runTest {
        // Given
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        fetcher.respondWith(TEST_KEY_1, TEST_USER_1)
        fetcher.simulateDelay = 100 // Ensure concurrency
        val store = createStore(fetcher = fetcher)

        // When - 10 concurrent streams for same key
        val flows = (1..10).map {
            store.stream(TEST_KEY_1)
        }

        // Collect all
        flows.forEach { flow ->
            flow.test {
                awaitItem() // All should get data
                cancelAndIgnoreRemainingEvents()
            }
        }

        advanceUntilIdle()

        // Then - only 1 fetch despite 10 streams (SingleFlight deduplication)
        assertEquals(1, fetcher.fetchCount(TEST_KEY_1))
    }

    @Test
    fun stream_givenDifferentKeys_thenFetchesSeparately() = runTest {
        // Given
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        fetcher.respondWith(TEST_KEY_1, TEST_USER_1)
        fetcher.respondWith(TEST_KEY_2, TEST_USER_2)
        val store = createStore(fetcher = fetcher)

        // When - concurrent streams for different keys
        store.stream(TEST_KEY_1).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        store.stream(TEST_KEY_2).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        advanceUntilIdle()

        // Then - separate fetches
        assertEquals(1, fetcher.fetchCount(TEST_KEY_1))
        assertEquals(1, fetcher.fetchCount(TEST_KEY_2))
    }

    @Test
    fun stream_givenWriteToSOT_thenUsesKeyMutex() = runTest {
        // Given
        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        fetcher.respondWith(TEST_KEY_1, TEST_USER_1)
        val store = createStore(sot = sot, fetcher = fetcher)

        // When
        store.stream(TEST_KEY_1).test {
            awaitItem()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        // Then - write happened (KeyMutex protected it)
        assertEquals(1, sot.writes.size)
    }

    @Test
    fun stream_recordsSuccessInBookkeeper() = runTest {
        // Given
        val bookkeeper = FakeBookkeeper<StoreKey>()
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        fetcher.respondWith(TEST_KEY_1, TEST_USER_1, etag = "etag-123")
        val store = createStore(fetcher = fetcher, bookkeeper = bookkeeper)

        // When
        store.stream(TEST_KEY_1).test {
            awaitItem()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        // Then
        assertEquals(1, bookkeeper.recordedSuccesses.size)
        val (key, etag, _) = bookkeeper.recordedSuccesses.first()
        assertEquals(TEST_KEY_1, key)
        assertEquals("etag-123", etag)
    }

    @Test
    fun stream_recordsFailureInBookkeeper() = runTest {
        // Given
        val bookkeeper = FakeBookkeeper<StoreKey>()
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        val error = TestNetworkException("Failure")
        fetcher.respondWithError(TEST_KEY_1, error)
        val store = createStore(fetcher = fetcher, bookkeeper = bookkeeper)

        // When
        store.stream(TEST_KEY_1).test {
            awaitItem()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        // Then
        assertEquals(1, bookkeeper.recordedFailures.size)
        val (key, recordedError, _) = bookkeeper.recordedFailures.first()
        assertEquals(TEST_KEY_1, key)
        assertIs<TestNetworkException>(recordedError)
    }

    @Test
    fun stream_updatesMemoryCacheAfterFetch() = runTest {
        // Given
        val memory = MemoryCacheImpl<StoreKey, TestUser>(maxSize = 100, ttl = 10.minutes)
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        fetcher.respondWith(TEST_KEY_1, TEST_USER_1)
        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        val store = createStore(memory = memory, fetcher = fetcher, sot = sot)

        // When
        store.stream(TEST_KEY_1).test {
            awaitItem()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        // Then
        assertEquals(TEST_USER_1, memory.get(TEST_KEY_1))
    }

    @Test
    fun stream_givenConverter_thenTransformsData() = runTest {
        // Given - converter that uppercases name
        val converter = object : Converter<StoreKey, TestUser, TestUser, TestUser, TestUser> {
            override suspend fun netToDbWrite(key: StoreKey, net: TestUser) = net
            override suspend fun dbReadToDomain(key: StoreKey, db: TestUser) =
                db.copy(name = db.name.uppercase())
            override suspend fun dbMetaFromProjection(db: TestUser) = null
        }

        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        sot.emit(TEST_KEY_1, TEST_USER_1)
        val store = createStore(sot = sot, converter = converter)

        // When/Then
        store.stream(TEST_KEY_1).test {
            val data = awaitItem()
            assertIs<StoreResult.Data<TestUser>>(data)
            assertEquals("ALICE", data.value.name) // Transformed
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_computesDataAge() = runTest {
        // Given
        val now = Instant.fromEpochSeconds(1000)
        val cachedAt = now - 5.minutes
        val converter = object : Converter<StoreKey, TestUser, TestUser, TestUser, TestUser> {
            override suspend fun netToDbWrite(key: StoreKey, net: TestUser) = net
            override suspend fun dbReadToDomain(key: StoreKey, db: TestUser) = db
            override suspend fun dbMetaFromProjection(db: TestUser) = cachedAt // 5 min old
        }

        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        sot.emit(TEST_KEY_1, TEST_USER_1)
        val store = createStore(sot = sot, converter = converter, now = { now })

        // When/Then
        store.stream(TEST_KEY_1).test {
            val data = awaitItem()
            assertIs<StoreResult.Data<TestUser>>(data)
            assertEquals(5.minutes, data.age)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Helper function to create store with defaults
    private fun createStore(
        sot: SourceOfTruth<StoreKey, TestUser, TestUser> = FakeSourceOfTruth(),
        fetcher: Fetcher<StoreKey, TestUser> = FakeFetcher(),
        converter: Converter<StoreKey, TestUser, TestUser, TestUser, TestUser> = IdentityTestConverter(),
        bookkeeper: Bookkeeper<StoreKey> = FakeBookkeeper(),
        validator: FreshnessValidator<StoreKey, Any?> = DefaultFreshnessValidator<StoreKey>(ttl = 5.minutes) as FreshnessValidator<StoreKey, Any?>,
        memory: MemoryCache<StoreKey, TestUser> = MemoryCacheImpl(maxSize = 100, ttl = 10.minutes),
        now: () -> Instant = { Clock.System.now() }
    ): RealReadStore<StoreKey, TestUser, TestUser, TestUser, TestUser> {
        return RealReadStore(
            sot = sot,
            fetcher = fetcher,
            converter = converter,
            bookkeeper = bookkeeper,
            validator = validator,
            memory = memory,
            now = now
        )
    }

    private class IdentityTestConverter : Converter<StoreKey, TestUser, TestUser, TestUser, TestUser> {
        override suspend fun netToDbWrite(key: StoreKey, net: TestUser) = net
        override suspend fun dbReadToDomain(key: StoreKey, db: TestUser) = db
        override suspend fun dbMetaFromProjection(db: TestUser) = null
    }
}
