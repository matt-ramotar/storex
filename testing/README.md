# StoreX Testing

**Test utilities and helpers for StoreX applications**

The `:testing` module provides comprehensive testing utilities for StoreX, including fake implementations, test builders, assertion helpers, and integration with popular testing frameworks.

> **Status**: 🚧 **Placeholder Implementation** - Full implementation planned for future release

## 📦 What's Included

This module will provide:

- **`TestStore`** - In-memory test implementation
- **`FakeSourceOfTruth`** - Controllable fake persistence
- **`FakeFetcher`** - Controllable network responses
- **`StoreTestRule`** - JUnit test rule for StoreX
- **Assertion Helpers** - Fluent assertions for Store behavior
- **Turbine Integration** - Flow testing utilities
- **Mock Builders** - DSL for creating test data

## 🎯 When to Use

Use this module for:

- **Unit testing** store logic
- **Integration testing** with fake backends
- **Flow testing** with Turbine
- **Assertion helpers** for cleaner test code
- **Test data builders** for consistent fixtures

## 🚀 Planned Usage

```kotlin
import dev.mattramotar.storex.testing.*
import dev.mattramotar.storex.testing.turbine.*

class UserStoreTest {
    @Test
    fun `get should fetch from network when cache miss`() = runTest {
        // Create test store with fake backend
        val testStore = testStore<ByIdKey, User> {
            fakeSourceOfTruth {
                // Pre-populate with test data
                put(userKey, testUser)
            }

            fakeFetcher {
                // Control network responses
                onFetch(userKey) { FetcherResult.Success(networkUser) }
            }
        }

        // Test with Turbine
        testStore.stream(userKey).test {
            // Assert emissions
            awaitItem().shouldBeLoading()
            awaitItem().shouldBeData(networkUser)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `update should apply optimistically`() = runTest {
        val store = testMutationStore<ByIdKey, User, UserPatch, UserDraft> {
            initialData = mapOf(userKey to testUser)
        }

        store.stream(userKey).test {
            val initial = awaitItem()
            initial.shouldBeData(testUser)

            // Trigger optimistic update
            store.update(userKey, UserPatch(name = "New Name"))

            // Should emit optimistic value immediately
            val optimistic = awaitItem()
            optimistic.shouldBeData().name shouldBe "New Name"

            // Then emit server response
            val synced = awaitItem()
            synced.shouldBeData().name shouldBe "Server Name"
        }
    }
}
```

## 📚 Planned Features

### TestStore

```kotlin
val testStore = testStore<ByIdKey, User> {
    // Pre-populate cache
    memoryCache {
        put(key1, user1)
        put(key2, user2)
    }

    // Control network responses
    fakeFetcher {
        onFetch(key1) { FetcherResult.Success(user1) }
        onFetch(key2) { FetcherResult.Error(IOException()) }
    }

    // Control persistence
    fakeSourceOfTruth {
        put(key1, dbUser1)
        // key2 not in DB (cache miss)
    }

    // Configure behavior
    config {
        delay = 100.milliseconds  // Simulate network latency
        failureRate = 0.1          // 10% random failures
    }
}
```

### Assertion Helpers

```kotlin
// Fluent assertions
result.shouldBeData(expectedUser)
result.shouldBeLoading()
result.shouldBeError<IOException>()

// Flow assertions with Turbine
store.stream(key).test {
    awaitItem().shouldBeLoading()
    awaitItem().shouldBeData().apply {
        name shouldBe "Alice"
        email shouldBe "alice@example.com"
    }
    expectNoEvents()
}

// State assertions
snapshot.items.shouldContainExactly(item1, item2, item3)
snapshot.loadState.shouldBeIdle()
```

### Test Builders

```kotlin
// Build test data with DSL
val testUser = user {
    id = "user-123"
    name = "Alice"
    email = "alice@example.com"
    posts = listOf(
        post {
            id = "post-1"
            title = "Test Post"
        }
    )
}

// Build test keys
val testKey = byIdKey {
    namespace = "users"
    entityType = "User"
    entityId = "123"
}
```

### Fake Implementations

```kotlin
class FakeFetcher<K, V> : Fetcher<K, V> {
    private val responses = mutableMapOf<K, FetcherResult<V>>()
    private val calls = mutableListOf<K>()

    fun onFetch(key: K, result: () -> FetcherResult<V>) {
        responses[key] = result()
    }

    override fun fetch(key: K, request: FetchRequest): Flow<FetcherResult<V>> = flow {
        calls.add(key)
        val result = responses[key] ?: FetcherResult.Error(IllegalStateException("No response configured"))
        emit(result)
    }

    fun verify(key: K, times: Int = 1) {
        calls.count { it == key } shouldBe times
    }
}
```

## 🏗️ Architecture

### Module Dependencies

```
testing
├── core (API dependency)
│   └── Store interface
├── mutations (API dependency)
│   └── MutationStore interface
├── kotlin-test
├── kotlinx-coroutines-test
├── turbine (Flow testing)
└── kotest-assertions (optional)
```

### Package Structure

```
dev.mattramotar.storex.testing
├── TestStore.kt                  # Test store implementation (planned)
├── FakeFetcher.kt                # Fake network (planned)
├── FakeSourceOfTruth.kt          # Fake persistence (planned)
├── assertions/                   # Assertion helpers (planned)
│   ├── StoreResultAssertions.kt
│   ├── PagingAssertions.kt
│   └── NormalizationAssertions.kt
├── builders/                     # Test data builders (planned)
│   ├── UserBuilder.kt
│   ├── KeyBuilder.kt
│   └── PageBuilder.kt
└── turbine/                      # Turbine integration (planned)
    └── StoreTurbineExtensions.kt
```

## 🔗 Related Modules

- **`:core`** - Base Store functionality (tested by this module)
- **`:mutations`** - Mutation operations (tested by this module)
- **`:normalization:runtime`** - Normalization (tested by this module)
- **`:paging`** - Pagination (tested by this module)

## 💡 Planned Best Practices

1. **Use TestStore for unit tests** - Don't use real network/DB
2. **Use Turbine for Flow testing** - Cleaner than manual collection
3. **Pre-populate test data** - Make tests deterministic
4. **Verify network calls** - Ensure correct caching behavior
5. **Test error scenarios** - Network failures, timeouts, etc.
6. **Test optimistic updates** - Verify rollback on failure
7. **Use builders** - Reduce test boilerplate

## 📊 Roadmap

### v1.1 (Planned)
- [ ] `TestStore` implementation
- [ ] `FakeFetcher` and `FakeSourceOfTruth`
- [ ] Basic assertion helpers
- [ ] Turbine integration

### v1.2 (Planned)
- [ ] Test builders DSL
- [ ] StoreTestRule for JUnit
- [ ] Advanced assertions
- [ ] Kotest matchers

### v2.0 (Future)
- [ ] Property-based testing
- [ ] Snapshot testing
- [ ] Visual regression testing
- [ ] Performance benchmarks

## 📄 License

Apache 2.0 - See [LICENSE](../LICENSE) for details.
