package dev.mattramotar.storex.core.contract

import app.cash.turbine.test
import dev.mattramotar.storex.core.Converter
import dev.mattramotar.storex.core.Freshness
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.StoreResult
import dev.mattramotar.storex.core.TimeSource
import dev.mattramotar.storex.core.seams.Bookkeeper
import dev.mattramotar.storex.core.seams.ConditionalRequest
import dev.mattramotar.storex.core.seams.DefaultDbMeta
import dev.mattramotar.storex.core.seams.DefaultFreshnessValidator
import dev.mattramotar.storex.core.seams.Fetcher
import dev.mattramotar.storex.core.seams.FetchPlan
import dev.mattramotar.storex.core.seams.FreshnessContext
import dev.mattramotar.storex.core.seams.FreshnessValidator
import dev.mattramotar.storex.core.seams.MemoryCache
import dev.mattramotar.storex.core.internal.MemoryCacheImpl
import dev.mattramotar.storex.core.internal.RealReadStore
import dev.mattramotar.storex.core.seams.SourceOfTruth
import dev.mattramotar.storex.core.utils.ARTICLE_KEY_1
import dev.mattramotar.storex.core.utils.FakeBookkeeper
import dev.mattramotar.storex.core.utils.FakeFetcher
import dev.mattramotar.storex.core.utils.FakeSourceOfTruth
import dev.mattramotar.storex.core.utils.TEST_KEY_1
import dev.mattramotar.storex.core.utils.TEST_KEY_2
import dev.mattramotar.storex.core.utils.TEST_USER_1
import dev.mattramotar.storex.core.utils.TEST_USER_2
import dev.mattramotar.storex.core.utils.TestNetworkException
import dev.mattramotar.storex.core.utils.TestTimeSource
import dev.mattramotar.storex.core.utils.TestUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ReadCoreContractTest {

    @Test
    fun invalidate_key_isNonDestructive() = runTest {
        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        sot.emit(TEST_KEY_1, TEST_USER_1)
        val memory = MemoryCacheImpl<StoreKey, TestUser>(maxSize = 100, ttl = 10.minutes, timeSource = TimeSource.SYSTEM)
        memory.put(TEST_KEY_1, TEST_USER_1)
        val store = createStore(scope = backgroundScope, sot = sot, memory = memory)

        store.invalidate(TEST_KEY_1)
        advanceUntilIdle()

        assertNull(memory.get(TEST_KEY_1))
        assertEquals(TEST_USER_1, sot.getData(TEST_KEY_1))
        assertTrue(sot.deletes.isEmpty())
    }

    @Test
    fun clear_key_isDestructive() = runTest {
        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        sot.emit(TEST_KEY_1, TEST_USER_1)
        val memory = MemoryCacheImpl<StoreKey, TestUser>(maxSize = 100, ttl = 10.minutes, timeSource = TimeSource.SYSTEM)
        memory.put(TEST_KEY_1, TEST_USER_1)
        val store = createStore(scope = backgroundScope, sot = sot, memory = memory)

        store.clear(TEST_KEY_1)
        advanceUntilIdle()

        assertNull(memory.get(TEST_KEY_1))
        assertNull(sot.getData(TEST_KEY_1))
        assertEquals(listOf(TEST_KEY_1), sot.deletes)
    }

    @Test
    fun invalidate_namespace_onlyTouchesMatchingNamespace() = runTest {
        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        sot.emit(TEST_KEY_1, TEST_USER_1)
        sot.emit(TEST_KEY_2, TEST_USER_2)
        sot.emit(ARTICLE_KEY_1, TEST_USER_1)
        val memory = MemoryCacheImpl<StoreKey, TestUser>(maxSize = 100, ttl = 10.minutes, timeSource = TimeSource.SYSTEM)
        memory.put(TEST_KEY_1, TEST_USER_1)
        memory.put(TEST_KEY_2, TEST_USER_2)
        memory.put(ARTICLE_KEY_1, TEST_USER_1)
        val store = createStore(scope = backgroundScope, sot = sot, memory = memory)

        store.invalidateNamespace(TEST_KEY_1.namespace)
        advanceUntilIdle()

        assertNull(memory.get(TEST_KEY_1))
        assertNull(memory.get(TEST_KEY_2))
        assertEquals(TEST_USER_1, memory.get(ARTICLE_KEY_1))
        assertEquals(TEST_USER_1, sot.getData(TEST_KEY_1))
        assertEquals(TEST_USER_2, sot.getData(TEST_KEY_2))
        assertEquals(TEST_USER_1, sot.getData(ARTICLE_KEY_1))
        assertTrue(sot.deletes.isEmpty())
    }

    @Test
    fun clear_namespace_isDestructiveForMatchingNamespaceOnly() = runTest {
        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        sot.emit(TEST_KEY_1, TEST_USER_1)
        sot.emit(TEST_KEY_2, TEST_USER_2)
        sot.emit(ARTICLE_KEY_1, TEST_USER_1)
        val memory = MemoryCacheImpl<StoreKey, TestUser>(maxSize = 100, ttl = 10.minutes, timeSource = TimeSource.SYSTEM)
        memory.put(TEST_KEY_1, TEST_USER_1)
        memory.put(TEST_KEY_2, TEST_USER_2)
        memory.put(ARTICLE_KEY_1, TEST_USER_1)
        val store = createStore(scope = backgroundScope, sot = sot, memory = memory)

        store.clearNamespace(TEST_KEY_1.namespace)
        advanceUntilIdle()

        assertNull(memory.get(TEST_KEY_1))
        assertNull(memory.get(TEST_KEY_2))
        assertEquals(TEST_USER_1, memory.get(ARTICLE_KEY_1))
        assertNull(sot.getData(TEST_KEY_1))
        assertNull(sot.getData(TEST_KEY_2))
        assertEquals(TEST_USER_1, sot.getData(ARTICLE_KEY_1))
    }

    @Test
    fun stream_activeCollector_refetchesAfterInvalidate() = runTest {
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        fetcher.respondWith(TEST_KEY_1, TEST_USER_1)
        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        val store = createStore(scope = backgroundScope, sot = sot, fetcher = fetcher)

        store.stream(TEST_KEY_1, Freshness.CachedOrFetch).test {
            assertIs<StoreResult.Loading>(awaitItem())
            val first = awaitItem()
            assertIs<StoreResult.Data<TestUser>>(first)
            assertEquals(TEST_USER_1, first.value)

            fetcher.respondWith(TEST_KEY_1, TEST_USER_2)
            store.invalidate(TEST_KEY_1)

            val second = awaitItem()
            assertIs<StoreResult.Data<TestUser>>(second)
            assertEquals(TEST_USER_2, second.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_mustBeFresh_fetchFailureRemainsTerminal() = runTest {
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        fetcher.respondWithError(TEST_KEY_1, TestNetworkException("offline"))
        val store = createStore(scope = backgroundScope, fetcher = fetcher)

        store.stream(TEST_KEY_1, Freshness.MustBeFresh).test {
            val error = awaitItem()
            assertIs<StoreResult.Error>(error)
            assertFalse(error.servedStale)
            awaitComplete()
        }
    }

    @Test
    fun stream_staleIfError_honorsStaleWindow() = runTest {
        val timeSource = TestTimeSource.atNow()
        val cachedAt = timeSource.now() - 1.minutes
        val converter = fixedMetaConverter(cachedAt)
        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        sot.emit(TEST_KEY_1, TEST_USER_1)
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        fetcher.respondWithError(TEST_KEY_1, TestNetworkException("temporary"))
        val store = createStore(
            scope = backgroundScope,
            sot = sot,
            fetcher = fetcher,
            converter = converter,
            timeSource = timeSource,
            staleIfError = 2.minutes
        )

        store.stream(TEST_KEY_1, Freshness.StaleIfError).test {
            val data = awaitItem()
            assertIs<StoreResult.Data<TestUser>>(data)

            val error = awaitItem()
            assertIs<StoreResult.Error>(error)
            assertTrue(error.servedStale)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun minAge_isEnforcedEndToEnd() = runTest {
        val timeSource = TestTimeSource.atNow()
        val cachedAt = timeSource.now() - 1.minutes
        val converter = fixedMetaConverter(cachedAt)
        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        sot.emit(TEST_KEY_1, TEST_USER_1)
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        fetcher.respondWith(TEST_KEY_1, TEST_USER_2)
        val store = createStore(
            scope = backgroundScope,
            sot = sot,
            fetcher = fetcher,
            converter = converter,
            timeSource = timeSource
        )

        val cached = store.get(TEST_KEY_1, Freshness.MinAge(2.minutes))
        assertEquals(TEST_USER_1, cached)
        assertEquals(0, fetcher.fetchCount(TEST_KEY_1))

        store.stream(TEST_KEY_1, Freshness.MinAge(30.seconds)).test {
            val stale = awaitItem()
            assertIs<StoreResult.Data<TestUser>>(stale)
            assertEquals(TEST_USER_1, stale.value)

            val refreshed = awaitItem()
            assertIs<StoreResult.Data<TestUser>>(refreshed)
            assertEquals(TEST_USER_2, refreshed.value)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, fetcher.fetchCount(TEST_KEY_1))
    }

    @Test
    fun conditionalRequest_isPropagatedWhenDataIsStale() = runTest {
        val timeSource = TestTimeSource.atNow()
        val cachedAt = timeSource.now() - 10.minutes
        val converter = fixedMetaConverter(cachedAt, etag = "etag-123")
        val sot = FakeSourceOfTruth<StoreKey, TestUser>()
        sot.emit(TEST_KEY_1, TEST_USER_1)
        val fetcher = FakeFetcher<StoreKey, TestUser>()
        fetcher.respondWith(TEST_KEY_1, TEST_USER_2)
        val validator = FreshnessValidator<StoreKey, Any?> {
            FetchPlan.Conditional(
                ConditionalRequest(
                    etag = "etag-123",
                    lastModified = cachedAt
                )
            )
        }
        val store = createStore(
            scope = backgroundScope,
            sot = sot,
            fetcher = fetcher,
            converter = converter,
            validator = validator,
            timeSource = timeSource
        )

        val value = store.get(TEST_KEY_1, Freshness.MustBeFresh)
        assertEquals(TEST_USER_2, value)

        val request = fetcher.fetchRequests.firstOrNull()?.second
        assertNotNull(request)
        assertEquals("etag-123", request.conditional?.etag)
        assertEquals(cachedAt, request.conditional?.lastModified)
    }
}

suspend fun runReadCoreContractSmokeScenario() {
    val fetcher = FakeFetcher<StoreKey, TestUser>()
    fetcher.respondWith(TEST_KEY_1, TEST_USER_1)
    val store = RealReadStore(
        sot = FakeSourceOfTruth<StoreKey, TestUser>(),
        fetcher = fetcher,
        converter = object : Converter<StoreKey, TestUser, TestUser, TestUser, TestUser> {
            override suspend fun netToDbWrite(key: StoreKey, net: TestUser) = net
            override suspend fun dbReadToDomain(key: StoreKey, db: TestUser) = db
            override suspend fun dbMetaFromProjection(db: TestUser) = null
        },
        bookkeeper = FakeBookkeeper(),
        validator = defaultAnyMetaValidator(),
        memory = MemoryCacheImpl(maxSize = 100, ttl = 10.minutes, timeSource = TimeSource.SYSTEM),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        timeSource = TimeSource.SYSTEM
    )
    val value = store.get(TEST_KEY_1, Freshness.MustBeFresh)
    assertEquals(TEST_USER_1, value)
}

private fun fixedMetaConverter(
    updatedAt: Instant,
    etag: String? = null
): Converter<StoreKey, TestUser, TestUser, TestUser, TestUser> {
    return object : Converter<StoreKey, TestUser, TestUser, TestUser, TestUser> {
        override suspend fun netToDbWrite(key: StoreKey, net: TestUser) = net
        override suspend fun dbReadToDomain(key: StoreKey, db: TestUser) = db
        override suspend fun dbMetaFromProjection(db: TestUser): Any? {
            return DefaultDbMeta(updatedAt = updatedAt, etag = etag)
        }
    }
}

private fun createStore(
    sot: SourceOfTruth<StoreKey, TestUser, TestUser> = FakeSourceOfTruth(),
    fetcher: Fetcher<StoreKey, TestUser> = FakeFetcher(),
    converter: Converter<StoreKey, TestUser, TestUser, TestUser, TestUser> = object : Converter<StoreKey, TestUser, TestUser, TestUser, TestUser> {
        override suspend fun netToDbWrite(key: StoreKey, net: TestUser) = net
        override suspend fun dbReadToDomain(key: StoreKey, db: TestUser) = db
        override suspend fun dbMetaFromProjection(db: TestUser) = null
    },
    bookkeeper: Bookkeeper<StoreKey> = FakeBookkeeper(),
    validator: FreshnessValidator<StoreKey, Any?> = defaultAnyMetaValidator(),
    memory: MemoryCache<StoreKey, TestUser> = MemoryCacheImpl(maxSize = 100, ttl = 10.minutes, timeSource = TimeSource.SYSTEM),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    timeSource: TimeSource = TimeSource.SYSTEM,
    staleIfError: Duration? = null
): RealReadStore<StoreKey, TestUser, TestUser, TestUser, TestUser> {
    return RealReadStore(
        sot = sot,
        fetcher = fetcher,
        converter = converter,
        bookkeeper = bookkeeper,
        validator = validator,
        memory = memory,
        staleErrorDuration = staleIfError,
        scope = scope,
        timeSource = timeSource
    )
}

private fun defaultAnyMetaValidator(
    ttl: Duration = 5.minutes
): FreshnessValidator<StoreKey, Any?> {
    val delegate = DefaultFreshnessValidator<StoreKey>(ttl)
    return FreshnessValidator { ctx ->
        val meta = ctx.sotMeta as? DefaultDbMeta
        delegate.plan(
            FreshnessContext(
                key = ctx.key,
                now = ctx.now,
                freshness = ctx.freshness,
                sotMeta = meta,
                status = ctx.status
            )
        )
    }
}
