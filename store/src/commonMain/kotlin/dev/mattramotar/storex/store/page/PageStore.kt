package dev.mattramotar.storex.store.page

import dev.mattramotar.storex.store.Freshness
import dev.mattramotar.storex.store.StoreKey
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class PageToken(val before: String?, val after: String?) // bidirectional
data class Page<T>(val items: List<T>, val next: PageToken?, val prev: PageToken?)


interface PageStore<K : StoreKey, V> {

    /** Observe a paged list for a given query key. */
    fun stream(
        key: K,
        initial: PageToken? = null,
        config: PagingConfig = PagingConfig(pageSize = 30),
        freshness: Freshness = Freshness.CachedOrFetch
    ): Flow<PagingEvent<V>>

    /** Imperative loads (e.g., scroll triggers). Idempotent per token. */
    suspend fun load(
        key: K,
        direction: LoadDirection,
        from: PageToken? = null,            // absent means use current end/start
        freshness: Freshness = Freshness.CachedOrFetch
    )
}

enum class LoadDirection { INITIAL, APPEND, PREPEND }

data class PagingConfig(
    val pageSize: Int,
    val prefetchDistance: Int = pageSize,
    val maxSize: Int = pageSize * 10,     // total items kept in index window
    val placeholders: Boolean = false,
    val jumpThreshold: Int = Int.MAX_VALUE, // >= pageSize*3 usually
    val pageTtl: Duration = 5.minutes,    // Freshness for page slots
    val retryBackoffBase: Duration = 1.seconds
)

sealed interface LoadState {
    data object NotLoading : LoadState
    data object Loading : LoadState
    data class Error(val error: Throwable, val canServeStale: Boolean) : LoadState
}

data class PagingSnapshot<V>(
    val items: List<V>,
    val next: PageToken?,                 // after
    val prev: PageToken?,                 // before
    val sourceStates: Map<LoadDirection, LoadState>,
    val fullyLoaded: Boolean              // true if server signaled end
)

sealed interface PagingEvent<V> {
    data class Snapshot<V>(val value: PagingSnapshot<V>) : PagingEvent<V>
    data class StateUpdate<V>(val direction: LoadDirection, val state: LoadState) : PagingEvent<V>
}


