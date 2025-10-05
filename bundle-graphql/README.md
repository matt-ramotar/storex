# StoreX Bundle: GraphQL

**All-in-one bundle for GraphQL applications**

This bundle aggregates all modules needed for building GraphQL applications with StoreX, including normalized caching with graph composition, mutations with optimistic updates, and interceptor support.

## 📦 What's Included

This bundle includes the following modules:

- **`:core`** - Core Store functionality (read-only operations, caching, persistence)
- **`:mutations`** - Mutation support (create, update, delete, upsert)
- **`:normalization:runtime`** - Normalized graph storage with automatic composition
- **`:interceptors`** - Request/response interception for logging, metrics, auth

## 🎯 When to Use

Use this bundle when building applications that:

- Use **GraphQL** for data fetching
- Need **normalized caching** to avoid data duplication and maintain referential integrity
- Require **graph composition** to assemble related entities from normalized storage
- Want **optimistic updates** for mutations
- Need **interceptors** for authentication, logging, or metrics

Perfect for:
- Mobile apps consuming GraphQL APIs
- Web apps with complex data relationships
- Applications requiring offline-first capabilities with GraphQL

## 🚀 Getting Started

### Installation

Add the bundle to your project:

```kotlin
// build.gradle.kts
dependencies {
    implementation("dev.mattramotar.storex:bundle-graphql:1.0.0")
}
```

This single dependency brings in all the modules you need for GraphQL applications.

### Basic Usage

```kotlin
import dev.mattramotar.storex.core.*
import dev.mattramotar.storex.mutations.*
import dev.mattramotar.storex.normalization.*
import dev.mattramotar.storex.interceptors.*

// Define your GraphQL entities
@Normalizable
data class User(
    val id: String,
    val name: String,
    val posts: List<Post>
)

@Normalizable
data class Post(
    val id: String,
    val title: String,
    val author: User
)

// Create a normalized store with mutations
val userStore = normalizedStore<UserKey, User, UserPatch, UserDraft> {
    backend = graphQLBackend
    schema = schemaRegistry
    shape = UserShape

    fetcher { key ->
        graphQLClient.query(GetUserQuery(key.id))
    }

    mutations {
        updater { key, patch ->
            graphQLClient.mutate(UpdateUserMutation(key.id, patch))
        }
    }

    interceptors {
        add(LoggingInterceptor())
        add(AuthInterceptor(tokenProvider))
        add(MetricsInterceptor())
    }
}

// Optimistic update with automatic normalization
suspend fun updateUserName(userId: String, newName: String) {
    userStore.update(
        key = UserKey(userId),
        patch = UserPatch(name = newName),
        policy = UpdatePolicy.OptimisticUpdate
    )
}
```

## 📚 Key Features

### Normalized Graph Storage

Entities are stored in normalized form and composed on read, ensuring:
- **No data duplication**: Each entity stored once
- **Referential integrity**: Changes propagate automatically
- **Efficient updates**: Update one entity, all references reflect the change

### Optimistic Updates

Mutations can be applied optimistically for instant UI feedback:
- Update UI immediately while mutation is in flight
- Automatic rollback on failure
- Provisional key handling for creates

### Interceptors

Add cross-cutting concerns without modifying store logic:
- **Authentication**: Add auth tokens to requests
- **Logging**: Track all store operations
- **Metrics**: Collect performance data
- **Caching**: Implement custom cache policies

## 🔗 Alternative Bundles

- **`bundle-rest`**: For REST API applications (without normalization)
- **`bundle-android`**: For Android apps with Compose and platform integrations

## 📖 Documentation

For detailed documentation, see:
- [StoreX Documentation](../../README.md)
- [Normalization Guide](../normalization/runtime/README.md)
- [Mutations Guide](../mutations/README.md)
- [Interceptors Guide](../interceptors/README.md)

## 🏗️ Module Structure

```
bundle-graphql
├── core (API)
│   ├── Store interface
│   ├── Caching
│   └── Persistence
├── mutations (API)
│   ├── MutationStore
│   ├── Optimistic updates
│   └── CQRS support
├── normalization:runtime (API)
│   ├── Normalized storage
│   ├── Graph composition
│   └── Entity adapters
└── interceptors (API)
    ├── Interceptor interface
    └── Common interceptors
```

## 📄 License

Apache 2.0 - See [LICENSE](../../LICENSE) for details.
