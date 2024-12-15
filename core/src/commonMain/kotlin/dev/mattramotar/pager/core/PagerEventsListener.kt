package dev.mattramotar.pager.core


/**
 * A listener interface for pager lifecycle events.
 *
 * Allows clients to integrate custom logic (e.g., logging, UI updates) without manually checking states.
 */
interface PagerEventsListener<Key: Any, Value: Any> {
    // Called after the initial load (success, partial success, or error).
    fun onInitialLoadComplete(result: PagerResult<List<Value>>)

    // Called when a refresh operation starts.
    fun onRefreshStart()

    // Called when a refresh operation ends (success, partial, or error).
    fun onRefreshEnd(result: PagerResult<List<Value>>)

    // Called when an append load operation starts.
    fun onAppendStart()

    // Called when an append load operation ends (success, partial, or error).
    fun onAppendEnd(result: PagerResult<List<Value>>)

    // Called when a prepend load operation starts.
    fun onPrependStart()

    // Called when a prepend load operation ends (success, partial, or error).
    fun onPrependEnd(result: PagerResult<List<Value>>)
}

