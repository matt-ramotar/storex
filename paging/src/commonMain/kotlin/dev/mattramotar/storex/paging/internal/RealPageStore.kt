package dev.mattramotar.storex.paging.internal

import dev.mattramotar.storex.core.Freshness
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.TimeSource
import dev.mattramotar.storex.paging.LoadDirection
import dev.mattramotar.storex.paging.LoadState
import dev.mattramotar.storex.paging.Page
import dev.mattramotar.storex.paging.PageStore
import dev.mattramotar.storex.paging.PageToken
import dev.mattramotar.storex.paging.PagingConfig
import dev.mattramotar.storex.paging.PagingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Real implementation of [PageStore].
 *
 * Manages paging state internally using [PagingState] and exposes
 * reactive updates via [Flow].
 *
 * Thread-safe through:
 * - Immutable [PagingState]
 * - Mutex-protected load operations
 * - StateFlow for reactive updates
 */
internal class RealPageStore<K : StoreKey, V : Any>(
    private val fetcher: suspend (key: K, token: PageToken?) -> Page<V>,
    private val config: PagingConfig,
    private val scope: CoroutineScope,
    private val timeSource: TimeSource = TimeSource.SYSTEM
) : PageStore<K, V> {

    // State per user key
    private val states = mutableMapOf<K, MutableStateFlow<PagingState<V>>>()

    // Mutex per user key to prevent concurrent loads
    private val loadMutexes = mutableMapOf<K, Mutex>()

    override fun stream(
        key: K,
        initial: PageToken?,
        config: PagingConfig?,
        freshness: Freshness
    ): Flow<PagingEvent<V>> {
        // Get or create state flow synchronously
        val stateFlow = synchronized(this) {
            states.getOrPut(key) {
                // Use per-operation config if provided, otherwise use instance config
                val effectiveConfig = config ?: this.config
                val flow = MutableStateFlow(PagingState.initial<V>(effectiveConfig))

                // Trigger initial load automatically with specified freshness
                scope.launch {
                    load(key, LoadDirection.INITIAL, initial, freshness)
                }

                flow
            }
        }

        return stateFlow.map { state ->
            PagingEvent.Snapshot(state.toSnapshot())
        }
    }

    override suspend fun load(
        key: K,
        direction: LoadDirection,
        from: PageToken?,
        freshness: Freshness
    ) {
        val stateFlow = synchronized(this) {
            states.getOrPut(key) {
                MutableStateFlow(PagingState.initial<V>(config))
            }
        }

        val mutex = synchronized(this) {
            loadMutexes.getOrPut(key) { Mutex() }
        }

        mutex.withLock {
            val currentState = stateFlow.value

            // Don't load if already loading in this direction
            if (currentState.loadStates[direction] is LoadState.Loading) {
                return
            }

            // Don't load if already fully loaded
            if (currentState.fullyLoaded && direction != LoadDirection.INITIAL) {
                return
            }

            // Determine which token to use
            val token = when (direction) {
                LoadDirection.INITIAL -> from
                LoadDirection.APPEND -> from ?: currentState.nextToken
                LoadDirection.PREPEND -> from ?: currentState.prevToken
            }

            // Don't load if no token available (end of list)
            if (direction == LoadDirection.APPEND && token == null && currentState.pages.isNotEmpty()) {
                return
            }
            if (direction == LoadDirection.PREPEND && token == null && currentState.pages.isNotEmpty()) {
                return
            }

            // Check freshness - only for INITIAL loads (refreshes)
            // APPEND and PREPEND always fetch if token available (user-initiated pagination)
            val shouldFetch = if (direction == LoadDirection.INITIAL) {
                when (freshness) {
                    is Freshness.CachedOrFetch -> {
                        // Serve cached if available and fresh, trigger background refresh if stale
                        currentState.pages.isEmpty() || isStale(currentState, currentState.config.pageTtl)
                    }
                    is Freshness.MinAge -> {
                        // Fetch if older than required minimum age
                        currentState.lastLoadTime == null ||
                        (timeSource.now() - currentState.lastLoadTime) > freshness.notOlderThan
                    }
                    is Freshness.MustBeFresh -> {
                        // Always fetch to ensure data is fresh
                        true
                    }
                    is Freshness.StaleIfError -> {
                        // Try to fetch, will handle errors by serving stale in catch block
                        true
                    }
                }
            } else {
                // APPEND and PREPEND always fetch (pagination actions)
                true
            }

            // Early return if cached data is acceptable (only for INITIAL loads)
            if (!shouldFetch && currentState.pages.isNotEmpty()) {
                return
            }

            // Update state to loading and capture the new state
            val loadingState = currentState.withLoadState(direction, LoadState.Loading)
            stateFlow.value = loadingState

            try {
                // Fetch the page
                val page = fetcher(key, token)

                // Update state with the new page using loadingState, including timestamp
                val newState = loadingState.addPage(page, direction, timestamp = timeSource.now())
                stateFlow.value = newState

            } catch (e: Exception) {
                // Handle StaleIfError freshness - serve cached data if available
                if (freshness is Freshness.StaleIfError && loadingState.pages.isNotEmpty()) {
                    // Keep existing pages, just update load state to error
                    val errorState = loadingState.withError(
                        direction = direction,
                        error = e,
                        canServeStale = true
                    )
                    stateFlow.value = errorState
                } else {
                    // Normal error handling - no stale data or freshness doesn't allow it
                    val errorState = loadingState.withError(
                        direction = direction,
                        error = e,
                        canServeStale = loadingState.pages.isNotEmpty()
                    )
                    stateFlow.value = errorState
                }
            }
        }
    }

    /**
     * Check if paging state is stale based on TTL.
     */
    private fun isStale(state: PagingState<V>, ttl: kotlin.time.Duration): Boolean {
        return state.lastLoadTime?.let { lastLoad ->
            (timeSource.now() - lastLoad) > ttl
        } ?: true  // No timestamp means stale
    }

    /**
     * Get current state for a key (for testing).
     */
    internal fun getState(key: K): StateFlow<PagingState<V>>? {
        return synchronized(this) {
            states[key]
        }
    }
}
