package dev.mattramotar.storex.mutablestore.telemetry

import kotlin.time.Duration

/**
 * Sealed class for all telemetry events.
 * Expand with whatever fields your production
 * teams need to measure or trace.
 */
sealed class StoreTelemetryEvent<Key : Any, Value : Any> : MutableStoreTelemetryEvent<Key, Nothing, Value, Nothing>() {

    /**
     * Emitted when the Store is about to fetch from the network.
     *
     * @param key The input key used for the fetch.
     */
    data class FetchStarted<Key : Any, Value : Any>(
        val key: Key
    ) : StoreTelemetryEvent<Key, Value>()

    /**
     * Emitted when a fetch has completed, regardless of success/failure.
     *
     * @param key The input key used for the fetch.
     * @param duration The time spent in the fetcher.
     * @param success Whether the fetch succeeded (non-null).
     */
    data class FetchCompleted<Key : Any, Value : Any>(
        val key: Key,
        val duration: Duration,
        val success: Boolean
    ) : StoreTelemetryEvent<Key, Value>()

    /**
     * Emitted when a memory cache hit occurs.
     *
     * @param key The input key that was found in memory.
     */
    data class MemoryHit<Key : Any, Value : Any>(
        val key: Key
    ) : StoreTelemetryEvent<Key, Value>()


    /**
     * Emitted when a SOT hit occurs.
     *
     * @param key The input key that was found in SOT.
     */
    data class SourceOfTruthHit<Key : Any, Value : Any>(
        val key: Key
    ) : StoreTelemetryEvent<Key, Value>()

    /**
     * Emitted when SOT is written to.
     *
     * @param key The input key that was written to SOT.
     */
    data class SourceOfTruthWrite<Key : Any, Value : Any>(
        val key: Key
    ) : StoreTelemetryEvent<Key, Value>()

    /**
     * Emitted when cache is written to.
     *
     * @param key The input key that was written to cache.
     */
    data class MemoryWrite<Key : Any, Value : Any>(
        val key: Key
    ) : StoreTelemetryEvent<Key, Value>()


    /**
     * Emitted when the read pipeline makes a decision about skipping memory or SOT.
     *
     * @param key The input key.
     * @param skipMemoryCache Whether memory was skipped.
     * @param skipSourceOfTruth Whether SOT was skipped.
     */
    data class ReadPolicyDecision<Key : Any, Value : Any>(
        val key: Key,
        val skipMemoryCache: Boolean,
        val skipSourceOfTruth: Boolean,
        val forceNetworkFetch: Boolean,
        val fallbackToSOT: Boolean
    ) : StoreTelemetryEvent<Key, Value>()

    /**
     * Emitted when the store invalidates or clears the key.
     */
    data class Invalidate<Key : Any, Value : Any>(
        val key: Key
    ) : StoreTelemetryEvent<Key, Value>()

    data class MemoryClear<Key : Any, Value : Any>(
        val key: Key
    ) : StoreTelemetryEvent<Key, Value>()

    data class SourceOfTruthClear<Key : Any, Value : Any>(
        val key: Key
    ) : StoreTelemetryEvent<Key, Value>()


    /**
     * Emitted when the store invalidates all.
     */
    data class InvalidateAll<Key: Any, Value: Any>(
        val unit: Unit = Unit
    ) : StoreTelemetryEvent<Key, Value>()

    data class MemoryEntryExpired<Key : Any, Value : Any>(
        val key: Key
    ) : StoreTelemetryEvent<Key, Value>()

}