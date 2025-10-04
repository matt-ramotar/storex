# StoreX Architecture

**Last Updated**: 2025-10-04
**Version**: 1.0

## Table of Contents
1. [Overview](#overview)
2. [Core Architecture](#core-architecture)
3. [Data Flow](#data-flow)
4. [Key Components](#key-components)
5. [Normalization Architecture](#normalization-architecture)
6. [Mutations & Writes](#mutations--writes)
7. [Caching Strategy](#caching-strategy)
8. [Extension Points](#extension-points)

---

## Overview

StoreX is a Kotlin Multiplatform library that provides a sophisticated **caching layer with normalization** for modern mobile and multiplatform applications. It implements the **Source of Truth (SoT)** pattern, ensuring consistent data across multiple sources while providing offline-first capabilities.

### Key Design Principles

1. **Single Source of Truth**: All data flows through a central, normalized store
2. **Offline-First**: Prioritize local cache with background synchronization
3. **Type-Safe**: Leverage Kotlin's type system for compile-time guarantees
4. **Reactive**: Flow-based APIs for automatic UI updates
5. **Multiplatform**: Shared core logic across JVM, Native, and JS platforms
6. **Concurrent-Safe**: Proper synchronization and structured concurrency

### Use Cases

- **GraphQL Clients**: Normalized caching for graph queries (similar to Apollo Client)
- **REST APIs**: Entity caching with relationships
- **Offline-First Apps**: Local persistence with background sync
- **Real-time Data**: Reactive updates across app components

---

## Core Architecture

### Layer Overview

```
┌─────────────────────────────────────────────────────────┐
│                      UI Layer                           │
│                 (Collect Flow<StoreResult>)             │
└───────────────────┬─────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────┐
│                    Store Layer                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Store<K, V> / MutationStore<K, V, P, D>          │  │
│  │ - Coordinates between Memory, SoT, Fetcher       │  │
│  │ - Manages freshness policies                     │  │
│  │ - Provides reactive Flow APIs                    │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                    │
        ┌───────────┼───────────┐
        │           │           │
┌───────▼───┐  ┌───▼────┐  ┌──▼────────┐
│  Memory   │  │  SoT   │  │  Fetcher  │
│  Cache    │  │ (Local │  │ (Network) │
│ (RAM/LRU) │  │   DB)  │  │           │
└───────────┘  └────────┘  └───────────┘
```

### Component Responsibilities

| Component | Responsibility | Lifecycle |
|-----------|---------------|-----------|
| **Store** | API surface, orchestration | Scoped to ViewModel/Screen |
| **MemoryCache** | In-memory LRU cache | Same as Store |
| **SourceOfTruth** | Persistent local storage | App lifetime |
| **Fetcher** | Remote data loading | Transient (per request) |
| **Bookkeeper** | Metadata tracking (ETags, timestamps) | App lifetime |

---

## Data Flow

### Read Flow (stream/get)

```kotlin
User calls store.stream(key)
        │
        ▼
1. Check MemoryCache ──► Hit: emit cached value
        │
        ▼
2. Check Freshness Policy
        │
   ┌────┴─────┐
   │          │
   ▼          ▼
Fresh?    Stale?
   │          │
   │          ▼
   │    3. Launch Background Fetch
   │          │
   │          ▼
   │    4. Fetch from Network
   │          │
   │          ▼
   │    5. Convert: NetOut → WriteDb
   │          │
   │          ▼
   │    6. Write to SoT (transactional)
   │          │
   └──────────┼────────► 7. SoT emits Flow<ReadDb>
              │              │
              ▼              ▼
         8. Convert: ReadDb → Domain (V)
              │
              ▼
         9. Emit StoreResult.Data<V>
              │
              ▼
         10. Update MemoryCache
```

### Freshness Policies

| Policy | Behavior | Use Case |
|--------|----------|----------|
| **CachedOrFetch** | Serve cache immediately, trigger refresh in background | Default, most UI cases |
| **MinAge(duration)** | Only fetch if cached data is older than duration | Time-sensitive data |
| **MustBeFresh** | Always fetch, fail if network unavailable | Critical operations |
| **StaleIfError** | Serve stale cache if fetch fails | Offline resilience |

### Write Flow (update/create/delete/upsert)

```kotlin
User calls store.update(key, patch)
        │
        ▼
1. Read current value from SoT (if needed)
        │
        ▼
2. Optimistic local update (optional)
        │
   ┌────┴─────┐
   │          │
   ▼          ▼
Local DB   Memory Cache
update     update
   │          │
   └────┬─────┘
        │
        ▼
3. Encode patch → Network format
        │
        ▼
4. Send to remote API (Updater)
        │
   ┌────┴──────┬──────────┐
   ▼           ▼          ▼
Success    Conflict    Failure
   │           │          │
   │           │          ▼
   │           │    Rollback/Retry
   │           │
   └───────────┼──────────►
               │
               ▼
5. Update SoT with server echo
               │
               ▼
6. Invalidate dependencies
               │
               ▼
7. Emit updated value via Flow
```

---

## Key Components

### 1. Store Interface

```kotlin
interface Store<K : StoreKey, out V> {
    suspend fun get(key: K, freshness: Freshness = CachedOrFetch): V
    fun stream(key: K, freshness: Freshness = CachedOrFetch): Flow<StoreResult<V>>
    fun invalidate(key: K)
    fun invalidateNamespace(ns: StoreNamespace)
    fun invalidateAll()
}
```

**Design Notes:**
- **Covariant in `V`** (`out V`) for safe read-only subtype substitution
- `stream()` returns `Flow<StoreResult<V>>` for reactive updates
- `get()` suspends until first value or throws
- Invalidation triggers reactive updates for all collectors

### 2. StoreKey Abstraction

```kotlin
sealed interface StoreKey {
    val namespace: StoreNamespace
    fun stableHash(): Long
}

data class ByIdKey(override val namespace: StoreNamespace, val entity: EntityId) : StoreKey
data class QueryKey(override val namespace: StoreNamespace, val query: Map<String, String>) : StoreKey
```

**Design Notes:**
- `stableHash()` provides consistent hashing for cache keys
- `namespace` enables bulk invalidation
- Extensible: users can define custom key types

### 3. SourceOfTruth (SoT)

```kotlin
interface SourceOfTruth<K, ReadDb, WriteDb> {
    fun reader(key: K): Flow<ReadDb?>
    suspend fun write(key: K, value: WriteDb)
    suspend fun delete(key: K)
    suspend fun withTransaction(block: suspend () -> Unit)
    suspend fun rekey(old: K, new: K, reconcile: suspend (ReadDb, ReadDb?) -> ReadDb)
}
```

**Implementations:**
- **SQL-based**: Room (Android), SQLDelight (KMP)
- **Key-Value**: DataStore, SharedPreferences
- **Normalized**: NormalizedEntitySot, NormalizedListSot (graph-based)

**Design Notes:**
- Separate `ReadDb` and `WriteDb` types for read/write projections
- `reader()` returns `Flow` for reactive updates
- `withTransaction()` ensures atomicity
- `rekey()` supports server-assigned IDs (optimistic creates)

### 4. Fetcher

```kotlin
fun interface Fetcher<K : StoreKey, NetOut> {
    fun fetch(key: K, request: FetchRequest): Flow<FetcherResult<NetOut>>
}

sealed interface FetcherResult<out V> {
    data class Success<V>(val body: V, val etag: String? = null) : FetcherResult<V>
    data class NotModified(val etag: String) : FetcherResult<Nothing>
    data class Error(val error: Throwable) : FetcherResult<Nothing>
}
```

**Design Notes:**
- `Flow` return type supports streaming/chunked responses
- `NotModified` (304) avoids unnecessary writes
- `etag` support for conditional requests

### 5. Converter

```kotlin
interface Converter<K : StoreKey, V, ReadDb, NetOut, WriteDb> {
    suspend fun netToDbWrite(key: K, net: NetOut): WriteDb
    suspend fun dbReadToDomain(key: K, db: ReadDb): V
    suspend fun dbMetaFromProjection(db: ReadDb): Any?
    suspend fun netMeta(net: NetOut): NetMeta
    suspend fun domainToDbWrite(key: K, value: V): WriteDb?  // Optional: optimistic writes
}
```

**Design Notes:**
- Separate read/write projections
- Meta extraction for freshness tracking
- `domainToDbWrite` enables optimistic UI updates

### 6. Concurrency Primitives

#### SingleFlight
```kotlin
internal class SingleFlight<K, R> {
    suspend fun launch(scope: CoroutineScope, key: K, block: suspend () -> R): CompletableDeferred<R>
}
```

**Purpose**: Coalesce concurrent requests for the same key into a single execution

**Implementation Highlights:**
- Atomic get-or-create with `Mutex`
- Identity check in `finally` block prevents premature removal
- Proper `CancellationException` handling

#### KeyMutex
```kotlin
internal class KeyMutex<K>(private val maxSize: Int = 1000) {
    suspend fun forKey(key: K): Mutex
}
```

**Purpose**: Per-key locking for fine-grained concurrency control

**Implementation Highlights:**
- LRU eviction with `LinkedHashMap`
- Thread-safe with internal mutex
- Prevents unbounded memory growth

---

## Normalization Architecture

### Overview

StoreX includes a **graph normalization system** for applications that work with interconnected entities (e.g., GraphQL, relational data).

### Normalization Flow

```
Network Response (Graph Structure)
        │
        ▼
┌────────────────────────┐
│ Normalizer<NetOut, K>  │
│ - Extract entities     │
│ - Assign EntityKeys    │
│ - Build ChangeSet      │
└───────────┬────────────┘
            │
            ▼
     NormalizedWrite<K>
     ┌──────────────┐
     │  ChangeSet   │ ──► Batch write to NormalizationBackend
     ├──────────────┤
     │ IndexUpdate  │ ──► Update root entity lists
     └──────────────┘
            │
            ▼
┌───────────────────────────┐
│  NormalizationBackend     │
│  - Stores NormalizedRecord│
│  - Tracks dependencies    │
│  - Emits invalidations    │
└──────────┬────────────────┘
           │
           ▼
    Denormalization (on read)
           │
           ▼
    Domain Value (V)
```

### Key Normalization Components

#### 1. EntityKey
```kotlin
data class EntityKey(val typeName: String, val id: Map<String, Any>)
```

Uniquely identifies an entity in the normalized store.

#### 2. NormalizedRecord
```kotlin
data class NormalizedRecord(
    val key: EntityKey,
    val data: Map<String, Any>  // Scalars + EntityKey references
)
```

Flat representation of an entity with references to other entities.

#### 3. Shape
```kotlin
interface Shape<V : Any> {
    val id: ShapeId
    val maxDepth: Int
    fun outboundRefs(record: NormalizedRecord): Set<EntityKey>
}
```

Defines the **traversal shape** for graph composition (e.g., "User with Posts and Comments").

#### 4. Graph Composition (BFS)

```kotlin
suspend fun <V: Any> composeFromRoot(
    root: EntityKey,
    shape: Shape<V>,
    registry: SchemaRegistry,
    backend: NormalizationBackend
): ComposeResult<V>
```

**Algorithm:**
1. Start at `root` entity
2. BFS traversal following `outboundRefs()`
3. Batch reads from `NormalizationBackend` (256 entities per batch)
4. Track depth to enforce `maxDepth` (cycle prevention)
5. Handle errors gracefully (track failed entities, continue with partial data)
6. Denormalize via `SchemaRegistry`

**Error Handling:**
- Backend read failures tracked per entity
- Composition continues with partial data
- `GraphCompositionException` includes full diagnostic context

### Benefits of Normalization

- **Automatic Cache Coherence**: Update an entity once, all views update
- **Memory Efficiency**: Each entity stored once
- **Relationship Management**: Automatic tracking and invalidation
- **Pagination Support**: Incremental list updates without re-fetching entire collections

---

## Mutations & Writes

### MutationStore Interface

```kotlin
interface MutationStore<K : StoreKey, V, Patch, Draft> : Store<K, V> {
    suspend fun update(key: K, patch: Patch, policy: UpdatePolicy = UpdatePolicy()): UpdateResult
    suspend fun create(draft: Draft, policy: CreatePolicy = CreatePolicy()): CreateResult<K>
    suspend fun delete(key: K, policy: DeletePolicy = DeletePolicy()): DeleteResult
    suspend fun upsert(key: K, value: V, policy: UpsertPolicy = UpsertPolicy()): UpsertResult<K>
    suspend fun replace(key: K, value: V, policy: ReplacePolicy = ReplacePolicy()): ReplaceResult
}
```

### Mutation Flow

1. **Optimistic Update** (optional): Immediately update local cache
2. **Network Request**: Send mutation to remote API
3. **Echo Reconciliation**: Apply server response (canonical truth)
4. **Rollback on Failure**: Revert optimistic changes if needed

### Policies

```kotlin
data class UpdatePolicy(
    val requireOnline: Boolean = false,
    val precondition: Precondition? = null
)

sealed interface Precondition {
    data class IfEtag(val etag: String) : Precondition
    data class IfUnmodifiedSince(val timestamp: Instant) : Precondition
}
```

**Policy Options:**
- `requireOnline`: Fail immediately if offline
- `precondition`: Conditional writes (optimistic concurrency control)

### Mutation Results

```kotlin
sealed interface UpdateResult {
    data object Synced : UpdateResult
    data object Enqueued : UpdateResult  // Queued for later (offline)
    data class Failed(val error: Throwable) : UpdateResult
}
```

---

## Caching Strategy

### Three-Tier Cache

1. **Memory Cache** (L1): In-process, volatile
   - LRU eviction
   - Configurable max size
   - Thread-safe (Mutex-protected)

2. **Source of Truth** (L2): Persistent storage
   - Survives process death
   - Reactive (Flow-based)
   - Transactional writes

3. **Network** (L3): Remote API
   - Conditional requests (ETags)
   - Configurable timeouts/retries

### Cache Invalidation

```kotlin
// Granular invalidation
store.invalidate(key)                    // Single key
store.invalidateNamespace(namespace)     // All keys in namespace
store.invalidateAll()                    // Nuclear option

// Automatic invalidation (normalization)
// When entity X is updated, all views containing X are invalidated
```

### Freshness Tracking

```kotlin
interface Bookkeeper<K> {
    suspend fun recordSuccess(key: K, etag: String?, at: Instant)
    suspend fun recordFailure(key: K, error: Throwable, at: Instant)
    suspend fun lastStatus(key: K): FetchStatus?
}

data class FetchStatus(
    val timestamp: Instant,
    val etag: String?,
    val error: Throwable?
)
```

**FreshnessValidator** uses `Bookkeeper` to decide when to refetch:
- Check cache age
- Validate ETags
- Apply freshness policies

---

## Extension Points

### 1. Custom StoreKey Types

```kotlin
data class CustomKey(
    override val namespace: StoreNamespace,
    val customField: String
) : StoreKey {
    override fun stableHash(): Long = TODO()
}
```

### 2. Custom SourceOfTruth

```kotlin
class RedisSourceOfTruth<K, V> : SourceOfTruth<K, V, V> {
    // Implement using Redis client
}
```

### 3. Interceptors (Future)

```kotlin
fun interface StoreInterceptor<K: StoreKey, V> {
    suspend fun intercept(
        chain: Chain<K, V>,
        key: K,
        proceed: suspend () -> StoreResult<V>
    ): StoreResult<V>
}
```

**Use Cases:**
- Logging
- Analytics
- Request/response transformation
- Circuit breaking

### 4. Custom Normalizers

```kotlin
class CustomNormalizer<NetOut, K> : Normalizer<NetOut, K> {
    override fun normalize(
        requestKey: K,
        response: NetOut,
        updatedAt: Instant
    ): NormalizedWrite<K> {
        // Custom normalization logic
    }
}
```

---

## Platform Considerations

### JVM
- Efficient thread pools via `Dispatchers.IO`
- Room/SQLDelight for SoT
- OkHttp/Ktor for Fetcher

### Native (iOS/Android NDK)
- Careful with thread affinity
- SQLDelight native drivers
- Ktor native engine

### JS (Browser/Node)
- IndexedDB for SoT
- Fetch API for network
- Single-threaded event loop considerations

---

## Performance Characteristics

| Operation | Time Complexity | Notes |
|-----------|----------------|-------|
| Memory cache hit | O(1) | HashMap lookup |
| Memory cache miss + SoT hit | O(1) + DB latency | Depends on DB index |
| Normalization BFS | O(V + E) | V = vertices, E = edges |
| Batch read (normalized) | O(batch size) | Batched at 256 entities |
| Invalidation (single key) | O(1) | Direct map lookup |
| Invalidation (namespace) | O(n) | n = keys in namespace |

---

## Best Practices

1. **Use Namespaces**: Group related keys for efficient bulk operations
2. **Configure Dispatchers**: Use `Dispatchers.IO` for DB operations
3. **Bound Graph Depth**: Set `maxDepth` to prevent infinite recursion
4. **Implement Stale-If-Error**: Provide graceful offline UX
5. **Leverage Optimistic Updates**: Instant UI feedback for writes
6. **Monitor Cache Hit Rates**: Adjust `maxSize` based on usage patterns
7. **Use Structured Concurrency**: Scope stores to ViewModels/presenters

---

## Migration Guide (from Store5, Apollo, etc.)

See [MIGRATION.md](./MIGRATION.md) for detailed migration instructions.

---

## See Also

- [THREADING.md](./THREADING.md) - Concurrency model and thread safety guarantees
- [PERFORMANCE.md](./PERFORMANCE.md) - Optimization tips and benchmarks
- [MIGRATION.md](./MIGRATION.md) - Migration from other libraries
- [API Documentation](./docs/api/) - Comprehensive API reference

---

**Last Updated**: 2025-10-04
