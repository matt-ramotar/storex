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
 * - Tracked state per key with config validation
 */
internal class RealPageStore<K : StoreKey, V : Any>(
    private val fetcher: suspend (key: K, token: PageToken?) -> Page<V>,
    private val config: PagingConfig,
    private val scope: CoroutineScope,
    private val timeSource: TimeSource = TimeSource.SYSTEM
) : PageStore<K, V> {

    /**
     * Tracked state per key, including:
     * - The StateFlow for reactive updates
     * - The config used to create this state
     * - Whether initial load has been triggered
     */
    private data class KeyState<V>(
        val stateFlow: MutableStateFlow<PagingState<V>>,
        val config: PagingConfig,
        var initialLoadTriggered: Boolean = false
    )

    // State per user key with config tracking
    private val keyStates = mutableMapOf<K, KeyState<V>>()

    // Mutex per user key to prevent concurrent loads
    private val loadMutexes = mutableMapOf<K, Mutex>()

    // Mutex for protecting keyStates and loadMutexes map access
    private val stateMutex = Mutex()

    override fun stream(
        key: K,
        initial: PageToken?,
        config: PagingConfig?,
        freshness: Freshness
    ): Flow<PagingEvent<V>> = kotlinx.coroutines.flow.flow {
        // Get or create tracked state
        val (stateFlow, shouldTriggerLoad) = stateMutex.withLock {
            val keyState = keyStates.getOrPut(key) {
                // Use per-operation config if provided, otherwise use instance config
                val effectiveConfig = config ?: this@RealPageStore.config
                val flow = MutableStateFlow(PagingState.initial<V>(effectiveConfig))
                KeyState(flow, effectiveConfig, initialLoadTriggered = false)
            }

            // Check if we should trigger initial load
            val shouldTrigger = !keyState.initialLoadTriggered
            if (shouldTrigger) {
                keyState.initialLoadTriggered = true
            }

            keyState.stateFlow to shouldTrigger
        }

        // Trigger initial load if this is the first stream() call for this key
        if (shouldTriggerLoad) {
            scope.launch {
                load(key, LoadDirection.INITIAL, initial, freshness)
            }
        }

        // Emit snapshots from the state flow
        stateFlow.map { state ->
            PagingEvent.Snapshot(state.toSnapshot())
        }.collect { event ->
            emit(event)
        }
    }

    override suspend fun load(
        key: K,
        direction: LoadDirection,
        from: PageToken?,
        freshness: Freshness
    ) {
        // Get or create tracked state
        val stateFlow = stateMutex.withLock {
            keyStates.getOrPut(key) {
                // If load() is called before stream(), create state with instance config
                val flow = MutableStateFlow(PagingState.initial<V>(config))
                KeyState(flow, config, initialLoadTriggered = true)
            }.stateFlow
        }

        val mutex = stateMutex.withLock {
            loadMutexes.getOrPut(key) { Mutex() }
        }

        mutex.withLock {
            val currentState = stateFlow.value

            // Don't load if already loading in this direction
            if (currentState.loadStates[direction] is LoadState.Loading) {
                return
            }

            // Determine which token to use (considers explicit 'from' parameter)
            val token = when (direction) {
                LoadDirection.INITIAL -> from
                LoadDirection.APPEND -> from ?: currentState.nextToken
                LoadDirection.PREPEND -> from ?: currentState.prevToken
            }

            // Don't load if no token available for non-INITIAL loads
            // This properly handles:
            // - Unidirectional pagination (one token always null)
            // - Explicit tokens via 'from' parameter
            // - One direction exhausted while other still has data
            if (direction != LoadDirection.INITIAL && token == null) {
                return
            }

            // Check freshness
            // For INITIAL: controls re-fetching of existing data
            // For APPEND/PREPEND: prevents redundant rapid loads
            if (direction == LoadDirection.INITIAL) {
                // INITIAL load freshness - check if we should re-fetch existing data
                when (freshness) {
                    is Freshness.CachedOrFetch -> {
                        // Serve cached if available, trigger background refresh if stale
                        if (currentState.pages.isNotEmpty()) {
                            if (isStale(currentState, currentState.config.pageTtl)) {
                                // Data is stale but available - trigger background refresh
                                scope.launch {
                                    // Use MustBeFresh to force refresh without recursion
                                    try {
                                        load(key, direction, from, Freshness.MustBeFresh)
                                    } catch (e: Exception) {
                                        // Silently fail background refresh - caller already has cached data
                                    }
                                }
                            }
                            // Serve cached data immediately (fresh or stale)
                            return
                        }
                        // No cached data - fall through to fetch
                    }
                    is Freshness.MinAge -> {
                        // Fetch if older than required minimum age
                        val isFreshEnough = currentState.lastLoadTime?.let { lastLoad ->
                            (timeSource.now() - lastLoad) <= freshness.notOlderThan
                        } ?: false

                        if (isFreshEnough && currentState.pages.isNotEmpty()) {
                            return // Data is fresh enough, don't fetch
                        }
                        // Data is too old or not available - fall through to fetch
                    }
                    is Freshness.MustBeFresh -> {
                        // Always fetch to ensure data is fresh
                        // Fall through to fetch
                    }
                    is Freshness.StaleIfError -> {
                        // Try to fetch, will handle errors by serving stale in catch block
                        // Fall through to fetch
                    }
                }
            } else {
                // APPEND/PREPEND freshness - prevent redundant rapid pagination
                when (freshness) {
                    is Freshness.CachedOrFetch -> {
                        // For pagination, always fetch new pages
                        // CachedOrFetch doesn't apply to loading new pages
                        // Fall through to fetch
                    }
                    is Freshness.MinAge -> {
                        // Check if we recently did ANY load
                        val isTooRecent = currentState.lastLoadTime?.let { lastLoad ->
                            (timeSource.now() - lastLoad) <= freshness.notOlderThan
                        } ?: false

                        if (isTooRecent) {
                            return // Too soon since last load, skip this pagination request
                        }
                        // Enough time has passed - fall through to fetch
                    }
                    is Freshness.MustBeFresh -> {
                        // Always fetch
                        // Fall through to fetch
                    }
                    is Freshness.StaleIfError -> {
                        // Try to fetch, will handle errors by serving stale in catch block
                        // Fall through to fetch
                    }
                }
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
    internal suspend fun getState(key: K): StateFlow<PagingState<V>>? {
        return stateMutex.withLock {
            keyStates[key]?.stateFlow
        }
    }
}
