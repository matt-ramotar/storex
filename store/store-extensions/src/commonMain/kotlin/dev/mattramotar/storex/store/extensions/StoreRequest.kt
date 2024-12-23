package dev.mattramotar.storex.store.extensions

import dev.mattramotar.storex.store.extensions.policies.read.ForceNetworkFetchPolicy
import dev.mattramotar.storex.store.extensions.policies.read.ReadPolicy
import dev.mattramotar.storex.store.extensions.policies.read.SkipMemoryCachePolicy
import kotlinx.coroutines.flow.StateFlow

/**
 * refresh might indicate a user explicitly wants fresh data.
 * skipMemoryCache might indicate ignoring an in-memory cache.
 * fallbackToSOT might indicate that if the network fails, read from SOT.
 * readPolicies can be a list of advanced policies or even a single composite policy.
 *
 *  * val request = StoreRequest(key = userId)
 *  *     .forceNetwork()
 *  *     .skipMemoryCache()
 *  *
 *  * That’s far more composable and doesn’t require booleans in StoreRequest.
 */
data class StoreRequest<Key : Any, Value : Any> internal constructor(
    val key: Key,
    val readPolicies: List<ReadPolicy<Key, Value>> = emptyList(),
    internal val forceNetworkFetch: Boolean = false,
    internal val skipMemoryCache: Boolean = false,
    internal val skipSourceOfTruth: Boolean = false,
    internal val fallbackToSOT: Boolean = false,
) {
    companion object {
        fun <Key : Any, Value : Any> from(key: Key, readPolicies: List<ReadPolicy<Key, Value>> = emptyList()): StoreRequest<Key, Value> {
            return StoreRequest(
                key,
                readPolicies
            )
        }

        fun <Key : Any, Value : Any> forceNetworkFetch(
            key: Key,
            policy: ReadPolicy<Key, Value> = ForceNetworkFetchPolicy()
        ): StoreRequest<Key, Value> {
            return StoreRequest(key, listOf(policy))
        }

        fun <Key : Any, Value : Any> skipMemoryCache(
            key: Key,
            policy: ReadPolicy<Key, Value> = SkipMemoryCachePolicy()
        ): StoreRequest<Key, Value> {
            return StoreRequest(key, listOf(policy))
        }
    }
}

fun <Key : Any, Value : Any> StoreRequest<Key, Value>.addForceNetworkFetch(
    policy: ReadPolicy<Key, Value> = ForceNetworkFetchPolicy()
): StoreRequest<Key, Value> {
    return copy(readPolicies = readPolicies + policy)
}

fun <Key : Any, Value : Any> StoreRequest<Key, Value>.addSkipMemoryCache(
    policy: ReadPolicy<Key, Value> = SkipMemoryCachePolicy()
): StoreRequest<Key, Value> {
    return copy(readPolicies = readPolicies + policy)
}


interface OfflineChecker {
    val isOffline: StateFlow<Boolean>
}

// For tracking last fetch times
interface LastFetchTracker<Key : Any> {
    fun getLastFetchTime(key: Key): Long?
    fun setLastFetchTime(key: Key, time: Long)
}

