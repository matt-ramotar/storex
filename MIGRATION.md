# StoreX Migration Guide

**Last Updated**: 2025-10-04
**Version**: 1.0

## Table of Contents
1. [Overview](#overview)
2. [Migration from Store5](#migration-from-store5)
3. [Migration from Apollo Client](#migration-from-apollo-client)
4. [Migration from Room/Realm](#migration-from-roomrealm)
5. [Migration from Custom Cache](#migration-from-custom-cache)
6. [Breaking Changes](#breaking-changes)
7. [Compatibility Layer](#compatibility-layer)

---

## Overview

This guide helps you migrate to StoreX from other popular caching and data management libraries.

### Migration Complexity Estimates

| From Library | Estimated Effort | Difficulty |
|-------------|-----------------|------------|
| **Store5** | 1-3 days | Low (similar API) |
| **Apollo Client (Android)** | 3-7 days | Medium (normalization concepts transfer) |
| **Room (direct)** | 5-10 days | High (architectural shift) |
| **Custom cache** | Variable | Medium-High |

---

## Migration from Store5

### Conceptual Mapping

| Store5 | StoreX | Notes |
|--------|--------|-------|
| `Store<Key, Output>` | `Store<K : StoreKey, V>` | StoreX requires `StoreKey` |
| `StoreBuilder` | `store { }` DSL | Simplified builder |
| `Fetcher` | `Fetcher` | Same concept, slightly different API |
| `SourceOfTruth` | `SourceOfTruth` | Enhanced with rekey support |
| `Converter` | `Converter` | Split ReadDb/WriteDb types |
| `MutableStore` | `MutationStore` | Extended with update/create/delete/upsert |

### Code Migration

#### 1. Store Creation

**Store5:**
```kotlin
val store = StoreBuilder
    .from(
        fetcher = Fetcher.of { key: String ->
            api.getUser(key)
        },
        sourceOfTruth = SourceOfTruthBuilder
            .fromNonFlow(
                reader = { key -> database.getUser(key) },
                writer = { key, value -> database.saveUser(value) }
            )
            .build()
    )
    .build()
```

**StoreX:**
```kotlin
val store = store<ByIdKey, User> {
    fetcher { key ->
        flow {
            val user = api.getUser(key.entity.id)
            emit(FetcherResult.Success(user))
        }
    }

    sourceOfTruth(
        reader = { key -> database.getUserFlow(key.entity.id) },
        writer = { key, user -> database.saveUser(user) }
    )

    converter(
        netToDbWrite = { key, net -> net },  // User is both net and db type
        dbReadToDomain = { key, db -> db }
    )
}
```

#### 2. Key Types

**Store5:** Simple types (String, Int, etc.)

**StoreX:** Must implement `StoreKey`

```kotlin
// Define custom key
data class UserKey(val userId: String) : StoreKey {
    override val namespace = StoreNamespace("users")
    override fun stableHash(): Long = userId.hashCode().toLong()
}

// Or use built-in ByIdKey
val key = ByIdKey(
    namespace = StoreNamespace("users"),
    entity = EntityId("User", userId)
)
```

#### 3. Reading Data

**Store5:**
```kotlin
// Get once
val user = store.get(userId)

// Stream updates
store.stream(StoreRequest.cached(userId, refresh = true))
    .collect { response ->
        when (response) {
            is StoreResponse.Data -> println(response.value)
            is StoreResponse.Loading -> println("Loading...")
            is StoreResponse.Error -> println(response.error)
        }
    }
```

**StoreX:**
```kotlin
// Get once
val user = store.get(key)

// Stream updates
store.stream(key, Freshness.CachedOrFetch)
    .collect { result ->
        when (result) {
            is StoreResult.Data -> println(result.value)
            is StoreResult.Loading -> println("Loading...")
            is StoreResult.Error -> println(result.throwable)
        }
    }
```

#### 4. Writing Data (NEW in StoreX)

**Store5:** Limited write support (clear only)

**StoreX:** Full CRUD operations

```kotlin
// Update (PATCH)
store.update(key, UserPatch(name = "New Name"))

// Create (POST)
val newKey = store.create(UserDraft(name = "Alice"))

// Delete (DELETE)
store.delete(key)

// Upsert (PUT - create or replace)
store.upsert(key, user)

// Replace (PUT - must exist)
store.replace(key, user)
```

#### 5. Invalidation

**Store5:**
```kotlin
store.clear(userId)
```

**StoreX:**
```kotlin
store.invalidate(key)  // Single key
store.invalidateNamespace(namespace)  // All keys in namespace
store.invalidateAll()  // Nuclear option
```

### Migration Checklist

- [ ] Replace `String`/`Int` keys with `StoreKey` implementations
- [ ] Update `StoreBuilder` to `store { }` DSL
- [ ] Add `Converter` for type conversions
- [ ] Change `StoreResponse` to `StoreResult`
- [ ] Update `StoreRequest.cached()` to `Freshness` enum
- [ ] Implement write operations if needed (`MutationStore`)
- [ ] Update invalidation calls
- [ ] Test all read/write flows

---

## Migration from Apollo Client

### Conceptual Mapping

| Apollo Client | StoreX | Notes |
|--------------|--------|-------|
| `ApolloClient` | `Store` + normalization | StoreX provides similar capabilities |
| `InMemoryCache` | `MemoryCache` + `NormalizationBackend` | Similar caching strategy |
| `CacheKey` | `EntityKey` | Entity identification |
| `TypePolicy` | `EntityAdapter` | Custom normalization rules |
| `Query` | `Fetcher` + `Shape` | Data fetching + graph traversal |
| `Mutation` | `MutationStore.update/create/delete` | Write operations |
| `useLazyQuery` | `store.stream()` | Reactive data loading |
| `useQuery` | `store.get()` | One-time fetch |
| `writeFragment` | `backend.apply(changeSet)` | Direct cache writes |

### Code Migration

#### 1. Client Setup

**Apollo Client:**
```kotlin
val apolloClient = ApolloClient.Builder()
    .serverUrl("https://api.example.com/graphql")
    .normalizedCache(
        normalizedCacheFactory = MemoryCacheFactory(maxSizeBytes = 10 * 1024 * 1024)
    )
    .build()
```

**StoreX:**
```kotlin
val backend = InMemoryNormalizationBackend()
val registry = SchemaRegistry()

val store = normalizedStore<QueryKey, User> {
    fetcher { key ->
        flow {
            val response = graphQLClient.query(key.query)
            emit(FetcherResult.Success(response))
        }
    }

    normalization {
        backend = backend
        registry = registry
        normalizer = GraphQLNormalizer(registry)
    }

    memoryCache {
        maxSize = 500
    }
}
```

#### 2. Queries

**Apollo Client:**
```kotlin
val response = apolloClient.query(GetUserQuery(userId = "123")).execute()
val user = response.data?.user
```

**StoreX:**
```kotlin
val key = ByIdKey(
    namespace = StoreNamespace("graphql"),
    entity = EntityId("User", "123")
)
val user = store.get(key)
```

#### 3. Mutations

**Apollo Client:**
```kotlin
val response = apolloClient.mutation(
    UpdateUserMutation(userId = "123", name = "New Name")
).execute()
```

**StoreX:**
```kotlin
store.update(
    key = ByIdKey(namespace, EntityId("User", "123")),
    patch = UserPatch(name = "New Name")
)
```

#### 4. Cache Updates

**Apollo Client:**
```kotlin
apolloClient.apolloStore.writeFragment(
    fragment = UserFragment(),
    cacheKey = CacheKey("User:123"),
    data = UserFragment.Data(name = "Alice", email = "alice@example.com")
)
```

**StoreX:**
```kotlin
val changeSet = ChangeSet(
    writes = mapOf(
        EntityKey("User", mapOf("id" to "123")) to NormalizedRecord(
            key = EntityKey("User", mapOf("id" to "123")),
            data = mapOf("name" to "Alice", "email" to "alice@example.com")
        )
    )
)
backend.apply(changeSet)
```

#### 5. Type Policies

**Apollo Client:**
```kotlin
val apolloClient = ApolloClient.Builder()
    .normalizedCache(
        normalizedCacheFactory = MemoryCacheFactory(),
        cacheKeyGenerator = object : CacheKeyGenerator {
            override fun cacheKeyForObject(obj: Map<String, Any?>, context: CacheKeyGeneratorContext): CacheKey? {
                val typename = obj["__typename"] as? String
                val id = obj["id"] as? String
                return if (typename != null && id != null) {
                    CacheKey("$typename:$id")
                } else {
                    null
                }
            }
        }
    )
    .build()
```

**StoreX:**
```kotlin
class UserAdapter : EntityAdapter<User> {
    override suspend fun denormalize(record: NormalizedRecord, context: DenormalizationContext): User {
        return User(
            id = record.data["id"] as String,
            name = record.data["name"] as? String ?: "",
            email = record.data["email"] as? String
        )
    }
}

registry.register("User", UserAdapter())
```

### GraphQL-Specific Considerations

#### 1. Normalization

**Apollo Client:**
- Automatic normalization based on `__typename` and `id`
- Flattens nested objects into separate entities

**StoreX:**
- Manual normalization via `Normalizer` interface
- Full control over entity extraction

**Migration tip:** Implement a `GraphQLNormalizer` that mimics Apollo's behavior:

```kotlin
class GraphQLNormalizer(private val registry: SchemaRegistry) : Normalizer<GraphQLResponse, QueryKey> {
    override fun normalize(requestKey: QueryKey, response: GraphQLResponse, updatedAt: Instant): NormalizedWrite<QueryKey> {
        val entities = extractEntities(response.data)
        val changeSet = ChangeSet(
            writes = entities.associateWith { entity ->
                NormalizedRecord(entity.key, entity.data)
            }
        )
        return NormalizedWrite(changeSet, indexUpdate = null)
    }

    private fun extractEntities(data: Any?): List<Entity> {
        // Recursively extract entities with __typename and id
        // Similar to Apollo's logic
    }
}
```

#### 2. Fragments

**Apollo Client:**
```graphql
fragment UserFields on User {
  id
  name
  email
}
```

**StoreX:**
```kotlin
val userShape = Shape<User>(
    id = ShapeId("UserFields"),
    maxDepth = 1,
    outboundRefs = { record ->
        // Define which fields are references to other entities
        emptySet()  // No nested entities for simple fragment
    }
)
```

### Migration Checklist

- [ ] Replace `ApolloClient` with `Store` + normalization
- [ ] Implement `GraphQLNormalizer` for automatic entity extraction
- [ ] Migrate queries to `store.get()` or `store.stream()`
- [ ] Migrate mutations to `store.update/create/delete()`
- [ ] Replace `TypePolicy` with `EntityAdapter`
- [ ] Update cache writes to use `ChangeSet`
- [ ] Implement `Shape` for fragment equivalents
- [ ] Test all GraphQL operations

---

## Migration from Room/Realm

### Conceptual Differences

| Concept | Room/Realm | StoreX |
|---------|-----------|--------|
| **Primary Use** | Local database | Cache layer with sync |
| **Data Source** | Single (local DB) | Multi-tier (memory/DB/network) |
| **Reactivity** | `Flow<T>` from DB | `Flow<StoreResult<T>>` from Store |
| **Writes** | Direct DB writes | Optimistic + sync to server |
| **Offline** | Always available | Offline-first with sync |

### Migration Strategy

**Option 1: StoreX as Cache Layer (Recommended)**
- Keep Room/Realm as persistent SoT
- Add StoreX on top for caching + sync
- Room/Realm provides `SourceOfTruth`

**Option 2: Replace with StoreX**
- Migrate all data layer to StoreX
- Use SQLDelight or custom DB as SoT backend
- Higher effort, full control

### Code Migration (Option 1)

#### 1. Existing Room Setup

**Room:**
```kotlin
@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :userId")
    fun getUser(userId: String): Flow<User?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)
}

// Usage
userDao.getUser(userId).collect { user ->
    println(user)
}
```

#### 2. Add StoreX Layer

**StoreX + Room:**
```kotlin
val store = store<ByIdKey, User> {
    fetcher { key ->
        flow {
            val user = api.getUser(key.entity.id)
            emit(FetcherResult.Success(user))
        }
    }

    sourceOfTruth(
        reader = { key -> userDao.getUser(key.entity.id) },
        writer = { key, user -> userDao.insertUser(user) }
    )

    converter(
        netToDbWrite = { key, net -> net },
        dbReadToDomain = { key, db -> db }
    )
}

// Usage
store.stream(key).collect { result ->
    when (result) {
        is StoreResult.Data -> println(result.value)
        is StoreResult.Loading -> println("Loading...")
        is StoreResult.Error -> showError(result.throwable)
    }
}
```

#### 3. Migration Benefits

- **Before (Room only):** Manual sync, network calls in ViewModels
- **After (StoreX + Room):** Automatic sync, offline-first, reactive updates

### Migration Checklist

- [ ] Identify data that needs network sync (candidates for StoreX)
- [ ] Keep Room DAOs for `SourceOfTruth` implementation
- [ ] Add `Fetcher` for network calls
- [ ] Implement `Converter` if needed
- [ ] Replace direct Room calls with `store.stream()` / `store.get()`
- [ ] Add write operations (`update`, `create`, `delete`)
- [ ] Test offline behavior
- [ ] Migrate ViewModels to use Store instead of Room

---

## Migration from Custom Cache

### Common Custom Cache Patterns

#### 1. Simple In-Memory Cache

**Before:**
```kotlin
class UserCache {
    private val cache = mutableMapOf<String, User>()
    private val mutex = Mutex()

    suspend fun get(userId: String): User? = mutex.withLock {
        cache[userId]
    }

    suspend fun put(userId: String, user: User) = mutex.withLock {
        cache[userId] = user
    }
}

// Usage
val user = cache.get(userId) ?: run {
    val fetched = api.getUser(userId)
    cache.put(userId, fetched)
    fetched
}
```

**After (StoreX):**
```kotlin
val store = store<ByIdKey, User> {
    fetcher { key ->
        flow {
            val user = api.getUser(key.entity.id)
            emit(FetcherResult.Success(user))
        }
    }

    memoryCache { maxSize = 100 }
}

// Usage
val user = store.get(key)  // Handles cache + fetch automatically
```

#### 2. LRU Cache with TTL

**Before:**
```kotlin
class TTLCache<K, V>(
    private val maxSize: Int,
    private val ttl: Duration
) {
    private data class Entry<V>(val value: V, val timestamp: Instant)
    private val cache = LinkedHashMap<K, Entry<V>>(maxSize, 0.75f, true)

    fun get(key: K): V? {
        val entry = cache[key] ?: return null
        return if (Clock.System.now() - entry.timestamp < ttl) {
            entry.value
        } else {
            cache.remove(key)
            null
        }
    }
}
```

**After (StoreX):**
```kotlin
val store = store<K, V> {
    // Built-in LRU
    memoryCache { maxSize = 100 }

    // TTL via Freshness policy
    // Usage: store.stream(key, Freshness.MinAge(ttl))
}
```

#### 3. Multi-Tier Cache

**Before:**
```kotlin
class MultiTierCache {
    private val memoryCache = MemoryCache()
    private val diskCache = DiskCache()

    suspend fun get(key: String): User? {
        return memoryCache.get(key)
            ?: diskCache.get(key)?.also { memoryCache.put(key, it) }
            ?: api.getUser(key).also {
                memoryCache.put(key, it)
                diskCache.put(key, it)
            }
    }
}
```

**After (StoreX):**
```kotlin
val store = store<ByIdKey, User> {
    memoryCache { maxSize = 100 }  // L1

    sourceOfTruth(  // L2 (disk)
        reader = { key -> diskCache.getFlow(key) },
        writer = { key, user -> diskCache.put(key, user) }
    )

    fetcher { key ->  // L3 (network)
        flow { emit(FetcherResult.Success(api.getUser(key))) }
    }
}
```

### Migration Checklist

- [ ] Identify cache tiers (memory, disk, network)
- [ ] Replace manual cache-check logic with `store.get()`
- [ ] Migrate TTL/freshness logic to `Freshness` policies
- [ ] Replace manual invalidation with `store.invalidate()`
- [ ] Add reactive updates via `store.stream()`
- [ ] Implement proper key types (`StoreKey`)
- [ ] Test edge cases (cache miss, network errors, etc.)

---

## Breaking Changes

### From Previous StoreX Versions (if applicable)

#### Version 0.x → 1.0

1. **Dispatcher Default Changed**
   ```kotlin
   // v0.x: Dispatchers.Default
   // v1.0: Dispatchers.IO (TASK-008 fix)
   ```

2. **Store Variance**
   ```kotlin
   // v0.x: interface Store<K : StoreKey, V>
   // v1.0: interface Store<K : StoreKey, out V>
   ```
   **Impact:** Read-only stores can now be safely covariant

3. **MemoryCache Return Type**
   ```kotlin
   // v0.x: suspend fun put(key: K, value: V): Unit
   // v1.0: suspend fun put(key: K, value: V): Boolean
   ```
   **Impact:** Returns `true` if new entry, `false` if updated

4. **CancellationException Handling**
   ```kotlin
   // v0.x: catch (t: Throwable) { }  // Caught CancellationException
   // v1.0: catch (e: Exception) { }  // Never catches CancellationException
   ```
   **Impact:** Proper structured concurrency

---

## Compatibility Layer

### Temporary Adapter for Gradual Migration

```kotlin
/**
 * Adapter to make StoreX look like Store5 during migration
 */
class Store5Adapter<Key, Output>(
    private val storeX: Store<ByIdKey, Output>,
    private val namespace: StoreNamespace
) {
    suspend fun get(key: Key): Output {
        return storeX.get(keyAdapter(key))
    }

    fun stream(request: StoreRequest<Key>): Flow<StoreResponse<Output>> {
        return storeX.stream(keyAdapter(request.key), freshnessAdapter(request))
            .map { result ->
                when (result) {
                    is StoreResult.Data -> StoreResponse.Data(result.value, result.origin)
                    is StoreResult.Loading -> StoreResponse.Loading(result.fromCache)
                    is StoreResult.Error -> StoreResponse.Error.Exception(result.throwable)
                }
            }
    }

    private fun keyAdapter(key: Key): ByIdKey {
        return ByIdKey(namespace, EntityId("Entity", key.toString()))
    }

    private fun freshnessAdapter(request: StoreRequest<Key>): Freshness {
        return if (request.refresh) Freshness.MustBeFresh else Freshness.CachedOrFetch
    }
}
```

---

## Migration Timeline

### Recommended Phases

**Phase 1: Evaluation (1-2 weeks)**
- [ ] Read architecture documentation
- [ ] Build proof-of-concept for one data type
- [ ] Benchmark performance vs current solution
- [ ] Identify migration complexity

**Phase 2: Foundation (2-4 weeks)**
- [ ] Add StoreX dependency
- [ ] Implement core `StoreKey` types
- [ ] Create compatibility layer (if needed)
- [ ] Set up infrastructure (DI, logging)

**Phase 3: Migration (4-12 weeks)**
- [ ] Migrate one feature at a time
- [ ] Write tests for each migration
- [ ] Monitor performance and stability
- [ ] Iterate based on feedback

**Phase 4: Cleanup (1-2 weeks)**
- [ ] Remove compatibility layer
- [ ] Remove old cache code
- [ ] Update documentation
- [ ] Train team on new patterns

---

## Getting Help

### Resources

- **Documentation:** [ARCHITECTURE.md](./ARCHITECTURE.md), [THREADING.md](./THREADING.md), [PERFORMANCE.md](./PERFORMANCE.md)
- **Examples:** `/samples` directory
- **Issues:** https://github.com/anthropics/storex/issues
- **Discussions:** https://github.com/anthropics/storex/discussions

### Common Migration Issues

| Issue | Solution |
|-------|----------|
| "How do I convert my String keys?" | Implement `StoreKey` or use `ByIdKey` |
| "My cache hit rate is low" | Tune `maxSize`, use `CachedOrFetch` policy |
| "Writes aren't syncing" | Implement `Updater`/`Creator`/`Deleter` |
| "Too many network requests" | SingleFlight handles this automatically |
| "Memory leaks" | Ensure proper scope management (ViewModel) |

---

**Last Updated**: 2025-10-04
