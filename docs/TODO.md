# StoreX 1.0 - Release Roadmap

**Last Updated**: 2025-10-05
**Overall Progress**: ~92% Complete
**Status**: 🟢 Core migration complete, finalizing for 1.0 release

---

## 📋 Executive Summary

StoreX modular migration is **92% complete**. All code migrated from monolithic `:store` to 16 focused modules. All P0/P1 production issues resolved. Remaining work: samples, testing, code review, and release preparation.

**Total Time to 1.0**: 9-12 hours
**Post-1.0 Enhancements**: 4-8 hours (optional)

---

## 🔴 Pre-1.0 Critical Path (7 tasks)

Must complete before 1.0.0 release.

### 1. Sample App Updates (2 hours) 🔴 HIGH PRIORITY

**Goal**: Demonstrate new modular architecture with working examples

**Tasks**:
- [ ] Update `sample/build.gradle.kts` - Replace normalization.runtime dependency with appropriate bundles
- [ ] Update sample code imports - Change to new package structure
- [ ] Create GraphQL sample - Demonstrate `:bundle-graphql` usage
- [ ] Create REST API sample - Demonstrate `:bundle-rest` usage
- [ ] Create Android sample - Demonstrate `:bundle-android` usage
- [ ] Add README to each sample - Explain bundle selection and usage

**Why Critical**: Users need working examples to understand the new architecture

**Files to Update**:
- `sample/build.gradle.kts`
- `sample/src/commonMain/kotlin/**/*.kt`

---

### 2. Test Migration (2-3 hours) 🔴 HIGH PRIORITY

**Goal**: Move tests from deleted `:store` module to appropriate new modules

**Current State**: Tests exist in deleted `store/src/commonTest/` but haven't been migrated

**Tasks**:
- [ ] Identify all tests from archived store module
  - Concurrency tests (StreamCancellationTest, MemoryCacheThreadSafetyTest, SingleFlightTest, GraphCompositionErrorHandlingTest)
  - Unit tests for core functionality
  - Integration tests
- [ ] Move tests to appropriate modules:
  - [ ] Core tests → `:core/src/commonTest/`
  - [ ] Mutation tests → `:mutations/src/commonTest/`
  - [ ] Normalization tests → `:normalization:runtime/src/commonTest/`
  - [ ] Paging tests → `:paging/src/commonTest/`
- [ ] Update test imports to new package structure
- [ ] Run full test suite: `./gradlew test`
- [ ] Fix any compilation or test failures
- [ ] Achieve >80% code coverage per module (run `./gradlew koverVerify`)

**Why Critical**: Cannot ship without comprehensive test coverage

---

### 3. Consolidate Policy Classes (1 hour) 🟡 MEDIUM PRIORITY

**Goal**: Simplify mutation policy API before 1.0 release

**Current State**: Multiple policy classes (CreatePolicy, UpdatePolicy, DeletePolicy, UpsertPolicy, ReplacePolicy) with overlapping configuration

**Tasks**:
- [ ] Analyze policy class overlap
- [ ] Design unified MutationPolicy interface
- [ ] Update mutation methods to use unified policy
- [ ] Update DSL builders
- [ ] Update documentation and examples

**Why Important**: API breaking change - must do before 1.0 or never

**Complexity**: Medium - requires careful API design

**Files to Update**:
- `mutations/src/commonMain/kotlin/dev/mattramotar/storex/mutations/MutationStore.kt`
- `mutations/src/commonMain/kotlin/dev/mattramotar/storex/mutations/dsl/`

---

### 4. Platform Compatibility Testing (2 hours) 🔴 HIGH PRIORITY

**Goal**: Ensure inline classes work correctly across all platforms

**Current State**: Value classes used for StoreNamespace, EntityId but not tested cross-platform

**Tasks**:
- [ ] Write JVM tests for inline class serialization
- [ ] Write JS tests for inline class interop
- [ ] Write Native tests for inline class behavior
- [ ] Test reflection behavior (where applicable)
- [ ] Test type erasure edge cases
- [ ] Document any platform-specific gotchas

**Why Critical**: Multiplatform library must work on all targets

**Files to Test**:
- `core/src/jvmTest/kotlin/` (new)
- `core/src/jsTest/kotlin/` (new)
- `core/src/nativeTest/kotlin/` (new)

---

### 5. Final Code Review (1-2 hours) 🟡 MEDIUM PRIORITY

**Goal**: Polish codebase to production quality

**Tasks**:
- [ ] Review all public APIs for consistency across modules
- [ ] Verify all generics use new naming (Key, Domain, Entity)
- [ ] Verify all KDoc is complete and accurate
- [ ] Search for TODO comments: `git grep -n "TODO"` and resolve
- [ ] Check for unused imports: `./gradlew lintKotlin` (if configured)
- [ ] Run code formatter: `./gradlew spotlessApply` (if configured)
- [ ] Remove any debug logging or print statements
- [ ] Verify version numbers are consistent (1.0.0)

**Why Important**: First impressions matter for 1.0 release

---

### 6. Create CHANGELOG (1 hour) 🔴 HIGH PRIORITY

**Goal**: Document all changes for 1.0.0 release

**Tasks**:
- [ ] Create `CHANGELOG.md` at project root
- [ ] Document new modular architecture
- [ ] List all 16 production modules
- [ ] Explain bundle system
- [ ] Document breaking changes from Store5/Store6 (if any existed)
- [ ] List all P0/P1 fixes from production readiness review:
  - Race condition fixes
  - Thread safety improvements
  - API simplifications
  - Documentation additions
- [ ] Include migration guide summary
- [ ] Add upgrade instructions

**Why Critical**: Users need to understand what changed

**Template Structure**:
```markdown
# Changelog

## [1.0.0] - 2025-10-XX

### 🎉 New Modular Architecture
- Split monolithic :store into 16 focused modules
- Added 3 convenience bundles (graphql, rest, android)
...

### ✨ Features
### 🐛 Bug Fixes
### 📚 Documentation
### ⚠️ Breaking Changes
```

---

### 7. Release Preparation (1 hour) 🔴 HIGH PRIORITY

**Goal**: Prepare for Maven Central publication

**Tasks**:
- [ ] Update root `README.md`:
  - Remove "unreleased" warnings
  - Add installation instructions for all modules
  - Update examples to use 1.0.0
  - Add badge for Maven Central
- [ ] Prepare GitHub release notes:
  - Highlight modular architecture
  - Link to migration guide
  - List key features
  - Thank contributors
- [ ] Test local Maven publishing: `./gradlew publishToMavenLocal`
- [ ] Verify all 16 modules publish correctly
- [ ] Check POM files for correct dependencies
- [ ] Verify BOM constraints work
- [ ] Tag version 1.0.0: `git tag -a v1.0.0 -m "StoreX 1.0.0 - Modular Architecture Release"`
- [ ] Publish to Maven Central: `./gradlew publish --no-daemon`
- [ ] Create GitHub release with tag
- [ ] Announce release (optional - Twitter, Reddit, etc.)

**Why Critical**: This is the actual release!

---

## 🟢 Post-1.0 Enhancements (2 tasks)

Can be done after 1.0.0 release without breaking changes.

### 8. Incremental Recomposition (4-6 hours) 🟢 ENHANCEMENT

**Goal**: Optimize Compose recomposition for better UI performance

**Current State**: `:compose` module exists but only has placeholder implementation

**Tasks**:
- [ ] Implement `@Stable` wrappers for StoreResult
- [ ] Create `collectAsState()` extension for Store
- [ ] Implement structural equality for cache invalidation
- [ ] Add Compose-specific caching layer
- [ ] Write comprehensive tests
- [ ] Update `:bundle-android` to include optimization
- [ ] Document best practices

**Why Enhancement**: Performance optimization, not core functionality

**Priority**: Post-1.0 (can ship in 1.1.0)

**Complexity**: High - requires deep Compose knowledge

---

### 9. Verify Pagination Implementation (2 hours) 🟢 VERIFICATION

**Goal**: Check if existing `:paging` module satisfies TASK-019 requirements

**Current State**: `:paging` module exists with PageStore implementation

**Tasks**:
- [ ] Review `:paging` module implementation
- [ ] Check if it supports prefetch/cursor-based pagination
- [ ] Compare against original TASK-019 requirements
- [ ] Test with real-world pagination use case
- [ ] If incomplete:
  - [ ] Implement missing features
  - [ ] Add prefetch strategies
  - [ ] Add cursor/offset pagination variants
- [ ] Update documentation with examples

**Why Low Priority**: Module may already be complete

**Outcome**: Either "already done" or "needs enhancement for 1.1.0"

---

## 📊 Module Status Reference

| Module | Status | Version | Tests | Docs | Notes |
|--------|--------|---------|-------|------|-------|
| `:core` | ✅ Production | 1.0.0 | ⚠️ Need migration | ✅ Complete | Read-only store, 14 files |
| `:mutations` | ✅ Production | 1.0.0 | ⚠️ Need migration | ✅ Complete | Write operations, 9 files |
| `:paging` | ✅ Production | 1.0.0 | ⚠️ Need check | ✅ Complete | Pagination support |
| `:normalization:runtime` | ✅ Production | 1.0.0 | ⚠️ Need migration | ✅ Complete | Graph normalization, 17 files |
| `:normalization:ksp` | ✅ Production | 1.0.0 | ✅ Tests exist | ✅ Complete | Code generation |
| `:resilience` | ✅ Production | 1.0.0 | ✅ All pass | ✅ Complete | Retry/fallback, fully tested |
| `:interceptors` | ⚠️ Placeholder | 1.0.0 | N/A | ✅ Complete | Compiles, post-1.0 impl |
| `:serialization-kotlinx` | ⚠️ Placeholder | 1.0.0 | N/A | ✅ Complete | Compiles, post-1.0 impl |
| `:testing` | ⚠️ Placeholder | 1.0.0 | N/A | ✅ Complete | Compiles, post-1.0 impl |
| `:telemetry` | ⚠️ Placeholder | 1.0.0 | N/A | ✅ Complete | Compiles, post-1.0 impl |
| `:android` | ⚠️ Placeholder | 1.0.0 | N/A | ✅ Complete | Compiles, post-1.0 impl |
| `:compose` | ⚠️ Placeholder | 1.0.0 | N/A | ✅ Complete | Compiles, task #8 post-1.0 |
| `:ktor-client` | ⚠️ Placeholder | 1.0.0 | N/A | ✅ Complete | Compiles, post-1.0 impl |
| `:bundle-graphql` | ✅ Production | 1.0.0 | ✅ Deps only | ✅ Complete | Meta-package |
| `:bundle-rest` | ✅ Production | 1.0.0 | ✅ Deps only | ✅ Complete | Meta-package |
| `:bundle-android` | ✅ Production | 1.0.0 | ✅ Deps only | ✅ Complete | Meta-package |
| `:bom` | ✅ Configured | 1.0.0 | N/A | ✅ Complete | Bill of materials |

**Legend**:
- ✅ Ready for 1.0
- ⚠️ Needs work before 1.0
- 🟢 Post-1.0 enhancement

---

## 🎯 Success Criteria for 1.0 Release

### Code & Architecture ✅
- [x] All 16 production modules compile independently
- [x] Zero circular dependencies
- [x] `:core` module < 6K LOC (actual: ~494 lines)
- [x] Clean separation: reads in `:core`, writes in `:mutations`
- [x] Legacy `:store` module deleted

### Documentation ✅
- [x] All modules have comprehensive README files
- [x] Architecture documentation complete (400+ pages)
- [x] Migration guides written
- [x] Bundle usage guides complete

### Testing ⚠️
- [x] All modules compile on all platforms
- [ ] Tests migrated from old `:store` module (Task #2)
- [ ] Platform-specific tests written (Task #4)
- [ ] >80% code coverage per module
- [ ] Full test suite passes

### Quality 🟡
- [x] All P0 critical issues resolved
- [x] All P1 high priority issues resolved
- [ ] Final code review complete (Task #5)
- [ ] Policy consolidation (Task #3 - if time permits)

### Release ⏳
- [ ] CHANGELOG.md created (Task #6)
- [ ] Samples updated (Task #1)
- [ ] Version 1.0.0 tagged
- [ ] Published to Maven Central (Task #7)
- [ ] GitHub release created

---

## 🚀 Quick Commands

```bash
# Full build (all modules, all platforms)
./gradlew build

# Run all tests
./gradlew test

# Check code coverage
./gradlew koverHtmlReport
open build/reports/kover/html/index.html

# Publish to local Maven for testing
./gradlew publishToMavenLocal

# Clean build
./gradlew clean build

# Format code (if spotless configured)
./gradlew spotlessApply

# Find TODOs
git grep -n "TODO"

# Check for unused dependencies (if configured)
./gradlew buildHealth
```

---

## 📝 Prioritization Guide

**Must Do Before 1.0** (9-12 hours):
1. 🔴 Test Migration (2-3h) - **START HERE**
2. 🔴 Platform Testing (2h)
3. 🔴 Sample Apps (2h)
4. 🔴 CHANGELOG (1h)
5. 🔴 Release Prep (1h)
6. 🟡 Code Review (1-2h)
7. 🟡 Policy Consolidation (1h) - if time permits

**Nice to Have** (Post-1.0):
8. 🟢 Incremental Recomposition (4-6h) - Ship in 1.1.0
9. 🟢 Verify Pagination (2h) - May already be complete

---

## 📚 Related Documentation

- **Architecture**: `docs/ARCHITECTURE.md`
- **Migration Guide**: `docs/MIGRATION.md`
- **Performance**: `docs/PERFORMANCE.md`
- **Threading**: `docs/THREADING.md`
- **Module Selection**: `docs/CHOOSING_MODULES.md`
- **Bundle Guide**: `docs/BUNDLE_GUIDE.md`

---

## 📦 Archive References

Completed work archived in:
- **Migration Tracking**: `docs/archive/migration-2025-10/`
- **Production Readiness**: `docs/archive/production-readiness-2025-10/`

---

**Ready to ship**: Complete tasks 1-7 above and StoreX 1.0.0 is ready for the world! 🎉
