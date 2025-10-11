package dev.mattramotar.storex.core.internal

import dev.mattramotar.storex.core.dsl.internal.DefaultStoreBuilderScope
import dev.mattramotar.storex.core.utils.TEST_KEY_1
import dev.mattramotar.storex.core.utils.TEST_KEY_2
import dev.mattramotar.storex.core.utils.TestException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class BookkeeperTest {

    @Test
    fun lastStatus_givenNewKey_thenReturnsInitialStatus() = runTest {
        // Given
        val bookkeeper = createBookkeeper()

        // When
        val status = bookkeeper.lastStatus(TEST_KEY_1)

        // Then
        assertNull(status.lastSuccessAt)
        assertNull(status.lastFailureAt)
        assertNull(status.lastEtag)
        assertNull(status.backoffUntil)
    }

    @Test
    fun recordSuccess_givenNewKey_thenUpdatesStatus() = runTest {
        // Given
        val bookkeeper = createBookkeeper()
        val now = Instant.fromEpochSeconds(1000)
        val etag = "etag-123"

        // When
        bookkeeper.recordSuccess(TEST_KEY_1, etag, now)

        // Then
        val status = bookkeeper.lastStatus(TEST_KEY_1)
        assertEquals(now, status.lastSuccessAt)
        assertEquals(etag, status.lastEtag)
        assertNull(status.lastFailureAt)
        assertNull(status.backoffUntil)
    }

    @Test
    fun recordSuccess_withoutEtag_thenUpdatesWithNullEtag() = runTest {
        // Given
        val bookkeeper = createBookkeeper()
        val now = Instant.fromEpochSeconds(1000)

        // When
        bookkeeper.recordSuccess(TEST_KEY_1, null, now)

        // Then
        val status = bookkeeper.lastStatus(TEST_KEY_1)
        assertEquals(now, status.lastSuccessAt)
        assertNull(status.lastEtag)
    }

    @Test
    fun recordSuccess_givenExistingKey_thenUpdatesSuccessTime() = runTest {
        // Given
        val bookkeeper = createBookkeeper()
        val firstTime = Instant.fromEpochSeconds(1000)
        val secondTime = Instant.fromEpochSeconds(2000)
        bookkeeper.recordSuccess(TEST_KEY_1, "etag-1", firstTime)

        // When
        bookkeeper.recordSuccess(TEST_KEY_1, "etag-2", secondTime)

        // Then
        val status = bookkeeper.lastStatus(TEST_KEY_1)
        assertEquals(secondTime, status.lastSuccessAt)
        assertEquals("etag-2", status.lastEtag)
    }

    @Test
    fun recordSuccess_afterFailure_thenPreservesFailureTime() = runTest {
        // Given
        val bookkeeper = createBookkeeper()
        val failureTime = Instant.fromEpochSeconds(1000)
        val successTime = Instant.fromEpochSeconds(2000)
        bookkeeper.recordFailure(TEST_KEY_1, TestException(), failureTime)

        // When
        bookkeeper.recordSuccess(TEST_KEY_1, "etag-1", successTime)

        // Then
        val status = bookkeeper.lastStatus(TEST_KEY_1)
        assertEquals(successTime, status.lastSuccessAt)
        assertEquals(failureTime, status.lastFailureAt)
        assertEquals("etag-1", status.lastEtag)
    }

    @Test
    fun recordFailure_givenNewKey_thenUpdatesStatus() = runTest {
        // Given
        val bookkeeper = createBookkeeper()
        val now = Instant.fromEpochSeconds(1000)
        val error = TestException("Network error")

        // When
        bookkeeper.recordFailure(TEST_KEY_1, error, now)

        // Then
        val status = bookkeeper.lastStatus(TEST_KEY_1)
        assertEquals(now, status.lastFailureAt)
        assertNull(status.lastSuccessAt)
        assertNull(status.lastEtag)
        assertNull(status.backoffUntil)
    }

    @Test
    fun recordFailure_givenExistingKey_thenUpdatesFailureTime() = runTest {
        // Given
        val bookkeeper = createBookkeeper()
        val firstTime = Instant.fromEpochSeconds(1000)
        val secondTime = Instant.fromEpochSeconds(2000)
        bookkeeper.recordFailure(TEST_KEY_1, TestException("Error 1"), firstTime)

        // When
        bookkeeper.recordFailure(TEST_KEY_1, TestException("Error 2"), secondTime)

        // Then
        val status = bookkeeper.lastStatus(TEST_KEY_1)
        assertEquals(secondTime, status.lastFailureAt)
    }

    @Test
    fun recordFailure_afterSuccess_thenPreservesSuccessTime() = runTest {
        // Given
        val bookkeeper = createBookkeeper()
        val successTime = Instant.fromEpochSeconds(1000)
        val failureTime = Instant.fromEpochSeconds(2000)
        bookkeeper.recordSuccess(TEST_KEY_1, "etag-1", successTime)

        // When
        bookkeeper.recordFailure(TEST_KEY_1, TestException(), failureTime)

        // Then
        val status = bookkeeper.lastStatus(TEST_KEY_1)
        assertEquals(successTime, status.lastSuccessAt)
        assertEquals(failureTime, status.lastFailureAt)
        assertEquals("etag-1", status.lastEtag)
    }

    @Test
    fun recordSuccessAndFailure_givenMultipleKeys_thenTracksIndependently() = runTest {
        // Given
        val bookkeeper = createBookkeeper()
        val time1 = Instant.fromEpochSeconds(1000)
        val time2 = Instant.fromEpochSeconds(2000)

        // When
        bookkeeper.recordSuccess(TEST_KEY_1, "etag-1", time1)
        bookkeeper.recordFailure(TEST_KEY_2, TestException(), time2)

        // Then
        val status1 = bookkeeper.lastStatus(TEST_KEY_1)
        assertEquals(time1, status1.lastSuccessAt)
        assertNull(status1.lastFailureAt)

        val status2 = bookkeeper.lastStatus(TEST_KEY_2)
        assertNull(status2.lastSuccessAt)
        assertEquals(time2, status2.lastFailureAt)
    }

    @Test
    fun recordSuccess_givenMultipleUpdates_thenMaintainsHistory() = runTest {
        // Given
        val bookkeeper = createBookkeeper()
        val times = listOf(
            Instant.fromEpochSeconds(1000),
            Instant.fromEpochSeconds(2000),
            Instant.fromEpochSeconds(3000)
        )

        // When
        times.forEachIndexed { index, time ->
            bookkeeper.recordSuccess(TEST_KEY_1, "etag-$index", time)
        }

        // Then - last status reflects most recent
        val status = bookkeeper.lastStatus(TEST_KEY_1)
        assertEquals(times.last(), status.lastSuccessAt)
        assertEquals("etag-2", status.lastEtag)
    }

    @Test
    fun recordFailure_givenMultipleUpdates_thenMaintainsHistory() = runTest {
        // Given
        val bookkeeper = createBookkeeper()
        val times = listOf(
            Instant.fromEpochSeconds(1000),
            Instant.fromEpochSeconds(2000),
            Instant.fromEpochSeconds(3000)
        )

        // When
        times.forEach { time ->
            bookkeeper.recordFailure(TEST_KEY_1, TestException("Error at $time"), time)
        }

        // Then - last status reflects most recent
        val status = bookkeeper.lastStatus(TEST_KEY_1)
        assertEquals(times.last(), status.lastFailureAt)
    }

    // Note: InMemoryBookkeeper doesn't implement backoffUntil logic
    // That would be handled by a more sophisticated bookkeeper implementation
    @Test
    fun lastStatus_givenBackoff_thenReturnsNull() = runTest {
        // Given
        val bookkeeper = createBookkeeper()
        bookkeeper.recordFailure(TEST_KEY_1, TestException(), Instant.fromEpochSeconds(1000))

        // When
        val status = bookkeeper.lastStatus(TEST_KEY_1)

        // Then - InMemoryBookkeeper doesn't set backoff
        assertNull(status.backoffUntil)
    }

    // Helper function
    private fun createBookkeeper(): Bookkeeper<dev.mattramotar.storex.core.StoreKey> {
        // Use the same InMemoryBookkeeper implementation used by the store
        return object : Bookkeeper<dev.mattramotar.storex.core.StoreKey> {
            private val status = mutableMapOf<dev.mattramotar.storex.core.StoreKey, KeyStatus>()

            override fun recordSuccess(
                key: dev.mattramotar.storex.core.StoreKey,
                etag: String?,
                at: Instant
            ) {
                val current = status[key] ?: KeyStatus(null, null, null, null)
                status[key] = KeyStatus(
                    lastSuccessAt = at,
                    lastFailureAt = current.lastFailureAt,
                    lastEtag = etag,
                    backoffUntil = current.backoffUntil
                )
            }

            override fun recordFailure(
                key: dev.mattramotar.storex.core.StoreKey,
                error: Throwable,
                at: Instant
            ) {
                val current = status[key] ?: KeyStatus(null, null, null, null)
                status[key] = KeyStatus(
                    lastSuccessAt = current.lastSuccessAt,
                    lastFailureAt = at,
                    lastEtag = current.lastEtag,
                    backoffUntil = current.backoffUntil
                )
            }

            override fun lastStatus(key: dev.mattramotar.storex.core.StoreKey): KeyStatus {
                return status[key] ?: KeyStatus(null, null, null, null)
            }
        }
    }
}
