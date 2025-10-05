# StoreX Bundle: REST

**All-in-one bundle for REST API applications**

This bundle aggregates all modules needed for building REST API applications with StoreX, including automatic serialization with kotlinx.serialization, resilience patterns, and mutation support.

## 📦 What's Included

This bundle includes the following modules:

- **`:core`** - Core Store functionality (read-only operations, caching, persistence)
- **`:mutations`** - Mutation support (create, update, delete, upsert, replace)
- **`:resilience`** - Resilience patterns (retry, circuit breaker, rate limiting, bulkhead)
- **`:serialization-kotlinx`** - Automatic converters for `@Serializable` types

## 🎯 When to Use

Use this bundle when building applications that:

- Use **REST APIs** for data fetching
- Need **automatic serialization/deserialization** with kotlinx.serialization
- Require **resilience patterns** to handle network failures gracefully
- Want **type-safe converters** without manual mapping code
- Need **CRUD operations** with optimistic updates

Perfect for:
- Mobile apps consuming REST APIs
- Web apps with CRUD operations
- Applications requiring offline-first capabilities with REST
- Microservices clients

## 🚀 Getting Started

### Installation

Add the bundle to your project:

```kotlin
// build.gradle.kts
dependencies {
    implementation("dev.mattramotar.storex:bundle-rest:1.0.0")
}
```

This single dependency brings in all the modules you need for REST API applications.

### Basic Usage

```kotlin
import dev.mattramotar.storex.core.*
import dev.mattramotar.storex.mutations.*
import dev.mattramotar.storex.resilience.*
import dev.mattramotar.storex.serialization.*
import kotlinx.serialization.Serializable

// Define your REST API models
@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String
)

@Serializable
data class UserEntity(
    val id: String,
    val name: String,
    val email: String,
    val cachedAt: Long
)

@Serializable
data class UserPatch(
    val name: String? = null,
    val email: String? = null
)

// Create a store with automatic serialization and resilience
val userStore = mutationStore<UserKey, User, UserPatch, User> {
    // Automatic converter for @Serializable types
    converter = serializableConverter(
        domainSerializer = User.serializer(),
        entitySerializer = UserEntity.serializer()
    )

    // JSON-based persistence
    sourceOfTruth = jsonSourceOfTruth(
        fileSystem = fileSystem,
        serializer = UserEntity.serializer()
    )

    // REST API fetcher with resilience
    fetcher = resilientFetcher(
        retry = RetryPolicy.exponentialBackoff(maxAttempts = 3),
        circuitBreaker = CircuitBreakerPolicy.default(),
        rateLimiter = RateLimiter.perSecond(10)
    ) { key ->
        httpClient.get("/api/users/${key.id}")
    }

    // Mutations with automatic serialization
    mutations {
        updater { key, patch ->
            httpClient.patch("/api/users/${key.id}") {
                setBody(patch)
            }
        }

        creator { draft ->
            httpClient.post("/api/users") {
                setBody(draft)
            }
        }

        deleter { key ->
            httpClient.delete("/api/users/${key.id}")
        }
    }
}

// Use with optimistic updates
suspend fun updateUser(userId: String, newName: String) {
    userStore.update(
        key = UserKey(userId),
        patch = UserPatch(name = newName),
        policy = UpdatePolicy.OptimisticUpdate
    )
}
```

## 📚 Key Features

### Automatic Serialization

Zero-boilerplate converters for `@Serializable` types:
- **Type-safe**: Compile-time checks for serialization
- **Automatic**: No manual mapping code needed
- **Efficient**: kotlinx.serialization performance

### Resilience Patterns

Built-in protection against failures:
- **Retry**: Exponential backoff for transient failures
- **Circuit Breaker**: Prevent cascading failures
- **Rate Limiting**: Control request rate
- **Bulkhead**: Isolate resource pools
- **Timeout**: Prevent hanging requests

### CRUD Operations

Full mutation support for REST APIs:
- **Create**: POST new resources
- **Read**: GET resources (via Store)
- **Update**: PATCH/PUT resources
- **Delete**: DELETE resources
- **Replace**: Full replacement with PUT

### JSON Persistence

Simple file-based persistence with kotlinx.serialization:
- **Type-safe**: Strongly typed storage
- **Efficient**: Fast JSON encoding/decoding
- **Cross-platform**: Works on all Kotlin targets

## 🔗 Alternative Bundles

- **`bundle-graphql`**: For GraphQL applications with normalized caching
- **`bundle-android`**: For Android apps with Compose and platform integrations

## 📖 Documentation

For detailed documentation, see:
- [StoreX Documentation](../../README.md)
- [Mutations Guide](../mutations/README.md)
- [Resilience Guide](../resilience/README.md)
- [Serialization Guide](../serialization-kotlinx/README.md)

## 🏗️ Module Structure

```
bundle-rest
├── core (API)
│   ├── Store interface
│   ├── Caching
│   └── Persistence
├── mutations (API)
│   ├── MutationStore
│   ├── CRUD operations
│   └── Optimistic updates
├── resilience (API)
│   ├── Retry policies
│   ├── Circuit breaker
│   ├── Rate limiter
│   └── Bulkhead
└── serialization-kotlinx (API)
    ├── Auto converters
    ├── JSON SourceOfTruth
    └── Type-safe serialization
```

## 📄 License

Apache 2.0 - See [LICENSE](../../LICENSE) for details.
