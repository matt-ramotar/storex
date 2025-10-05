# Simplified API Guide

## Overview

StoreX now provides a simplified API that reduces generic parameter complexity for common use cases.

### Problem

The full `RealStore` implementation has 10 generic parameters:

```kotlin
RealStore<
    K,          // Key type
    V,          // Domain value
    ReadDb,     // Read database type
    WriteDb,    // Write database type
    NetOut,     // Network output type
    Patch,      // Patch type
    Draft,      // Draft type
    NetPatch,   // Network patch encoding
    NetDraft,   // Network draft encoding
    NetPut      // Network put encoding
>
```

This creates several issues:
- Complex type signatures in error messages
- Type inference failures
- Difficult to read and maintain
- Intimidating for new users

### Solution

The simplified API provides:

1. **Reduced Interfaces**: SimpleConverter (3 params), SimpleMutationEncoder (4 params)
2. **Type Aliases**: SimpleReadStore, BasicReadStore, BasicMutationStore, etc.
3. **Smart Defaults**: Identity converters and encoders for common cases

## Quick Start Examples

### Read-Only Store (Simplest)

When domain == network == database:

```kotlin
// ✅ NEW: Simple and clean
val userStore = store<UserKey, User> {
    fetcher { key -> api.getUser(key.id) }
}

// Type: SimpleReadStore<UserKey, User>
// Instead of: RealStore<UserKey, User, User, User, User, Nothing, Nothing, Nothing?, Nothing?, Nothing?>
```

### Store with Separate Database Type

When you need a conversion layer:

```kotlin
// ✅ NEW: Only specify types that differ
val articleStore = store<ArticleKey, Article> {
    fetcher { key -> api.getArticle(key.id) }

    converter(object : SimpleConverter<ArticleKey, Article, ArticleEntity> {
        override suspend fun toDomain(key: ArticleKey, value: ArticleEntity): Article {
            return Article(
                id = value.id,
                title = value.title,
                author = Author(value.authorId, value.authorName),
                publishedAt = Instant.parse(value.publishedAtIso)
            )
        }

        override suspend fun fromDomain(key: ArticleKey, value: Article): ArticleEntity {
            return ArticleEntity(
                id = value.id,
                title = value.title,
                authorId = value.author.id,
                authorName = value.author.name,
                publishedAtIso = value.publishedAt.toString()
            )
        }

        override suspend fun fromNetwork(key: ArticleKey, network: ArticleEntity): ArticleEntity {
            return network
        }
    })

    persistence {
        reader { key -> database.getArticle(key.id) }
        writer { key, entity -> database.save(entity) }
    }
}

// Type: BasicReadStore<ArticleKey, Article, ArticleEntity>
// Instead of: RealStore<ArticleKey, Article, ArticleEntity, ArticleEntity, ArticleEntity, Nothing, Nothing, Nothing?, Nothing?, Nothing?>
```

### Store with Mutations

```kotlin
// ✅ NEW: Simplified mutation store
val userStore = mutationStore<UserKey, User, UserPatch, UserDraft> {
    fetcher { key -> api.getUser(key.id) }

    converter(UserConverter())  // SimpleConverter<UserKey, User, UserEntity>

    mutations {
        update { key, patch ->
            val response = api.updateUser(key.id, patch)
            UpdateOutcome.Success(response, response.etag)
        }

        create { draft ->
            val response = api.createUser(draft)
            CreateOutcome.Success(UserKey(response.id), response, response.etag)
        }

        delete { key ->
            api.deleteUser(key.id)
            DeleteOutcome.Success(alreadyDeleted = false)
        }
    }
}

// Type: BasicMutationStore<UserKey, User, UserEntity, UserPatch, UserDraft>
// Instead of: RealStore<UserKey, User, UserEntity, UserEntity, UserEntity, UserPatch, UserDraft, Any?, Any?, Any?>
```

## Type Aliases Reference

### SimpleReadStore<K, V>

**When to use:**
- Domain == Database == Network (simplest case)
- No transformation needed
- In-memory only or simple JSON storage

**Example:**
```kotlin
val store: SimpleReadStore<UserKey, User> = store {
    fetcher { key -> api.getUser(key.id) }
}
```

**Equivalent to:**
```kotlin
RealStore<UserKey, User, User, User, User, Nothing, Nothing, Nothing?, Nothing?, Nothing?>
```

---

### BasicReadStore<K, V, Db>

**When to use:**
- Domain type differs from database/network type
- Single conversion layer (Db used for both read and write)
- Most common pattern for production apps

**Example:**
```kotlin
val store: BasicReadStore<ArticleKey, Article, ArticleEntity> = store {
    fetcher { key -> api.getArticle(key.id) }
    converter(ArticleConverter())  // SimpleConverter<ArticleKey, Article, ArticleEntity>
}
```

**Equivalent to:**
```kotlin
RealStore<ArticleKey, Article, ArticleEntity, ArticleEntity, ArticleEntity, Nothing, Nothing, Nothing?, Nothing?, Nothing?>
```

---

### BasicMutationStore<K, V, Db, Patch, Draft>

**When to use:**
- You need CRUD operations
- Patches and drafts are domain types (or easily convertible)
- Standard REST API

**Example:**
```kotlin
val store: BasicMutationStore<UserKey, User, UserEntity, UserPatch, UserDraft> =
    mutationStore {
        fetcher { key -> api.getUser(key.id) }
        converter(UserConverter())

        mutations {
            update { key, patch -> api.updateUser(key.id, patch) }
            create { draft -> api.createUser(draft) }
            delete { key -> api.deleteUser(key.id) }
        }
    }
```

**Equivalent to:**
```kotlin
RealStore<UserKey, User, UserEntity, UserEntity, UserEntity, UserPatch, UserDraft, Any?, Any?, Any?>
```

---

### AdvancedMutationStore<K, V, Db, Patch, Draft, NetPatch, NetDraft, NetPut>

**When to use:**
- Your API requires different DTOs for different operations
- PATCH uses different shape than POST
- PUT uses different shape than PATCH/POST

**Example:**
```kotlin
val store: AdvancedMutationStore<
    OrderKey,
    Order,
    OrderEntity,
    OrderPatch,
    OrderDraft,
    OrderPatchDto,
    OrderDraftDto,
    OrderPutDto
> = mutationStore {
    // Complex configuration with custom encoders
}
```

**Equivalent to:**
```kotlin
RealStore<OrderKey, Order, OrderEntity, OrderEntity, OrderEntity, OrderPatch, OrderDraft, OrderPatchDto, OrderDraftDto, OrderPutDto>
```

---

### CqrsStore<K, V, ReadDb, WriteDb, Patch, Draft>

**When to use:**
- Read model (projections) differs from write model (aggregates)
- Event sourcing architecture
- Denormalized reads, normalized writes

**Example:**
```kotlin
val store: CqrsStore<
    UserKey,
    UserView,           // Read: denormalized view
    UserProjection,     // ReadDb: query projection
    UserAggregate,      // WriteDb: normalized aggregate
    UserCommand,        // Patches are commands
    CreateUser          // Drafts are commands
> = mutationStore {
    // CQRS configuration
}
```

**Equivalent to:**
```kotlin
RealStore<UserKey, UserView, UserProjection, UserAggregate, UserProjection, UserCommand, CreateUser, Any?, Any?, Any?>
```

## Migration Guide

### From Full RealStore to Simplified API

#### Before (Complex)
```kotlin
val store = RealStore<
    UserKey,
    User,
    UserEntity,
    UserEntity,
    UserEntity,
    Nothing,
    Nothing,
    Nothing?,
    Nothing?,
    Nothing?
>(
    sot = sot,
    fetcher = fetcher,
    updater = null,
    creator = null,
    deleter = null,
    putser = null,
    converter = converter,
    encoder = NoOpMutationEncoder(),
    bookkeeper = bookkeeper,
    validator = validator,
    memory = cache,
    scope = scope
)
```

#### After (Simple)
```kotlin
val store: BasicReadStore<UserKey, User, UserEntity> = store {
    fetcher { key -> api.getUser(key.id) }
    converter(UserConverter())

    persistence {
        reader { key -> database.getUser(key.id) }
        writer { key, entity -> database.save(entity) }
    }
}
```

### From Store5 to StoreX Simplified API

#### Store5 (Old)
```kotlin
val store = StoreBuilder
    .from<UserKey, User>(
        fetcher = Fetcher.of { key -> api.getUser(key.id) },
        sourceOfTruth = SourceOfTruth.of(
            reader = { key -> database.getUser(key.id) },
            writer = { key, value -> database.save(value) }
        )
    )
    .build()
```

#### StoreX Simplified (New)
```kotlin
val store = store<UserKey, User> {
    fetcher { key -> api.getUser(key.id) }

    persistence {
        reader { key -> database.getUser(key.id) }
        writer { key, value -> database.save(value) }
    }
}
```

## Best Practices

### 1. Start Simple

Always start with the simplest type that works:
```kotlin
// ✅ Good: Start simple
val store = store<UserKey, User> {
    fetcher { key -> api.getUser(key.id) }
}

// ❌ Bad: Over-engineering
val store: CqrsStore<...> = mutationStore { /* complex setup */ }
```

### 2. Use Type Aliases in Public APIs

```kotlin
// ✅ Good: Clear, self-documenting
class UserRepository(
    private val store: BasicMutationStore<UserKey, User, UserEntity, UserPatch, UserDraft>
) {
    suspend fun getUser(id: String): User = store.get(UserKey(id))
}

// ❌ Bad: Overwhelming
class UserRepository(
    private val store: RealStore<UserKey, User, UserEntity, UserEntity, UserEntity, UserPatch, UserDraft, Any?, Any?, Any?>
) {
    suspend fun getUser(id: String): User = store.get(UserKey(id))
}
```

### 3. Create Reusable Converters

```kotlin
// ✅ Good: Reusable converter
class UserConverter : SimpleConverter<UserKey, User, UserEntity> {
    override suspend fun toDomain(key: UserKey, value: UserEntity): User {
        return User(value.id, value.name, value.email)
    }

    override suspend fun fromDomain(key: UserKey, value: User): UserEntity {
        return UserEntity(value.id, value.name, value.email)
    }

    override suspend fun fromNetwork(key: UserKey, network: UserEntity): UserEntity {
        return network
    }
}

// Use it
val store = store<UserKey, User> {
    fetcher { key -> api.getUser(key.id) }
    converter(UserConverter())
}
```

### 4. Leverage Identity Converters

```kotlin
// ✅ Good: Use identity converter when no transformation needed
val store = store<UserKey, UserJson> {
    fetcher { key -> api.getUser(key.id) }
    converter(identityConverter())  // Explicit
}

// ✅ Better: Implicit identity converter (default)
val store = store<UserKey, UserJson> {
    fetcher { key -> api.getUser(key.id) }
    // converter auto-detected as identity
}
```

## API Comparison

| Pattern | Old API (10 params) | New API (Type Alias) | Reduction |
|---------|---------------------|----------------------|-----------|
| Simple Store | `RealStore<K, V, V, V, V, Nothing, Nothing, Nothing?, Nothing?, Nothing?>` | `SimpleReadStore<K, V>` | **80%** |
| Store with DB | `RealStore<K, V, Db, Db, Db, Nothing, Nothing, Nothing?, Nothing?, Nothing?>` | `BasicReadStore<K, V, Db>` | **70%** |
| Mutation Store | `RealStore<K, V, Db, Db, Db, P, D, Any?, Any?, Any?>` | `BasicMutationStore<K, V, Db, P, D>` | **50%** |
| CQRS Store | `RealStore<K, V, RDb, WDb, RDb, P, D, Any?, Any?, Any?>` | `CqrsStore<K, V, RDb, WDb, P, D>` | **40%** |

## Summary

The simplified API reduces complexity for common cases while preserving full power when needed:

1. **SimpleConverter** reduces Converter from 5 params to 3
2. **SimpleMutationEncoder** reduces MutationEncoder from 6 params to 4
3. **Type aliases** reduce RealStore from 10 params to 2-6 depending on use case
4. **Smart defaults** eliminate boilerplate for 90% of use cases
5. **Backward compatibility** - existing code continues to work

**Key Principle:** Start simple, add complexity only when needed.
