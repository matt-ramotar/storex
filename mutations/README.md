# StoreX Mutations

**Write operations and CRUD support for reactive stores**

The `:mutations` module extends StoreX with full CRUD (Create, Read, Update, Delete) capabilities, optimistic updates, and bidirectional synchronization. It builds on `:core` to add write operations while maintaining reactive updates and offline-first behavior.

## 📦 What's Included

This module provides:

- **`MutationStore<Key, Domain, Patch, Draft>`** - Extended store with write operations
- **CRUD Operations** - `update()`, `create()`, `delete()`, `upsert()`, `replace()`
- **Optimistic Updates** - Instant UI updates with automatic rollback on failure
- **Provisional Keys** - Handle server-assigned IDs for created entities
- **Mutation Policies** - Control online requirements, preconditions, and conflict resolution
- **Result Types** - Detailed success/failure information for each operation
- **DSL Builder** - Kotlin DSL for configuring mutation stores ✅ **Implemented**
- **Test Coverage** - 78.4% line coverage with 28 comprehensive tests

## 🎯 When to Use

Use this module when you need:

- **Write operations** in addition to reads (create, update, delete)
- **Optimistic updates** for instant UI feedback
- **Offline-first writes** with background synchronization
- **CRUD operations** with proper error handling
- **Bidirectional sync** between client and server

**Requires:**
- `:core` module (provides base Store functionality)

## 🚀 Getting Started

### Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")
}
```

### Basic Usage

```kotlin
import dev.mattramotar.storex.core.*
import dev.mattramotar.storex.mutations.*
import dev.mattramotar.storex.mutations.dsl.*

// Domain models
data class User(
    val id: String,
    val name: String,
    val email: String
)

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

// Create a mutation store
val userStore = mutationStore<ByIdKey, User, UserPatch, UserDraft> {
    // Network fetcher
    fetcher { key: ByIdKey ->
        api.getUser(key.entity.id)
    }

    // Local persistence
    persistence {
        reader { key: ByIdKey -> database.getUser(key.entity.id) }
        writer { key: ByIdKey, user: User -> database.saveUser(user) }
    }

    // Mutation operations
    mutations {
        // PATCH - partial update
        update { key: ByIdKey, patch: UserPatch ->
            val response = api.updateUser(key.entity.id, patch)
            PatchClient.Response.Success(response, response.etag)
        }

        // POST - create new entity
        create { draft: UserDraft ->
            val response = api.createUser(draft)
            PostClient.Response.Success(
                canonicalKey = ByIdKey(
                    namespace = StoreNamespace("users"),
                    entity = EntityId("User", response.id)
                ),
                echo = response,
                etag = response.etag
            )
        }

        // DELETE - remove entity
        delete { key: ByIdKey ->
            api.deleteUser(key.entity.id)
            DeleteClient.Response.Success(alreadyDeleted = false)
        }

        // PUT - create or replace
        upsert { key: ByIdKey, value: User ->
            val response = api.upsertUser(key.entity.id, value)
            if (response.created) {
                PutClient.Response.Created(response, response.etag)
            } else {
                PutClient.Response.Replaced(response, response.etag)
            }
        }

        // PUT - replace existing
        replace { key: ByIdKey, value: User ->
            val response = api.replaceUser(key.entity.id, value)
            PutClient.Response.Replaced(response, response.etag)
        }
    }
}
```

## 📚 Key Operations

### Update (PATCH)

Partial update of an existing entity:

```kotlin
suspend fun updateUserName(userId: String, newName: String) {
    val key = ByIdKey(
        namespace = StoreNamespace("users"),
        entity = EntityId("User", userId)
    )

    when (val result = userStore.update(key, UserPatch(name = newName))) {
        is UpdateResult.Synced -> println("Update synced to server")
        is UpdateResult.Enqueued -> println("Update queued (offline)")
        is UpdateResult.Failed -> println("Update failed: ${result.error}")
    }
}
```

### Create (POST)

Create a new entity:

```kotlin
suspend fun createUser(name: String, email: String): String {
    val draft = UserDraft(name, email)

    when (val result = userStore.create(draft)) {
        is CreateResult.Synced -> return result.key.entity.id
        is CreateResult.Enqueued -> return result.provisionalKey.entity.id
        is CreateResult.Failed -> throw result.error
    }
}
```

### Delete (DELETE)

Remove an entity:

```kotlin
suspend fun deleteUser(userId: String) {
    val key = ByIdKey(
        namespace = StoreNamespace("users"),
        entity = EntityId("User", userId)
    )

    when (val result = userStore.delete(key)) {
        is DeleteResult.Synced -> println("Deleted")
        is DeleteResult.Enqueued -> println("Delete queued")
        is DeleteResult.Failed -> throw result.error
    }
}
```

### Upsert (PUT - create or replace)

Create if missing, replace if exists:

```kotlin
suspend fun saveUser(userId: String, user: User) {
    val key = ByIdKey(
        namespace = StoreNamespace("users"),
        entity = EntityId("User", userId)
    )

    userStore.upsert(key, user)
}
```

### Replace (PUT - must exist)

Replace existing entity (fails if missing):

```kotlin
suspend fun replaceUser(userId: String, user: User) {
    val key = ByIdKey(
        namespace = StoreNamespace("users"),
        entity = EntityId("User", userId)
    )

    userStore.replace(key, user)
}
```

## 🚀 Advanced Features

### Optimistic Updates

Update UI immediately while sync happens in background:

```kotlin
userStore.update(
    key = userKey,
    patch = UserPatch(name = "New Name"),
    policy = UpdatePolicy(
        optimistic = true  // UI updates immediately
    )
)

// Store automatically:
// 1. Applies patch to local cache
// 2. Updates UI via reactive Flow
// 3. Sends request to server
// 4. Reconciles with server response
// 5. Rolls back if server rejects
```

### Preconditions (Optimistic Concurrency Control)

Prevent conflicting updates with ETags or timestamps:

```kotlin
userStore.update(
    key = userKey,
    patch = UserPatch(name = "New Name"),
    policy = UpdatePolicy(
        precondition = Precondition.IfEtag("abc123")  // Only update if ETag matches
    )
)

// Server returns 412 Precondition Failed if ETag doesn't match
```

### Provisional Keys

Handle server-assigned IDs for creates:

```kotlin
// Create returns provisional key immediately
val result = userStore.create(draft, policy = CreatePolicy(optimistic = true))
val provisionalKey = (result as CreateResult.Enqueued).provisionalKey

// UI can reference provisional key
userStore.stream(provisionalKey).collect { user ->
    // Updates when server responds with real ID
}

// Store automatically "rekeys" from provisional → server-assigned ID
```

### Offline Queueing

Mutations are queued when offline and synced when online:

```kotlin
// User is offline
userStore.update(key, patch)  // Returns UpdateResult.Enqueued

// Later, when online
// Store automatically syncs queued mutations
// Emits updated values via reactive Flow
```

## 🔧 Configuration

### Mutation Policies

Control mutation behavior:

```kotlin
mutations {
    updater { key, patch ->
        api.updateUser(key.entity.id, patch)
    }
}

// Usage with policies
userStore.update(
    key = key,
    patch = patch,
    policy = UpdatePolicy(
        requireOnline = false,      // Allow offline queueing
        optimistic = true,           // Update UI immediately
        precondition = Precondition.IfEtag("xyz"),  // Conditional update
        timeout = 30.seconds         // Request timeout
    )
)
```

### Mutation Encoders

Convert between domain and network types:

```kotlin
mutations {
    encoder = object : MutationEncoder<Key, Domain, Patch, Draft, NetPatch, NetDraft> {
        override suspend fun encodePatch(key: Key, patch: Patch): NetPatch {
            return NetPatch(
                name = patch.name,
                email = patch.email
            )
        }

        override suspend fun encodeDraft(draft: Draft): NetDraft {
            return NetDraft(
                name = draft.name,
                email = draft.email
            )
        }
    }
}
```

## 🏗️ Architecture

### Module Dependencies

```
mutations
├── core (API dependency)
│   ├── Store interface
│   ├── Fetcher
│   └── SourceOfTruth
├── kotlinx-coroutines-core
└── kotlinx-datetime
```

### Package Structure

```
dev.mattramotar.storex.mutations
├── MutationStore.kt          # Main interface
├── SimpleMutationEncoder.kt  # Helper encoder
├── TypeAliases.kt            # Convenience aliases
├── dsl/
│   ├── MutationStoreBuilder.kt      # DSL entry point
│   ├── MutationsConfig.kt           # Configuration
│   └── MutationStoreBuilderScope.kt # Builder scope
└── internal/
    ├── RealMutationStore.kt  # Implementation
    ├── Updater.kt            # Update logic
    └── UpdateOutcome.kt      # Result types
```

## 🔗 Related Modules

- **`:core`** - Base Store functionality (required dependency)
- **`:normalization:runtime`** - Normalized graph storage for mutations
- **`:resilience`** - Retry, circuit breaking for failed mutations
- **`:bundle-graphql`** - Pre-configured bundle (includes `:mutations`)
- **`:bundle-rest`** - Pre-configured bundle (includes `:mutations`)

## 📖 Documentation

For detailed information, see:

- [Core Module](../core/README.md) - Base store functionality
- [ARCHITECTURE.md](../ARCHITECTURE.md) - Mutations architecture
- [MIGRATION.md](../MIGRATION.md) - Migration from other libraries
- [API Documentation](../docs/api/mutations/) - Complete API reference

## 💡 Best Practices

1. **Use optimistic updates for UI** - Instant feedback improves UX
2. **Handle CreateResult.Enqueued** - Support offline creates properly
3. **Use preconditions for conflicts** - Prevent lost updates with ETags
4. **Queue offline mutations** - Don't fail immediately when offline
5. **Reconcile server echoes** - Server response is canonical truth
6. **Use appropriate policies** - `requireOnline` for critical operations
7. **Test rollback scenarios** - Ensure optimistic updates roll back correctly

## ⚡ Performance

- **Optimistic update latency**: < 1ms (local only)
- **Network mutation**: 50-500ms (network-dependent)
- **Offline queue**: Bounded by available storage
- **Concurrent mutations**: Safe with per-key locking

See [PERFORMANCE.md](../PERFORMANCE.md) for benchmarks and optimization tips.

## 📄 License

Apache 2.0 - See [LICENSE](../LICENSE) for details.
