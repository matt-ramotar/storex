package dev.mattramotar.clerk.core

import dev.mattramotar.clerk.store.LoadDirection



/**
 * A telemetry interface to observe paging events, such as initial loads, refreshes, and loadMore operations.
 *
 * Implement this interface to collect metrics, logging, or analytics data about how paging is performing.
 *
 * Methods are called at the start and end of each operation, providing information about keys, success/failure states,
 * item counts, and errors encountered.
 *
 * @param Key The type representing page keys.
 * @param Value The type of items being paged.
 */
interface PagingTelemetryCollector<Key : Any, Value : Any> {
    // Called when an initial load starts
    fun onInitialLoadStart(key: Key)

    // Called when an initial load ends (success or failure)
    fun onInitialLoadEnd(key: Key, success: Boolean, itemCount: Int?, error: Throwable?)

    // Called when a refresh starts
    fun onRefreshStart(key: Key)

    // Called when a refresh ends
    fun onRefreshEnd(key: Key, success: Boolean, itemCount: Int?, error: Throwable?)

    // Called when loadMore starts for append or prepend
    fun onLoadMoreStart(key: Key, direction: LoadDirection)

    // Called when loadMore ends for append or prepend
    fun onLoadMoreEnd(key: Key, direction: LoadDirection, success: Boolean, itemCount: Int?, error: Throwable?)

    // Called when invalidate is called
    fun onInvalidate()
}

