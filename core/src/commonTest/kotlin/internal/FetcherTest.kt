package dev.mattramotar.storex.core.seams

import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.utils.TEST_KEY_1
import dev.mattramotar.storex.core.utils.TEST_USER_1
import dev.mattramotar.storex.core.utils.TEST_USER_2
import dev.mattramotar.storex.core.utils.TestException
import dev.mattramotar.storex.core.utils.TestUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class FetcherTest {

    @Test
    fun fetcherOf_givenSuccess_thenEmitsSuccess() = runTest {
        val fetcher = fetcherOf<StoreKey, TestUser> { key ->
            assertEquals(TEST_KEY_1, key)
            TEST_USER_1
        }

        val result = fetcher.fetch(TEST_KEY_1, FetchRequest()).single()

        val success = assertIs<FetcherResult.Success<TestUser>>(result)
        assertEquals(TEST_USER_1, success.body)
    }

    @Test
    fun fetcherOf_givenException_thenMapsToStoreException() = runTest {
        val failure = TestException("boom")
        val fetcher = fetcherOf<StoreKey, TestUser> {
            throw failure
        }

        val result = fetcher.fetch(TEST_KEY_1, FetchRequest()).single()

        val error = assertIs<FetcherResult.Error>(result)
        val storeError = assertIs<StoreException.Unknown>(error.error)
        assertEquals("boom", storeError.message)
        assertSame(failure, storeError.cause)
    }

    @Test
    fun streamingFetcherOf_givenValues_thenWrapsEachEmissionAsSuccess() = runTest {
        val fetcher = streamingFetcherOf<StoreKey, TestUser> { key ->
            assertEquals(TEST_KEY_1, key)
            flowOf(TEST_USER_1, TEST_USER_2)
        }

        val results = fetcher.fetch(TEST_KEY_1, FetchRequest()).toList()

        assertEquals(2, results.size)
        assertEquals(TEST_USER_1, assertIs<FetcherResult.Success<TestUser>>(results[0]).body)
        assertEquals(TEST_USER_2, assertIs<FetcherResult.Success<TestUser>>(results[1]).body)
    }

    @Test
    fun streamingFetcherOf_givenUpstreamException_thenMapsToStoreException() = runTest {
        val failure = TestException("stream boom")
        val fetcher = streamingFetcherOf<StoreKey, TestUser> {
            flow {
                emit(TEST_USER_1)
                throw failure
            }
        }

        val results = fetcher.fetch(TEST_KEY_1, FetchRequest()).toList()

        assertEquals(2, results.size)
        assertEquals(TEST_USER_1, assertIs<FetcherResult.Success<TestUser>>(results[0]).body)

        val error = assertIs<FetcherResult.Error>(results[1])
        val storeError = assertIs<StoreException.Unknown>(error.error)
        assertEquals("stream boom", storeError.message)
        assertSame(failure, storeError.cause)
    }
}
