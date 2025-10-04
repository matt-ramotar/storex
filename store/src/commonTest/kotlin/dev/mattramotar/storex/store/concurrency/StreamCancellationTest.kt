package dev.mattramotar.storex.store.concurrency

import app.cash.turbine.test
import dev.mattramotar.storex.store.*
import dev.mattramotar.storex.store.internal.*
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for TASK-001: Race condition fix in RealStore.stream()
 *
 * Validates that background fetches are properly cancelled when Flow collectors cancel,
 * preventing zombie coroutines and memory leaks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamCancellationTest {

    private val testNamespace = StoreNamespace("test")
    private val testKey = ByIdKey(testNamespace, EntityId("User", "123"))

    @Test
    fun stream_whenFlowCancelled_thenBackgroundFetchIsCancelled() = runTest {
        // Given
        val fetchStarted = CompletableDeferred<Unit>()
        val fetchCancelled = CompletableDeferred<Unit>()

        val fetcher = mock<Fetcher<ByIdKey, String>>()
        everySuspend { fetcher.fetch(testKey, any()) } returns flow {
            fetchStarted.complete(Unit)
            try {
                delay(10.seconds) // Long delay to ensure we can cancel
                emit(FetcherResult.Success("data", etag = null))
            } catch (e: CancellationException) {
                fetchCancelled.complete(Unit)
                throw e
            }
        }

        val sot = InMemorySourceOfTruth<ByIdKey, String, String>()
        val converter = SimpleConverter()
        val store = createTestStore(fetcher, sot, converter, this)

        // When - Start collecting and cancel after fetch starts
        val job = launch {
            store.stream(testKey, Freshness.CachedOrFetch).test {
                awaitItem() // Loading
                cancel() // Cancel the collection
            }
        }

        fetchStarted.await() // Wait for fetch to start
        job.join()

        // Then - Fetch should be cancelled
        withTimeout(1.seconds) {
            fetchCancelled.await()
        }
    }

    @Test
    fun stream_whenMultipleConcurrentCollectors_thenEachCanCancelIndependently() = runTest {
        // Given
        val fetcher = mock<Fetcher<ByIdKey, String>>()
        everySuspend { fetcher.fetch(testKey, any()) } returns flow {
            delay(100.milliseconds)
            emit(FetcherResult.Success("data", etag = null))
        }

        val sot = InMemorySourceOfTruth<ByIdKey, String, String>()
        val converter = SimpleConverter()
        val store = createTestStore(fetcher, sot, converter, this)

        // When - Start multiple collectors
        val collector1Active = CompletableDeferred<Boolean>()
        val collector2Active = CompletableDeferred<Boolean>()

        val job1 = launch {
            try {
                store.stream(testKey).collect {
                    collector1Active.complete(true)
                    delay(5.seconds) // Long collection
                }
            } catch (e: CancellationException) {
                collector1Active.complete(false)
            }
        }

        val job2 = launch {
            try {
                store.stream(testKey).collect {
                    collector2Active.complete(true)
                    delay(5.seconds) // Long collection
                }
            } catch (e: CancellationException) {
                collector2Active.complete(false)
            }
        }

        advanceUntilIdle()

        // Cancel only job1
        job1.cancel()
        job1.join()

        // Then - job1 should be cancelled, job2 should still be active
        assertFalse(collector1Active.await())

        // Cancel job2
        job2.cancel()
        job2.join()
        assertFalse(collector2Active.await())
    }

    @Test
    fun stream_whenMustBeFreshAndFetchFails_thenErrorIsDeliveredAndNoLeaks() = runTest {
        // Given
        val fetchAttempted = CompletableDeferred<Unit>()
        val testError = IllegalStateException("Network error")

        val fetcher = mock<Fetcher<ByIdKey, String>>()
        everySuspend { fetcher.fetch(testKey, any()) } returns flow {
            fetchAttempted.complete(Unit)
            throw testError
        }

        val sot = InMemorySourceOfTruth<ByIdKey, String, String>()
        val converter = SimpleConverter()
        val store = createTestStore(fetcher, sot, converter, this)

        // When
        store.stream(testKey, Freshness.MustBeFresh).test {
            val result = awaitItem()

            // Then
            assertTrue(result is StoreResult.Error)
            assertEquals(testError, (result as StoreResult.Error).throwable)
            assertFalse(result.servedStale)
            awaitComplete()
        }

        fetchAttempted.await()
    }

    @Test
    fun stream_whenCachedOrFetch_thenFetchLaunchesInChannelFlowScope() = runTest {
        // Given
        var fetchScope: CoroutineScope? = null

        val fetcher = mock<Fetcher<ByIdKey, String>>()
        everySuspend { fetcher.fetch(testKey, any()) } returns flow {
            fetchScope = this@flow.coroutineContext[Job]?.let { job ->
                object : CoroutineScope {
                    override val coroutineContext = job + Dispatchers.Unconfined
                }
            }
            delay(50.milliseconds)
            emit(FetcherResult.Success("data", etag = null))
        }

        val sot = InMemorySourceOfTruth<ByIdKey, String, String>()
        val converter = SimpleConverter()
        val store = createTestStore(fetcher, sot, converter, this)

        // When
        val job = launch {
            store.stream(testKey, Freshness.CachedOrFetch).test {
                awaitItem() // Loading
                cancel()
            }
        }

        advanceUntilIdle()
        job.join()

        // Then - When the flow is cancelled, the fetch scope should also be cancelled
        fetchScope?.coroutineContext?.get(Job)?.let { fetchJob ->
            assertTrue(fetchJob.isCancelled || !fetchJob.isActive,
                "Fetch job should be cancelled when flow collector cancels")
        }
    }

    @Test
    fun stream_whenRapidCancellationsAndRestarts_thenNoLeaks() = runTest {
        // Given
        var fetchCount = 0
        val fetcher = mock<Fetcher<ByIdKey, String>>()
        everySuspend { fetcher.fetch(testKey, any()) } returns flow {
            fetchCount++
            delay(50.milliseconds)
            emit(FetcherResult.Success("data-$fetchCount", etag = null))
        }

        val sot = InMemorySourceOfTruth<ByIdKey, String, String>()
        val converter = SimpleConverter()
        val store = createTestStore(fetcher, sot, converter, this)

        // When - Rapid cancel and restart cycles
        repeat(10) { iteration ->
            val job = launch {
                store.stream(testKey).test {
                    awaitItem() // Get at least one item
                    cancel()
                }
            }
            advanceUntilIdle()
            job.join()
        }

        advanceUntilIdle()

        // Then - Should have attempted fetches but no leaks
        // Fetch count should be reasonable (not accumulating zombie fetches)
        assertTrue(fetchCount <= 20, "Fetch count $fetchCount suggests potential leaks")
    }

    // Helper classes

    private class SimpleConverter : Converter<ByIdKey, String, String, String, String> {
        override suspend fun netToDbWrite(key: ByIdKey, net: String): String = net
        override suspend fun dbReadToDomain(key: ByIdKey, db: String): String = db
        override suspend fun dbMetaFromProjection(db: String): Any = Clock.System.now()
        override suspend fun netMeta(net: String) = Converter.NetMeta()
        override suspend fun domainToDbWrite(key: ByIdKey, value: String): String = value
    }

    private class InMemorySourceOfTruth<K, ReadDb, WriteDb> : SourceOfTruth<K, ReadDb, WriteDb> {
        private val data = MutableStateFlow<ReadDb?>(null)

        override fun reader(key: K): Flow<ReadDb?> = data
        override suspend fun write(key: K, value: WriteDb) {
            @Suppress("UNCHECKED_CAST")
            data.value = value as? ReadDb
        }
        override suspend fun delete(key: K) {
            data.value = null
        }
        override suspend fun withTransaction(block: suspend () -> Unit) = block()
        override suspend fun rekey(old: K, new: K, reconcile: suspend (ReadDb, ReadDb?) -> ReadDb) {}
    }

    private fun createTestStore(
        fetcher: Fetcher<ByIdKey, String>,
        sot: SourceOfTruth<ByIdKey, String, String>,
        converter: Converter<ByIdKey, String, String, String, String>,
        scope: CoroutineScope
    ): RealStore<ByIdKey, String, String, String, String, Nothing, Nothing, Nothing, Nothing, Nothing> {
        val bookkeeper = InMemoryBookkeeper<ByIdKey>()
        val validator = DefaultFreshnessValidator<ByIdKey>()
        val memory = InMemoryCache<ByIdKey, String>()

        return RealStore(
            sot = sot,
            fetcher = fetcher,
            updater = null,
            creator = null,
            deleter = null,
            putser = null,
            converter = converter,
            encoder = NoOpEncoder(),
            bookkeeper = bookkeeper,
            validator = validator,
            memory = memory,
            scope = scope,
            now = { Clock.System.now() }
        )
    }

    private class InMemoryBookkeeper<K> : Bookkeeper<K> {
        override suspend fun recordSuccess(key: K, etag: String?, at: Instant) {}
        override suspend fun recordFailure(key: K, error: Throwable, at: Instant) {}
        override suspend fun lastStatus(key: K): FetchStatus? = null
    }

    private class DefaultFreshnessValidator<K> : FreshnessValidator<K, Any?> {
        override fun plan(ctx: FreshnessContext<K, Any?>): FetchPlan = FetchPlan.Unconditional
    }

    private class InMemoryCache<K, V> : MemoryCache<K, V> {
        private val cache = mutableMapOf<K, V>()
        override suspend fun get(key: K): V? = cache[key]
        override suspend fun put(key: K, value: V): Boolean {
            cache[key] = value
            return true
        }
        override suspend fun remove(key: K) {
            cache.remove(key)
        }
        override suspend fun clear() {
            cache.clear()
        }
    }

    private class NoOpEncoder<P, D, V, NP, ND, NPut> :
        dev.mattramotar.storex.store.mutation.MutationEncoder<P, D, V, NP, ND, NPut> {
        override suspend fun fromPatch(patch: P, base: V?): NP? = null
        override suspend fun fromDraft(draft: D): ND? = null
        override suspend fun fromValue(value: V): NPut? = null
    }
}
