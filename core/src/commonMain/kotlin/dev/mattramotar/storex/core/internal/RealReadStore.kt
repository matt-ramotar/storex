package dev.mattramotar.storex.core.internal

import dev.mattramotar.storex.core.Converter
import dev.mattramotar.storex.core.Freshness
import dev.mattramotar.storex.core.KeyMutex
import dev.mattramotar.storex.core.Origin
import dev.mattramotar.storex.core.SingleFlight
import dev.mattramotar.storex.core.Store
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.StoreNamespace
import dev.mattramotar.storex.core.StoreResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Read-only store implementation.
 *
 * Coordinates between memory cache, Source of Truth (SoT), and remote fetcher to provide:
 * - **Multi-layer caching**: Memory → Persistence → Network
 * - **Freshness control**: Configurable staleness policies
 * - **Reactive updates**: Flow-based observers with automatic invalidation
 *
 * ## Architecture
 *
 * ```
 * ┌─────────┐      ┌─────────┐      ┌─────────┐
 * │ Memory  │ ───> │   SoT   │ ───> │ Fetcher │
 * │  Cache  │ <─── │ (Local) │ <─── │ (Remote)│
 * └─────────┘      └─────────┘      └─────────┘
 *     Fast           Durable          Source
 * ```
 *
 * @param Key The [StoreKey] subtype identifying stored entities
 * @param Domain The application's domain model type
 * @param ReadEntity The database read projection type
 * @param WriteEntity The database write model type
 * @param NetworkResponse The type returned from network fetch operations
 */
class RealReadStore<
    Key : StoreKey,
    Domain : Any,
    ReadEntity,
    WriteEntity,
    NetworkResponse : Any
>(
    private val sot: SourceOfTruth<Key, ReadEntity, WriteEntity>,
    private val fetcher: Fetcher<Key, NetworkResponse>,
    private val converter: Converter<Key, Domain, ReadEntity, NetworkResponse, WriteEntity>,
    private val bookkeeper: Bookkeeper<Key>,
    private val validator: FreshnessValidator<Key, Any?>,
    private val memory: MemoryCache<Key, Domain>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val now: () -> Instant = { Clock.System.now() }
) : Store<Key, Domain>, AutoCloseable {

    private val storeScope = scope
    private val fetchSingleFlight = SingleFlight<Key, Unit>()
    private val perKeyMutex = KeyMutex<Key>()

    override fun stream(key: Key, freshness: Freshness): Flow<StoreResult<Domain>> = channelFlow {
        val errorEvents = Channel<StoreResult.Error>(capacity = Channel.BUFFERED)

        // 1. Read from SoT to check if we have data
        val initialDb: ReadEntity? = try {
            sot.reader(key).firstOrNull()
        } catch (e: Exception) {
            null
        }

        // 2. Determine if we need to fetch
        val dbMeta = initialDb?.let { converter.dbMetaFromProjection(it) }
        val status = bookkeeper.lastStatus(key)
        val plan = validator.plan(FreshnessContext(key, now(), freshness, dbMeta, status))

        // 3. Execute fetch if needed
        suspend fun doFetch() {
            runBlockingFetch(key, plan, errorEvents)
        }

        when (freshness) {
            Freshness.MustBeFresh -> {
                try {
                    if (plan !is FetchPlan.Skip) doFetch()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    send(StoreResult.Error(t, servedStale = false))
                    return@channelFlow
                }
            }
            else -> if (plan !is FetchPlan.Skip) launch { doFetch() }
        }

        // 4. Emit loading state if no cached data
        if (initialDb == null) send(StoreResult.Loading(fromCache = false))

        // 5. Stream updates from SoT
        val sotJob = launch {
            sot.reader(key).mapNotNull { it }.collect { dbValue ->
                val domain = converter.dbReadToDomain(key, dbValue)
                val meta = converter.dbMetaFromProjection(dbValue)
                val updatedAt = meta.extractUpdatedAt() ?: Instant.fromEpochMilliseconds(0)
                val age = now() - updatedAt
                memory.put(key, domain)
                send(StoreResult.Data(domain, origin = Origin.SOT, age = age))
            }
        }

        val errJob = launch { for (e in errorEvents) send(e) }
        joinAll(sotJob, errJob)
    }

    override suspend fun get(key: Key, freshness: Freshness): Domain {
        // Fast path: serve from memory if available
        if (freshness == Freshness.CachedOrFetch) {
            memory.get(key)?.let { return it }
        }

        // Slow path: stream and get first data
        return stream(key, freshness).mapNotNull { res ->
            when (res) {
                is StoreResult.Data -> res.value
                is StoreResult.Error -> if (!res.servedStale) throw res.throwable else null
                else -> null
            }
        }.firstOrNull() ?: error("Store.get($key) finished without data or error")
    }

    override fun invalidate(key: Key) {
        storeScope.launch { memory.remove(key) }
    }

    override fun invalidateNamespace(ns: StoreNamespace) {
        storeScope.launch { memory.clear() }
    }

    override fun invalidateAll() {
        storeScope.launch { memory.clear() }
    }

    override fun close() {
        storeScope.cancel()
    }

    private suspend fun runBlockingFetch(key: Key, plan: FetchPlan, errorEvents: Channel<StoreResult.Error>) {
        fetchSingleFlight.launch(storeScope, key) {
            try {
                val req = when (plan) {
                    is FetchPlan.Conditional -> FetchRequest(conditional = plan.request)
                    FetchPlan.Unconditional -> FetchRequest()
                    FetchPlan.Skip -> return@launch
                }

                fetcher.fetch(key, req).collect { resp ->
                    when (resp) {
                        is FetcherResult.Success -> {
                            val writeDb: WriteEntity = converter.netToDbWrite(key, resp.body)
                            val m = perKeyMutex.forKey(key)
                            m.withLock {
                                sot.write(key, writeDb)
                            }
                            bookkeeper.recordSuccess(key, resp.etag, now())
                        }
                        is FetcherResult.NotModified -> {
                            bookkeeper.recordSuccess(key, resp.etag, now())
                        }
                        is FetcherResult.Error -> {
                            throw resp.error
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                bookkeeper.recordFailure(key, t, now())
                errorEvents.trySend(StoreResult.Error(t, servedStale = true))
            }
        }.await()
    }
}

/* Helper to extract updatedAt from arbitrary meta objects */
private fun Any?.extractUpdatedAt(): Instant? = this as? Instant
