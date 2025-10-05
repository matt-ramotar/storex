package dev.mattramotar.storex.testing

import dev.mattramotar.storex.core.Freshness
import dev.mattramotar.storex.core.Store
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.StoreResult
import kotlinx.coroutines.flow.Flow

/**
 * Test implementation of Store for unit testing.
 *
 * Provides a fake, in-memory Store implementation with configurable responses,
 * delays, and error scenarios for comprehensive testing.
 *
 * **Planned Features** (to be implemented):
 * - In-memory storage with immediate responses
 * - Configurable mock responses per key
 * - Simulated network delays
 * - Error injection for testing error handling
 * - Request history tracking for verification
 * - Turbine extensions for Flow testing
 * - Coroutine test helpers
 *
 * Example usage (future):
 * ```kotlin
 * @Test
 * fun `test user fetch`() = runTest {
 *     val testStore = TestStore<UserKey, User>()
 *     testStore.mockResponse(UserKey("123"), User(id = "123", name = "Alice"))
 *
 *     val result = testStore.get(UserKey("123"))
 *     assertEquals(User(id = "123", name = "Alice"), result.requireData())
 *
 *     // Verify request was made
 *     testStore.assertRequested(UserKey("123"))
 * }
 * ```
 *
 * @param K The store key type
 * @param V The domain value type
 */
interface TestStore<K : StoreKey, V> : Store<K, V> {
    /**
     * Configures a mock response for a specific key.
     *
     * @param key The key to mock
     * @param value The value to return
     */
    fun mockResponse(key: K, value: V)

    /**
     * Configures a mock error for a specific key.
     *
     * @param key The key to mock
     * @param error The error to throw
     */
    fun mockError(key: K, error: Throwable)

    /**
     * Clears all mock responses and request history.
     */
    fun clear()

    /**
     * Returns the request history for verification.
     */
    fun getRequestHistory(): List<K>
}

// TODO: Implement the following in future phases:
// - FakeStore: Basic in-memory implementation
// - MockFetcher: Configurable fetcher for testing
// - InMemorySourceOfTruth: Simple persistence for tests
// - turbineTest(): Extension for testing Store flows with Turbine
// - runStoreTest(): Coroutine test scope with time control
// - StoreTestRule: JUnit test rule for Store testing
// - assertData(): Custom assertions for StoreResult
// - awaitData(): Suspending assertion helpers
