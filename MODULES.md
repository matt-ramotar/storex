# StoreX Modules Reference

**Complete reference for all StoreX modules**

StoreX v1.0 is organized into 17 focused modules, each providing specific functionality. This document provides a comprehensive reference for all modules, their dependencies, platform support, and API stability.

**Last Updated**: 2025-10-05
**Version**: 1.0.0

---

## Table of Contents

1. [Module Architecture](#module-architecture)
2. [Module Catalog](#module-catalog)
3. [Dependency Graph](#dependency-graph)
4. [Platform Support Matrix](#platform-support-matrix)
5. [API Stability Guarantees](#api-stability-guarantees)
6. [Version Compatibility](#version-compatibility)

---

## Module Architecture

StoreX follows a **layered module architecture** to ensure clean separation of concerns and minimal dependencies:

```
Layer 6: Convenience (Meta-Packages)
├── bundle-graphql (GraphQL bundle)
├── bundle-rest (REST bundle)
├── bundle-android (Android bundle)
└── bom (Bill of Materials)

Layer 5: Development & Observability
├── testing (Test utilities)
└── telemetry (Metrics & monitoring)

Layer 4: Integrations & Extensions
├── interceptors (Request/response interception)
├── serialization-kotlinx (Kotlinx Serialization)
├── android (Android platform)
├── compose (Jetpack Compose)
└── ktor-client (Ktor HTTP client)

Layer 3: Advanced Features
├── normalization:runtime (Graph normalization)
├── normalization:ksp (Code generation)
└── paging (Bidirectional pagination)

Layer 2: Write Operations
└── mutations (CRUD operations)

Layer 1: Foundation (Zero Dependencies)
├── core (Read-only store)
└── resilience (Retry, circuit breaking)
```

---

## Module Catalog

### Layer 1: Foundation

#### `:core`
**Read-only store with multi-tier caching**

- **Purpose**: Foundation module providing reactive caching and data synchronization
- **Key Components**:
  - `Store<Key, Domain>` - Read-only store interface
  - `SourceOfTruth` - Persistent storage interface
  - `Fetcher` - Network data fetching
  - `Converter` - Type conversions
  - `MemoryCache` - LRU in-memory caching
- **Dependencies**: kotlinx-coroutines, kotlinx-datetime, kotlinx-serialization
- **Platforms**: JVM, JS, Native, iOS, Android
- **Status**: ✅ Production-ready (494 lines, 14 files)
- **Documentation**: [core/README.md](core/README.md)

#### `:resilience`
**Retry policies, circuit breaking, and rate limiting**

- **Purpose**: Resilience patterns for network operations
- **Key Components**:
  - Retry with exponential backoff
  - Circuit breaker
  - Rate limiting
  - Timeout policies
- **Dependencies**: kotlinx-coroutines
- **Platforms**: JVM, JS, Native, iOS, Android
- **Status**: ✅ Production-ready (v1.0.0)
- **Documentation**: [resilience/README.md](resilience/README.md)

---

### Layer 2: Write Operations

#### `:mutations`
**CRUD operations with optimistic updates**

- **Purpose**: Add write capabilities (create, update, delete, upsert, replace)
- **Key Components**:
  - `MutationStore<Key, Domain, Patch, Draft>` - Extended store interface
  - `UpdatePolicy`, `CreatePolicy`, `DeletePolicy` - Mutation policies
  - Optimistic updates with rollback
  - Provisional keys for server-assigned IDs
- **Dependencies**: `:core`
- **Platforms**: JVM, JS, Native, iOS, Android
- **Status**: ✅ Production-ready (9 files)
- **Documentation**: [mutations/README.md](mutations/README.md)

---

### Layer 3: Advanced Features

#### `:normalization:runtime`
**Graph normalization and composition**

- **Purpose**: Normalized storage for interconnected entities (GraphQL, relational data)
- **Key Components**:
  - `NormalizationBackend` - Normalized entity storage
  - `EntityKey` - Unique entity identification
  - `NormalizedRecord` - Flat entity representation
  - `Shape` - Graph traversal specifications
  - `Normalizer` - Entity extraction
  - `EntityAdapter` - Denormalization logic
- **Dependencies**: `:core`, `:mutations`
- **Platforms**: JVM, JS, Native, iOS, Android
- **Status**: ✅ Production-ready (17 files)
- **Documentation**: [normalization/runtime/README.md](normalization/runtime/README.md)

#### `:normalization:ksp`
**KSP code generation for normalizers**

- **Purpose**: Automatic normalizer/denormalizer generation
- **Key Components**:
  - `@Normalizable` annotation
  - Code generation for entity adapters
- **Dependencies**: `:normalization:runtime`, KSP
- **Platforms**: JVM (code generation), multiplatform (generated code)
- **Status**: ✅ Production-ready
- **Documentation**: [normalization/ksp/README.md](normalization/ksp/README.md)

#### `:paging`
**Bidirectional pagination support**

- **Purpose**: Efficient loading of large datasets with cursor/offset-based pagination
- **Key Components**:
  - `PageStore<Key, Item>` - Paginated store interface
  - `Page<Item>` - Page container with metadata
  - `PageToken` - Navigation cursors
  - `PagingConfig` - Configuration (page size, prefetch)
  - `LoadState` - Loading states
- **Dependencies**: `:core`
- **Platforms**: JVM, JS, Native, iOS, Android
- **Status**: ✅ Production-ready (2 files)
- **Documentation**: [paging/README.md](paging/README.md)

---

### Layer 4: Integrations & Extensions

#### `:interceptors`
**Request/response interception**

- **Purpose**: Add cross-cutting concerns (auth, logging, metrics, caching)
- **Key Components**:
  - `Interceptor<Key, Value>` - Base interceptor interface
  - `InterceptorChain` - Chain-of-responsibility
  - Built-in interceptors (planned)
- **Dependencies**: `:core`
- **Platforms**: JVM, JS, Native, iOS, Android
- **Status**: 🚧 Placeholder (interface defined)
- **Documentation**: [interceptors/README.md](interceptors/README.md)

#### `:serialization-kotlinx`
**Kotlinx Serialization integration**

- **Purpose**: Automatic JSON/ProtoBuf serialization for converters
- **Key Components**:
  - `SerializationConverter` - Automatic converter
  - Custom serializers for common types
- **Dependencies**: `:core`, kotlinx-serialization
- **Platforms**: JVM, JS, Native, iOS, Android
- **Status**: 🚧 Placeholder
- **Documentation**: [serialization-kotlinx/README.md](serialization-kotlinx/README.md)

#### `:android`
**Android platform integrations**

- **Purpose**: Android-specific helpers (lifecycle, Room, WorkManager)
- **Key Components**:
  - Lifecycle integration (viewModelScope)
  - Room SourceOfTruth helpers
  - WorkManager background sync
  - Connectivity monitoring
  - DataStore integration
- **Dependencies**: `:core`, `:mutations`, AndroidX libraries
- **Platforms**: Android only
- **Status**: 🚧 Placeholder
- **Documentation**: [android/README.md](android/README.md)

#### `:compose`
**Jetpack Compose helpers**

- **Purpose**: Compose integration for reactive UI
- **Key Components**:
  - `collectAsState()` - Convert Flow to State
  - `StoreLoadingState` - Composable loading states
  - Paging composables for LazyColumn
  - Pull-to-refresh integration
- **Dependencies**: `:core`, `:paging`, Jetpack Compose
- **Platforms**: Android (Compose Multiplatform planned)
- **Status**: 🚧 Placeholder
- **Documentation**: [compose/README.md](compose/README.md)

#### `:ktor-client`
**Ktor HTTP client integration**

- **Purpose**: Pre-configured Ktor fetchers with retry, ETags, auth
- **Key Components**:
  - `ktorFetcher` - Ktor-based fetcher builder
  - Automatic retry with exponential backoff
  - ETag support
  - Auth integration
  - Platform-optimized engines
- **Dependencies**: `:core`, Ktor client
- **Platforms**: JVM, JS, Native, iOS, Android
- **Status**: 🚧 Placeholder
- **Documentation**: [ktor-client/README.md](ktor-client/README.md)

---

### Layer 5: Development & Observability

#### `:testing`
**Test utilities and helpers**

- **Purpose**: Comprehensive testing utilities for StoreX
- **Key Components**:
  - `TestStore` - In-memory test implementation
  - `FakeFetcher`, `FakeSourceOfTruth`
  - Assertion helpers
  - Turbine integration for Flow testing
- **Dependencies**: `:core`, `:mutations`, kotlin-test, Turbine
- **Platforms**: JVM, JS, Native, iOS, Android
- **Status**: 🚧 Placeholder
- **Documentation**: [testing/README.md](testing/README.md)

#### `:telemetry`
**Observability and metrics**

- **Purpose**: Metrics collection, distributed tracing, monitoring
- **Key Components**:
  - `Telemetry` - Metrics and tracing API
  - Metrics collection (cache hits, latencies, errors)
  - Distributed tracing integration
  - Export adapters (Prometheus, OpenTelemetry, DataDog)
- **Dependencies**: `:core`, optional backends
- **Platforms**: JVM, JS, Native, iOS, Android
- **Status**: 🚧 Placeholder
- **Documentation**: [telemetry/README.md](telemetry/README.md)

---

### Layer 6: Convenience (Meta-Packages)

#### `:bundle-graphql`
**All-in-one bundle for GraphQL applications**

- **Purpose**: Pre-configured bundle for GraphQL apps
- **Includes**:
  - `:core` - Base functionality
  - `:mutations` - Write operations
  - `:normalization:runtime` - Graph normalization
  - `:interceptors` - Request interception
- **Dependencies**: See included modules
- **Platforms**: JVM, JS, Native, iOS, Android
- **Status**: ✅ Production-ready
- **Documentation**: [bundle-graphql/README.md](bundle-graphql/README.md)

#### `:bundle-rest`
**All-in-one bundle for REST API applications**

- **Purpose**: Pre-configured bundle for REST APIs
- **Includes**:
  - `:core` - Base functionality
  - `:mutations` - Write operations
  - `:resilience` - Retry, circuit breaking
  - `:serialization-kotlinx` - JSON serialization
- **Dependencies**: See included modules
- **Platforms**: JVM, JS, Native, iOS, Android
- **Status**: ✅ Production-ready
- **Documentation**: [bundle-rest/README.md](bundle-rest/README.md)

#### `:bundle-android`
**All-in-one bundle for Android applications**

- **Purpose**: Pre-configured bundle for Android apps
- **Includes**:
  - `:core` - Base functionality
  - `:mutations` - Write operations
  - `:android` - Android integrations
  - `:compose` - Jetpack Compose helpers
- **Dependencies**: See included modules
- **Platforms**: Android only
- **Status**: ✅ Production-ready
- **Documentation**: [bundle-android/README.md](bundle-android/README.md)

#### `:bom`
**Bill of Materials for version alignment**

- **Purpose**: Version management across all modules
- **Usage**: Ensures all StoreX modules use compatible versions
- **Platforms**: All platforms
- **Status**: ✅ Production-ready
- **Documentation**: See Gradle setup

---

## Dependency Graph

### Dependency Tree

```
:bundle-graphql
├── :core
├── :mutations
│   └── :core
├── :normalization:runtime
│   ├── :core
│   └── :mutations
└── :interceptors
    └── :core

:bundle-rest
├── :core
├── :mutations
│   └── :core
├── :resilience
└── :serialization-kotlinx
    └── :core

:bundle-android
├── :core
├── :mutations
│   └── :core
├── :android
│   ├── :core
│   └── :mutations (optional)
└── :compose
    ├── :core
    └── :paging (optional)

:normalization:ksp
└── :normalization:runtime
    ├── :core
    └── :mutations

:paging
└── :core

:testing
├── :core
└── :mutations (optional)

:telemetry
└── :core

:ktor-client
└── :core
```

### Dependency Rules

1. **No Circular Dependencies**: Enforced by layered architecture
2. **API vs Implementation**: Use `api()` for transitive dependencies, `implementation()` for internal
3. **Zero Dependencies**: `:core` and `:resilience` have no internal dependencies
4. **Platform-Specific**: Android modules only depend on other Android modules

---

## Platform Support Matrix

| Module | JVM | JS | Native | iOS | Android | Status |
|--------|-----|-----|--------|-----|---------|--------|
| **:core** | ✅ | ✅ | ✅ | ✅ | ✅ | Production |
| **:resilience** | ✅ | ✅ | ✅ | ✅ | ✅ | Production |
| **:mutations** | ✅ | ✅ | ✅ | ✅ | ✅ | Production |
| **:normalization:runtime** | ✅ | ✅ | ✅ | ✅ | ✅ | Production |
| **:normalization:ksp** | ✅* | ✅* | ✅* | ✅* | ✅* | Production |
| **:paging** | ✅ | ✅ | ✅ | ✅ | ✅ | Production |
| **:interceptors** | ✅ | ✅ | ✅ | ✅ | ✅ | Placeholder |
| **:serialization-kotlinx** | ✅ | ✅ | ✅ | ✅ | ✅ | Placeholder |
| **:testing** | ✅ | ✅ | ✅ | ✅ | ✅ | Placeholder |
| **:telemetry** | ✅ | ✅ | ✅ | ✅ | ✅ | Placeholder |
| **:android** | ❌ | ❌ | ❌ | ❌ | ✅ | Placeholder |
| **:compose** | ❌ | ❌ | ❌ | ❌ | ✅ | Placeholder |
| **:ktor-client** | ✅ | ✅ | ✅ | ✅ | ✅ | Placeholder |
| **:bundle-graphql** | ✅ | ✅ | ✅ | ✅ | ✅ | Production |
| **:bundle-rest** | ✅ | ✅ | ✅ | ✅ | ✅ | Production |
| **:bundle-android** | ❌ | ❌ | ❌ | ❌ | ✅ | Production |
| **:bom** | ✅ | ✅ | ✅ | ✅ | ✅ | Production |

\* KSP runs on JVM for code generation; generated code is multiplatform

---

## API Stability Guarantees

### Production Modules (v1.0.0)

Modules marked as "Production" follow semantic versioning:

- **Major version** (2.0.0): Breaking changes allowed
- **Minor version** (1.1.0): Backward-compatible new features
- **Patch version** (1.0.1): Backward-compatible bug fixes

**Production modules:**
- `:core` ✅
- `:resilience` ✅
- `:mutations` ✅
- `:normalization:runtime` ✅
- `:normalization:ksp` ✅
- `:paging` ✅
- `:bundle-*` ✅

### Placeholder Modules

Modules marked as "Placeholder" have:

- **Interface defined**: Public API is designed
- **Implementation pending**: Full implementation planned for v1.1+
- **Breaking changes possible**: Until v1.1 release

**Placeholder modules:**
- `:interceptors` 🚧
- `:serialization-kotlinx` 🚧
- `:testing` 🚧
- `:telemetry` 🚧
- `:android` 🚧
- `:compose` 🚧
- `:ktor-client` 🚧

---

## Version Compatibility

### Current Version: 1.0.0

All modules are released with synchronized versions:

```kotlin
// Recommended: Use BOM for version alignment
dependencies {
    implementation(platform("dev.mattramotar.storex:bom:1.0.0"))
    implementation("dev.mattramotar.storex:core")  // Version from BOM
    implementation("dev.mattramotar.storex:mutations")  // Version from BOM
}

// Or specify versions explicitly
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")
}
```

### Compatibility Matrix

| StoreX Version | Kotlin | Coroutines | Serialization | Ktor |
|---------------|--------|------------|---------------|------|
| 1.0.0 | 1.9.0+ | 1.7.0+ | 1.5.0+ | 2.3.0+ |

### Migration Path

- **From monolithic `:store`**: See [MIGRATION.md](MIGRATION.md#from-monolithic-store6-to-modular-10)
- **From other libraries**: See [MIGRATION.md](MIGRATION.md)

---

## Module Selection Guide

**Quick links:**
- [CHOOSING_MODULES.md](CHOOSING_MODULES.md) - Which modules do I need?
- [BUNDLE_GUIDE.md](BUNDLE_GUIDE.md) - Bundles vs individual modules

---

## Related Documentation

- [README.md](README.md) - Project overview
- [ARCHITECTURE.md](ARCHITECTURE.md) - Architecture details
- [CHOOSING_MODULES.md](CHOOSING_MODULES.md) - Module selection guide
- [BUNDLE_GUIDE.md](BUNDLE_GUIDE.md) - Bundle usage patterns
- [MIGRATION.md](MIGRATION.md) - Migration guides
- [PERFORMANCE.md](PERFORMANCE.md) - Performance optimization
- [THREADING.md](THREADING.md) - Concurrency model

---

**Last Updated**: 2025-10-05
**Version**: 1.0.0
