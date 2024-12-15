package dev.mattramotar.pager.core

/**
 * A no-op implementation of PagerEventsListener that does nothing.
 * Useful as a default if no listener is provided.
 */
internal class NoOpPagerEventsListener<Key: Any, Value: Any>: PagerEventsListener<Key, Value> {
    override fun onInitialLoadComplete(result: PagerResult<List<Value>>) {}
    override fun onRefreshStart() {}
    override fun onRefreshEnd(result: PagerResult<List<Value>>) {}
    override fun onAppendStart() {}
    override fun onAppendEnd(result: PagerResult<List<Value>>) {}
    override fun onPrependStart() {}
    override fun onPrependEnd(result: PagerResult<List<Value>>) {}
}