package dev.mattramotar.storex.store.core.impl

import dev.mattramotar.storex.mutablestore.telemetry.StoreTelemetry
import dev.mattramotar.storex.mutablestore.telemetry.StoreTelemetryEvent
import dev.mattramotar.storex.store.core.api.Cache
import dev.mattramotar.storex.store.core.api.MemoryPolicy
import dev.mattramotar.storex.store.core.api.SourceOfTruth
import dev.mattramotar.storex.store.core.api.Store
import dev.mattramotar.storex.store.internal.hooks.ReadPolicyContext
import dev.mattramotar.storex.store.internal.hooks.StoreDataHooks
import dev.mattramotar.storex.store.internal.hooks.StoreFlowTelemetryHooks
import dev.mattramotar.storex.store.internal.hooks.StoreReadPolicyHooks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.time.measureTime

/**
 * A concrete implementation of [Store] that:
 * 1) Ensures thread safety for inflight requests and SharedFlows.
 * 2) Properly logs telemetry events.
 * 3) Manages memory caching and SOT with read policy overrides.
 * 4) Handles forced network fetch, fallback logic, and TTL-based eviction.
 */
class RealStore<Key : Any, Value : Any>(
    private val fetcher: suspend (Key) -> Value?,
    private val memoryCache: Cache<Key, CacheEntry<Value>>,
    private val sourceOfTruth: SourceOfTruth<Key, Value>?,
    private val scope: CoroutineScope,
    private val memoryPolicy: MemoryPolicy<Key, Value>?,
    private val telemetry: StoreTelemetry<Key, Value>? = null,
) : Store<Key, Value>,
    StoreDataHooks<Key, Value>,
    StoreReadPolicyHooks<Key, Value>,
    StoreFlowTelemetryHooks<Key, Value> {

    /**
     * A concurrent map to track in-flight network requests
     * so multiple callers don’t duplicate the same fetch.
     */
    private val inflightRequests = mutableMapOf<Key, Deferred<Value?>>()
    private val inflightRequestsMutex = Mutex()

    /**
     * A concurrent map of per-key flows for streaming the latest data.
     */
    private val keyFlows = mutableMapOf<Key, MutableSharedFlow<Value>>()
    private val keyFlowsMutex = Mutex()


    override fun stream(key: Key): Flow<Value> {
        return stream(key, ReadPolicyContext())
    }

    override suspend fun get(key: Key): Value? {
        return get(key, ReadPolicyContext())
    }

    override suspend fun clear(key: Key) {
        removeFromMemory(key)
        removeFromSOT(key)
        onEvent(StoreTelemetryEvent.Invalidate(key))
    }

    override suspend fun clearAll() {
        memoryCache.invalidateAll()
        sourceOfTruth?.deleteAll()
        onEvent(StoreTelemetryEvent.InvalidateAll())
    }


    override suspend fun write(key: Key, value: Value) {
        writeToSOT(key, value)
        storeInMemory(key, value)
    }

    override suspend fun delete(key: Key) {
        removeFromSOT(key)
        removeFromMemory(key)
    }

    override fun readFromSOT(key: Key): Flow<Value?> = channelFlow {
        sourceOfTruth?.read(key)?.collect { sotValue ->
            send(sotValue)
            onEvent(StoreTelemetryEvent.SourceOfTruthHit(key))
        }
    }


    override fun stream(key: Key, context: ReadPolicyContext): Flow<Value> {
        return flow {
            onEvent(
                StoreTelemetryEvent.ReadPolicyDecision(
                    key,
                    context.skipMemoryCache,
                    context.skipSourceOfTruth,
                    context.forceNetworkFetch,
                    context.fallbackToSOT,
                )
            )

            // 1) Optional memory cache
            val memoryFlow = if (!context.skipMemoryCache) {
                flow {
                    getFromMemoryIfValid(key)?.let { emit(it) }
                }
            } else emptyFlow()

            // 2) SOT flow
            val sourceOfTruthFlow: Flow<Value> = if (sourceOfTruth != null && !context.skipSourceOfTruth) {
                sourceOfTruth.read(key).transform { dbValue ->
                    if (dbValue != null) {
                        onEvent(StoreTelemetryEvent.SourceOfTruthHit(key))
                        storeInMemory(key, dbValue)
                        emit(dbValue)
                    } else {
                        val fetched = fetch(key)
                        if (fetched != null) {
                            storeInMemory(key, fetched)
                            writeToSOT(key, fetched)
                            emit(fetched)
                        }
                    }
                }
            } else emptyFlow()

            // 3) Combine memory + SOT flows
            val mergedFlow = merge(memoryFlow, sourceOfTruthFlow)

            // 4) If forced network, do a refresh, then emit combined results
            val finalFlow = if (context.forceNetworkFetch) {
                flow {
                    refresh(key) // triggers a network fetch
                    emitAll(mergedFlow)
                }
            } else {
                mergedFlow
            }

            // 5) Fallback to SOT if no data was emitted
            if (context.fallbackToSOT) {
                emitAll(finalFlow.onEmpty {
                    // If the flow is empty, attempt direct read from SOT
                    sourceOfTruth?.read(key)?.firstOrNull()?.let { emit(it) }
                })
            } else {
                emitAll(finalFlow)
            }
        }
    }

    override suspend fun get(key: Key, context: ReadPolicyContext): Value? {
        onEvent(
            StoreTelemetryEvent.ReadPolicyDecision(
                key,
                context.skipMemoryCache,
                context.skipSourceOfTruth,
                context.forceNetworkFetch,
                context.fallbackToSOT,
            )
        )

        // 1) Check memory
        if (!context.skipMemoryCache) {
            getFromMemoryIfValid(key)?.let { memoryValue ->
                if (context.forceNetworkFetch) refresh(key)
                return memoryValue
            }
        }

        // 2) Check SOT
        if (!context.skipSourceOfTruth) {
            val sotValue = sourceOfTruth?.read(key)?.firstOrNull()
            if (sotValue != null) {
                storeInMemory(key, sotValue)
                if (context.forceNetworkFetch) refresh(key)
                return sotValue
            }
        }

        // 3) Network fetch (with inflight dedup)
        val fetched = fetch(key)
        if (fetched != null) {
            storeInMemory(key, fetched)
            writeToSOT(key, fetched)
            return fetched
        }

        // 4) Fallback to SOT if requested
        return if (context.fallbackToSOT) {
            val sotFallbackValue = sourceOfTruth?.read(key)?.firstOrNull()
            sotFallbackValue?.let { storeInMemory(key, it) }
            sotFallbackValue
        } else {
            null
        }
    }


    private val _events = MutableSharedFlow<StoreTelemetryEvent<Key, Value>>(replay = 0)
    override val storeFlowTelemetryEvents: SharedFlow<StoreTelemetryEvent<Key, Value>> =
        _events.asSharedFlow()


    /**
     * Fetches from network with inflight deduplication.
     */
    private suspend fun fetch(input: Key): Value? = inflightRequestsMutex.withLock {
        inflightRequests[input]?.let { return it.await() }

        onEvent(StoreTelemetryEvent.FetchStarted(input))

        val fetchJob = scope.async {
            var result: Value? = null
            val duration = measureTime {
                result = fetcher(input)
            }
            // Ensure we remove ourselves from the map after finishing
            inflightRequestsMutex.withLock {
                val deferred = inflightRequests.remove(input)
                deferred?.cancel()
            }
            onEvent(
                StoreTelemetryEvent.FetchCompleted(
                    key = input,
                    duration = duration,
                    success = (result != null)
                )
            )
            result
        }
        inflightRequests[input] = fetchJob
        return@withLock fetchJob.await()
    }

    /**
     * Return a memory-cached value if it hasn't expired.
     * Otherwise, invalidate it and return null.
     */
    private suspend fun getFromMemoryIfValid(input: Key): Value? {
        val entry = memoryCache.getIfPresent(input) ?: return null
        onEvent(StoreTelemetryEvent.MemoryHit(input))

        memoryPolicy?.expireAfterWriteMillis?.let { expiration ->
            val now = nowInEpochMilliseconds()
            if (now - entry.writeTime > expiration) {
                onEvent(StoreTelemetryEvent.MemoryEntryExpired(input))
                memoryCache.invalidate(input)
                onEvent(StoreTelemetryEvent.MemoryClear(input))
                return null
            }
        }
        return entry.value
    }

    /**
     * Write to memory cache (if present) and emit to per-key flow.
     */
    private suspend fun storeInMemory(input: Key, value: Value) {
        memoryCache.let {
            it.put(input, CacheEntry(value))
            onEvent(StoreTelemetryEvent.MemoryWrite(input))
        }

        keyFlowsMutex.withLock {
            val flowForKey = keyFlows.getOrPut(input) {
                MutableSharedFlow(replay = 1)
            }
            flowForKey.emit(value)
        }
    }

    private suspend fun removeFromMemory(input: Key) {
        memoryCache.let {
            it.invalidate(input)
            onEvent(StoreTelemetryEvent.MemoryClear(input))
        }
        // If desired, you could also remove the keyFlows entry.
    }

    private suspend fun writeToSOT(input: Key, value: Value) {
        sourceOfTruth?.let {
            it.write(input, value)
            onEvent(StoreTelemetryEvent.SourceOfTruthWrite(input))
        }
    }

    private suspend fun removeFromSOT(input: Key) {
        sourceOfTruth?.let {
            it.delete(input)
            onEvent(StoreTelemetryEvent.SourceOfTruthClear(input))
        }
    }

    private suspend fun refresh(input: Key) {
        // Force a new fetch
        val refreshed = fetch(input)
        if (refreshed != null) {
            storeInMemory(input, refreshed)
            writeToSOT(input, refreshed)
        }
    }

    private fun nowInEpochMilliseconds(): Long = Clock.System.now().toEpochMilliseconds()


    private suspend fun onEvent(event: StoreTelemetryEvent<Key, Value>) {
        telemetry?.onEvent(event)
        _events.emit(event)
    }

}
