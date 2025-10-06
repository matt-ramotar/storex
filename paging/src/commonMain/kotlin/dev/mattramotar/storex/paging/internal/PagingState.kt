package dev.mattramotar.storex.paging.internal

import dev.mattramotar.storex.paging.LoadDirection
import dev.mattramotar.storex.paging.LoadState
import dev.mattramotar.storex.paging.Page
import dev.mattramotar.storex.paging.PageToken
import dev.mattramotar.storex.paging.PagingConfig
import dev.mattramotar.storex.paging.PagingSnapshot

/**
 * Internal state machine for managing paged data.
 *
 * Tracks:
 * - All loaded pages in order
 * - Load states for each direction (INITIAL, APPEND, PREPEND)
 * - Current page tokens (next, prev)
 * - Fully loaded status
 *
 * Thread-safe through immutability - all mutations return new instances.
 */
internal data class PagingState<V>(
    val pages: List<Page<V>> = emptyList(),
    val loadStates: Map<LoadDirection, LoadState> = mapOf(
        LoadDirection.INITIAL to LoadState.NotLoading,
        LoadDirection.APPEND to LoadState.NotLoading,
        LoadDirection.PREPEND to LoadState.NotLoading
    ),
    val nextToken: PageToken? = null,
    val prevToken: PageToken? = null,
    val fullyLoaded: Boolean = false,
    val config: PagingConfig
) {

    /**
     * Get all items from all loaded pages.
     */
    val items: List<V>
        get() = pages.flatMap { it.items }

    /**
     * Check if currently loading in any direction.
     */
    val isLoading: Boolean
        get() = loadStates.values.any { it is LoadState.Loading }

    /**
     * Create a snapshot for external consumption.
     */
    fun toSnapshot(): PagingSnapshot<V> = PagingSnapshot(
        items = items,
        next = nextToken,
        prev = prevToken,
        sourceStates = loadStates,
        fullyLoaded = fullyLoaded
    )

    /**
     * Update load state for a specific direction.
     */
    fun withLoadState(direction: LoadDirection, state: LoadState): PagingState<V> {
        return copy(
            loadStates = loadStates + (direction to state)
        )
    }

    /**
     * Add a page in the specified direction.
     */
    fun addPage(page: Page<V>, direction: LoadDirection): PagingState<V> {
        val newPages = when (direction) {
            LoadDirection.INITIAL -> {
                // Initial load replaces everything
                listOf(page)
            }
            LoadDirection.APPEND -> {
                // Add to end
                pages + page
            }
            LoadDirection.PREPEND -> {
                // Add to beginning
                listOf(page) + pages
            }
        }

        // Apply max size constraint by dropping oldest pages
        val trimmedPages = if (config.maxSize > 0) {
            val totalItems = newPages.sumOf { it.items.size }
            if (totalItems > config.maxSize) {
                // Drop from the direction opposite to load direction
                when (direction) {
                    LoadDirection.APPEND -> dropOldestFromStart(newPages, config.maxSize)
                    LoadDirection.PREPEND -> dropOldestFromEnd(newPages, config.maxSize)
                    LoadDirection.INITIAL -> newPages // Don't trim initial load
                }
            } else {
                newPages
            }
        } else {
            newPages
        }

        // Update tokens based on direction
        // APPEND: Update nextToken from page, preserve prevToken
        // PREPEND: Update prevToken from page, preserve nextToken
        // INITIAL: Update both tokens from page
        val newNextToken = when (direction) {
            LoadDirection.INITIAL, LoadDirection.APPEND -> page.next
            LoadDirection.PREPEND -> nextToken  // Preserve - still points to end of data
        }

        val newPrevToken = when (direction) {
            LoadDirection.INITIAL, LoadDirection.PREPEND -> page.prev
            LoadDirection.APPEND -> prevToken  // Preserve - still points to start of data
        }

        return copy(
            pages = trimmedPages,
            nextToken = newNextToken,
            prevToken = newPrevToken,
            fullyLoaded = newNextToken == null && newPrevToken == null,
            loadStates = loadStates + (direction to LoadState.NotLoading)
        )
    }

    /**
     * Handle error for a specific direction.
     */
    fun withError(direction: LoadDirection, error: Throwable, canServeStale: Boolean): PagingState<V> {
        return copy(
            loadStates = loadStates + (direction to LoadState.Error(error, canServeStale))
        )
    }

    /**
     * Reset to initial state (for refresh).
     */
    fun reset(): PagingState<V> {
        return copy(
            pages = emptyList(),
            loadStates = mapOf(
                LoadDirection.INITIAL to LoadState.NotLoading,
                LoadDirection.APPEND to LoadState.NotLoading,
                LoadDirection.PREPEND to LoadState.NotLoading
            ),
            nextToken = null,
            prevToken = null,
            fullyLoaded = false
        )
    }

    private fun dropOldestFromStart(pages: List<Page<V>>, maxSize: Int): List<Page<V>> {
        var totalItems = 0
        val keptPages = mutableListOf<Page<V>>()

        // Keep pages from the end until we hit maxSize
        for (page in pages.reversed()) {
            val pageSize = page.items.size
            if (totalItems + pageSize <= maxSize) {
                keptPages.add(0, page)
                totalItems += pageSize
            } else {
                // Partially include this page if we have room
                val remainingSpace = maxSize - totalItems
                if (remainingSpace > 0) {
                    val trimmedItems = page.items.takeLast(remainingSpace)
                    keptPages.add(0, page.copy(items = trimmedItems))
                }
                break
            }
        }

        // Handle edge case: single page exceeds maxSize
        if (keptPages.isEmpty() && pages.isNotEmpty()) {
            val lastPage = pages.last()
            val trimmedItems = lastPage.items.takeLast(maxSize)
            return listOf(lastPage.copy(items = trimmedItems))
        }

        return keptPages
    }

    private fun dropOldestFromEnd(pages: List<Page<V>>, maxSize: Int): List<Page<V>> {
        var totalItems = 0
        val keptPages = mutableListOf<Page<V>>()

        // Keep pages from the start until we hit maxSize
        for (page in pages) {
            val pageSize = page.items.size
            if (totalItems + pageSize <= maxSize) {
                keptPages.add(page)
                totalItems += pageSize
            } else {
                // Partially include this page if we have room
                val remainingSpace = maxSize - totalItems
                if (remainingSpace > 0) {
                    val trimmedItems = page.items.take(remainingSpace)
                    keptPages.add(page.copy(items = trimmedItems))
                }
                break
            }
        }

        // Handle edge case: single page exceeds maxSize
        if (keptPages.isEmpty() && pages.isNotEmpty()) {
            val firstPage = pages.first()
            val trimmedItems = firstPage.items.take(maxSize)
            return listOf(firstPage.copy(items = trimmedItems))
        }

        return keptPages
    }

    companion object {
        /**
         * Create initial empty state.
         */
        fun <V> initial(config: PagingConfig): PagingState<V> {
            return PagingState(config = config)
        }
    }
}
