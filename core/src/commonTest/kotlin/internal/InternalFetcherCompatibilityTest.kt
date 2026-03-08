package dev.mattramotar.storex.core.internal

import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.seams.FetcherResult
import dev.mattramotar.storex.core.utils.TEST_KEY_1
import dev.mattramotar.storex.core.utils.TEST_USER_1
import dev.mattramotar.storex.core.utils.TestUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class InternalFetcherCompatibilityTest {

    @Test
    fun fetcherOf_forwardsToSeamFetcher() = runTest {
        val fetcher = fetcherOf<StoreKey, TestUser> { TEST_USER_1 }

        val result = fetcher.fetch(TEST_KEY_1, FetchRequest()).single()

        assertEquals(TEST_USER_1, assertIs<FetcherResult.Success<TestUser>>(result).body)
    }

    @Test
    fun streamingFetcherOf_forwardsToSeamFetcher() = runTest {
        val fetcher = streamingFetcherOf<StoreKey, TestUser> { flowOf(TEST_USER_1) }

        val result = fetcher.fetch(TEST_KEY_1, FetchRequest()).single()

        assertEquals(TEST_USER_1, assertIs<FetcherResult.Success<TestUser>>(result).body)
    }
}
