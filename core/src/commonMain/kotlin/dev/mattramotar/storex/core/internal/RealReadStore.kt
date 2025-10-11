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
import dev.mattramotar.storex.core.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlin.time.Duration

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
    private val timeSource: TimeSource = TimeSource.SYSTEM
) : Store<Key, Domain>, AutoCloseable {

    private val storeScope = scope
    private val fetchSingleFlight = SingleFlight<Key, Unit>()
    private val perKeyMutex = KeyMutex<Key>()
    private fun now(): Instant = timeSource.now()

    override fun stream(key: Key, freshness: Freshness): Flow<StoreResult<Domain>> = channelFlow {
        val errorEvents = Channel<StoreResult.Error>(capacity = Channel.BUFFERED)

        // 1. Read from SoT to check if we have data
        val initialDb: ReadEntity? = try {
            sot.reader(key).firstOrNull()
        } catch (e: Exception) {
            null
        }
        val hadCachedData = initialDb != null

        // 2. Determine if we need to fetch
        val dbMeta = initialDb?.let { converter.dbMetaFromProjection(it) }
        val statusProvider = { bookkeeper.lastStatus(key) }
        val status = statusProvider()
        val nowInstant = now()
        val plan = validator.plan(FreshnessContext(key, nowInstant, freshness, dbMeta, status))
        val staleWindow = if (validator is StaleIfErrorPolicy) {
            validator.staleIfErrorDuration.takeIf { it > Duration.ZERO }
        } else {
            null
        }
        val serveStaleOnError: () -> Boolean = {
            shouldServeStale(
                freshness = freshness,
                hadCachedData = hadCachedData,
                staleWindow = staleWindow,
                dbMeta = dbMeta,
                status = statusProvider(),
                now = now()
            )
        }

        // 3. Execute fetch if needed
        when (freshness) {
            Freshness.MustBeFresh -> {
                if (plan !is FetchPlan.Skip) {
                    try {
                        runBlockingFetch(key, plan, errorEvents)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        send(StoreResult.Error(t, servedStale = false))
                        return@channelFlow
                    }
                }
            }
            else -> if (plan !is FetchPlan.Skip) {
                launch {
                    try {
                        runBlockingFetch(key, plan, errorEvents)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        errorEvents.send(StoreResult.Error(t, servedStale = serveStaleOnError()))
                    }
                }
            }
        }

        // 4. Emit loading state if no cached data
        if (initialDb == null) send(StoreResult.Loading(fromCache = false))

        // 5. Stream updates from SoT
        launch {
            sot.reader(key).mapNotNull { it }.collect { dbValue ->
                val domain = converter.dbReadToDomain(key, dbValue)
                val meta = converter.dbMetaFromProjection(dbValue)
                val updatedAt = meta.extractUpdatedAt() ?: Instant.fromEpochMilliseconds(0)
                val age = now() - updatedAt
                memory.put(key, domain)
                send(StoreResult.Data(domain, origin = Origin.SOT, age = age))
            }
        }

        launch { for (e in errorEvents) send(e) }

        // Keep flow open until canceled - jobs will be automatically canceled
        awaitCancellation()
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
        storeScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            memory.remove(key)
            sot.clearCache(key)
            sot.delete(key)
        }
    }

    override fun invalidateNamespace(ns: StoreNamespace) {
        storeScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            memory.clear()
        }
    }

    override fun invalidateAll() {
        storeScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            memory.clear()
        }
    }

    override fun close() {
        storeScope.cancel()
    }

    private suspend fun runBlockingFetch(key: Key, plan: FetchPlan, errorEvents: Channel<StoreResult.Error>) {
        fetchSingleFlight.launch(storeScope, key) {
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
                        bookkeeper.recordFailure(key, resp.error, now())
                        // Let caller handle the error (for MustBeFresh) or send to errorEvents (for background fetch)
                        throw resp.error
                    }
                }
            }
        }.await()
    }
}

private fun shouldServeStale(
    freshness: Freshness,
    hadCachedData: Boolean,
    staleWindow: Duration?,
    dbMeta: Any?,
    status: KeyStatus,
    now: Instant
): Boolean {
    if (!hadCachedData) return false

    val allowStale = when (freshness) {
        Freshness.MustBeFresh -> return false
        Freshness.StaleIfError,
        Freshness.CachedOrFetch -> true
        is Freshness.MinAge -> true
    }

    if (!allowStale) return false

    val window = staleWindow ?: return true
    val referenceTime = dbMeta.extractUpdatedAt() ?: status.lastSuccessAt ?: return false
    val age = now - referenceTime
    return age <= window
}

/* Helper to extract updatedAt from arbitrary meta objects */
private fun Any?.extractUpdatedAt(): Instant? = when (this) {
    is Instant -> this
    is DefaultDbMeta -> this.updatedAt
    else -> null
}
