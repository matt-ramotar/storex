package dev.mattramotar.storex.paging

import dev.mattramotar.storex.core.QueryKey
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.StoreNamespace

/**
 * Test key for pagination tests - uses QueryKey to avoid sealed class issues.
 */
fun TestKey(id: String = "test"): StoreKey {
    return QueryKey(
        namespace = StoreNamespace("test"),
        query = mapOf("id" to id)
    )
}

/**
 * Test item for pagination tests.
 */
data class TestItem(
    val id: String,
    val value: String
)

/**
 * Offset-based page token for tests.
 */
fun OffsetToken(offset: Int): PageToken = PageToken(before = null, after = offset.toString())

/**
 * Cursor-based page token for tests.
 */
fun CursorToken(cursor: String): PageToken = PageToken(before = null, after = cursor)

/**
 * Generate a test page with sequential items.
 */
fun generateTestPage(
    startIndex: Int,
    pageSize: Int,
    hasNext: Boolean = true,
    hasPrev: Boolean = startIndex > 0
): Page<TestItem> {
    val items = (startIndex until startIndex + pageSize).map { index ->
        TestItem(
            id = "item-$index",
            value = "Value $index"
        )
    }

    return Page(
        items = items,
        next = if (hasNext) OffsetToken(startIndex + pageSize) else null,
        prev = if (hasPrev) OffsetToken(maxOf(0, startIndex - pageSize)) else null
    )
}

/**
 * Create a simple test fetcher that generates pages.
 */
fun createTestFetcher(
    totalItems: Int = 100,
    pageSize: Int = 20,
    delay: Long = 0
): suspend (StoreKey, PageToken?) -> Page<TestItem> = { _, token ->
    if (delay > 0) {
        kotlinx.coroutines.delay(delay)
    }

    val offsetStr = token?.after
    val offset = offsetStr?.toIntOrNull() ?: 0
    val remaining = totalItems - offset
    val actualPageSize = minOf(pageSize, remaining)

    generateTestPage(
        startIndex = offset,
        pageSize = actualPageSize,
        hasNext = offset + actualPageSize < totalItems,
        hasPrev = offset > 0
    )
}

/**
 * Create a test fetcher that throws an error.
 */
fun createErrorFetcher(
    error: Throwable = TestException("Test error")
): suspend (StoreKey, PageToken?) -> Page<TestItem> = { _, _ ->
    throw error
}

/**
 * Test exception.
 */
class TestException(message: String) : Exception(message)

/**
 * Test network exception.
 */
class TestNetworkException(message: String) : Exception(message)
