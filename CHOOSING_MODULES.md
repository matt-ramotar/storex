# Choosing StoreX Modules

**A decision guide for selecting the right modules for your project**

StoreX v1.0 provides 17 focused modules. This guide helps you decide which modules you need based on your use case, platform, and requirements.

**Last Updated**: 2025-10-05

---

## Quick Decision Tree

```
START
│
├─ Are you building a GraphQL application?
│  └─ YES → Use `:bundle-graphql` ✅ Done!
│  └─ NO ↓
│
├─ Are you building a REST API application?
│  └─ YES → Use `:bundle-rest` ✅ Done!
│  └─ NO ↓
│
├─ Are you building an Android app with Compose?
│  └─ YES → Use `:bundle-android` ✅ Done!
│  └─ NO ↓
│
└─ Build custom module set → Continue reading ↓
```

---

## Common Use Cases

### 1. GraphQL Mobile/Web App

**Scenario**: Mobile app consuming GraphQL API with normalized caching

**Recommended modules:**
```kotlin
dependencies {
    // Option A: Use bundle (easiest)
    implementation("dev.mattramotar.storex:bundle-graphql:1.0.0")

    // Option B: Individual modules
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")
    implementation("dev.mattramotar.storex:normalization-runtime:1.0.0")
    kapt("dev.mattramotar.storex:normalization-ksp:1.0.0")  // For @Normalizable
    implementation("dev.mattramotar.storex:interceptors:1.0.0")  // For auth
}
```

**Why these modules:**
- `:core` - Base caching and reactive updates
- `:mutations` - Create/update/delete operations
- `:normalization:runtime` - Automatic graph normalization (like Apollo Client)
- `:normalization:ksp` - Code generation for normalizers
- `:interceptors` - Auth tokens, logging, metrics

---

### 2. REST API Application

**Scenario**: App consuming traditional REST APIs with JSON

**Recommended modules:**
```kotlin
dependencies {
    // Option A: Use bundle
    implementation("dev.mattramotar.storex:bundle-rest:1.0.0")

    // Option B: Individual modules
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")
    implementation("dev.mattramotar.storex:resilience:1.0.0")  // Retry, circuit breaking
    implementation("dev.mattramotar.storex:serialization-kotlinx:1.0.0")  // JSON
    implementation("dev.mattramotar.storex:ktor-client:1.0.0")  // HTTP client
}
```

**Why these modules:**
- `:core` - Base functionality
- `:mutations` - CRUD operations
- `:resilience` - Retry failed requests, circuit breaking
- `:serialization-kotlinx` - Automatic JSON parsing
- `:ktor-client` - HTTP client integration

---

### 3. Android App with Jetpack Compose

**Scenario**: Android app using Jetpack Compose, Room, and WorkManager

**Recommended modules:**
```kotlin
dependencies {
    // Option A: Use bundle
    implementation("dev.mattramotar.storex:bundle-android:1.0.0")

    // Option B: Individual modules
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")
    implementation("dev.mattramotar.storex:android:1.0.0")  // Room, WorkManager
    implementation("dev.mattramotar.storex:compose:1.0.0")  // Compose helpers
    implementation("dev.mattramotar.storex:paging:1.0.0")  // For lists
}
```

**Why these modules:**
- `:core` - Base functionality
- `:mutations` - Write operations
- `:android` - Lifecycle, Room, WorkManager
- `:compose` - Composable helpers, LazyColumn integration
- `:paging` - Infinite scroll lists

---

### 4. Read-Only Caching Layer

**Scenario**: Simple caching layer with no write operations

**Recommended modules:**
```kotlin
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:resilience:1.0.0")  // Optional: retry
}
```

**Why these modules:**
- `:core` - All you need for read-only caching
- `:resilience` - Optional: retry failed fetches

**NOT needed:**
- `:mutations` ❌ (no writes)
- `:normalization:runtime` ❌ (no graph data)
- `:paging` ❌ (no pagination)

---

### 5. Infinite Scroll Social Feed

**Scenario**: Social media feed with infinite scrolling and mutations

**Recommended modules:**
```kotlin
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")
    implementation("dev.mattramotar.storex:paging:1.0.0")  // Infinite scroll
    implementation("dev.mattramotar.storex:compose:1.0.0")  // For LazyColumn
}
```

**Why these modules:**
- `:core` - Base functionality
- `:mutations` - Like/comment/share actions
- `:paging` - Cursor-based pagination
- `:compose` - LazyColumn integration

---

### 6. Offline-First Note Taking App

**Scenario**: Note taking app with offline-first, sync when online

**Recommended modules:**
```kotlin
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")
    implementation("dev.mattramotar.storex:android:1.0.0")  // WorkManager for sync
}
```

**Why these modules:**
- `:core` - Caching and persistence
- `:mutations` - Create/update/delete notes
- `:android` - WorkManager for background sync when online

---

## Module Selection Matrix

### By Feature Need

| Feature | Required Modules |
|---------|------------------|
| **Read-only caching** | `:core` |
| **CRUD operations** | `:core`, `:mutations` |
| **Graph data (GraphQL)** | `:core`, `:mutations`, `:normalization:runtime` |
| **Pagination** | `:core`, `:paging` |
| **Offline-first** | `:core`, `:mutations`, `:android` (WorkManager) |
| **Auth/Logging/Metrics** | `:core`, `:interceptors` |
| **Retry/Circuit breaking** | `:core`, `:resilience` |

### By Platform

| Platform | Base Modules | Optional Modules |
|----------|-------------|------------------|
| **Android** | `:core`, `:mutations` | `:android`, `:compose`, `:paging` |
| **iOS** | `:core`, `:mutations` | `:paging`, `:normalization:runtime` |
| **JVM Backend** | `:core`, `:mutations` | `:resilience`, `:telemetry` |
| **JavaScript/Web** | `:core`, `:mutations` | `:paging` |

### By Data Source

| Data Source | Recommended Modules |
|-------------|---------------------|
| **GraphQL API** | `:bundle-graphql` or `:core` + `:mutations` + `:normalization:runtime` |
| **REST API** | `:bundle-rest` or `:core` + `:mutations` + `:resilience` |
| **Local-only** | `:core` (no network needed) |
| **Hybrid (local + remote)** | `:core` + `:mutations` + platform modules |

---

## Bundle vs Individual Modules

### When to Use Bundles

✅ **Use bundles when:**
- You're starting a new project
- Your use case matches a bundle (GraphQL, REST, Android)
- You want simplicity over customization
- You don't care about dependency size
- You want all features available

**Pros:**
- ✅ Single dependency
- ✅ All features included
- ✅ Pre-configured
- ✅ No missing dependencies

**Cons:**
- ❌ Larger app size (includes unused modules)
- ❌ Less control over dependencies

### When to Use Individual Modules

✅ **Use individual modules when:**
- You need minimal dependencies
- You want to minimize app size
- You have specific requirements
- You're migrating from another library
- You only need read operations (`:core` only)

**Pros:**
- ✅ Smaller app size
- ✅ Precise control
- ✅ Only pay for what you use
- ✅ Clear dependencies

**Cons:**
- ❌ More complex dependency management
- ❌ Risk of missing required modules
- ❌ More configuration needed

---

## Decision Flow Charts

### "Do I need normalization?"

```
Do you have interconnected entities?
│
├─ YES (User → Posts → Comments → Likes)
│  │
│  └─ Are you using GraphQL?
│     │
│     ├─ YES → ✅ Use `:normalization:runtime`
│     │         (automatic graph normalization)
│     │
│     └─ NO → ⚠️  Consider `:normalization:runtime`
│               (works with REST too, but more setup)
│
└─ NO (independent entities)
   │
   └─ ❌ Don't use `:normalization:runtime`
      (`:core` + `:mutations` is enough)
```

### "Do I need mutations?"

```
Do you need to write data (create/update/delete)?
│
├─ YES → ✅ Use `:mutations`
│
└─ NO (read-only)
   │
   └─ ❌ Don't use `:mutations`
      (`:core` is enough)
```

### "Do I need paging?"

```
Do you have lists with 100+ items?
│
├─ YES → ✅ Use `:paging`
│  │
│  └─ Do you use Jetpack Compose?
│     │
│     ├─ YES → Also use `:compose`
│     │         (LazyColumn integration)
│     │
│     └─ NO → Just `:paging`
│
└─ NO (small lists)
   │
   └─ ❌ Don't use `:paging`
      (load entire list in `:core`)
```

---

## Minimal Setups

### Absolute Minimum (Read-Only)

```kotlin
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
}
```

**Use for:** Simple caching, no writes, no pagination

---

### Common Minimum (Read + Write)

```kotlin
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")
}
```

**Use for:** Basic CRUD app, no special features

---

### Recommended Minimum (Production)

```kotlin
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")
    implementation("dev.mattramotar.storex:resilience:1.0.0")  // Retry, circuit breaking
}
```

**Use for:** Production apps with network resilience

---

## Advanced Configurations

### GraphQL with Custom Normalizers

```kotlin
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")
    implementation("dev.mattramotar.storex:normalization-runtime:1.0.0")

    // Option A: Use KSP for code generation
    kapt("dev.mattramotar.storex:normalization-ksp:1.0.0")

    // Option B: Write normalizers manually (no KSP)
    // (no additional dependencies)
}
```

### Production Monitoring Setup

```kotlin
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")
    implementation("dev.mattramotar.storex:telemetry:1.0.0")  // Metrics
    implementation("dev.mattramotar.storex:interceptors:1.0.0")  // Logging
}
```

### Testing Setup

```kotlin
dependencies {
    // Production
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")

    // Testing
    testImplementation("dev.mattramotar.storex:testing:1.0.0")
    testImplementation("app.cash.turbine:turbine:1.0.0")  // Flow testing
}
```

---

## Migration Paths

### From Monolithic `:store`

**Before** (v0.x):
```kotlin
dependencies {
    implementation("dev.mattramotar.storex:store:0.9.0")
}
```

**After** (v1.0):
```kotlin
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")  // If you use MutableStore
}
```

### From Apollo Android

```kotlin
dependencies {
    // Replace Apollo
    // implementation("com.apollographql.apollo3:apollo-runtime:3.x.x")

    // With StoreX GraphQL bundle
    implementation("dev.mattramotar.storex:bundle-graphql:1.0.0")
}
```

### From Paging3 (Android)

```kotlin
dependencies {
    // Keep Paging3 for UI
    implementation("androidx.paging:paging-runtime:3.x.x")
    implementation("androidx.paging:paging-compose:3.x.x")

    // Add StoreX for data layer
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:paging:1.0.0")
}
```

---

## Performance & Size Considerations

### Dependency Sizes (Approximate)

| Configuration | APK Size Impact | Functionality |
|--------------|-----------------|---------------|
| `:core` only | +200 KB | Read-only caching |
| `:core` + `:mutations` | +300 KB | Full CRUD |
| `:bundle-graphql` | +500 KB | Complete GraphQL support |
| `:bundle-rest` | +400 KB | Complete REST support |
| `:bundle-android` | +450 KB | Complete Android support |

**Recommendation**: Start with bundles, optimize later if needed.

---

## FAQ

### Q: Can I mix bundles and individual modules?

**A:** Not recommended. Choose either bundles OR individual modules, not both.

❌ **Bad:**
```kotlin
dependencies {
    implementation("dev.mattramotar.storex:bundle-graphql:1.0.0")
    implementation("dev.mattramotar.storex:paging:1.0.0")  // Redundant!
}
```

✅ **Good:**
```kotlin
dependencies {
    implementation("dev.mattramotar.storex:bundle-graphql:1.0.0")
    // `:paging` already included via `:bundle-graphql`
}
```

---

### Q: Do I need `:resilience` with `:core`?

**A:** Optional but recommended for production.

- ✅ Use `:resilience` if: Network can be unreliable
- ❌ Skip if: You handle retries yourself

---

### Q: When should I use `:normalization:ksp`?

**A:** Only if you want automatic code generation for normalizers.

- ✅ Use if: You have many entities (>10)
- ❌ Skip if: You prefer manual control or have few entities (<5)

---

### Q: Is `:testing` required for tests?

**A:** No, but it makes testing much easier.

- ✅ Use if: You want fake implementations and helpers
- ❌ Skip if: You write your own test doubles

---

## Summary Cheat Sheet

| Scenario | Quick Choice |
|----------|-------------|
| **GraphQL app** | `:bundle-graphql` |
| **REST app** | `:bundle-rest` |
| **Android + Compose** | `:bundle-android` |
| **Read-only cache** | `:core` only |
| **Simple CRUD** | `:core` + `:mutations` |
| **Infinite scroll** | `:core` + `:paging` + `:compose` |
| **Offline-first** | `:core` + `:mutations` + `:android` |

---

## Related Documentation

- [MODULES.md](MODULES.md) - Complete module reference
- [BUNDLE_GUIDE.md](BUNDLE_GUIDE.md) - Detailed bundle guide
- [ARCHITECTURE.md](ARCHITECTURE.md) - Architecture overview
- [MIGRATION.md](MIGRATION.md) - Migration guides

---

**Last Updated**: 2025-10-05
**Version**: 1.0.0
