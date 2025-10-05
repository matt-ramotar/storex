# StoreX Normalization Runtime

**Graph normalization and composition for relational data**

The `:normalization:runtime` module provides sophisticated graph normalization capabilities for applications dealing with interconnected entities. It automatically normalizes nested data structures into flat, deduplicated storage and reassembles them on demand, similar to Apollo Client's normalized cache.

## 📦 What's Included

This module provides:

- **`NormalizationBackend`** - Normalized entity storage with reactive invalidations
- **`EntityKey`** - Unique identification for normalized entities
- **`NormalizedRecord`** - Flat representation of entities with references
- **`Shape`** - Graph traversal specifications for composition
- **`Normalizer`** - Interface for extracting entities from network responses
- **`EntityAdapter`** - Denormalization logic for entity types
- **`SchemaRegistry`** - Central registry for entity adapters
- **Graph Composition** - BFS-based reassembly with depth limiting
- **Dependency Tracking** - Automatic invalidation propagation

## 🎯 When to Use

Use this module when you have:

- **GraphQL APIs** with interconnected entities
- **Relational data** with references between entities
- **Complex object graphs** that share common entities
- **Need for automatic cache coherence** (update one entity, all views update)
- **Memory efficiency concerns** (store each entity once)

**Perfect for:**
- GraphQL mobile/web clients
- Applications with user → posts → comments → likes relationships
- Offline-first apps with complex data models

**Requires:**
- `:core` module (base Store functionality)
- `:mutations` module (for write operations)

## 🚀 Getting Started

### Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")
    implementation("dev.mattramotar.storex:normalization-runtime:1.0.0")
}
```

### Basic Usage

```kotlin
import dev.mattramotar.storex.normalization.*

// Define your entities
data class User(
    val id: String,
    val name: String,
    val posts: List<Post>
)

data class Post(
    val id: String,
    val title: String,
    val authorId: String
)

// Create a normalization backend
val backend = InMemoryNormalizationBackend()

// Create a schema registry
val registry = SchemaRegistry()

// Register entity adapters
registry.register("User", object : EntityAdapter<User> {
    override suspend fun denormalize(
        record: NormalizedRecord,
        context: DenormalizationContext
    ): User {
        val postsKeys = record.data["posts"] as? List<EntityKey> ?: emptyList()
        val posts = context.fetchEntities(postsKeys).mapNotNull { it as? Post }

        return User(
            id = record.key.id["id"] as String,
            name = record.data["name"] as String,
            posts = posts
        )
    }
})

registry.register("Post", object : EntityAdapter<Post> {
    override suspend fun denormalize(
        record: NormalizedRecord,
        context: DenormalizationContext
    ): Post {
        return Post(
            id = record.key.id["id"] as String,
            title = record.data["title"] as String,
            authorId = record.data["authorId"] as String
        )
    }
})

// Define a shape for graph traversal
val userWithPostsShape = Shape<User>(
    id = ShapeId("UserWithPosts"),
    maxDepth = 2,  // Limit traversal depth
    outboundRefs = { record ->
        // Define which fields contain references to other entities
        (record.data["posts"] as? List<EntityKey>)?.toSet() ?: emptySet()
    }
)

// Compose a graph from a root entity
val rootKey = EntityKey("User", mapOf("id" to "user-123"))
val result = composeFromRoot(
    root = rootKey,
    shape = userWithPostsShape,
    registry = registry,
    backend = backend
)

when (result) {
    is ComposeResult.Success -> {
        val user = result.value
        println("User: ${user.name}, Posts: ${user.posts.size}")
    }
    is ComposeResult.Error -> {
        println("Composition failed: ${result.error}")
    }
}
```

## 📚 Key Concepts

### EntityKey

Uniquely identifies a normalized entity:

```kotlin
val userKey = EntityKey(
    typeName = "User",
    id = mapOf("id" to "123")
)

val commentKey = EntityKey(
    typeName = "Comment",
    id = mapOf(
        "postId" to "456",
        "commentId" to "789"
    )  // Composite key
)
```

### NormalizedRecord

Flat representation with scalar values and entity references:

```kotlin
val userRecord = NormalizedRecord(
    key = EntityKey("User", mapOf("id" to "123")),
    data = mapOf(
        "name" to "Alice",
        "email" to "alice@example.com",
        "posts" to listOf(
            EntityKey("Post", mapOf("id" to "post-1")),
            EntityKey("Post", mapOf("id" to "post-2"))
        )  // References, not embedded objects
    )
)
```

### Shape

Defines graph traversal for composition:

```kotlin
val shape = Shape<User>(
    id = ShapeId("UserWithPostsAndComments"),
    maxDepth = 3,  // User → Posts → Comments
    outboundRefs = { record ->
        when (record.key.typeName) {
            "User" -> {
                (record.data["posts"] as? List<EntityKey>)?.toSet() ?: emptySet()
            }
            "Post" -> {
                (record.data["comments"] as? List<EntityKey>)?.toSet() ?: emptySet()
            }
            else -> emptySet()
        }
    }
)
```

### Normalizer

Extracts entities from network responses:

```kotlin
class GraphQLNormalizer(
    private val registry: SchemaRegistry
) : Normalizer<GraphQLResponse, QueryKey> {
    override fun normalize(
        requestKey: QueryKey,
        response: GraphQLResponse,
        updatedAt: Instant
    ): NormalizedWrite<QueryKey> {
        val entities = extractEntities(response.data)
        val changeSet = ChangeSet(
            writes = entities.associateBy({ it.key }, { it })
        )
        return NormalizedWrite(changeSet, indexUpdate = null)
    }

    private fun extractEntities(data: Any?): List<NormalizedRecord> {
        // Recursively extract entities with __typename and id
        // Similar to Apollo Client's normalization
        TODO()
    }
}
```

## 🔧 Advanced Features

### Automatic Invalidation

When a normalized entity changes, all dependent graphs are invalidated:

```kotlin
// Update a post
backend.apply(ChangeSet(
    writes = mapOf(
        postKey to NormalizedRecord(postKey, mapOf("title" to "Updated Title"))
    )
))

// All users who reference this post are automatically invalidated
backend.rootInvalidations.collect { invalidation ->
    println("Invalidated: ${invalidation.rootRef}")
    // Re-compose affected graphs
}
```

### Batch Reads

Graph composition uses batched reads for efficiency:

```kotlin
// Internally batches at 256 entities per read
val records = backend.read(setOf(key1, key2, ..., key256))
```

### Depth Limiting

Prevent infinite cycles in graph traversal:

```kotlin
val shape = Shape<User>(
    id = ShapeId("UserGraph"),
    maxDepth = 3,  // Stop after 3 levels
    outboundRefs = { record -> /* ... */ }
)

// If graph has cycles (User → Post → User → Post → ...),
// traversal stops at maxDepth to prevent infinite recursion
```

### Dependency Tracking

Track which root entities depend on which normalized entities:

```kotlin
backend.updateRootDependencies(
    rootRef = RootRef(userKey, shapeId),
    dependencies = setOf(postKey1, postKey2, commentKey1)
)

// Later, when commentKey1 changes:
backend.invalidateEntity(commentKey1)
// → Automatically invalidates userKey graph
```

## 🏗️ Architecture

### Module Dependencies

```
normalization:runtime
├── core (API dependency)
│   ├── Store interface
│   └── Caching primitives
├── mutations (API dependency)
│   └── Write operations
├── kotlinx-coroutines-core
└── kotlinx-datetime
```

### Package Structure

```
dev.mattramotar.storex.normalization
├── EntityKey.kt              # Entity identification
├── NormalizedRecord.kt       # Flat entity representation
├── NormalizationBackend.kt   # Storage interface
├── Shape.kt                  # Graph traversal spec
├── Normalizer.kt             # Normalization logic
├── EntityAdapter.kt          # Denormalization logic
├── SchemaRegistry.kt         # Adapter registry
├── ChangeSet.kt              # Batch write operations
└── compose/
    ├── GraphComposition.kt   # BFS composition
    └── ComposeResult.kt      # Result types
```

## 🔗 Related Modules

- **`:core`** - Base Store functionality (required dependency)
- **`:mutations`** - Write operations (required dependency)
- **`:normalization:ksp`** - KSP code generation for normalizers (optional)
- **`:bundle-graphql`** - Pre-configured bundle (includes normalization)

## 📖 Documentation

For detailed information, see:

- [Core Module](../../core/README.md) - Base store functionality
- [Mutations Module](../../mutations/README.md) - Write operations
- [ARCHITECTURE.md](../../ARCHITECTURE.md) - Normalization architecture
- [API Documentation](../../docs/api/normalization/) - Complete API reference

## 💡 Best Practices

1. **Limit graph depth** - Use `maxDepth = 2-4` to prevent performance issues
2. **Use selective shapes** - Fetch only what's needed for each screen
3. **Register all entity types** - Ensure SchemaRegistry has all adapters
4. **Handle partial failures** - Graph composition continues on entity read errors
5. **Batch writes** - Use ChangeSet for multiple entity updates
6. **Monitor invalidations** - Use `.conflate()` to avoid overwhelming UI
7. **Use composite keys** - For entities with multi-field primary keys

## ⚡ Performance

### Normalization

- **Small graph** (10 entities): ~2ms
- **Medium graph** (100 entities): ~15ms
- **Large graph** (1000 entities): ~120ms

### Denormalization (BFS Composition)

- **Small graph** (10 entities, depth 2): ~3ms
- **Medium graph** (100 entities, depth 3): ~25ms
- **Large graph** (1000 entities, depth 4): ~180ms

### Best Practices

- Keep graphs < 1000 entities via `maxDepth`
- Use batching (256 entities per read)
- Selective shapes (fetch only needed data)

See [PERFORMANCE.md](../../PERFORMANCE.md) for detailed benchmarks.

## 🆚 Comparison to Apollo Client

| Feature | Apollo Client | StoreX Normalization |
|---------|--------------|---------------------|
| **Normalization** | Automatic via `__typename` + `id` | Manual via `Normalizer` |
| **Cache Key** | `CacheKey("Type:id")` | `EntityKey("Type", mapOf("id" to "..."))` |
| **Denormalization** | Automatic via GraphQL schema | Manual via `EntityAdapter` |
| **Fragments** | GraphQL fragments | `Shape` objects |
| **Invalidation** | Manual or via `refetchQueries` | Automatic via dependency tracking |
| **Optimistic Updates** | Supported | Supported (via `:mutations`) |
| **Offline** | Via plugins | Built-in offline-first |

## 📄 License

Apache 2.0 - See [LICENSE](../../LICENSE) for details.
