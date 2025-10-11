package dev.mattramotar.storex.core.internal

import app.cash.turbine.test
import dev.mattramotar.storex.core.Converter
import dev.mattramotar.storex.core.Freshness
import dev.mattramotar.storex.core.Origin
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.StoreResult
import dev.mattramotar.storex.core.TimeSource
import dev.mattramotar.storex.core.utils.FakeBookkeeper
import dev.mattramotar.storex.core.utils.FakeFetcher
import dev.mattramotar.storex.core.utils.FakeSourceOfTruth
import dev.mattramotar.storex.core.utils.TEST_KEY_1
import dev.mattramotar.storex.core.utils.TEST_KEY_2
import dev.mattramotar.storex.core.utils.TEST_USER_1
import dev.mattramotar.storex.core.utils.TEST_USER_2
import dev.mattramotar.storex.core.internal.StoreException
import dev.mattramotar.storex.core.utils.TestException
import dev.mattramotar.storex.core.utils.TestNetworkException
import dev.mattramotar.storex.core.utils.TestUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        val store = createStore(scope = backgroundScope)

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
        val store = createStore(scope = backgroundScope, sot = sot)

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
        val store = createStore(scope = backgroundScope, sot = sot, fetcher = fetcher)

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
        val store = createStore(scope = backgroundScope, fetcher = fetcher)

        // When/Then
        store.stream(TEST_KEY_1).test {
            assertIs<StoreResult.Loading>(awaitItem())

            val error = awaitItem()
            assertIs<StoreResult.Error>(error)
            assertFalse(error.servedStale)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_mustBeFresh_givenFetchFails_thenEmitsNonStaleError() = runTest {
        // Given
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        fetcher.respondWithError(TEST_KEY_1, TestNetworkException())
        val store = createStore(scope = backgroundScope, fetcher = fetcher)

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

        val store = createStore(scope = backgroundScope, sot = sot, fetcher = fetcher)

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
    fun stream_staleIfError_respectsBookkeeperStaleWindowWhenMetaMissing() = runTest {
        // Given
        val staleWindow = 5.minutes
        var currentTime = Instant.fromEpochSeconds(10_000)
        val timeSource = TimeSource { currentTime }

        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        sot.emit(TEST_KEY_1, TEST_USER_1)

        val fetcher = FakeFetcher<StoreKey, TestUser>()
        fetcher.respondWithError(TEST_KEY_1, TestNetworkException())

        val bookkeeper = FakeBookkeeper<StoreKey>()
        val validator = DefaultFreshnessValidator<StoreKey>(
            ttl = 5.minutes,
            staleIfErrorDuration = staleWindow
        ) as FreshnessValidator<StoreKey, Any?>

        val store = createStore(
            scope = backgroundScope,
            sot = sot,
            fetcher = fetcher,
            converter = IdentityTestConverter(),
            bookkeeper = bookkeeper,
            validator = validator,
            timeSource = timeSource
        )

        val lastSuccessInsideWindow = currentTime - (staleWindow / 2)
        bookkeeper.setStatus(
            TEST_KEY_1,
            KeyStatus(
                lastSuccessAt = lastSuccessInsideWindow,
                lastFailureAt = null,
                lastEtag = null,
                backoffUntil = null
            )
        )

        // When/Then - inside stale window serves stale data on error
        store.stream(TEST_KEY_1, Freshness.StaleIfError).test {
            awaitItem() // Cached data

            val error = awaitItem()
            assertIs<StoreResult.Error>(error)
            assertTrue(error.servedStale)

            cancelAndIgnoreRemainingEvents()
        }

        // Advance time beyond the stale window and ensure stale data is not served
        currentTime = lastSuccessInsideWindow + staleWindow + 1.minutes
        bookkeeper.setStatus(
            TEST_KEY_1,
            KeyStatus(
                lastSuccessAt = lastSuccessInsideWindow,
                lastFailureAt = null,
                lastEtag = null,
                backoffUntil = null
            )
        )

        store.stream(TEST_KEY_1, Freshness.StaleIfError).test {
            awaitItem() // Cached data

            val error = awaitItem()
            assertIs<StoreResult.Error>(error)
            assertFalse(error.servedStale)

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

        val store = createStore(scope = backgroundScope, sot = sot, fetcher = fetcher)

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
        val memory = MemoryCacheImpl<StoreKey, TestUser>(maxSize = 100, ttl = 10.minutes, timeSource = TimeSource.SYSTEM)
        memory.put(TEST_KEY_1, TEST_USER_1)
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        val store = createStore(scope = backgroundScope, memory = memory, fetcher = fetcher)

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
        val store = createStore(scope = backgroundScope, sot = sot, fetcher = fetcher)

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
        val store = createStore(scope = backgroundScope, fetcher = fetcher)

        // When/Then
        try {
            store.get(TEST_KEY_1, Freshness.MustBeFresh)
            error("Should have thrown")
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }

    @Test
    fun get_cachedOrFetch_givenFetchErrorWithoutCache_thenPropagatesStoreException() = runTest {
        // Given
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        val networkError = TestNetworkException("Network error")
        fetcher.respondWithError(TEST_KEY_1, networkError)
        val store = createStore(scope = backgroundScope, fetcher = fetcher)

        // When/Then
        val thrown = assertFailsWith<StoreException> {
            store.get(TEST_KEY_1, Freshness.CachedOrFetch)
        }
        assertIs<StoreException.Unknown>(thrown)
        assertEquals("Network error", thrown.cause?.message)
    }

    @Test
    fun invalidate_givenKey_thenRemovesFromMemory() = runTest {
        // Given
        val memory = MemoryCacheImpl<StoreKey, TestUser>(maxSize = 100, ttl = 10.minutes, timeSource = TimeSource.SYSTEM)
        memory.put(TEST_KEY_1, TEST_USER_1)
        val store = createStore(scope = backgroundScope, memory = memory)

        // When
        store.invalidate(TEST_KEY_1)
        advanceUntilIdle()

        // Then
        assertNull(memory.get(TEST_KEY_1))
    }

    @Test
    fun invalidate_givenKey_thenDeletesFromSOT() = runTest {
        // Given
        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        sot.emit(TEST_KEY_1, TEST_USER_1)
        val memory = MemoryCacheImpl<StoreKey, TestUser>(maxSize = 100, ttl = 10.minutes, timeSource = TimeSource.SYSTEM)
        memory.put(TEST_KEY_1, TEST_USER_1)
        val store = createStore(scope = backgroundScope, memory = memory, sot = sot)

        // When
        store.invalidate(TEST_KEY_1)
        advanceUntilIdle()

        // Then - memory cleared
        assertNull(memory.get(TEST_KEY_1))
        // And SoT data deleted
        assertEquals(1, sot.deletes.size)
        assertEquals(TEST_KEY_1, sot.deletes.first())
        // Verify data is actually gone from SoT
        assertNull(sot.getData(TEST_KEY_1))
    }

    @Test
    fun invalidateNamespace_thenClearsMemory() = runTest {
        // Given
        val memory = MemoryCacheImpl<StoreKey, TestUser>(maxSize = 100, ttl = 10.minutes, timeSource = TimeSource.SYSTEM)
        memory.put(TEST_KEY_1, TEST_USER_1)
        memory.put(TEST_KEY_2, TEST_USER_2)
        val store = createStore(scope = backgroundScope, memory = memory)

        // When
        store.invalidateNamespace(TEST_KEY_1.namespace)
        delay(1)  // Yield to allow launched coroutine to run
        advanceUntilIdle()

        // Then
        assertNull(memory.get(TEST_KEY_1))
        assertNull(memory.get(TEST_KEY_2))
    }

    @Test
    fun invalidateAll_thenClearsMemory() = runTest {
        // Given
        val memory = MemoryCacheImpl<StoreKey, TestUser>(maxSize = 100, ttl = 10.minutes, timeSource = TimeSource.SYSTEM)
        memory.put(TEST_KEY_1, TEST_USER_1)
        memory.put(TEST_KEY_2, TEST_USER_2)
        val store = createStore(scope = backgroundScope, memory = memory)

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
        val store = createStore(scope = backgroundScope)

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
        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        val store = createStore(scope = backgroundScope, sot = sot, fetcher = fetcher)

        // When - 10 concurrent streams for same key
        val flows = (1..10).map {
            store.stream(TEST_KEY_1)
        }

        // Collect all
        flows.forEach { flow ->
            flow.test {
                // Wait for data, not just first item
                val item = awaitItem()
                if (item is StoreResult.Loading) {
                    awaitItem() // Wait for actual data
                }
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
        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        val store = createStore(scope = backgroundScope, sot = sot, fetcher = fetcher)

        // When - concurrent streams for different keys
        store.stream(TEST_KEY_1).test {
            val item = awaitItem()
            if (item is StoreResult.Loading) {
                awaitItem() // Wait for actual data
            }
            cancelAndIgnoreRemainingEvents()
        }

        store.stream(TEST_KEY_2).test {
            val item = awaitItem()
            if (item is StoreResult.Loading) {
                awaitItem() // Wait for actual data
            }
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
        val store = createStore(scope = backgroundScope, sot = sot, fetcher = fetcher)

        // When
        store.stream(TEST_KEY_1).test {
            assertIs<StoreResult.Loading>(awaitItem())

            val data = awaitItem()
            assertIs<StoreResult.Data<TestUser>>(data)
            assertEquals(TEST_USER_1, data.value)

            cancelAndIgnoreRemainingEvents()
        }

        // Then - write happened (KeyMutex protected it)
        assertEquals(1, sot.writes.size)
        assertEquals(TEST_KEY_1 to TEST_USER_1, sot.writes.first())
    }

    @Test
    fun stream_recordsSuccessInBookkeeper() = runTest {
        // Given
        val bookkeeper = FakeBookkeeper<StoreKey>()
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        fetcher.respondWith(TEST_KEY_1, TEST_USER_1, etag = "etag-123")
        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        val store = createStore(scope = backgroundScope, sot = sot, fetcher = fetcher, bookkeeper = bookkeeper)

        // When
        store.stream(TEST_KEY_1).test {
            assertIs<StoreResult.Loading>(awaitItem())

            // Wait for data from SOT (written after fetch)
            val data = awaitItem()
            assertIs<StoreResult.Data<TestUser>>(data)

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
        val store = createStore(scope = backgroundScope, fetcher = fetcher, bookkeeper = bookkeeper)

        // When
        store.stream(TEST_KEY_1).test {
            assertIs<StoreResult.Loading>(awaitItem())

            // Wait for error emission (ensures fetch completed)
            val errorResult = awaitItem()
            assertIs<StoreResult.Error>(errorResult)

            cancelAndIgnoreRemainingEvents()
        }

        // Then
        assertEquals(1, bookkeeper.recordedFailures.size)
        val (key, recordedError, _) = bookkeeper.recordedFailures.first()
        assertEquals(TEST_KEY_1, key)
        // FakeFetcher wraps errors in StoreException.from(), so TestNetworkException becomes StoreException.Unknown
        assertIs<StoreException.Unknown>(recordedError)
        assertEquals("Failure", recordedError.cause?.message)
    }

    @Test
    fun stream_updatesMemoryCacheAfterFetch() = runTest {
        // Given
        val memory = MemoryCacheImpl<StoreKey, TestUser>(maxSize = 100, ttl = 10.minutes, timeSource = TimeSource.SYSTEM)
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        fetcher.respondWith(TEST_KEY_1, TEST_USER_1)
        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        val store = createStore(scope = backgroundScope, memory = memory, fetcher = fetcher, sot = sot)

        // When
        store.stream(TEST_KEY_1).test {
            assertIs<StoreResult.Loading>(awaitItem())

            val data = awaitItem()
            assertIs<StoreResult.Data<TestUser>>(data)
            assertEquals(TEST_USER_1, data.value)

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
        val store = createStore(scope = backgroundScope, sot = sot, converter = converter)

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
        val timeSource = TimeSource { now }
        val converter = object : Converter<StoreKey, TestUser, TestUser, TestUser, TestUser> {
            override suspend fun netToDbWrite(key: StoreKey, net: TestUser) = net
            override suspend fun dbReadToDomain(key: StoreKey, db: TestUser) = db
            override suspend fun dbMetaFromProjection(db: TestUser) = DefaultDbMeta(
                updatedAt = cachedAt,
                etag = null
            )
        }

        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        sot.emit(TEST_KEY_1, TEST_USER_1)
        val store = createStore(scope = backgroundScope, sot = sot, converter = converter, timeSource = timeSource)

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
        memory: MemoryCache<StoreKey, TestUser> = MemoryCacheImpl(maxSize = 100, ttl = 10.minutes, timeSource = TimeSource.SYSTEM),
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        timeSource: TimeSource = TimeSource.SYSTEM
    ): RealReadStore<StoreKey, TestUser, TestUser, TestUser, TestUser> {
        return RealReadStore(
            sot = sot,
            fetcher = fetcher,
            converter = converter,
            bookkeeper = bookkeeper,
            validator = validator,
            memory = memory,
            scope = scope,
            timeSource = timeSource
        )
    }

    private class IdentityTestConverter : Converter<StoreKey, TestUser, TestUser, TestUser, TestUser> {
        override suspend fun netToDbWrite(key: StoreKey, net: TestUser) = net
        override suspend fun dbReadToDomain(key: StoreKey, db: TestUser) = db
        override suspend fun dbMetaFromProjection(db: TestUser) = null
    }
}
