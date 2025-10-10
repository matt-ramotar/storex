package dev.mattramotar.storex.paging.internal

import dev.mattramotar.storex.paging.LoadDirection
import dev.mattramotar.storex.paging.LoadState
import dev.mattramotar.storex.paging.OffsetToken
import dev.mattramotar.storex.paging.Page
import dev.mattramotar.storex.paging.PagingConfig
import dev.mattramotar.storex.paging.TestException
import dev.mattramotar.storex.paging.TestItem
import dev.mattramotar.storex.paging.generateTestPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PagingStateTest {

    private val config = PagingConfig(pageSize = 20, maxSize = 100)

    @Test
    fun initial_state_is_empty() {
        val state = PagingState.initial<TestItem>(config)

        assertTrue(state.pages.isEmpty())
        assertTrue(state.items.isEmpty())
        assertFalse(state.isLoading)
        assertFalse(state.fullyLoaded)
        assertEquals(LoadState.NotLoading, state.loadStates[LoadDirection.INITIAL])
        assertEquals(LoadState.NotLoading, state.loadStates[LoadDirection.APPEND])
        assertEquals(LoadState.NotLoading, state.loadStates[LoadDirection.PREPEND])
    }

    @Test
    fun addPage_initial_replaces_everything() {
        val state = PagingState.initial<TestItem>(config)
        val page1 = generateTestPage(0, 20)

        val newState = state.addPage(page1, LoadDirection.INITIAL)

        assertEquals(1, newState.pages.size)
        assertEquals(20, newState.items.size)
        assertEquals("item-0", newState.items.first().id)
        assertEquals("item-19", newState.items.last().id)
    }

    @Test
    fun addPage_append_adds_to_end() {
        val state = PagingState.initial<TestItem>(config)
            .addPage(generateTestPage(0, 20), LoadDirection.INITIAL)

        val newState = state.addPage(generateTestPage(20, 20), LoadDirection.APPEND)

        assertEquals(2, newState.pages.size)
        assertEquals(40, newState.items.size)
        assertEquals("item-0", newState.items.first().id)
        assertEquals("item-39", newState.items.last().id)
    }

    @Test
    fun addPage_prepend_adds_to_beginning() {
        val state = PagingState.initial<TestItem>(config)
            .addPage(generateTestPage(20, 20), LoadDirection.INITIAL)

        val newState = state.addPage(generateTestPage(0, 20), LoadDirection.PREPEND)

        assertEquals(2, newState.pages.size)
        assertEquals(40, newState.items.size)
        assertEquals("item-0", newState.items.first().id)
        assertEquals("item-39", newState.items.last().id)
    }

    @Test
    fun addPage_updates_tokens() {
        val state = PagingState.initial<TestItem>(config)
        val page = generateTestPage(0, 20, hasNext = true, hasPrev = false)

        val newState = state.addPage(page, LoadDirection.INITIAL)

        assertEquals(page.next, newState.nextToken)
        assertEquals(page.prev, newState.prevToken)
    }

    @Test
    fun addPage_sets_fullyLoaded_when_no_tokens() {
        val state = PagingState.initial<TestItem>(config)
        val page = generateTestPage(0, 20, hasNext = false, hasPrev = false)

        val newState = state.addPage(page, LoadDirection.INITIAL)

        assertTrue(newState.fullyLoaded)
    }

    @Test
    fun addPage_respects_maxSize_on_append() {
        val smallConfig = PagingConfig(pageSize = 20, maxSize = 50)
        var state = PagingState.initial<TestItem>(smallConfig)

        // Add 3 pages of 20 items each = 60 items total
        // Should drop oldest from start to stay under maxSize of 50
        state = state.addPage(generateTestPage(0, 20), LoadDirection.INITIAL)
        state = state.addPage(generateTestPage(20, 20), LoadDirection.APPEND)
        state = state.addPage(generateTestPage(40, 20), LoadDirection.APPEND)

        // Should have 50 items max (dropped first page partially)
        assertTrue(state.items.size <= 50)
    }

    @Test
    fun addPage_respects_maxSize_on_prepend() {
        val smallConfig = PagingConfig(pageSize = 20, maxSize = 50)
        var state = PagingState.initial<TestItem>(smallConfig)

        // Add initial page at offset 40
        state = state.addPage(generateTestPage(40, 20), LoadDirection.INITIAL)

        // Prepend 2 more pages
        state = state.addPage(generateTestPage(20, 20), LoadDirection.PREPEND)
        state = state.addPage(generateTestPage(0, 20), LoadDirection.PREPEND)

        // Should have 50 items max (dropped last page partially)
        assertTrue(state.items.size <= 50)
    }

    @Test
    fun withLoadState_updates_specific_direction() {
        val state = PagingState.initial<TestItem>(config)

        val newState = state.withLoadState(LoadDirection.APPEND, LoadState.Loading)

        assertEquals(LoadState.Loading, newState.loadStates[LoadDirection.APPEND])
        assertEquals(LoadState.NotLoading, newState.loadStates[LoadDirection.INITIAL])
        assertEquals(LoadState.NotLoading, newState.loadStates[LoadDirection.PREPEND])
    }

    @Test
    fun withLoadState_isLoading_true_when_any_loading() {
        val state = PagingState.initial<TestItem>(config)
            .withLoadState(LoadDirection.APPEND, LoadState.Loading)

        assertTrue(state.isLoading)
    }

    @Test
    fun withLoadState_isLoading_false_when_none_loading() {
        val state = PagingState.initial<TestItem>(config)

        assertFalse(state.isLoading)
    }

    @Test
    fun withError_updates_load_state() {
        val state = PagingState.initial<TestItem>(config)
        val error = TestException("Test error")

        val newState = state.withError(LoadDirection.APPEND, error, canServeStale = true)

        val loadState = newState.loadStates[LoadDirection.APPEND]
        assertIs<LoadState.Error>(loadState)
        assertEquals(error, loadState.error)
        assertTrue(loadState.canServeStale)
    }

    @Test
    fun withError_preserves_existing_pages() {
        val state = PagingState.initial<TestItem>(config)
            .addPage(generateTestPage(0, 20), LoadDirection.INITIAL)

        val error = TestException("Test error")
        val newState = state.withError(LoadDirection.APPEND, error, canServeStale = true)

        assertEquals(1, newState.pages.size)
        assertEquals(20, newState.items.size)
    }

    @Test
    fun reset_clears_all_state() {
        val state = PagingState.initial<TestItem>(config)
            .addPage(generateTestPage(0, 20), LoadDirection.INITIAL)
            .addPage(generateTestPage(20, 20), LoadDirection.APPEND)
            .withLoadState(LoadDirection.APPEND, LoadState.Loading)

        val resetState = state.reset()

        assertTrue(resetState.pages.isEmpty())
        assertTrue(resetState.items.isEmpty())
        assertFalse(resetState.isLoading)
        assertFalse(resetState.fullyLoaded)
        assertEquals(LoadState.NotLoading, resetState.loadStates[LoadDirection.INITIAL])
    }

    @Test
    fun toSnapshot_creates_correct_snapshot() {
        val state = PagingState.initial<TestItem>(config)
            .addPage(generateTestPage(0, 20, hasNext = true, hasPrev = false), LoadDirection.INITIAL)

        val snapshot = state.toSnapshot()

        assertEquals(20, snapshot.items.size)
        assertEquals(state.nextToken, snapshot.next)
        assertEquals(state.prevToken, snapshot.prev)
        assertEquals(state.loadStates, snapshot.sourceStates)
        assertEquals(state.fullyLoaded, snapshot.fullyLoaded)
    }

    @Test
    fun items_are_flattened_from_all_pages() {
        val state = PagingState.initial<TestItem>(config)
            .addPage(generateTestPage(0, 20), LoadDirection.INITIAL)
            .addPage(generateTestPage(20, 20), LoadDirection.APPEND)
            .addPage(generateTestPage(40, 20), LoadDirection.APPEND)

        val items = state.items

        assertEquals(60, items.size)
        assertEquals("item-0", items.first().id)
        assertEquals("item-59", items.last().id)
    }

    @Test
    fun addPage_initial_second_time_replaces_previous() {
        val state = PagingState.initial<TestItem>(config)
            .addPage(generateTestPage(0, 20), LoadDirection.INITIAL)
            .addPage(generateTestPage(20, 20), LoadDirection.APPEND)

        // Add new initial page - should replace everything
        val newState = state.addPage(generateTestPage(100, 20), LoadDirection.INITIAL)

        assertEquals(1, newState.pages.size)
        assertEquals(20, newState.items.size)
        assertEquals("item-100", newState.items.first().id)
    }

    @Test
    fun addPage_sets_load_state_to_not_loading() {
        val state = PagingState.initial<TestItem>(config)
            .withLoadState(LoadDirection.INITIAL, LoadState.Loading)

        val newState = state.addPage(generateTestPage(0, 20), LoadDirection.INITIAL)

        assertEquals(LoadState.NotLoading, newState.loadStates[LoadDirection.INITIAL])
    }

    @Test
    fun multiple_appends_maintain_order() {
        var state = PagingState.initial<TestItem>(config)

        state = state.addPage(generateTestPage(0, 10), LoadDirection.INITIAL)
        state = state.addPage(generateTestPage(10, 10), LoadDirection.APPEND)
        state = state.addPage(generateTestPage(20, 10), LoadDirection.APPEND)
        state = state.addPage(generateTestPage(30, 10), LoadDirection.APPEND)

        val items = state.items
        assertEquals(40, items.size)
        for (i in 0 until 40) {
            assertEquals("item-$i", items[i].id)
        }
    }

    @Test
    fun multiple_prepends_maintain_order() {
        var state = PagingState.initial<TestItem>(config)

        state = state.addPage(generateTestPage(30, 10), LoadDirection.INITIAL)
        state = state.addPage(generateTestPage(20, 10), LoadDirection.PREPEND)
        state = state.addPage(generateTestPage(10, 10), LoadDirection.PREPEND)
        state = state.addPage(generateTestPage(0, 10), LoadDirection.PREPEND)

        val items = state.items
        assertEquals(40, items.size)
        for (i in 0 until 40) {
            assertEquals("item-$i", items[i].id)
        }
    }

    @Test
    fun maxSize_zero_means_unlimited() {
        val unlimitedConfig = PagingConfig(pageSize = 20, maxSize = 0)
        var state = PagingState.initial<TestItem>(unlimitedConfig)

        // Add many pages
        state = state.addPage(generateTestPage(0, 20), LoadDirection.INITIAL)
        state = state.addPage(generateTestPage(20, 20), LoadDirection.APPEND)
        state = state.addPage(generateTestPage(40, 20), LoadDirection.APPEND)
        state = state.addPage(generateTestPage(60, 20), LoadDirection.APPEND)
        state = state.addPage(generateTestPage(80, 20), LoadDirection.APPEND)

        // All 100 items should be retained
        assertEquals(100, state.items.size)
    }

    // ========== Oversized Single Page Tests ==========

    @Test
    fun single_page_exceeding_maxSize_is_trimmed_on_initial() {
        val smallConfig = PagingConfig(pageSize = 20, maxSize = 30)
        val state = PagingState.initial<TestItem>(smallConfig)

        // Add a single page with 50 items (exceeds maxSize of 30)
        val largePage = generateTestPage(0, 50, hasNext = true, hasPrev = false)

        val newState = state.addPage(largePage, LoadDirection.INITIAL)

        // INITIAL loads are now trimmed to respect maxSize
        // Should keep first 30 items (trimming from end)
        assertEquals(30, newState.items.size)
        assertEquals("item-0", newState.items.first().id)
        assertEquals("item-29", newState.items.last().id)

        // nextToken should be updated to point to the boundary of retained data
        assertNotNull(newState.nextToken)
    }

    @Test
    fun single_page_exceeding_maxSize_is_trimmed_on_append() {
        val smallConfig = PagingConfig(pageSize = 20, maxSize = 30)
        var state = PagingState.initial<TestItem>(smallConfig)

        // Start with a small initial page
        state = state.addPage(generateTestPage(0, 10), LoadDirection.INITIAL)
        assertEquals(10, state.items.size)

        // Append a very large page (40 items)
        val largePage = generateTestPage(10, 40, hasNext = true, hasPrev = true)

        val newState = state.addPage(largePage, LoadDirection.APPEND)

        // Should trim to maxSize (30 items total)
        assertTrue(newState.items.size <= 30, "Expected <= 30 items, got ${newState.items.size}")
        // Should keep newest items (from the end)
        assertTrue(newState.items.last().id.contains("49") || newState.items.last().id.contains("39"))
    }

    @Test
    fun single_page_exceeding_maxSize_is_trimmed_on_prepend() {
        val smallConfig = PagingConfig(pageSize = 20, maxSize = 30)
        var state = PagingState.initial<TestItem>(smallConfig)

        // Start with a small initial page at high offset
        state = state.addPage(generateTestPage(100, 10), LoadDirection.INITIAL)
        assertEquals(10, state.items.size)

        // Prepend a very large page (40 items)
        val largePage = generateTestPage(60, 40, hasNext = true, hasPrev = true)

        val newState = state.addPage(largePage, LoadDirection.PREPEND)

        // Should trim to maxSize (30 items total)
        assertTrue(newState.items.size <= 30, "Expected <= 30 items, got ${newState.items.size}")
        // Should keep newest items (from the start)
        assertTrue(newState.items.first().id.contains("60") || newState.items.first().id.contains("70"))
    }

    @Test
    fun partial_page_trimming_when_multiple_pages_exceed_maxSize() {
        val smallConfig = PagingConfig(pageSize = 20, maxSize = 45)
        var state = PagingState.initial<TestItem>(smallConfig)

        // Add first page (20 items)
        state = state.addPage(generateTestPage(0, 20), LoadDirection.INITIAL)

        // Add second page (20 items, total 40)
        state = state.addPage(generateTestPage(20, 20), LoadDirection.APPEND)
        assertEquals(40, state.items.size)

        // Add third page (20 items, total would be 60)
        // Should trim to 45 by dropping from start and partially including pages
        val newState = state.addPage(generateTestPage(40, 20), LoadDirection.APPEND)

        assertEquals(45, newState.items.size)
        // Should have kept most recent items
        assertEquals("item-59", newState.items.last().id)
    }
}
