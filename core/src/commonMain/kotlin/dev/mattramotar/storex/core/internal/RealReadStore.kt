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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
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
    private val staleErrorDuration: Duration? = null,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val timeSource: TimeSource = TimeSource.SYSTEM
) : Store<Key, Domain>, AutoCloseable {

    private val storeScope = scope
    private val fetchSingleFlight = SingleFlight<Key, Unit>()
    private val perKeyMutex = KeyMutex<Key>()
    private val knownKeys = LinkedHashSet<Key>()
    private val knownKeysMutex = Mutex()
    private val invalidationSignals = MutableSharedFlow<InvalidationSignal<Key>>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private fun now(): Instant = timeSource.now()

    override fun stream(key: Key, freshness: Freshness): Flow<StoreResult<Domain>> = channelFlow {
        rememberKey(key)
        val errorEvents = Channel<StoreResult.Error>(capacity = Channel.BUFFERED)

        // 1. Read from SoT to check if we have data
        val initialDb: ReadEntity? = try {
            sot.reader(key).firstOrNull()
        } catch (e: Exception) {
            null
        }
        val hasCachedData = MutableStateFlow(initialDb != null)

        // 2. Determine if we need to fetch
        val dbMeta = initialDb?.let { converter.dbMetaFromProjection(it) }
        val status = bookkeeper.lastStatus(key)
        val plan = validator.plan(FreshnessContext(key, now(), freshness, dbMeta, status))
        val latestMeta = MutableStateFlow<Any?>(dbMeta)

        fun canServeStaleNow(): Boolean {
            if (!hasCachedData.value) return false
            val window = staleErrorDuration ?: return true
            val meta = latestMeta.value?.extractUpdatedAt()
                ?: bookkeeper.lastStatus(key).lastSuccessAt
                ?: return false
            return (now() - meta) <= window
        }

        // 3. Execute fetch if needed
        when (freshness) {
            Freshness.MustBeFresh -> {
                if (plan !is FetchPlan.Skip) {
                    try {
                        runBlockingFetch(key, plan)
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
                        runBlockingFetch(key, plan)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        val servedStale = canServeStaleNow()
                        errorEvents.send(StoreResult.Error(t, servedStale = servedStale))
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
                latestMeta.value = meta
                hasCachedData.value = true
                memory.put(key, domain)
                send(StoreResult.Data(domain, origin = Origin.SOT, age = age))
            }
        }

        launch {
            invalidationSignals.collect { signal ->
                if (!signal.matches(key)) return@collect

                if (signal.destructive) {
                    hasCachedData.value = false
                    latestMeta.value = null
                    send(StoreResult.Loading(fromCache = false))
                }

                try {
                    runBlockingFetch(key, FetchPlan.Unconditional)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    val servedStale = !signal.destructive && canServeStaleNow()
                    errorEvents.send(StoreResult.Error(t, servedStale = servedStale))
                }
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
            rememberKey(key)
            memory.remove(key)
            sot.clearCache(key)
            invalidationSignals.tryEmit(InvalidationSignal(key = key, destructive = false))
        }
    }

    override fun invalidateNamespace(ns: StoreNamespace) {
        storeScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            val keys = keysInNamespace(ns)
            keys.forEach { key ->
                memory.remove(key)
                sot.clearCache(key)
            }
            invalidationSignals.tryEmit(InvalidationSignal(namespace = ns, destructive = false))
        }
    }

    override fun invalidateAll() {
        storeScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            val keys = keysForMaintenance()
            memory.clear()
            keys.forEach { key ->
                sot.clearCache(key)
            }
            invalidationSignals.tryEmit(InvalidationSignal(all = true, destructive = false))
        }
    }

    override fun clear(key: Key) {
        storeScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            rememberKey(key)
            memory.remove(key)
            sot.clearCache(key)
            sot.delete(key)
            forgetKey(key)
            invalidationSignals.tryEmit(InvalidationSignal(key = key, destructive = true))
        }
    }

    override fun clearNamespace(ns: StoreNamespace) {
        storeScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            val keys = keysInNamespace(ns)
            keys.forEach { key ->
                memory.remove(key)
                sot.clearCache(key)
                sot.delete(key)
                forgetKey(key)
            }
            invalidationSignals.tryEmit(InvalidationSignal(namespace = ns, destructive = true))
        }
    }

    override fun clearAll() {
        storeScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            val keys = keysForMaintenance()
            memory.clear()
            keys.forEach { key ->
                sot.clearCache(key)
                sot.delete(key)
            }
            clearKnownKeys()
            invalidationSignals.tryEmit(InvalidationSignal(all = true, destructive = true))
        }
    }

    override fun close() {
        storeScope.cancel()
    }

    private suspend fun runBlockingFetch(key: Key, plan: FetchPlan) {
        rememberKey(key)
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

    private suspend fun rememberKey(key: Key) {
        knownKeysMutex.withLock {
            knownKeys.add(key)
        }
    }

    private suspend fun forgetKey(key: Key) {
        knownKeysMutex.withLock {
            knownKeys.remove(key)
        }
    }

    private suspend fun clearKnownKeys() {
        knownKeysMutex.withLock {
            knownKeys.clear()
        }
    }

    private suspend fun keysForMaintenance(): List<Key> {
        val memoryKeys = memory.keys()
        val known = knownKeysMutex.withLock { knownKeys.toSet() }
        return (known + memoryKeys).toList()
    }

    private suspend fun keysInNamespace(namespace: StoreNamespace): List<Key> {
        return keysForMaintenance().filter { key -> key.namespace == namespace }
    }
}

private data class InvalidationSignal<Key : StoreKey>(
    val key: Key? = null,
    val namespace: StoreNamespace? = null,
    val all: Boolean = false,
    val destructive: Boolean
) {
    fun matches(target: Key): Boolean {
        return all || key == target || namespace == target.namespace
    }
}

/* Helper to extract updatedAt from arbitrary meta objects */
private fun Any?.extractUpdatedAt(): Instant? = when (this) {
    is Instant -> this
    is DefaultDbMeta -> this.updatedAt
    else -> null
}
