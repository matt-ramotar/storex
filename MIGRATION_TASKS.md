# StoreX Module Restructure - Migration Tasks

**Started**: 2025-10-04
**Target Completion**: 2025-10-20 (3 weeks)
**Status**: 🟡 IN PROGRESS (15% complete)

---

## Overview

Complete restructure of StoreX from monolithic `:store` module into clean, modular architecture with 17 focused modules. No public releases yet = no deprecation overhead.

## Progress Summary

- ✅ **Module Structure**: 13/17 modules created
- ✅ **Build Files**: 4/17 build.gradle.kts complete
- ✅ **Settings**: settings.gradle.kts updated
- ⏳ **Code Migration**: 0% complete
- ⏳ **Documentation**: 0% complete

---

## Phase 1: Module Structure & Build Setup ✅ 75% COMPLETE

### 1.1 Create Module Directories ✅ DONE
- [x] `:core` directory structure
- [x] `:mutations` directory structure
- [x] `:paging` directory structure
- [x] `:interceptors` directory structure
- [x] `:serialization-kotlinx` directory structure
- [x] `:testing` directory structure
- [x] `:telemetry` directory structure
- [x] `:bom` directory structure
- [x] `:bundle-graphql` directory structure
- [x] `:bundle-rest` directory structure
- [x] `:bundle-android` directory structure
- [ ] `:android` directory structure (for Android-specific code)
- [ ] `:compose` directory structure (for Compose helpers)
- [ ] `:ktor-client` directory structure (for Ktor integration)

### 1.2 Create Build Files ⏳ IN PROGRESS (31% complete)
- [x] `:core/build.gradle.kts` - minimal deps (coroutines, datetime)
- [x] `:mutations/build.gradle.kts` - depends on :core
- [x] `:paging/build.gradle.kts` - depends on :core
- [x] `:bom/build.gradle.kts` - version constraints
- [ ] `:interceptors/build.gradle.kts`
- [ ] `:serialization-kotlinx/build.gradle.kts`
- [ ] `:testing/build.gradle.kts`
- [ ] `:telemetry/build.gradle.kts`
- [ ] `:android/build.gradle.kts`
- [ ] `:compose/build.gradle.kts`
- [ ] `:ktor-client/build.gradle.kts`
- [ ] `:bundle-graphql/build.gradle.kts`
- [ ] `:bundle-rest/build.gradle.kts`
- [ ] `:bundle-android/build.gradle.kts`

### 1.3 Update Configuration Files ✅ DONE
- [x] `settings.gradle.kts` - include all new modules
- [ ] Update `:resilience/build.gradle.kts` version to 1.0.0
- [ ] Update `:normalization:ksp/build.gradle.kts` version to 1.0.0
- [ ] Update `:normalization:runtime/build.gradle.kts` version to 1.0.0

### 1.4 Update Version Catalog ⏳ TODO
- [ ] Update `gradle/libs.versions.toml` with 1.0.0 versions
- [ ] Remove old `storex-store = "6.0.0-SNAPSHOT"` reference
- [ ] Add new module version references

---

## Phase 2: Core Module Migration ⏳ NOT STARTED

### 2.1 Extract Read-Only Store to `:core` ⏳ TODO
**Files to move from `:store/src/commonMain/kotlin/dev/mattramotar/storex/store/`:**

- [ ] `Store.kt` → `:core/src/commonMain/kotlin/dev/mattramotar/storex/core/Store.kt`
  - Extract: `Store<Key, Domain>` interface
  - Extract: `StoreKey`, `ByIdKey`, `QueryKey`
  - Extract: `StoreResult<Domain>` (Data/Loading/Error)
  - Extract: `Freshness` sealed interface
  - Extract: `Origin` enum
  - Extract: `Converter<Key, Domain, ReadEntity, NetworkResponse, WriteEntity>`
  - Extract: `StoreInterceptor<Key, Domain>`
  - Extract: `SingleFlight<Key, Result>` internal class
  - Extract: `KeyMutex<Key>` internal class
  - **Remove**: MutationStore references (goes to `:mutations`)

- [ ] `SimpleConverter.kt` → `:core/src/commonMain/kotlin/dev/mattramotar/storex/core/SimpleConverter.kt`
  - Move entire file (3-param converter)
  - Update package: `dev.mattramotar.storex.core`

- [ ] `TypeAliases.kt` → `:core/src/commonMain/kotlin/dev/mattramotar/storex/core/TypeAliases.kt`
  - Extract read-only aliases: `SimpleReadStore`, `BasicReadStore`, `CqrsStore`
  - Extract converter adapters
  - Update imports to `:core` package
  - **Remove**: Mutation-related aliases (move to `:mutations`)

### 2.2 Extract Internal Implementation to `:core` ⏳ TODO
**Files to move from `:store/src/commonMain/kotlin/dev/mattramotar/storex/store/internal/`:**

- [ ] Split `RealStore.kt`:
  - [ ] Create `:core/.../internal/RealReadStore.kt` with read-only implementation
  - [ ] Keep mutation parts for `:mutations` module
  - [ ] Update generics to use new naming (Key, Domain, etc.)

- [ ] `StoreImpl.kt` → `:core/.../internal/StoreImpl.kt`
  - [ ] Extract `MemoryCache` implementation
  - [ ] Extract `SourceOfTruth` implementation
  - [ ] Extract `Fetcher` implementation
  - [ ] Extract `StoreException` hierarchy
  - [ ] Update package references

### 2.3 Extract DSL for `:core` ⏳ TODO
**Files to move from `:store/src/commonMain/kotlin/dev/mattramotar/storex/store/dsl/`:**

- [ ] Split `StoreBuilder.kt`:
  - [ ] Create `:core/.../dsl/StoreBuilder.kt` with read-only builder
  - [ ] Extract: `store<Key, Domain> { }` DSL function
  - [ ] Extract: fetcher, sourceOfTruth, converter configuration
  - [ ] **Remove**: mutations configuration (goes to `:mutations`)

- [ ] `ConfigScopes.kt` → `:core/.../dsl/ConfigScopes.kt`
  - [ ] Extract: `ConverterConfig<Key, Domain, Entity>`
  - [ ] Extract: Basic configuration scopes
  - [ ] **Remove**: Mutation-specific configs

- [ ] `StoreBuilderScope.kt` → `:core/.../dsl/StoreBuilderScope.kt`
  - [ ] Move entire file
  - [ ] Update package references

### 2.4 Update `:core` Imports & Package Structure ⏳ TODO
- [ ] Change all packages from `dev.mattramotar.storex.store.*` → `dev.mattramotar.storex.core.*`
- [ ] Update all internal imports
- [ ] Remove all references to mutations/normalization
- [ ] Verify `:core` compiles independently

---

## Phase 3: Mutations Module Migration ⏳ NOT STARTED

### 3.1 Extract MutationStore to `:mutations` ⏳ TODO
**Files to move from `:store/src/commonMain/kotlin/dev/mattramotar/storex/store/mutation/`:**

- [ ] `MutationStore.kt` → `:mutations/.../MutationStore.kt`
  - [ ] Move entire file
  - [ ] Update imports: `import dev.mattramotar.storex.core.Store`
  - [ ] Update package: `dev.mattramotar.storex.mutations`

- [ ] `SimpleMutationEncoder.kt` → `:mutations/.../SimpleMutationEncoder.kt`
  - [ ] Move entire file
  - [ ] Update package references

- [ ] `MutationEncoder.kt` (if separate) → `:mutations/.../MutationEncoder.kt`

- [ ] `Deleter.kt`, `Creator.kt`, `Putser.kt` → `:mutations/.../`
  - [ ] Move all mutation operator interfaces
  - [ ] Update package references

- [ ] `Mutation.kt` (sealed interface) → `:mutations/.../Mutation.kt`

### 3.2 Extract Mutation Implementation ⏳ TODO
**From `:store/src/commonMain/kotlin/dev/mattramotar/storex/store/internal/`:**

- [ ] Split `RealStore.kt`:
  - [ ] Create `:mutations/.../internal/RealMutationStore.kt`
  - [ ] Extract: update, create, delete, upsert, replace implementations
  - [ ] Extract: optimistic update logic
  - [ ] Extract: provisional key handling
  - [ ] Update to depend on `:core` RealReadStore

### 3.3 Extract Mutation DSL ⏳ TODO
**From `:store/src/commonMain/kotlin/dev/mattramotar/storex/store/dsl/`:**

- [ ] `MutationsConfig.kt` → `:mutations/.../dsl/MutationsConfig.kt`
  - [ ] Move entire file
  - [ ] Update imports from `:core`

- [ ] Split `StoreBuilder.kt`:
  - [ ] Create `:mutations/.../dsl/MutationStoreBuilder.kt`
  - [ ] Extract: `mutationStore<Key, Domain, Patch, Draft> { }` DSL
  - [ ] Extract: mutations { } block configuration

### 3.4 Extract Type Aliases for Mutations ⏳ TODO
**From `:store/.../TypeAliases.kt`:**

- [ ] Create `:mutations/.../TypeAliases.kt`
- [ ] Move: `BasicMutationStore<Key, Domain, Entity, Patch, Draft>`
- [ ] Move: `AdvancedMutationStore<...>`
- [ ] Move: Mutation-specific adapter functions

### 3.5 Update `:mutations` Package Structure ⏳ TODO
- [ ] Change packages to `dev.mattramotar.storex.mutations.*`
- [ ] Update all imports from `:core`
- [ ] Verify `:mutations` compiles with `:core` dependency

---

## Phase 4: Normalization Module Migration ⏳ NOT STARTED

### 4.1 Merge Normalization Modules ⏳ TODO

**Goal**: Merge `:normalization:runtime` + `:store/normalization/` → `:normalization-runtime`

**From `:normalization:runtime/src/commonMain/kotlin/dev/mattramotar/storex/normalization/`:**
- [ ] `Normalizable.kt` → `:normalization-runtime/.../Normalizable.kt`
- [ ] `schema/EntityAdapter.kt` → `:normalization-runtime/.../schema/EntityAdapter.kt`
- [ ] `format/NormalizedValue.kt` → `:normalization-runtime/.../format/NormalizedValue.kt`
- [ ] `keys/EntityKey.kt` → `:normalization-runtime/.../keys/EntityKey.kt`

**From `:store/src/commonMain/kotlin/dev/mattramotar/storex/store/normalization/`:**
- [ ] `Normalizer.kt` → `:normalization-runtime/.../Normalizer.kt`
- [ ] `GraphProjection.kt` → `:normalization-runtime/.../GraphProjection.kt`
- [ ] `NormalizedWrite.kt` → `:normalization-runtime/.../NormalizedWrite.kt`
- [ ] `NormalizerEngine.kt` → `:normalization-runtime/.../NormalizerEngine.kt`
- [ ] `GraphCompositionException.kt` → `:normalization-runtime/.../GraphCompositionException.kt`
- [ ] `IndexManager.kt` → `:normalization-runtime/.../IndexManager.kt`
- [ ] `backend/NormalizationBackend.kt` → `:normalization-runtime/.../backend/`
- [ ] `internal/ComposeResult.kt` → `:normalization-runtime/.../internal/`
- [ ] `internal/RootResolver.kt` → `:normalization-runtime/.../internal/`
- [ ] `internal/ListSot.kt` → `:normalization-runtime/.../internal/`
- [ ] `internal/NormalizationConverter.kt` → `:normalization-runtime/.../internal/`
- [ ] `internal/builder.kt` → `:normalization-runtime/.../internal/`

### 4.2 Update Normalization Dependencies ⏳ TODO
- [ ] Update `:normalization-runtime/build.gradle.kts`:
  - [ ] Add dependency: `api(projects.core)`
  - [ ] Add dependency: `api(projects.mutations)`
  - [ ] Update version to 1.0.0

- [ ] Update `:normalization-ksp/build.gradle.kts`:
  - [ ] Update dependency: `compileOnly(projects.normalizationRuntime)`
  - [ ] Update version to 1.0.0

### 4.3 Extract Normalization DSL ⏳ TODO
**From `:store/src/commonMain/kotlin/dev/mattramotar/storex/store/dsl/`:**

- [ ] `NormalizedStoreBuilder.kt` → `:normalization-runtime/.../dsl/NormalizedStoreBuilder.kt`
  - [ ] Move entire file
  - [ ] Update imports from `:core` and `:mutations`

### 4.4 Update Normalization Package Structure ⏳ TODO
- [ ] Change all packages to `dev.mattramotar.storex.normalization.*`
- [ ] Update all imports from `:core` and `:mutations`
- [ ] Delete old `:normalization:runtime` module directory
- [ ] Verify `:normalization-runtime` compiles

---

## Phase 5: Paging Module Migration ⏳ NOT STARTED

### 5.1 Extract Paging to `:paging` ⏳ TODO
**From `:store/src/commonMain/kotlin/dev/mattramotar/storex/store/page/`:**

- [ ] `PageStore.kt` → `:paging/.../PageStore.kt`
  - [ ] Move entire file
  - [ ] Update imports from `:core`
  - [ ] Update package: `dev.mattramotar.storex.paging`

- [ ] `internal/*` → `:paging/.../internal/`
  - [ ] Move all internal paging implementation files
  - [ ] Update package references

### 5.2 Update `:paging` Package Structure ⏳ TODO
- [ ] Change packages to `dev.mattramotar.storex.paging.*`
- [ ] Verify `:paging` compiles with `:core` dependency

---

## Phase 6: New Module Implementation ⏳ NOT STARTED

### 6.1 Implement `:interceptors` Module 🆕 TODO
- [ ] Create `Interceptor.kt` interface
- [ ] Create `InterceptorChain.kt` implementation
- [ ] Create `LoggingInterceptor.kt`
- [ ] Create `MetricsInterceptor.kt`
- [ ] Create `AuthInterceptor.kt`
- [ ] Create `CachingInterceptor.kt` (HTTP cache headers)
- [ ] Write unit tests
- [ ] Write KDoc documentation

### 6.2 Implement `:serialization-kotlinx` Module 🆕 TODO
- [ ] Create auto-converter for @Serializable types
- [ ] Create JSON SourceOfTruth implementation
- [ ] Create NetworkResponse → Entity mapping
- [ ] Write unit tests
- [ ] Write KDoc documentation

### 6.3 Implement `:testing` Module 🆕 TODO
- [ ] Create `TestStore` fake implementation
- [ ] Create `InMemorySourceOfTruth`
- [ ] Create `MockFetcher` with configurable responses
- [ ] Create Turbine extensions for Flow testing
- [ ] Create coroutine test helpers
- [ ] Write documentation and examples

### 6.4 Implement `:telemetry` Module 🆕 TODO
- [ ] Create OpenTelemetry integration
- [ ] Create metrics collectors (cache hit rate, fetch latency, error rate)
- [ ] Create distributed tracing support
- [ ] Create performance monitoring hooks
- [ ] Write unit tests
- [ ] Write KDoc documentation

### 6.5 Implement `:android` Module 🆕 TODO
- [ ] Create Room SourceOfTruth adapter
- [ ] Create DataStore integration
- [ ] Create WorkManager background sync
- [ ] Create AndroidX Lifecycle awareness
- [ ] Create Compose state helpers (or move to `:compose`)
- [ ] Write unit tests

### 6.6 Implement `:compose` Module 🆕 TODO
- [ ] Create `rememberStore()` composition function
- [ ] Create `Store.collectAsState()` extension
- [ ] Create `LaunchedStoreEffect` composable
- [ ] Create optimistic UI state management helpers
- [ ] Write unit tests
- [ ] Write documentation with examples

### 6.7 Implement `:ktor-client` Module 🆕 TODO
- [ ] Create automatic Fetcher from Ktor HttpClient
- [ ] Create auth plugin integration
- [ ] Create conditional request support (ETag, If-Modified-Since)
- [ ] Write unit tests
- [ ] Write documentation

---

## Phase 7: Bundle Modules ⏳ NOT STARTED

### 7.1 Create `:bundle-graphql` 🆕 TODO
- [ ] Create build.gradle.kts with dependencies:
  - [ ] `api(projects.core)`
  - [ ] `api(projects.mutations)`
  - [ ] `api(projects.normalizationRuntime)`
  - [ ] `api(projects.interceptors)`
- [ ] Configure Maven publishing
- [ ] Write README for GraphQL bundle

### 7.2 Create `:bundle-rest` 🆕 TODO
- [ ] Create build.gradle.kts with dependencies:
  - [ ] `api(projects.core)`
  - [ ] `api(projects.mutations)`
  - [ ] `api(projects.resilience)`
  - [ ] `api(projects.serializationKotlinx)`
- [ ] Configure Maven publishing
- [ ] Write README for REST bundle

### 7.3 Create `:bundle-android` 🆕 TODO
- [ ] Create build.gradle.kts with dependencies:
  - [ ] `api(projects.core)`
  - [ ] `api(projects.mutations)`
  - [ ] `api(projects.android)`
  - [ ] `api(projects.compose)`
- [ ] Configure Maven publishing
- [ ] Write README for Android bundle

---

## Phase 8: Update Resilience Module ⏳ NOT STARTED

### 8.1 Update `:resilience` to 1.0.0 ⏳ TODO
- [ ] Update `resilience/build.gradle.kts` version to 1.0.0
- [ ] Update `resilience/gradle.properties` VERSION_NAME=1.0.0
- [ ] Ensure clean separation from `:store`
- [ ] Verify compilation
- [ ] Update KDoc if needed

---

## Phase 9: Documentation Updates ⏳ NOT STARTED

### 9.1 Update Architecture Documentation ⏳ TODO
- [ ] Update `ARCHITECTURE.md` with new module structure
- [ ] Add module dependency diagram
- [ ] Document each module's responsibility
- [ ] Update data flow diagrams

### 9.2 Create Module-Specific READMEs 🆕 TODO
- [ ] Create `core/README.md`
- [ ] Create `mutations/README.md`
- [ ] Create `normalization-runtime/README.md`
- [ ] Create `paging/README.md`
- [ ] Create `interceptors/README.md`
- [ ] Create `serialization-kotlinx/README.md`
- [ ] Create `testing/README.md`
- [ ] Create `telemetry/README.md`
- [ ] Create `android/README.md`
- [ ] Create `compose/README.md`
- [ ] Create `ktor-client/README.md`

### 9.3 Update Migration Guide ⏳ TODO
- [ ] Update `MIGRATION.md` with new module structure
- [ ] Add "From Monolithic Store6 to Modular 1.0" section
- [ ] Provide before/after dependency examples
- [ ] Document breaking changes
- [ ] Provide automated migration scripts if possible

### 9.4 Update Main README ⏳ TODO
- [ ] Update root `README.md` with new module structure
- [ ] Add dependency examples for common use cases
- [ ] Update quick start guide
- [ ] Add module selection guide (which modules do I need?)
- [ ] Update badge URLs

### 9.5 Create New Documentation 🆕 TODO
- [ ] Create `MODULES.md` - comprehensive module reference
- [ ] Create `CHOOSING_MODULES.md` - decision guide
- [ ] Create `BUNDLE_GUIDE.md` - when to use bundles vs individual modules
- [ ] Update `PERFORMANCE.md` with module-specific optimizations
- [ ] Update `THREADING.md` with module-specific concurrency notes

---

## Phase 10: Sample App Updates ⏳ NOT STARTED

### 10.1 Update Sample App ⏳ TODO
- [ ] Update `sample/build.gradle.kts` to use new modules
- [ ] Replace `:store` dependency with modular dependencies
- [ ] Update imports to new packages
- [ ] Verify sample app compiles and runs
- [ ] Add examples for each module
- [ ] Create separate sample apps for different use cases:
  - [ ] GraphQL sample (uses `:bundle-graphql`)
  - [ ] REST API sample (uses `:bundle-rest`)
  - [ ] Android sample (uses `:bundle-android`)

---

## Phase 11: Testing & Verification ⏳ NOT STARTED

### 11.1 Unit Tests ⏳ TODO
- [ ] Verify all existing tests still pass
- [ ] Move tests to appropriate modules:
  - [ ] `:core` tests
  - [ ] `:mutations` tests
  - [ ] `:normalization-runtime` tests
  - [ ] `:paging` tests
- [ ] Write new tests for new modules
- [ ] Achieve >80% code coverage per module

### 11.2 Integration Tests ⏳ TODO
- [ ] Test cross-module integration
- [ ] Test bundle modules work correctly
- [ ] Test BOM version alignment
- [ ] Verify no circular dependencies

### 11.3 Compilation Verification ⏳ TODO
- [ ] `./gradlew :core:build` - verify core compiles independently
- [ ] `./gradlew :mutations:build` - verify with :core dependency
- [ ] `./gradlew :normalization-runtime:build` - verify merged module
- [ ] `./gradlew :paging:build` - verify extraction
- [ ] `./gradlew build` - full project build
- [ ] Test all platform targets (JVM, Native, JS)

### 11.4 Publishing Verification ⏳ TODO
- [ ] Test `./gradlew publishToMavenLocal` for all modules
- [ ] Verify POM files are correct
- [ ] Verify BOM constraints work
- [ ] Test bundle module transitive dependencies

---

## Phase 12: Cleanup & Release ⏳ NOT STARTED

### 12.1 Delete Legacy Modules ⏳ TODO
- [ ] Delete `store/` directory (monolithic module)
- [ ] Delete `normalization/runtime/` (merged into `:normalization-runtime`)
- [ ] Delete `normalization/ksp/` (moved to root `:normalization-ksp`)
- [ ] Update `settings.gradle.kts` - remove legacy module includes
- [ ] Clean up any dangling references

### 12.2 Final Code Review ⏳ TODO
- [ ] Review all public APIs for consistency
- [ ] Ensure all generics use new naming (Key, Domain, Entity, etc.)
- [ ] Verify all KDoc is complete and accurate
- [ ] Check for any remaining TODOs in code
- [ ] Run code formatter on all modules

### 12.3 Release Preparation ⏳ TODO
- [ ] Update `CHANGELOG.md` with 1.0.0 release notes
- [ ] Create GitHub release notes
- [ ] Tag version 1.0.0
- [ ] Publish to Maven Central
- [ ] Announce release

---

## Risk Register

### High Risk Items
1. **Circular Dependencies** - Risk that modules inadvertently depend on each other
   - Mitigation: Strict layer hierarchy, use `api()` vs `implementation()` correctly

2. **Breaking Changes for Early Adopters** - If anyone is using unreleased versions
   - Mitigation: Check if any external projects reference GitHub dependency

3. **Test Coverage Gaps** - Tests may break during module splits
   - Mitigation: Run tests frequently during migration

### Medium Risk Items
4. **Build File Errors** - Incorrect dependency declarations
   - Mitigation: Incremental compilation verification

5. **Package Name Conflicts** - New package structure may conflict
   - Mitigation: Use distinct package roots per module

### Low Risk Items
6. **Documentation Staleness** - Docs may lag behind code
   - Mitigation: Update docs in same PRs as code changes

---

## Success Criteria

- [ ] All 17 modules compile independently
- [ ] Zero circular dependencies
- [ ] `:core` module < 6K LOC
- [ ] All public APIs have comprehensive KDoc
- [ ] 100% of existing tests pass
- [ ] Sample apps demonstrate each module
- [ ] Documentation complete for all modules
- [ ] Successfully published to Maven Central at 1.0.0
- [ ] GitHub release with migration guide
- [ ] No remaining references to old `:store` module

---

## Timeline

- **Week 1** (Oct 4-11): Phase 1-3 (Structure + Core + Mutations) ✅ 25% done
- **Week 2** (Oct 11-18): Phase 4-6 (Normalization + Paging + New Modules)
- **Week 3** (Oct 18-25): Phase 7-12 (Bundles + Docs + Testing + Cleanup)

---

## Notes

- No deprecation needed - nothing publicly released yet
- Can make breaking changes freely
- Focus on getting architecture right, not backward compatibility
- Use TypeScript Project References model as inspiration for bundles
- Consider creating `storex-all` mega-bundle for migration convenience

---

**Last Updated**: 2025-10-04 20:00 UTC
**Next Review**: 2025-10-07 (after Phase 2-3 completion)