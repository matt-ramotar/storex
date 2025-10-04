# Generic Parameter Naming Migration Guide

This guide documents the transition from cryptic single-letter generic parameter names to self-documenting names in StoreX's public API.

## Summary

**Goal:** Improve code readability and maintainability by using descriptive generic parameter names.

**Scope:** Public API only (Store, MutationStore, RealStore, SimpleConverter, SimpleMutationEncoder, TypeAliases)

**Status:** ✅ Complete

## Naming Changes

### Core Generic Parameters

| Old Name | New Name | Description | Category |
|----------|----------|-------------|----------|
| `V` | `Domain` | The application's domain model type | **Domain** |
| `K` | `Key` | The StoreKey subtype identifying entities | **Keys** |
| `ReadDb` | `ReadEntity` | Database read projection type (CQRS query side) | **Persistence** |
| `WriteDb` | `WriteEntity` | Database write model type (CQRS command side) | **Persistence** |
| `Db` | `Entity` | Unified database type (no CQRS) | **Persistence** |
| `NetOut` | `NetworkResponse` | Network fetch response type | **Network** |
| `Net` | `Network` | Unified network DTO type | **Network** |
| `NetPatch` | `NetworkPatch` | Network DTO for PATCH requests | **Network** |
| `NetDraft` | `NetworkDraft` | Network DTO for POST/create requests | **Network** |
| `NetPut` | `NetworkPut` | Network DTO for PUT/upsert requests | **Network** |

### Unchanged Parameters

These were already clear and remain unchanged:
- `Patch` - Type for partial updates (PATCH operations)
- `Draft` - Type for resource creation (POST operations)

## Migration by File

### 1. Store.kt

**Before:**
```kotlin
interface Store<K : StoreKey, out V>

interface Converter<K : StoreKey, V, ReadDb, NetOut, WriteDb>
```

**After:**
```kotlin
interface Store<Key : StoreKey, out Domain>

interface Converter<Key : StoreKey, Domain, ReadEntity, NetworkResponse, WriteEntity>
```

**Breaking Changes:**
- Any code explicitly referencing type parameters (rare) needs updating
- Type inference should handle most cases automatically

### 2. MutationStore.kt

**Before:**
```kotlin
interface MutationStore<K, V, Patch, Draft> : Store<K, V>

interface MutationEncoder<Patch, Draft, V, NetPatch, NetDraft, NetPut>
```

**After:**
```kotlin
interface MutationStore<Key, Domain, Patch, Draft> : Store<Key, Domain>

interface MutationEncoder<Patch, Draft, Domain, NetworkPatch, NetworkDraft, NetworkPut>
```

**Impact:**
- Affects custom MutationEncoder implementations
- Update parameter names in implementations

### 3. RealStore.kt

**Before:**
```kotlin
class RealStore<
    K : StoreKey,
    V: Any,
    ReadDb,
    WriteDb,
    NetOut: Any,
    Patch,
    Draft,
    NetPatch,
    NetDraft,
    NetPut
>
```

**After:**
```kotlin
class RealStore<
    Key : StoreKey,
    Domain: Any,
    ReadEntity,
    WriteEntity,
    NetworkResponse: Any,
    Patch,
    Draft,
    NetworkPatch,
    NetworkDraft,
    NetworkPut
>
```

**Impact:**
- Direct RealStore usage (rare, most use type aliases)
- Update all 10 type parameter references

### 4. SimpleConverter.kt

**Before:**
```kotlin
interface SimpleConverter<K : StoreKey, V, Db>

class IdentityConverter<K : StoreKey, V> : SimpleConverter<K, V, V>
```

**After:**
```kotlin
interface SimpleConverter<Key : StoreKey, Domain, Entity>

class IdentityConverter<Key : StoreKey, Domain> : SimpleConverter<Key, Domain, Domain>
```

**Impact:**
- Custom SimpleConverter implementations need parameter renames
- Method signatures updated (`toDomain(key: Key, value: Entity): Domain`)

### 5. SimpleMutationEncoder.kt

**Before:**
```kotlin
interface SimpleMutationEncoder<Patch, Draft, V, Net>

class IdentityMutationEncoder<T> : SimpleMutationEncoder<T, T, T, T>
```

**After:**
```kotlin
interface SimpleMutationEncoder<Patch, Draft, Domain, Network>

class IdentityMutationEncoder<Domain> : SimpleMutationEncoder<Domain, Domain, Domain, Domain>
```

**Impact:**
- Custom SimpleMutationEncoder implementations
- Method signatures updated (`encodePatch(patch: Patch, base: Domain?): Network?`)

### 6. TypeAliases.kt

All type aliases updated:

**Before:**
```kotlin
typealias SimpleReadStore<K, V> = RealStore<K, V, V, V, V, Nothing, Nothing, Nothing?, Nothing?, Nothing?>

typealias BasicReadStore<K, V, Db> = RealStore<K, V, Db, Db, Db, Nothing, Nothing, Nothing?, Nothing?, Nothing?>

typealias BasicMutationStore<K, V, Db, Patch, Draft> = RealStore<K, V, Db, Db, Db, Patch, Draft, Any?, Any?, Any?>

typealias AdvancedMutationStore<K, V, Db, Patch, Draft, NetPatch, NetDraft, NetPut> = ...

typealias CqrsStore<K, V, ReadDb, WriteDb, Patch, Draft> = ...
```

**After:**
```kotlin
typealias SimpleReadStore<Key, Domain> = RealStore<Key, Domain, Domain, Domain, Domain, Nothing, Nothing, Nothing?, Nothing?, Nothing?>

typealias BasicReadStore<Key, Domain, Entity> = RealStore<Key, Domain, Entity, Entity, Entity, Nothing, Nothing, Nothing?, Nothing?, Nothing?>

typealias BasicMutationStore<Key, Domain, Entity, Patch, Draft> = RealStore<Key, Domain, Entity, Entity, Entity, Patch, Draft, Any?, Any?, Any?>

typealias AdvancedMutationStore<Key, Domain, Entity, Patch, Draft, NetworkPatch, NetworkDraft, NetworkPut> = ...

typealias CqrsStore<Key, Domain, ReadEntity, WriteEntity, Patch, Draft> = ...
```

## Search & Replace Patterns

### Safe Automated Replacements

These patterns can be safely replaced in most codebases:

1. **Store interface:**
   - `Store<([A-Za-z0-9_]+),\s*([A-Za-z0-9_]+)>` → `Store<$1, $2>` (unchanged if already using type aliases)
   - But update implementations: `class MyStore : Store<K, V>` → `class MyStore : Store<Key, Domain>`

2. **SimpleConverter:**
   - `SimpleConverter<([^,]+),\s*([^,]+),\s*([^>]+)>` → `SimpleConverter<$1, $2, $3>` (safe if using consistent naming)

3. **Type Aliases - Update parameter order is preserved, just names changed**

### Manual Review Required

These changes require careful manual review:

1. **RealStore direct usage** - All 10 parameters need updating in order
2. **Custom Converter implementations** - Method signatures change
3. **Custom MutationEncoder implementations** - Method signatures change
4. **Extension functions on Store types** - Type parameter names in signatures

## Migration Strategy

### For Library Users

**Step 1: Update type aliases (if used)**
```kotlin
// Before
val store: BasicReadStore<UserKey, User, UserDb> = ...

// After
val store: BasicReadStore<UserKey, User, UserEntity> = ...
```

**Step 2: Update custom implementations**
```kotlin
// Before
class UserConverter : SimpleConverter<UserKey, User, UserDb> {
    override suspend fun toDomain(key: UserKey, value: UserDb): User = ...
    override suspend fun fromDomain(key: UserKey, value: User): UserDb? = ...
}

// After
class UserConverter : SimpleConverter<UserKey, User, UserEntity> {
    override suspend fun toDomain(key: UserKey, value: UserEntity): User = ...
    override suspend fun fromDomain(key: UserKey, value: User): UserEntity? = ...
}
```

**Step 3: Update any explicit type parameters**
```kotlin
// Before
fun <K : StoreKey, V> Store<K, V>.toFlow() = ...

// After
fun <Key : StoreKey, Domain> Store<Key, Domain>.toFlow() = ...
```

### For Internal Development

Internal files (not in public API) were **not** updated in this phase:
- `internal/` package files
- Builder DSL implementations
- Test utilities
- Advanced internal converters

These will be addressed in a future task to maintain momentum and allow validation of the public API changes first.

## Validation

### Compilation
✅ All public API files compile successfully with new naming

### Type Inference
✅ Kotlin's type inference handles most usage automatically

### Documentation
✅ All interfaces and classes have comprehensive KDoc explaining each generic parameter

## Benefits

1. **Self-documenting code** - `Domain` is clearer than `V`, `Entity` clearer than `Db`
2. **Industry alignment** - Uses standard DDD terminology (Domain, Entity)
3. **Reduced cognitive load** - No need to mentally map `K`/`V`/`Db`/`Net` to concepts
4. **Better IDE support** - More descriptive parameter names in autocomplete
5. **Easier onboarding** - New developers understand types at a glance

## Example: Before & After

### Before (cryptic)
```kotlin
class RealStore<K, V, ReadDb, WriteDb, NetOut, Patch, Draft, NetPatch, NetDraft, NetPut>(
    private val converter: Converter<K, V, ReadDb, NetOut, WriteDb>,
    private val encoder: MutationEncoder<Patch, Draft, V, NetPatch, NetDraft, NetPut>
) : MutationStore<K, V, Patch, Draft>
```

### After (self-documenting)
```kotlin
class RealStore<Key, Domain, ReadEntity, WriteEntity, NetworkResponse, Patch, Draft, NetworkPatch, NetworkDraft, NetworkPut>(
    private val converter: Converter<Key, Domain, ReadEntity, NetworkResponse, WriteEntity>,
    private val encoder: MutationEncoder<Patch, Draft, Domain, NetworkPatch, NetworkDraft, NetworkPut>
) : MutationStore<Key, Domain, Patch, Draft>
```

The intent is immediately clear: Domain is the core type, Entities are for persistence, NetworkResponse is from the API.

## Next Steps

1. ✅ Public API updated (this task)
2. ⏳ Monitor for issues in real usage
3. ⏳ Update internal implementations (future task)
4. ⏳ Update example code and documentation
5. ⏳ Consider deprecation warnings for old usage patterns (if needed)

## Questions?

For questions or issues with the migration:
1. Check this guide first
2. Review the comprehensive KDoc in each file
3. See examples in the updated type aliases
4. Open an issue if something is unclear
