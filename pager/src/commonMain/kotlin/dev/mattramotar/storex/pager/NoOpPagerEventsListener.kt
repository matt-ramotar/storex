package dev.mattramotar.storex.pager

/**
 * A no-op implementation of PagerEventsListener that does nothing.
 * Useful as a default if no listener is provided.
 */
internal class NoOpPagerEventsListener<Key : Any, Value : Any> : PagerEventsListener<Key, Value> {
    override fun onInitialLoadComplete(result: PagerResult<Value>) {}
    override fun onRefreshStart() {}
    override fun onRefreshEnd(result: PagerResult<Value>) {}
    override fun onAppendStart() {}
    override fun onAppendEnd(result: PagerResult<Value>) {}
    override fun onPrependStart() {}
    override fun onPrependEnd(result: PagerResult<Value>) {}
}