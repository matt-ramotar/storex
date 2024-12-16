package dev.mattramotar.storex.pager.core

import dev.mattramotar.storex.pager.coroutines.io
import dev.mattramotar.storex.pager.extensions.LoadStatesExtensions.update
import dev.mattramotar.storex.pager.store.LoadDirection
import dev.mattramotar.storex.pager.store.LoadStrategy
import dev.mattramotar.storex.pager.store.SkipCacheStrategy
import dev.mattramotar.storex.pager.store.StorePageLoadRequest
import dev.mattramotar.storex.pager.store.StorePageLoadResponse
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.mobilenativefoundation.store.store5.Store


/**
 * A default implementation of [Pager] that coordinates loading pages of data from a given [Store].
 *
 * This pager is configured via a [PagingConfig] that defines how pagination behaves (e.g., initial key, page size).
 * It automatically attempts to load the initial page upon creation and provides operations to load more data, refresh
 * existing content, and invalidate the current dataset.
 *
 * **Key Concepts:**
 * - **Initial Load:** Occurs immediately when the pager is created, using the [PagingConfig.initialKey].
 * - **Load More:** Fetches additional pages in a given direction ([LoadDirection.Append] or [LoadDirection.Prepend]).
 * - **Refresh:** Attempts to re-fetch fresh data without discarding currently displayed items. If the refresh fails,
 *   old data remains visible.
 * - **Invalidate:** Discards all current items and resets the pager, starting again from the initial key.
 *
 * **Error Handling and Retry:**
 * Methods like [loadMore], [refresh], and [invalidate] return a [PagerResult]. On success, a [PagerResult.Success]
 * includes newly fetched items. On failure, a [PagerResult.Error] is returned, providing a `retry()` function to
 * attempt the same operation again.
 *
 * This class also supports a [NextKeyProvider] to determine subsequent keys to load, and a [PagingTelemetryCollector]
 * for optional metrics and analytics.
 *
 * @param Key The type representing keys used to request data pages.
 * @param Value The type of items in each loaded page.
 * @property store The [Store] source of paged data.
 * @property pagingConfig Configuration parameters for pagination.
 * @property nextKeyProvider A function that computes the next key to load based on the current key, direction, and loaded items.
 * @property telemetryCollector Optional collector for telemetry events, defaults to no-op.
 */

internal class DefaultPager<Key : Any, Value : Any>(
    private val store: Store<Key, List<Value>>,
    private val pagingConfig: PagingConfig<Key>,
    private val nextKeyProvider: NextKeyProvider<Key, Value>,
    private val telemetryCollector: PagingTelemetryCollector<Key, Value> = NoOpPagingTelemetryCollector(),
    private val eventsListener: PagerEventsListener<Key, Value> = NoOpPagerEventsListener(),
    mainCoroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.io
) : Pager<Key, Value> {

    // Maintains the current paging state, including loaded items and load states.
    private val _state: MutableStateFlow<PagingState<Key, Value>> =
        MutableStateFlow(PagingState())

    // The current paging key, starting from the initialKey defined in pagingConfig.
    private val currentAppendKey = atomic<Key?>(pagingConfig.initialKey)
    private val currentPrependKey = atomic<Key?>(null)


    // Flags indicating whether no more data is available for append or prepend directions.
    private val endOfAppendingPaginationReached = atomic(false)
    private val endOfPrependingPaginationReached = atomic(false)

    // A mutex to ensure thread-safe state modifications.
    private val stateMutex = Mutex()

    init {

        // Immediately attempt to load the initial page upon construction.
        mainCoroutineScope.launch {
            telemetryCollector.onInitialLoadStart(pagingConfig.initialKey)

            val result = loadFromInitialKey { _, newItems -> newItems }

            // Notify events listener
            eventsListener.onInitialLoadComplete(result)

            result
                .onSuccess { items ->
                    telemetryCollector.onInitialLoadEnd(pagingConfig.initialKey, true, items.size, null)
                }
                .onFailure { error ->
                    telemetryCollector.onInitialLoadEnd(pagingConfig.initialKey, false, null, error)
                }

        }
    }

    override val state: StateFlow<PagingState<Key, Value>>
        get() = _state.asStateFlow()


    override suspend fun refresh(): PagerResult<Value> {
        // Refresh attempts to re-fetch fresh data, but keeps items on error.

        // Uses atomic flags and locks minimally.
        endOfPrependingPaginationReached.value = false
        endOfAppendingPaginationReached.value = false

        // Update state to show refreshing (no lock needed if just rewriting state)
        _state.value = _state.value.copy(
            loadStates = _state.value.loadStates.copy(refresh = LoadState.Loading(endOfPaginationReached = false))
        )

        eventsListener.onRefreshStart()


        return withKeyForTelemetry(LoadDirection.Append, pagingConfig.initialKey) { key ->
            telemetryCollector.onRefreshStart(key)
            val result = loadFromInitialKey { _, newItems -> newItems }

            // Notify events listener
            eventsListener.onRefreshEnd(result)

            when (result) {
                is PagerResult.Success -> {
                    telemetryCollector.onRefreshEnd(key, true, result.items.size, null)
                    result
                }

                is PagerResult.Error -> {
                    telemetryCollector.onRefreshEnd(key, false, null, result.error)
                    result
                }
            }
        }
    }

    override suspend fun invalidate(): PagerResult<Value> {
        // Called when the data is totally obsolete, and you need a hard reset.
        // On error, you can call retry() on the returned PagerResult.Error to attempt invalidation again.

        telemetryCollector.onInvalidate()

        // Reset keys and flags atomically.
        currentAppendKey.value = pagingConfig.initialKey
        currentPrependKey.value = null
        endOfPrependingPaginationReached.value = false
        endOfAppendingPaginationReached.value = false

        // Resetting state completely, so lock for a consistent update
        stateMutex.withLock {
            _state.value = PagingState()
        }

        telemetryCollector.onInitialLoadStart(pagingConfig.initialKey)
        val result = loadFromInitialKey { _, newItems -> newItems }
        eventsListener.onInitialLoadComplete(result)

        return when (result) {
            is PagerResult.Success -> {
                telemetryCollector.onInitialLoadEnd(pagingConfig.initialKey, true, result.items.size, null)
                result
            }

            is PagerResult.Error -> {
                telemetryCollector.onInitialLoadEnd(pagingConfig.initialKey, false, null, result.error)
                result
            }
        }
    }


    override suspend fun loadMore(direction: LoadDirection, strategy: LoadStrategy, jumpKey: Key?): PagerResult<Value> {
        // Loads the next page of data in the specified direction.
        // On failure, a PagerResult.Error is returned with a retry lambda that calls loadMore with the same params.

        eventsListener.run {
            if (direction == LoadDirection.Append) onAppendStart() else onPrependStart()
        }

        return withKeyForTelemetry(direction, jumpKey) { key ->
            telemetryCollector.onLoadMoreStart(key, direction)

            val result = withContext(ioDispatcher) {
                when (direction) {
                    LoadDirection.Prepend -> prependNextPage(strategy)
                    LoadDirection.Append -> appendNextPage(strategy)
                }
            }

            // Notify events listener
            if (direction == LoadDirection.Append) {
                eventsListener.onAppendEnd(result)
            } else {
                eventsListener.onPrependEnd(result)
            }

            when (result) {
                is PagerResult.Success -> {
                    telemetryCollector.onLoadMoreEnd(key, direction, true, result.items.size, null)
                    result
                }

                is PagerResult.Error -> {
                    telemetryCollector.onLoadMoreEnd(key, direction, false, null, result.error)
                    result
                }
            }
        }
    }

    private suspend fun prependNextPage(strategy: LoadStrategy): PagerResult<Value> {
        if (endOfPrependingPaginationReached.value) {
            return PagerResult.Error(
                PagerError.UnknownError(IllegalStateException("End of prepending pagination reached."))
            ) { prependNextPage(strategy) }
        }

        val key = currentPrependKey.value ?: return PagerResult.Error(
            PagerError.UnknownError(IllegalStateException("No prepend key available."))
        ) { prependNextPage(strategy) }

        // Update load state without lock (atomic)
        _state.value = _state.value.copy(
            loadStates = _state.value.loadStates.copy(
                prepend = LoadState.Loading(endOfPrependingPaginationReached.value)
            )
        )

        return loadPageAndUpdateState(key, LoadDirection.Prepend, strategy) { prevItems, newItems ->
            newItems + prevItems
        }
    }

    private suspend fun appendNextPage(strategy: LoadStrategy): PagerResult<Value> {
        if (endOfAppendingPaginationReached.value) {
            return PagerResult.Error(
                PagerError.UnknownError(IllegalStateException("End of appending pagination reached."))
            ) { appendNextPage(strategy) }
        }

        val key = currentAppendKey.value ?: return PagerResult.Error(
            PagerError.UnknownError(IllegalStateException("No append key available."))
        ) { appendNextPage(strategy) }

        // Update load state without lock (atomic)
        _state.value = _state.value.copy(
            loadStates = _state.value.loadStates.copy(
                append = LoadState.Loading(endOfAppendingPaginationReached.value)
            )
        )

        return loadPageAndUpdateState(key, LoadDirection.Append, strategy) { prevItems, newItems ->
            prevItems + newItems
        }
    }

    private suspend fun loadFromInitialKey(reducer: (prevItems: List<Value>, newItems: List<Value>) -> List<Value>): PagerResult<Value> {
        _state.value = _state.value.copy(
            loadStates = _state.value.loadStates.copy(
                refresh = LoadState.Loading(endOfPaginationReached = false)
            )
        )
        endOfPrependingPaginationReached.value = false
        endOfAppendingPaginationReached.value = false
        currentAppendKey.value = pagingConfig.initialKey

        val response = withContext(ioDispatcher) {
            loadPageFromStore(
                store,
                key = pagingConfig.initialKey,
                direction = LoadDirection.Append,
                strategy = SkipCacheStrategy,
                pageSize = pagingConfig.pageSize
            )
        }

        return when (response) {
            is PagerResult.Error -> {
                // On refresh error, no items are guaranteed. Keep items as they are if refresh might happen after initial load.
                // If initial was empty before, they remain empty.
                _state.value = _state.value.copy(
                    loadStates = _state.value.loadStates.copy(
                        refresh = LoadState.Error(response.error, endOfPaginationReached = false)
                    )
                )
                response
            }

            is PagerResult.Success -> {

                val loadedItems = response.items
                endOfAppendingPaginationReached.value = loadedItems.size < pagingConfig.pageSize
                val newState = _state.value.copy(
                    items = reducer(_state.value.items, loadedItems),
                    loadStates = LoadStates(
                        refresh = LoadState.NotLoading(endOfAppendingPaginationReached.value),
                        prepend = LoadState.NotLoading.Incomplete,
                        append = LoadState.NotLoading(endOfAppendingPaginationReached.value)
                    )
                )
                // Update keys inside lock if needed to ensure consistency
                stateMutex.withLock {
                    currentAppendKey.value = computeNextKey(pagingConfig.initialKey, LoadDirection.Append, loadedItems)
                    _state.value = newState.copy(
                        currentAppendKey = currentAppendKey.value
                    )
                }
                response
            }
        }
    }


    private fun computeNextKey(currentKey: Key, direction: LoadDirection, loadedItems: List<Value>): Key {
        return nextKeyProvider.computeNextKey(currentKey, direction, loadedItems)
    }

    /**
     * Attempt to load a page and update state accordingly. This method uses the revised `loadPageFromStore`
     * that can return partial success or typed errors.
     */
    private suspend fun loadPageAndUpdateState(
        key: Key,
        direction: LoadDirection,
        strategy: LoadStrategy,
        reducer: (prevItems: List<Value>, newItems: List<Value>) -> List<Value>
    ): PagerResult<Value> {
        val response = withContext(ioDispatcher) {
            loadPageFromStore(
                store,
                key,
                direction,
                pagingConfig.pageSize,
                strategy
            )
        }

        return when (response) {
            is PagerResult.Success -> {
                val loadedItems = response.items

                if (direction == LoadDirection.Prepend) {
                    endOfPrependingPaginationReached.value = loadedItems.size < pagingConfig.pageSize
                } else {
                    endOfAppendingPaginationReached.value = loadedItems.size < pagingConfig.pageSize
                }

                // Update state and keys atomically in a single lock
                stateMutex.withLock {
                    val oldState = _state.value
                    val newItems = reducer(oldState.items, loadedItems)
                    val newLoadStates = oldState.loadStates.update(
                        direction,
                        LoadState.NotLoading(
                            if (direction == LoadDirection.Prepend) endOfPrependingPaginationReached.value else endOfAppendingPaginationReached.value
                        )
                    )

                    val newKey = computeNextKey(key, direction, loadedItems)
                    if (direction == LoadDirection.Prepend) {
                        currentPrependKey.value = newKey
                    } else {
                        currentAppendKey.value = newKey
                    }

                    _state.value = oldState.copy(
                        items = newItems,
                        loadStates = newLoadStates,
                        currentAppendKey = currentAppendKey.value,
                        currentPrependKey = currentPrependKey.value
                    )
                }

                response
            }

            is PagerResult.Error -> {
                _state.value = _state.value.copy(
                    loadStates = _state.value.loadStates.update(direction, LoadState.Error(response.error, false))
                )
                response
            }
        }
    }

    private suspend fun <Key : Any, Value : Any> loadPageFromStore(
        store: Store<Key, List<Value>>,
        key: Key,
        direction: LoadDirection,
        pageSize: Int,
        strategy: LoadStrategy
    ): PagerResult<Value> {

        val request = StorePageLoadRequest(
            key = key,
            pageSize, direction, strategy
        )

        return when (val response = strategy.loadPage(store, request)) {
            is StorePageLoadResponse.Failure -> {
                PagerResult.Error(response.error.toPagerError()) {
                    loadPageFromStore(store, key, direction, pageSize, strategy)
                }
            }

            is StorePageLoadResponse.Success -> {
                // TODO: Support partial successes
                PagerResult.Success(response.items)
            }
        }
    }

    private fun Throwable.toPagerError(): PagerError {
        // Map throwable to error type
        return PagerError.UnknownError(this)
    }

    private suspend inline fun <T> withKeyForTelemetry(
        direction: LoadDirection,
        jumpKey: Key? = null,
        block: (localKey: Key) -> T
    ): T {
        // Update keys atomically without a big lock:
        jumpKey?.let {
            if (direction == LoadDirection.Prepend) currentPrependKey.value = it
            else currentAppendKey.value = it
        }

        val key = when (direction) {
            LoadDirection.Prepend -> currentPrependKey.value ?: throw IllegalStateException("No prepend key available.")
            LoadDirection.Append -> currentAppendKey.value ?: throw IllegalStateException("No append key available.")
        }

        return block(key)
    }

    // Helper extension functions to make handling PagerResult easier
    private inline fun <T> PagerResult<T>.onSuccess(block: (List<T>) -> Unit): PagerResult<T> {
        if (this is PagerResult.Success) block(items)
        return this
    }

    private inline fun <T> PagerResult<T>.onFailure(block: (PagerError) -> Unit): PagerResult<T> {
        if (this is PagerResult.Error) block(error)
        return this
    }
}



