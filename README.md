# StoreX

**Kotlin Multiplatform reactive caching with normalization, mutations, and offline-first support**

[![codecov](https://codecov.io/gh/matt-ramotar/storex/graph/badge.svg?token=75WSVG106G)](https://codecov.io/gh/matt-ramotar/storex)
[![Maven Central](https://img.shields.io/maven-central/v/dev.mattramotar.storex/core)](https://search.maven.org/search?q=g:dev.mattramotar.storex)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.0+-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

StoreX is a sophisticated caching library for Kotlin Multiplatform applications, providing reactive data synchronization, graph normalization, and offline-first capabilities. Think of it as a combination of Apollo Client's normalized cache, Retrofit's simplicity, and Kotlin Flow's reactivity—all in one unified, modular architecture.

---

## ✨ Features

- **🎯 Reactive Caching** - Flow-based reactive updates across all data layers
- **🌐 Multiplatform** - JVM, Android, iOS, JS, Native (single codebase)
- **📊 Graph Normalization** - Automatic entity deduplication for GraphQL/relational data
- **✏️ Mutations** - Full CRUD with optimistic updates and rollback
- **📄 Pagination** - Bidirectional cursor/offset-based pagination
- **🔌 Offline-First** - Local-first with background sync
- **🔄 Multi-Tier Caching** - Memory → Database → Network
- **🎨 Modular** - 17 focused modules, use only what you need
- **🚀 Performance** - LRU caching, batched reads, request coalescing
- **🧪 Testable** - Comprehensive test utilities and fakes

---

## 📦 Installation

### Using Bundles (Recommended)

**GraphQL Applications:**
```kotlin
dependencies {
    implementation("dev.mattramotar.storex:bundle-graphql:1.0.0")
}
```

**REST API Applications:**
```kotlin
dependencies {
    implementation("dev.mattramotar.storex:bundle-rest:1.0.0")
}
```

**Android Applications:**
```kotlin
dependencies {
    implementation("dev.mattramotar.storex:bundle-android:1.0.0")
}
```

### Using Individual Modules

**Minimal Setup (Read-Only):**
```kotlin
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
}
```

**With Mutations:**
```kotlin
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")
}
```

**See:** [CHOOSING_MODULES.md](CHOOSING_MODULES.md) for detailed module selection guide

---

## 🚀 Quick Start

### Basic Read-Only Store

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

    // Memory cache
    memoryCache {
        maxSize = 100
    }
}

// Use the store
suspend fun loadUser(userId: String): User {
    val key = ByIdKey(
        namespace = StoreNamespace("users"),
        entity = EntityId("User", userId)
    )
    return userStore.get(key)  // Suspending get
}

// Observe reactive updates
fun observeUser(userId: String): Flow<User> {
    val key = ByIdKey(
        namespace = StoreNamespace("users"),
        entity = EntityId("User", userId)
    )
    return userStore.stream(key)
        .mapNotNull { result ->
            when (result) {
                is StoreResult.Data -> result.value
                is StoreResult.Loading -> null
                is StoreResult.Error -> throw result.throwable
            }
        }
}
```

### With Mutations (CRUD)

```kotlin
import dev.mattramotar.storex.mutations.*

// Patch for partial updates
data class UserPatch(
    val name: String? = null,
    val email: String? = null
)

// Draft for creation
data class UserDraft(
    val name: String,
    val email: String
)

// Create mutation store
val userStore = mutationStore<ByIdKey, User, UserPatch, UserDraft> {
    fetcher { key ->
        flow {
            val user = api.getUser(key.entity.id)
            emit(FetcherResult.Success(user))
        }
    }

    mutations {
        // UPDATE (PATCH)
        updater { key, patch ->
            api.updateUser(key.entity.id, patch)
        }

        // CREATE (POST)
        creator { draft ->
            val response = api.createUser(draft)
            CreateResult(
                key = ByIdKey(namespace, EntityId("User", response.id)),
                value = response
            )
        }

        // DELETE
        deleter { key ->
            api.deleteUser(key.entity.id)
        }
    }
}

// Use mutations
suspend fun updateUserName(userId: String, newName: String) {
    val key = ByIdKey(namespace, EntityId("User", userId))
    userStore.update(key, UserPatch(name = newName))  // Optimistic update!
}
```

### With Jetpack Compose (Android)

```kotlin
import dev.mattramotar.storex.compose.*

@Composable
fun UserScreen(userId: String, userStore: Store<ByIdKey, User>) {
    val userKey = remember(userId) {
        ByIdKey(StoreNamespace("users"), EntityId("User", userId))
    }

    // Collect store stream as Compose state
    val userState by userStore.stream(userKey).collectAsState()

    when (val state = userState) {
        is StoreResult.Loading -> LoadingIndicator()
        is StoreResult.Data -> UserProfile(user = state.value)
        is StoreResult.Error -> ErrorView(error = state.throwable)
    }
}
```

---

## 🏗️ Architecture

### Modular Design

StoreX is organized into 17 focused modules across 6 layers:

```
Layer 6: Convenience
├── bundle-graphql (GraphQL all-in-one)
├── bundle-rest (REST all-in-one)
└── bundle-android (Android all-in-one)

Layer 5: Development & Observability
├── testing (Test utilities)
└── telemetry (Metrics & monitoring)

Layer 4: Integrations
├── interceptors (Request/response hooks)
├── serialization-kotlinx (JSON/ProtoBuf)
├── android (Lifecycle, Room, WorkManager)
├── compose (Jetpack Compose helpers)
└── ktor-client (HTTP client integration)

Layer 3: Advanced Features
├── normalization:runtime (Graph normalization)
├── normalization:ksp (Code generation)
└── paging (Bidirectional pagination)

Layer 2: Write Operations
└── mutations (CRUD operations)

Layer 1: Foundation
├── core (Read-only store)
└── resilience (Retry, circuit breaking)
```

**See:** [MODULES.md](MODULES.md) for complete module reference

### Data Flow

```
┌──────────────────────────────────────────┐
│          Your Application                │
└──────────────┬───────────────────────────┘
               │ store.stream(key)
               ▼
┌──────────────────────────────────────────┐
│           StoreX (Core)                  │
│  ┌────────────────────────────────┐     │
│  │  1. Memory Cache (LRU)         │     │
│  │     └─► < 1ms                  │     │
│  └────────────┬───────────────────┘     │
│               │ miss                     │
│  ┌────────────▼───────────────────┐     │
│  │  2. Source of Truth (DB)       │     │
│  │     └─► < 10ms                 │     │
│  └────────────┬───────────────────┘     │
│               │ miss                     │
│  ┌────────────▼───────────────────┐     │
│  │  3. Fetcher (Network)          │     │
│  │     └─► 50-500ms               │     │
│  └────────────┬───────────────────┘     │
│               │                          │
│  ┌────────────▼───────────────────┐     │
│  │  4. Write to SoT               │     │
│  │     └─► Reactive Flow updates  │     │
│  └────────────────────────────────┘     │
└──────────────────────────────────────────┘
```

**See:** [ARCHITECTURE.md](ARCHITECTURE.md) for detailed architecture

---

## 🎯 Use Cases

### GraphQL Applications

StoreX provides Apollo Client-like normalized caching:

```kotlin
// Automatic entity deduplication
// When User:123 is updated, ALL views with that user update
val userStore = normalizedStore<UserKey, User> {
    normalization {
        backend = graphQLBackend
        schema = schemaRegistry
        normalizer = GraphQLNormalizer(schema)
    }
}
```

**See:** [bundle-graphql/README.md](bundle-graphql/README.md)

### REST APIs

Pre-configured for REST with retry, circuit breaking:

```kotlin
val userStore = store<ByIdKey, User> {
    fetcher { key ->
        ktorFetcher(httpClient) {
            get("https://api.example.com/users/${key.entity.id}")
        } withRetry {
            maxRetries = 3
            exponentialBackoff()
        }
    }
}
```

**See:** [bundle-rest/README.md](bundle-rest/README.md)

### Android Apps

Lifecycle-aware, Room integration, WorkManager sync:

```kotlin
class UserViewModel(
    private val userStore: Store<ByIdKey, User>
) : ViewModel() {
    val user: StateFlow<User?> = userStore
        .stream(userKey)
        .stateInViewModel(initialValue = null)
}
```

**See:** [bundle-android/README.md](bundle-android/README.md)

### Infinite Scroll Lists

Bidirectional pagination with LazyColumn:

```kotlin
@Composable
fun PostsList(pageStore: PageStore<PostsKey, Post>) {
    val posts by pageStore.stream(PostsKey()).collectAsPagedList()

    LazyColumn {
        items(posts) { post ->
            PostItem(post)
        }
    }
}
```

**See:** [paging/README.md](paging/README.md)

---

## 📚 Documentation

### Core Documentation

- **[MODULES.md](MODULES.md)** - Complete module reference (17 modules)
- **[CHOOSING_MODULES.md](CHOOSING_MODULES.md)** - Which modules do I need?
- **[BUNDLE_GUIDE.md](BUNDLE_GUIDE.md)** - Bundles vs individual modules
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Architecture deep dive
- **[MIGRATION.md](MIGRATION.md)** - Migration from other libraries

### Technical Documentation

- **[PERFORMANCE.md](PERFORMANCE.md)** - Optimization and benchmarks
- **[THREADING.md](THREADING.md)** - Concurrency model and thread safety

### Module-Specific Guides

- [core/README.md](core/README.md) - Read-only store
- [mutations/README.md](mutations/README.md) - CRUD operations
- [normalization/runtime/README.md](normalization/runtime/README.md) - Graph normalization
- [paging/README.md](paging/README.md) - Pagination
- [android/README.md](android/README.md) - Android integrations
- [compose/README.md](compose/README.md) - Jetpack Compose

---

## 🆚 Comparison

### vs Apollo Android

| Feature | Apollo Android | StoreX |
|---------|---------------|--------|
| **Normalization** | ✅ Automatic | ✅ Automatic (GraphQL) or Manual |
| **Mutations** | ✅ | ✅ |
| **Offline** | Limited | ✅ Offline-first built-in |
| **Multiplatform** | Android only | ✅ All platforms |
| **Non-GraphQL** | ❌ | ✅ REST, any API |
| **Modular** | Monolithic | ✅ 17 focused modules |

### vs Paging3 (Android)

| Feature | Paging3 | StoreX Paging |
|---------|---------|---------------|
| **Platform** | Android only | ✅ Multiplatform |
| **API Support** | Any | ✅ Any |
| **Caching** | Limited | ✅ Multi-tier |
| **Mutations** | ❌ | ✅ Built-in |
| **Offline** | Limited | ✅ Offline-first |

### vs Room (Standalone)

| Feature | Room Alone | StoreX + Room |
|---------|-----------|---------------|
| **Local DB** | ✅ | ✅ (uses Room) |
| **Network Sync** | Manual | ✅ Automatic |
| **Caching** | Database only | ✅ Memory + DB + Network |
| **Reactive** | ✅ Flow | ✅ Flow |
| **Multiplatform** | Android only | ✅ All platforms |

---

## 🌟 Highlights

### 1. Type-Safe

```kotlin
// Compile-time type safety
val store: Store<ByIdKey, User> = store { /* ... */ }
val user: User = store.get(key)  // ✅ Type-safe

// Won't compile:
// val post: Post = store.get(key)  // ❌ Type mismatch
```

### 2. Reactive

```kotlin
// Automatic UI updates
store.stream(key).collect { result ->
    // UI updates automatically when:
    // - Cache is populated
    // - Database changes
    // - Network fetch completes
    // - Another screen updates the same entity
}
```

### 3. Offline-First

```kotlin
// Works offline automatically
userStore.update(key, patch)  // Queued offline
// Later, when online:
// → Automatically syncs to server
// → UI updates with server response
```

### 4. Performance

- **Memory cache hit**: < 1ms
- **Database hit**: < 10ms
- **Request coalescing**: 100 concurrent requests → 1 network call
- **ETag support**: Conditional requests save bandwidth

---

## 🧪 Testing

```kotlin
import dev.mattramotar.storex.testing.*

class UserStoreTest {
    @Test
    fun `get should fetch from network when cache miss`() = runTest {
        val testStore = testStore<ByIdKey, User> {
            fakeFetcher {
                onFetch(userKey) { FetcherResult.Success(testUser) }
            }
        }

        val result = testStore.get(userKey)
        assertEquals(testUser, result)
    }
}
```

**See:** [testing/README.md](testing/README.md)

---

## 🤝 Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

## 📄 License

```
Copyright 2025 Matthew Ramotar

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

## 🔗 Links

- **GitHub**: https://github.com/matt-ramotar/storex
- **Documentation**: [MODULES.md](MODULES.md)
- **Issues**: https://github.com/matt-ramotar/storex/issues
- **Discussions**: https://github.com/matt-ramotar/storex/discussions

---

**Built with ❤️ using Kotlin Multiplatform**
