# StoreX Module Restructure - Migration Status Report

**Date**: 2025-10-04
**Overall Progress**: ~25% Complete

---

## ✅ Completed Work

### Phase 1: Module Structure (100% Complete)
- ✅ Created all module directories
- ✅ Created build.gradle.kts files for:
  - `:core`
  - `:mutations`
  - `:paging`
  - `:bom`
- ✅ Updated `settings.gradle.kts` to include all modules
- ✅ Updated version catalog with 1.0.0 versions

### Phase 2: Core Module Migration (40% Complete)
- ✅ `core/src/commonMain/kotlin/dev/mattramotar/storex/core/Store.kt` - Already present
- ✅ `core/src/commonMain/kotlin/dev/mattramotar/storex/core/SimpleConverter.kt` - Already present
- ✅ Created `core/src/commonMain/kotlin/dev/mattramotar/storex/core/internal/`:
  - `SourceOfTruth.kt` - Complete interface with CQRS support
  - `Fetcher.kt` - Complete with helper functions (fetcherOf, streamingFetcherOf)
  - `StoreException.kt` - Complete exception hierarchy
  - `MemoryCache.kt` - Complete with LRU implementation
  - `FreshnessValidator.kt` - Complete with DefaultFreshnessValidator
  - `Bookkeeper.kt` - Complete interface
- ✅ Fixed compilation issues:
  - Removed `@JvmOverloads` from interface methods
  - Added kotlinx.serialization dependency
- ✅ **Core module compiles successfully** (JVM target verified)

---

## ⏳ Remaining Work

### Phase 2: Core Module Migration (60% Remaining)

#### 2.1 Extract DSL Files
Need to create `core/src/commonMain/kotlin/dev/mattramotar/storex/core/dsl/`:
- [ ] `StoreBuilder.kt` - Extract read-only builder functions:
  - `store<K, V> { }` DSL function
  - `inMemoryStore<K, V>` helper
  - `cachedStore<K, V>` helper
  - Remove `mutationStore` function (goes to :mutations)
- [ ] `StoreBuilderScope.kt` - Extract read-only scope interface
- [ ] `ConfigScopes.kt` - Extract configuration classes:
  - `CacheConfig`
  - `FreshnessConfig`
  - `PersistenceConfig`
  - `ConverterConfig`
  - Remove `MutationsConfig` (goes to :mutations)
- [ ] `internal/DefaultStoreBuilder.kt` - Extract implementation

#### 2.2 Create TypeAliases
- [ ] `core/src/commonMain/kotlin/dev/mattramotar/storex/core/TypeAliases.kt`:
  - `SimpleReadStore<K, V>` - for simple cases (Domain == Entity)
  - `BasicReadStore<K, V, Entity>` - for basic persistence separation
  - Converter adapter functions

#### 2.3 Extract Read-Only Store Implementation
- [ ] Create `core/src/commonMain/kotlin/dev/mattramotar/storex/core/internal/RealReadStore.kt`:
  - Extract ONLY read operations from `RealStore.kt`:
    - `get(key, freshness)`
    - `stream(key, freshness)`
    - `invalidate(key)`
    - `invalidateNamespace(ns)`
    - `invalidateAll()`
  - Remove ALL mutation operations (update, create, delete, upsert, replace)
  - Update class to implement `Store<Key, Domain>` only (not MutationStore)
  - Simplify generics: Remove Patch, Draft, NetworkPatch, NetworkDraft, NetworkPut

#### 2.4 Create Simple SourceOfTruth Implementations
- [ ] Consider creating helper implementations:
  - `FlowBasedSourceOfTruth` - for simple Flow-based persistence
  - `InMemorySourceOfTruth` - for testing

#### 2.5 Verify Core Module
- [ ] Run `./gradlew :core:build` (full build with all targets)
- [ ] Verify no circular dependencies
- [ ] Check API surface is minimal and focused on read-only operations

---

### Phase 3: Mutations Module Migration (0% Complete)

#### 3.1 Move Mutation Interfaces
Move files from `store/src/commonMain/kotlin/dev/mattramotar/storex/store/mutation/` to `mutations/src/commonMain/kotlin/dev/mattramotar/storex/mutations/`:
- [ ] `MutationStore.kt` - Main interface
- [ ] `SimpleMutationEncoder.kt`
- [ ] `MutationEncoder.kt` (if separate)
- [ ] Mutation operation interfaces (Creator, Updater, Deleter, Putser)
- [ ] Mutation result types (CreateResult, UpdateResult, etc.)
- [ ] Mutation policy types (CreatePolicy, UpdatePolicy, etc.)
- [ ] `Precondition.kt`
- Update all package names to `dev.mattramotar.storex.mutations`

#### 3.2 Extract Mutation Implementation
- [ ] Create `mutations/src/commonMain/kotlin/dev/mattramotar/storex/mutations/internal/RealMutationStore.kt`:
  - Extract mutation operations from `RealStore.kt`:
    - `update(key, patch, policy)`
    - `create(draft, policy)`
    - `delete(key, policy)`
    - `upsert(key, value, policy)`
    - `replace(key, value, policy)`
  - Extend `RealReadStore` from :core
  - Implement `MutationStore<Key, Domain, Patch, Draft>`

#### 3.3 Extract Mutation DSL
- [ ] Create `mutations/src/commonMain/kotlin/dev/mattramotar/storex/mutations/dsl/`:
  - `MutationStoreBuilder.kt` - DSL function
  - `MutationsConfig.kt` - Mutation configuration
  - `MutationStoreBuilderScope.kt` - Builder scope interface

#### 3.4 Create Mutation TypeAliases
- [ ] `mutations/src/commonMain/kotlin/dev/mattramotar/storex/mutations/TypeAliases.kt`:
  - `BasicMutationStore<K, V, Entity, Patch, Draft>`
  - `AdvancedMutationStore<K, V, ...>`
  - Helper type aliases

#### 3.5 Update Build Dependencies
- [ ] Update `mutations/build.gradle.kts`:
  - Add `api(projects.core)` dependency
  - Verify version is set to 1.0.0

#### 3.6 Verify Mutations Module
- [ ] Run `./gradlew :mutations:build`
- [ ] Verify clean separation from :core
- [ ] Check no circular dependencies

---

### Phase 4: Normalization Module Migration (0% Complete)

#### 4.1 Merge Normalization Modules
Move files from `store/src/commonMain/kotlin/dev/mattramotar/storex/store/normalization/` to `normalization/runtime/src/commonMain/kotlin/dev/mattramotar/storex/normalization/`:
- [ ] All normalization implementation files
- [ ] Update package names
- [ ] Merge with existing `normalization/runtime` content

#### 4.2 Update Dependencies
- [ ] Update `normalization/runtime/build.gradle.kts`:
  - Add `api(projects.core)`
  - Add `api(projects.mutations)`
  - Set version to 1.0.0

#### 4.3 Extract Normalization DSL
- [ ] Move `store/.../dsl/NormalizedStoreBuilder.kt` to `normalization/runtime/.../dsl/`

#### 4.4 Verify
- [ ] Run `./gradlew :normalization:runtime:build`

---

### Phase 5: Paging Module Migration (0% Complete)

#### 5.1 Move Paging Files
Move files from `store/src/commonMain/kotlin/dev/mattramotar/storex/store/page/` to `paging/src/commonMain/kotlin/dev/mattramotar/storex/paging/`:
- [ ] `PageStore.kt`
- [ ] `internal/PageStoreImpl.kt`
- [ ] All other paging files

#### 5.2 Update Dependencies
- [ ] Verify `paging/build.gradle.kts` has `api(projects.core)`

#### 5.3 Verify
- [ ] Run `./gradlew :paging:build`

---

### Phase 6: New Module Stubs (0% Complete)

Create placeholder files for new modules to prevent empty module errors:
- [ ] `:interceptors` - Create basic package structure
- [ ] `:serialization-kotlinx` - Create basic package structure
- [ ] `:testing` - Create basic package structure
- [ ] `:telemetry` - Create basic package structure
- [ ] `:android` - Create basic package structure
- [ ] `:compose` - Create basic package structure
- [ ] `:ktor-client` - Create basic package structure

Each should have:
- Build file (already created)
- At least one Kotlin file to prevent empty source set errors
- Basic README or placeholder interface

---

### Phase 7: Bundle Modules (0% Complete)

- [ ] `:bundle-graphql` - Configure dependencies
- [ ] `:bundle-rest` - Configure dependencies
- [ ] `:bundle-android` - Configure dependencies

---

### Phase 8: Final Verification (0% Complete)

- [ ] Run `./gradlew build` (full project build)
- [ ] Fix any remaining compilation errors
- [ ] Check for circular dependencies
- [ ] Verify all modules compile together
- [ ] Run tests (if any)

---

## 🔧 Build Status

| Module | Build Status | Notes |
|--------|--------------|-------|
| `:core` | ✅ Compiles (JVM verified) | Has warnings about @JsExport but builds successfully |
| `:mutations` | ❌ Not started | Needs implementation |
| `:paging` | ❌ Not started | Needs migration |
| `:normalization:runtime` | ⚠️ Existing code | Needs merging with store normalization |
| `:resilience` | ✅ Existing | Should compile as-is |
| `:bom` | ⚠️ Structure created | Needs population |
| Other modules | ❌ Not started | Need stub implementations |

---

## 📝 Next Steps (Priority Order)

### Immediate (Complete Phase 2)
1. **Extract DSL files to :core/dsl/** - Critical for core functionality
2. **Create RealReadStore.kt** - Core implementation
3. **Create TypeAliases.kt** - Developer ergonomics
4. **Full core module build verification** - Ensure all targets compile

### Near-term (Phase 3)
5. **Move mutation files to :mutations** - Separate concerns
6. **Extract mutation implementation** - Complete mutation module
7. **Create mutation DSL** - Developer ergonomics
8. **Verify mutations module** - Ensure clean separation

### Medium-term (Phases 4-5)
9. **Merge normalization modules** - Consolidate normalization
10. **Move paging files** - Complete paging module

### Final (Phases 6-8)
11. **Create stub modules** - Prevent build errors
12. **Full build verification** - End-to-end testing
13. **Fix remaining issues** - Polish

---

## 🚧 Blockers & Issues

### Resolved
- ✅ Fixed `@JvmOverloads` on interface methods
- ✅ Added kotlinx.serialization dependency
- ✅ Fixed store module version reference

### Outstanding
- ⚠️ Large amount of code still needs to be migrated from `:store` to appropriate modules
- ⚠️ DSL extraction will require careful separation of read vs mutation operations
- ⚠️ RealStore.kt is complex and splitting it will require careful refactoring

---

## 📊 Estimated Time Remaining

- **Phase 2 completion**: 2-3 hours
- **Phase 3 completion**: 2-3 hours
- **Phases 4-5**: 1-2 hours
- **Phases 6-8**: 1-2 hours

**Total estimated time remaining**: 6-10 hours

---

## 🎯 Success Criteria

- [ ] All 17 modules compile independently
- [ ] Zero circular dependencies
- [ ] `:core` module < 6K LOC
- [ ] Clean separation: reads in :core, writes in :mutations
- [ ] All tests pass
- [ ] Ready for documentation phase (Phase 9-12)

---

## 📋 Files Created This Session

### Core Module - Internal
1. `/Users/matt/src/matt-ramotar/storex/core/src/commonMain/kotlin/dev/mattramotar/storex/core/internal/SourceOfTruth.kt`
2. `/Users/matt/src/matt-ramotar/storex/core/src/commonMain/kotlin/dev/mattramotar/storex/core/internal/Fetcher.kt`
3. `/Users/matt/src/matt-ramotar/storex/core/src/commonMain/kotlin/dev/mattramotar/storex/core/internal/StoreException.kt`
4. `/Users/matt/src/matt-ramotar/storex/core/src/commonMain/kotlin/dev/mattramotar/storex/core/internal/MemoryCache.kt`
5. `/Users/matt/src/matt-ramotar/storex/core/src/commonMain/kotlin/dev/mattramotar/storex/core/internal/FreshnessValidator.kt`
6. `/Users/matt/src/matt-ramotar/storex/core/src/commonMain/kotlin/dev/mattramotar/storex/core/internal/Bookkeeper.kt`

### Build Files Modified
- `/Users/matt/src/matt-ramotar/storex/core/build.gradle.kts` - Added serialization plugin and dependency
- `/Users/matt/src/matt-ramotar/storex/store/build.gradle.kts` - Fixed version reference

### Core Files Fixed
- `/Users/matt/src/matt-ramotar/storex/core/src/commonMain/kotlin/dev/mattramotar/storex/core/Store.kt` - Removed @JvmOverloads

---

**End of Status Report**
