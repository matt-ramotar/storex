# StoreX Core

**Foundation module for reactive caching and data synchronization**

The `:core` module provides the fundamental building blocks for StoreX: read-only stores with multi-tier caching, reactive updates, and freshness control. This is the foundation upon which all other StoreX modules are built.

## 📦 What's Included

This module provides:

- **`Store<Key, Domain>`** - Read-only store interface with reactive Flow APIs
- **`StoreKey`** - Type-safe key abstraction (`ByIdKey`, `QueryKey`)
- **`Freshness`** - Policies for controlling data staleness
- **`SourceOfTruth`** - Interface for persistent local storage
- **`Fetcher`** - Interface for remote data fetching
- **`Converter`** - Type conversion between network, database, and domain models
- **`MemoryCache`** - LRU in-memory caching
- **DSL Builder** - Kotlin DSL for configuring stores (`store<Key, Value> { }`)

## 🎯 When to Use

Use this module when you need:

- **Read-only data access** with caching and reactive updates
- **Multi-tier caching** (Memory → Local DB → Network)
- **Offline-first capabilities** with automatic synchronization
- **Freshness control** to manage cache staleness
- **Type-safe keys** for cache invalidation and querying

**NOT for:**
- Write operations (create/update/delete) → Use `:mutations`
- Graph normalization → Use `:normalization:runtime`
- Pagination → Use `:paging`

## 🚀 Getting Started

### Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
}
```

### Basic Usage

```kotlin
import dev.mattramotar.storex.core.*
import dev.mattramotar.storex.core.dsl.*

// Define your domain model
data class User(
    val id: String,
    val name: String,
    val email: String
)

// Create a store
val userStore = store<ByIdKey, User> {
    // Network fetcher
    fetcher { key ->
        flow {
            val user = api.getUser(key.entity.id)
            emit(FetcherResult.Success(user))
        }
    }

    // Local persistence (optional)
    sourceOfTruth(
        reader = { key -> database.getUserFlow(key.entity.id) },
        writer = { key, user -> database.saveUser(user) }
    )

    // Type conversion (if network/db types differ)
    converter(
        netToDbWrite = { key, net -> net },  // Network → Database
        dbReadToDomain = { key, db -> db }   // Database → Domain
    )

    // Memory cache configuration
    memoryCache {
        maxSize = 100
    }
}

// Read data (suspending get)
suspend fun loadUser(userId: String): User {
    val key = ByIdKey(
        namespace = StoreNamespace("users"),
        entity = EntityId("User", userId)
    )
    return userStore.get(key)
}

// Stream reactive updates
fun observeUser(userId: String): Flow<User> {
    val key = ByIdKey(
        namespace = StoreNamespace("users"),
        entity = EntityId("User", userId)
    )
    return userStore.stream(key, Freshness.CachedOrFetch)
        .mapNotNull { result ->
            when (result) {
                is StoreResult.Data -> result.value
                is StoreResult.Loading -> null
                is StoreResult.Error -> throw result.throwable
            }
        }
}
```

## 📚 Key Concepts

### Store Interface

The core `Store<Key, Domain>` interface provides:

```kotlin
interface Store<Key : StoreKey, out Domain> {
    // Suspend until first value (throws on error)
    suspend fun get(key: Key, freshness: Freshness = CachedOrFetch): Domain

    // Stream reactive updates
    fun stream(key: Key, freshness: Freshness = CachedOrFetch): Flow<StoreResult<Domain>>

    // Invalidation
    fun invalidate(key: Key)
    fun invalidateNamespace(namespace: StoreNamespace)
    fun invalidateAll()
}
```

### Freshness Policies

Control when data is fetched vs served from cache:

```kotlin
// Serve cached immediately, refresh in background
Freshness.CachedOrFetch  // Default - best for most UI

// Only fetch if older than threshold
Freshness.MinAge(5.minutes)  // Time-sensitive data

// Always fetch, fail if offline
Freshness.MustBeFresh  // Critical operations

// Serve stale cache if fetch fails
Freshness.StaleIfError  // Offline resilience
```

### StoreKey Types

Type-safe keys for cache lookups:

```kotlin
// By entity ID
val userKey = ByIdKey(
    namespace = StoreNamespace("users"),
    entity = EntityId("User", "123")
)

// By query parameters
val searchKey = QueryKey(
    namespace = StoreNamespace("search"),
    query = mapOf("q" to "kotlin", "limit" to "10")
)

// Custom keys (implement StoreKey)
data class CustomKey(...) : StoreKey {
    override val namespace: StoreNamespace = ...
    override fun stableHash(): Long = ...
}
```

### Data Flow

```
┌─────────────────────────────────────────┐
│          Your Application               │
└──────────────┬──────────────────────────┘
               │ store.stream(key)
               ▼
┌──────────────────────────────────────────┐
│           Store (Core)                   │
│  ┌────────────────────────────────┐     │
│  │  1. Check Memory Cache         │     │
│  │     └─► Hit: emit cached       │     │
│  └────────────┬───────────────────┘     │
│               │ Miss                     │
│  ┌────────────▼───────────────────┐     │
│  │  2. Query SourceOfTruth (DB)   │     │
│  │     └─► Flow<DbType?>          │     │
│  └────────────┬───────────────────┘     │
│               │                          │
│  ┌────────────▼───────────────────┐     │
│  │  3. Check Freshness Policy     │     │
│  │     └─► Stale? Launch fetch    │     │
│  └────────────┬───────────────────┘     │
│               │                          │
│  ┌────────────▼───────────────────┐     │
│  │  4. Fetcher (Network)          │     │
│  │     └─► Flow<FetcherResult>    │     │
│  └────────────┬───────────────────┘     │
│               │                          │
│  ┌────────────▼───────────────────┐     │
│  │  5. Write to SoT (DB)          │     │
│  │     └─► Triggers SoT Flow      │     │
│  └────────────┬───────────────────┘     │
│               │                          │
│  ┌────────────▼───────────────────┐     │
│  │  6. Convert & Emit             │     │
│  │     └─► StoreResult.Data<V>    │     │
│  └────────────┬───────────────────┘     │
│               │                          │
│  ┌────────────▼───────────────────┐     │
│  │  7. Update Memory Cache        │     │
│  └────────────────────────────────┘     │
└──────────────────────────────────────────┘
```

## 🔧 Advanced Features

### Custom Converters

Handle different types across layers:

```kotlin
converter(
    netToDbWrite = { key, networkUser ->
        DatabaseUser(
            id = networkUser.userId,
            name = networkUser.fullName,
            cachedAt = Clock.System.now()
        )
    },
    dbReadToDomain = { key, dbUser ->
        User(
            id = dbUser.id,
            name = dbUser.name,
            email = dbUser.email ?: ""
        )
    }
)
```

### ETags & Conditional Requests

Optimize bandwidth with conditional fetching:

```kotlin
fetcher { key ->
    flow {
        val response = api.getUserWithETag(key.entity.id, request.conditional?.ifNoneMatch)
        when (response.code) {
            200 -> emit(FetcherResult.Success(response.body, etag = response.etag))
            304 -> emit(FetcherResult.NotModified(etag = response.etag!!))
            else -> emit(FetcherResult.Error(HttpException(response.code)))
        }
    }
}
```

### Streaming Fetchers

Support streaming/chunked responses:

```kotlin
fetcher { key ->
    flow {
        api.streamLargeFile(key.entity.id).collect { chunk ->
            emit(FetcherResult.Success(chunk))
        }
    }
}
```

## 🏗️ Architecture

### Module Dependencies

```
core (zero dependencies)
├── kotlinx-coroutines-core
├── kotlinx-datetime
└── kotlinx-serialization-json
```

### Package Structure

```
dev.mattramotar.storex.core
├── Store.kt                  # Main interface
├── StoreKey.kt              # Key abstractions
├── SimpleConverter.kt       # Helper converters
├── TypeAliases.kt           # Convenience aliases
├── dsl/
│   ├── StoreBuilder.kt      # DSL entry point
│   ├── ConfigScopes.kt      # Configuration classes
│   └── internal/            # Implementation
└── internal/
    ├── RealReadStore.kt     # Core implementation
    ├── MemoryCache.kt       # LRU cache
    ├── SourceOfTruth.kt     # SoT interface
    ├── Fetcher.kt           # Fetcher interface
    ├── FreshnessValidator.kt # Freshness logic
    ├── Bookkeeper.kt        # Metadata tracking
    └── StoreException.kt    # Error types
```

## 🔗 Related Modules

- **`:mutations`** - Add write operations (update, create, delete, upsert, replace)
- **`:normalization:runtime`** - Graph normalization for relational data
- **`:paging`** - Bidirectional pagination support
- **`:resilience`** - Retry, circuit breaking, rate limiting
- **`:bundle-rest`** - Pre-configured bundle for REST APIs (includes `:core`)
- **`:bundle-graphql`** - Pre-configured bundle for GraphQL (includes `:core`)

## 📖 Documentation

For detailed information, see:

- [ARCHITECTURE.md](../ARCHITECTURE.md) - Overall architecture and design patterns
- [THREADING.md](../THREADING.md) - Concurrency model and thread safety
- [PERFORMANCE.md](../PERFORMANCE.md) - Performance optimization techniques
- [MIGRATION.md](../MIGRATION.md) - Migration from other libraries
- [API Documentation](../docs/api/core/) - Complete API reference

## 💡 Best Practices

1. **Scope stores to ViewModels/Presenters** - Don't use GlobalScope
2. **Use structured concurrency** - Always cancel store operations when done
3. **Configure appropriate maxSize** - Balance memory usage vs cache hit rate
4. **Use CachedOrFetch for UI** - Instant rendering with background refresh
5. **Implement ETags** - Reduce bandwidth and improve performance
6. **Use namespaces** - Enable efficient bulk invalidation
7. **Handle errors gracefully** - Use StaleIfError for offline resilience

## ⚡ Performance

- **Memory cache hit**: < 1ms
- **SoT (database) hit**: < 10ms
- **Network fetch**: 50-500ms (network-dependent)
- **Throughput**: 10,000+ reads/sec (memory cache)
- **Memory footprint**: ~100KB + (maxSize × avg value size)

See [PERFORMANCE.md](../PERFORMANCE.md) for benchmarks and optimization tips.

## 📄 License

Apache 2.0 - See [LICENSE](../LICENSE) for details.
