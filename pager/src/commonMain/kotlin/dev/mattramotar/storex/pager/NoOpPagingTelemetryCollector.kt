package dev.mattramotar.storex.pager

import dev.mattramotar.storex.store.LoadDirection

/**
 * A no-op implementation of [PagingTelemetryCollector].
 *
 * This collector does not record or report any telemetry. Use it as a default when no telemetry is required.
 *
 * @param Key The type representing page keys.
 * @param Value The type of items being paged.
 */
class NoOpPagingTelemetryCollector<Key : Any, Value : Any> : PagingTelemetryCollector<Key, Value> {
    override fun onInitialLoadStart(key: Key) {}
    override fun onInitialLoadEnd(key: Key, success: Boolean, itemCount: Int?, error: Throwable?) {}
    override fun onRefreshStart(key: Key) {}
    override fun onRefreshEnd(key: Key, success: Boolean, itemCount: Int?, error: Throwable?) {}
    override fun onLoadMoreStart(key: Key, direction: LoadDirection) {}
    override fun onLoadMoreEnd(key: Key, direction: LoadDirection, success: Boolean, itemCount: Int?, error: Throwable?) {}
    override fun onInvalidate() {}
}