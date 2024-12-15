package dev.mattramotar.pager.core

import kotlinx.coroutines.flow.StateFlow
import dev.mattramotar.pager.store.LoadDirection
import dev.mattramotar.pager.store.LoadStrategy

/**
 * A generic interface for a pager that loads data incrementally in response to pagination events.
 *
 * Implementations start from an initial state and can:
 * - Load additional pages of data (via [loadMore])
 * - Refresh existing data without clearing currently loaded items (via [refresh])
 * - Invalidate all data and start fresh (via [invalidate])
 *
 * The current paging state is exposed as a [StateFlow], and all operations return [PagerResult] which may either
 * succeed with new data or fail with an option to retry.
 *
 * @param Key The type used for page keys.
 * @param Value The type of the data items being paged.
 */
interface Pager<Key : Any, Value : Any> {
    /**
     * A [StateFlow] representing the current paging state.
     * Collect this [StateFlow] to receive updates whenever new pages are loaded or state changes.
     */
    val state: StateFlow<PagingState<Key, Value>>

    /**
     * Refreshes the current paging data.
     *
     * **What is Refresh?**
     * - Refresh acts like a "soft reset": it attempts to fetch fresh data, usually starting again at the
     *   initialKey. It does not immediately discard the currently displayed items. If the refresh fails,
     *   the user still sees the old data, maintaining continuity and a smoother user experience.
     *
     * **Use Case:**
     * - Call this when you suspect the data is stale, but you want to keep showing the old items
     *   until the new data arrives.
     *
     * **Return Value & Retry:**
     * - Returns a [PagerResult] that is either [PagerResult.Success] with the newly loaded items,
     *   or [PagerResult.Error] with a retry function.
     * - If the result is an error, you can invoke `result.retry()` to attempt the same refresh operation again.
     */
    suspend fun refresh(): PagerResult<Value>

    /**
     * Invalidates all currently loaded data and resets the pager to its initial state.
     *
     * **What is Invalidate?**
     * - Invalidate is a "hard reset." It discards all currently loaded items and treats the pager as if it
     *   were just constructed. The next load operation after invalidate will start fresh from the initialKey,
     *   with no previously cached or displayed data carried over.
     *
     * **Use Case:**
     * - Call this when the current data set is no longer relevant at all. For example, if a user logged out,
     *   and you need to load entirely different data, or a major filter changed, rendering old data invalid.
     *
     * **Return Value & Retry:**
     * - Returns a [PagerResult] that is either [PagerResult.Success] with the newly loaded items,
     *   or [PagerResult.Error] with a retry function.
     * - On error, calling `result.retry()` will attempt to invalidate and reload again.
     */
    suspend fun invalidate(): PagerResult<Value>


    /**
     * Loads more data in the given [direction]. Optionally, a [jumpKey] can be provided to jump to a specific
     * key before loading. This is used for paginating forward (append) or backward (prepend).
     *
     * The [strategy] dictates whether we skip cache, use cache first, or local-only loading.
     *
     * **Return Value & Retry:**
     * - Returns a [PagerResult] that is either [PagerResult.Success] with the newly loaded items,
     *   or [PagerResult.Error] with a retry function.
     * - On error, calling `result.retry()` will attempt the same `loadMore` call again with the same parameters.
     *
     * **Use Case:**
     * - When the user scrolls, or a "Load More" button is tapped, call this method to load the next page.
     * - If it fails (e.g., due to network errors), the returned [PagerResult.Error] provides a convenient retry
     *   function so you don't have to reconstruct the parameters manually.
     */
    suspend fun loadMore(
        direction: LoadDirection,
        strategy: LoadStrategy,
        jumpKey: Key? = null
    ): PagerResult<Value>
}


