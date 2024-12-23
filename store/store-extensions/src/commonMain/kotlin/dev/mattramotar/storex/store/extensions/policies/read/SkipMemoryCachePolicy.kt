package dev.mattramotar.storex.store.extensions.policies.read

import dev.mattramotar.storex.store.core.api.Cache
import dev.mattramotar.storex.store.core.api.SourceOfTruth
import dev.mattramotar.storex.store.extensions.LastFetchTracker
import dev.mattramotar.storex.store.extensions.OfflineChecker
import dev.mattramotar.storex.store.extensions.StoreRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock

/**
 * Skips reading from memory cache for this request.
 * If the final pipeline step normally reads memory cache,
 * we override it or we simply don't retrieve from it.
 */
class SkipMemoryCachePolicy<Key : Any, Value : Any> : ReadPolicy<Key, Value> {

    override suspend fun interceptRead(
        request: StoreRequest<Key, Value>,
        chain: ReadPolicy.Chain<Key, Value>
    ): Flow<Value> = flow {
        emitAll(chain.proceed(request.copy(skipMemoryCache = true)).filterNotNull())
    }
}


class SkipSourceOfTruthPolicy<Key : Any, Value : Any> : ReadPolicy<Key, Value> {

    override suspend fun interceptRead(
        request: StoreRequest<Key, Value>,
        chain: ReadPolicy.Chain<Key, Value>
    ): Flow<Value> = flow {
        emitAll(chain.proceed(request.copy(skipSourceOfTruth = true)).filterNotNull())
    }
}



class ForceNetworkFetchPolicy<Key : Any, Value : Any> : ReadPolicy<Key, Value> {

    override suspend fun interceptRead(
        request: StoreRequest<Key, Value>,
        chain: ReadPolicy.Chain<Key, Value>
    ): Flow<Value> = flow {
        // We might do something like set a "forceNetwork" flag in the request
        // that the final read logic interprets. Or we can literally skip local read:

        emitAll(
            chain.proceed(
                request.copy(
                    forceNetworkFetch = true
                )
            ).filterNotNull()
        )
    }
}


/**
 * Goal: If the device is offline, skip any network attempt and read only from SOT or memory. If online, proceed normally.
 *
 * TODO: In a real large-scale scenario, you might also check if there’s any pending local updates that must be synced. That can be done in a separate “sync local first” policy.
 */

class OfflineFirstPolicy<Key : Any, Value : Any>(
    private val offlineChecker: OfflineChecker,
    private val memoryCache: Cache<Key, Value>,
    private val sourceOfTruth: SourceOfTruth<Key, Value>
) : ReadPolicy<Key, Value> {

    override suspend fun interceptRead(
        request: StoreRequest<Key, Value>,
        chain: ReadPolicy.Chain<Key, Value>
    ): Flow<Value> = flow {

        // TODO: Support rechecking whether offline and proceeding with the chain when back online
        offlineChecker.isOffline.collectLatest { isOffline ->
            if (isOffline) {
                // Return local data from SOT

                val memoryCacheValue = memoryCache.getIfPresent(request.key)

                if (memoryCacheValue != null) {
                    emit(memoryCacheValue)
                }

                // We should still subscribe to SOT and emit updates
                sourceOfTruth.read(request.key).distinctUntilChanged().collectIndexed { index, value ->
                    if (index == 0 && value == memoryCacheValue) {
                        // Skip
                    } else if (value != null) {
                        emit(value)
                    }
                }
            } else {
                emitAll(chain.proceed(request).filterNotNull())
            }
        }
    }
}


class StaleAfterTTLPolicy<Key : Any, Value : Any>(
    private val ttlMillis: Long,
    private val lastFetchTracker: LastFetchTracker<Key>,
    private val sourceOfTruth: SourceOfTruth<Key, Value>,
    private val currentTime: () -> Long = { nowInEpochMilliseconds() }
) : ReadPolicy<Key, Value> {

    override suspend fun interceptRead(
        request: StoreRequest<Key, Value>,
        chain: ReadPolicy.Chain<Key, Value>
    ): Flow<Value> = flow {
        val lastFetched = lastFetchTracker.getLastFetchTime(request.key)
        val now = currentTime()
        val isStale = (lastFetched == null) || (now - lastFetched > ttlMillis)

        if (isStale) {
            // Proceed => typically triggers network fetch in final read
            emitAll(chain.proceed(request).filterNotNull())
        } else {
            // Emit local data from SOT (or memory).
            val local = sourceOfTruth.read(request.key).firstOrNull()
            if (local != null) {
                emit(local)
            } else {
                // If local is empty, we might proceed to fetch anyway
                emitAll(chain.proceed(request).filterNotNull())
            }
        }
    }
}

private fun nowInEpochMilliseconds() = Clock.System.now().toEpochMilliseconds()