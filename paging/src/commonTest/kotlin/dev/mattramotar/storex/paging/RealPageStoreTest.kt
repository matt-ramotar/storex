package dev.mattramotar.storex.paging

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RealPageStoreTest {

    @Test
    fun stream_triggers_initial_load_automatically() = runTest {
        val store = pageStore<TestKey, TestItem> {
            fetcher(createTestFetcher(totalItems = 100, pageSize = 20))
        }

        store.stream(TestKey()).test {
            val event = awaitItem()
            assertIs<PagingEvent.Snapshot<TestItem>>(event)

            val snapshot = event.value
            assertEquals(20, snapshot.items.size)
            assertEquals("item-0", snapshot.items.first().id)
            assertEquals("item-19", snapshot.items.last().id)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun load_append_adds_next_page() = runTest {
        val store = pageStore<TestKey, TestItem> {
            fetcher(createTestFetcher(totalItems = 100, pageSize = 20))
        }

        val key = TestKey()

        store.stream(key).test {
            // Initial load
            val initial = (awaitItem() as PagingEvent.Snapshot).value
            assertEquals(20, initial.items.size)

            // Load next page
            store.load(key, LoadDirection.APPEND)

            // Wait for append
            val appended = (awaitItem() as PagingEvent.Snapshot).value
            assertEquals(40, appended.items.size)
            assertEquals("item-0", appended.items.first().id)
            assertEquals("item-39", appended.items.last().id)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun load_prepend_adds_previous_page() = runTest {
        val store = pageStore<TestKey, TestItem> {
            fetcher { _, token ->
                val offset = (token as? OffsetToken)?.offset ?: 40
                generateTestPage(offset, 20, hasNext = true, hasPrev = offset > 0)
            }
        }

        val key = TestKey()

        store.stream(key).test {
            // Initial load (starts at offset 40)
            val initial = (awaitItem() as PagingEvent.Snapshot).value
            assertEquals(20, initial.items.size)
            assertEquals("item-40", initial.items.first().id)

            // Load previous page
            store.load(key, LoadDirection.PREPEND)

            // Wait for prepend
            val prepended = (awaitItem() as PagingEvent.Snapshot).value
            assertEquals(40, prepended.items.size)
            assertEquals("item-20", prepended.items.first().id)
            assertEquals("item-59", prepended.items.last().id)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun load_initial_replaces_existing_pages() = runTest {
        val store = pageStore<TestKey, TestItem> {
            fetcher(createTestFetcher(totalItems = 100, pageSize = 20))
        }

        val key = TestKey()

        store.stream(key).test {
            // Initial load
            val initial = (awaitItem() as PagingEvent.Snapshot).value
            assertEquals(20, initial.items.size)

            // Append a page
            store.load(key, LoadDirection.APPEND)
            val appended = (awaitItem() as PagingEvent.Snapshot).value
            assertEquals(40, appended.items.size)

            // Load initial again - should replace everything
            store.load(key, LoadDirection.INITIAL)
            val refreshed = (awaitItem() as PagingEvent.Snapshot).value
            assertEquals(20, refreshed.items.size)
            assertEquals("item-0", refreshed.items.first().id)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun load_handles_errors() = runTest {
        val errorFetcher = createErrorFetcher(TestNetworkException("Network error"))

        val store = pageStore<TestKey, TestItem> {
            fetcher(errorFetcher)
        }

        val key = TestKey()

        store.stream(key).test {
            val event = awaitItem()
            assertIs<PagingEvent.Snapshot<TestItem>>(event)

            val snapshot = event.value
            val loadState = snapshot.sourceStates[LoadDirection.INITIAL]
            assertIs<LoadState.Error>(loadState)
            assertTrue(loadState.error is TestNetworkException)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun load_does_not_duplicate_concurrent_requests() = runTest {
        var fetchCount = 0

        val store = pageStore<TestKey, TestItem> {
            fetcher { _, token ->
                fetchCount++
                delay(100) // Simulate slow network
                generateTestPage(0, 20)
            }
        }

        val key = TestKey()

        // Trigger multiple concurrent loads
        store.load(key, LoadDirection.INITIAL)
        store.load(key, LoadDirection.INITIAL)
        store.load(key, LoadDirection.INITIAL)

        delay(200) // Wait for loads to complete

        // Should only fetch once due to mutex
        assertEquals(1, fetchCount)
    }

    @Test
    fun load_respects_null_next_token() = runTest {
        val store = pageStore<TestKey, TestItem> {
            fetcher { _, _ ->
                // Return page with no next token (end of list)
                generateTestPage(0, 20, hasNext = false, hasPrev = false)
            }
        }

        val key = TestKey()

        store.stream(key).test {
            val initial = (awaitItem() as PagingEvent.Snapshot).value

            // Try to load more - should not trigger fetch
            store.load(key, LoadDirection.APPEND)

            // Should not emit new event since there's nothing to load
            // (no next token available)

            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_emits_updated_snapshots() = runTest {
        val store = pageStore<TestKey, TestItem> {
            fetcher(createTestFetcher(totalItems = 60, pageSize = 20))
        }

        val key = TestKey()

        store.stream(key).test {
            // Snapshot 1: Initial load
            val snapshot1 = (awaitItem() as PagingEvent.Snapshot).value
            assertEquals(20, snapshot1.items.size)

            // Load more
            store.load(key, LoadDirection.APPEND)

            // Snapshot 2: After append
            val snapshot2 = (awaitItem() as PagingEvent.Snapshot).value
            assertEquals(40, snapshot2.items.size)

            // Load more
            store.load(key, LoadDirection.APPEND)

            // Snapshot 3: After second append
            val snapshot3 = (awaitItem() as PagingEvent.Snapshot).value
            assertEquals(60, snapshot3.items.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun load_stops_when_fully_loaded() = runTest {
        val store = pageStore<TestKey, TestItem> {
            fetcher(createTestFetcher(totalItems = 40, pageSize = 20))
        }

        val key = TestKey()

        store.stream(key).test {
            // Initial load (20 items)
            val initial = (awaitItem() as PagingEvent.Snapshot).value
            assertEquals(20, initial.items.size)
            assertFalse(initial.fullyLoaded)

            // Append (20 more items, reaches end)
            store.load(key, LoadDirection.APPEND)
            val final = (awaitItem() as PagingEvent.Snapshot).value
            assertEquals(40, final.items.size)
            assertTrue(final.fullyLoaded)

            // Try to load more - should not emit since fully loaded
            store.load(key, LoadDirection.APPEND)
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun multiple_keys_maintain_separate_state() = runTest {
        val store = pageStore<TestKey, TestItem> {
            fetcher { key, token ->
                val offset = (token as? OffsetToken)?.offset ?: 0
                // Generate different data based on key
                val startId = if (key.id == "key1") 0 else 100
                Page(
                    items = (startId + offset until startId + offset + 20).map {
                        TestItem("item-$it", "Value $it")
                    },
                    next = OffsetToken(offset + 20),
                    prev = if (offset > 0) OffsetToken(offset - 20) else null
                )
            }
        }

        val key1 = TestKey("key1")
        val key2 = TestKey("key2")

        // Load from key1
        store.stream(key1).test {
            val snapshot1 = (awaitItem() as PagingEvent.Snapshot).value
            assertEquals("item-0", snapshot1.items.first().id)
            cancelAndIgnoreRemainingEvents()
        }

        // Load from key2
        store.stream(key2).test {
            val snapshot2 = (awaitItem() as PagingEvent.Snapshot).value
            assertEquals("item-100", snapshot2.items.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun load_with_custom_token() = runTest {
        val store = pageStore<TestKey, TestItem> {
            fetcher { _, token ->
                val offset = (token as? OffsetToken)?.offset ?: 0
                generateTestPage(offset, 20)
            }
        }

        val key = TestKey()

        // Load with custom token (skip to offset 40)
        store.load(key, LoadDirection.INITIAL, from = OffsetToken(40))

        store.stream(key).test {
            val snapshot = (awaitItem() as PagingEvent.Snapshot).value
            assertEquals(20, snapshot.items.size)
            assertEquals("item-40", snapshot.items.first().id)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun config_maxSize_is_respected() = runTest {
        val store = pageStore<TestKey, TestItem> {
            fetcher(createTestFetcher(totalItems = 200, pageSize = 20))

            config {
                maxSize = 50
            }
        }

        val key = TestKey()

        store.stream(key).test {
            // Initial: 20 items
            val initial = (awaitItem() as PagingEvent.Snapshot).value
            assertEquals(20, initial.items.size)

            // Append: 40 items total
            store.load(key, LoadDirection.APPEND)
            val second = (awaitItem() as PagingEvent.Snapshot).value
            assertEquals(40, second.items.size)

            // Append: Should trim to maxSize of 50
            store.load(key, LoadDirection.APPEND)
            val third = (awaitItem() as PagingEvent.Snapshot).value
            assertTrue(third.items.size <= 50)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
