# StoreX Bundle Guide

**When to use bundles vs individual modules**

StoreX provides three pre-configured bundles (`:bundle-graphql`, `:bundle-rest`, `:bundle-android`) that aggregate common module combinations. This guide helps you decide when to use bundles vs individual modules.

**Last Updated**: 2025-10-05

---

## Table of Contents

1. [What are Bundles?](#what-are-bundles)
2. [Available Bundles](#available-bundles)
3. [Bundles vs Individual Modules](#bundles-vs-individual-modules)
4. [When to Use Bundles](#when-to-use-bundles)
5. [When to Use Individual Modules](#when-to-use-individual-modules)
6. [Customizing Bundles](#customizing-bundles)
7. [Creating Custom Bundles](#creating-custom-bundles)

---

## What are Bundles?

**Bundles** are meta-packages that aggregate multiple StoreX modules into a single dependency. Think of them as pre-configured starter kits for common use cases.

### Bundle Characteristics

- **Single dependency**: Add one line to `build.gradle.kts`
- **Pre-configured**: Modules work together out-of-the-box
- **Transitive dependencies**: All included modules are `api` dependencies
- **Version-aligned**: Uses BOM for version management
- **Convenience over optimization**: Trades some app size for developer experience

---

## Available Bundles

### 1. `:bundle-graphql`

**For GraphQL applications with normalized caching**

**Includes:**
- `:core` - Read-only store, caching, persistence
- `:mutations` - CRUD operations with optimistic updates
- `:normalization:runtime` - Graph normalization and composition
- `:interceptors` - Request/response interception

**Perfect for:**
- Mobile apps consuming GraphQL APIs
- Apps needing normalized caching (like Apollo Client)
- Applications with complex entity relationships
- Offline-first GraphQL clients

**Installation:**
```kotlin
dependencies {
    implementation("dev.mattramotar.storex:bundle-graphql:1.0.0")
}
```

**See:** [bundle-graphql/README.md](bundle-graphql/README.md)

---

### 2. `:bundle-rest`

**For REST API applications**

**Includes:**
- `:core` - Read-only store, caching, persistence
- `:mutations` - CRUD operations
- `:resilience` - Retry, circuit breaking, rate limiting
- `:serialization-kotlinx` - JSON/ProtoBuf serialization

**Perfect for:**
- Apps consuming traditional REST APIs
- JSON-based APIs
- Applications needing retry logic
- Offline-first REST clients

**Installation:**
```kotlin
dependencies {
    implementation("dev.mattramotar.storex:bundle-rest:1.0.0")
}
```

**See:** [bundle-rest/README.md](bundle-rest/README.md)

---

### 3. `:bundle-android`

**For Android applications with Jetpack Compose**

**Includes:**
- `:core` - Read-only store, caching, persistence
- `:mutations` - CRUD operations
- `:android` - Lifecycle, Room, WorkManager, DataStore
- `:compose` - Jetpack Compose helpers, LazyColumn integration

**Perfect for:**
- Android apps using Jetpack Compose
- Apps needing Room database integration
- Offline-first Android apps with WorkManager sync
- Modern Android development

**Installation:**
```kotlin
dependencies {
    implementation("dev.mattramotar.storex:bundle-android:1.0.0")
}
```

**See:** [bundle-android/README.md](bundle-android/README.md)

---

## Bundles vs Individual Modules

### Comparison Table

| Aspect | Bundles | Individual Modules |
|--------|---------|-------------------|
| **Setup Complexity** | ✅ Simple (1 dependency) | ⚠️ Moderate (multiple dependencies) |
| **App Size** | ⚠️ Larger (includes unused modules) | ✅ Smaller (only what you need) |
| **Flexibility** | ⚠️ Fixed module set | ✅ Complete control |
| **Version Management** | ✅ Automatic (BOM) | ⚠️ Manual |
| **Risk of Missing Deps** | ✅ None (all included) | ⚠️ Possible |
| **Tree-Shaking** | ❌ Limited | ✅ Full |
| **Configuration** | ✅ Pre-configured | ⚠️ Manual |
| **Best For** | Prototypes, MVPs, standard use cases | Production optimization, custom needs |

---

## When to Use Bundles

### ✅ Use Bundles When:

1. **Starting a new project**
   - Don't know exact requirements yet
   - Want to get started quickly
   - Can optimize later

2. **Your use case matches a bundle**
   - Building a GraphQL app → `:bundle-graphql`
   - Building a REST app → `:bundle-rest`
   - Building an Android app → `:bundle-android`

3. **Simplicity over optimization**
   - Prefer single dependency
   - Don't care about 100-200 KB app size increase
   - Want minimal configuration

4. **Team productivity**
   - Junior developers on team
   - Want consistent setup across team
   - Reduce dependency management overhead

5. **Prototyping/MVP**
   - Moving fast
   - Features may change
   - Can refine later

### Example Scenarios

**Scenario 1: Startup MVP**
```kotlin
// Perfect for early-stage startup
dependencies {
    implementation("dev.mattramotar.storex:bundle-graphql:1.0.0")
}
// Benefits: Ship fast, iterate quickly, optimize later
```

**Scenario 2: Internal Tool**
```kotlin
// Perfect for internal tools where size doesn't matter
dependencies {
    implementation("dev.mattramotar.storex:bundle-rest:1.0.0")
}
// Benefits: Simple, maintainable, all features available
```

---

## When to Use Individual Modules

### ✅ Use Individual Modules When:

1. **App size matters**
   - Mobile app with size constraints
   - Targeting emerging markets (slow networks)
   - Every KB counts

2. **Custom requirements**
   - Need specific module combination
   - Don't fit bundle use cases
   - Want precise control

3. **Read-only caching**
   - Only need `:core`
   - No writes needed
   - Bundles include unnecessary modules

4. **Migrating from another library**
   - Incremental migration
   - Replacing specific functionality
   - Need exact module mapping

5. **Performance-critical**
   - Startup time matters
   - Method count limits (Android)
   - Minimal dependencies preferred

### Example Scenarios

**Scenario 1: Read-Only Cache**
```kotlin
// Minimal setup for read-only caching
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
}
// Benefits: Minimal size, no unnecessary deps
```

**Scenario 2: Custom GraphQL Setup**
```kotlin
// Custom GraphQL without normalization
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")
    implementation("dev.mattramotar.storex:ktor-client:1.0.0")
    // Skip :normalization:runtime (not needed for simple queries)
}
// Benefits: Smaller than bundle, fits exact needs
```

**Scenario 3: iOS App**
```kotlin
// iOS app doesn't need Android modules
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")
    implementation("dev.mattramotar.storex:paging:1.0.0")
    // No :android, :compose (not available on iOS)
}
// Benefits: Platform-specific optimization
```

---

## Customizing Bundles

### Excluding Modules from Bundles

You **cannot** exclude specific modules from bundles. Bundles are all-or-nothing.

❌ **This doesn't work:**
```kotlin
dependencies {
    implementation("dev.mattramotar.storex:bundle-graphql:1.0.0") {
        exclude(module = "normalization-runtime")  // ❌ Won't reduce app size
    }
}
```

✅ **Instead, use individual modules:**
```kotlin
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")
    implementation("dev.mattramotar.storex:interceptors:1.0.0")
    // Skip :normalization:runtime
}
```

### Adding Modules to Bundles

✅ **You can add** modules to bundles:

```kotlin
dependencies {
    implementation("dev.mattramotar.storex:bundle-graphql:1.0.0")
    implementation("dev.mattramotar.storex:paging:1.0.0")  // ✅ Add paging
    implementation("dev.mattramotar.storex:telemetry:1.0.0")  // ✅ Add monitoring
}
```

---

## Creating Custom Bundles

If none of the official bundles fit your needs, create a custom bundle module in your project.

### Custom Bundle Example

**1. Create a new module:**
```
my-app/
├── app/
├── data/
└── storex-bundle/  ← Custom bundle
    └── build.gradle.kts
```

**2. Configure the bundle:**
```kotlin
// storex-bundle/build.gradle.kts
plugins {
    kotlin("jvm")
}

dependencies {
    // Include exactly what you need
    api("dev.mattramotar.storex:core:1.0.0")
    api("dev.mattramotar.storex:mutations:1.0.0")
    api("dev.mattramotar.storex:paging:1.0.0")
    api("dev.mattramotar.storex:resilience:1.0.0")
    // Skip normalization, interceptors, etc.
}
```

**3. Use your custom bundle:**
```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":storex-bundle"))
}
```

### Benefits of Custom Bundles

- ✅ Reuse across multiple apps
- ✅ Consistent module selection
- ✅ Single dependency in app module
- ✅ Easier to update (change one place)

---

## Decision Matrix

### Choose Your Path

```
How many StoreX modules do you need?

┌─ 1-2 modules
│  └─> Use individual modules ✅
│
├─ 3-4 modules that match a bundle
│  └─> Use bundle ✅
│
├─ 3-4 modules that DON'T match a bundle
│  └─> Use individual modules ✅
│
├─ 5+ modules
│  └─> Create custom bundle ✅
│
└─ Don't know yet
   └─> Start with bundle, optimize later ✅
```

---

## Bundle Breakdown

### `:bundle-graphql` Deep Dive

**What's included:**

| Module | Purpose | Can Skip If... |
|--------|---------|----------------|
| `:core` | Base functionality | ❌ Never |
| `:mutations` | Write operations | You only read data |
| `:normalization:runtime` | Graph normalization | Simple non-relational queries |
| `:interceptors` | Auth, logging | You handle this elsewhere |

**Typical app size impact:** +500 KB

**Alternative (minimal GraphQL):**
```kotlin
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")
}
// Size impact: ~300 KB (40% smaller)
// Trade-off: No normalization (duplicate data in cache)
```

---

### `:bundle-rest` Deep Dive

**What's included:**

| Module | Purpose | Can Skip If... |
|--------|---------|----------------|
| `:core` | Base functionality | ❌ Never |
| `:mutations` | Write operations | You only read data |
| `:resilience` | Retry, circuit breaking | Network is reliable (unlikely!) |
| `:serialization-kotlinx` | JSON parsing | You parse manually |

**Typical app size impact:** +400 KB

**Alternative (minimal REST):**
```kotlin
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")
}
// Size impact: ~300 KB (25% smaller)
// Trade-off: No automatic retry, manual JSON parsing
```

---

### `:bundle-android` Deep Dive

**What's included:**

| Module | Purpose | Can Skip If... |
|--------|---------|----------------|
| `:core` | Base functionality | ❌ Never |
| `:mutations` | Write operations | You only read data |
| `:android` | Room, WorkManager, Lifecycle | You don't use AndroidX |
| `:compose` | Compose helpers | You use XML views |

**Typical app size impact:** +450 KB

**Alternative (minimal Android):**
```kotlin
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")
}
// Size impact: ~300 KB (33% smaller)
// Trade-off: Manual Room/Compose integration
```

---

## Migration Strategies

### From Bundle → Individual Modules

**When:** App size becomes a concern

**Strategy:**
1. Analyze which modules you actually use
2. Replace bundle with individual modules
3. Remove unused imports
4. Test thoroughly

**Example:**
```kotlin
// Before
dependencies {
    implementation("dev.mattramotar.storex:bundle-graphql:1.0.0")
}

// After (customized)
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")
    // Removed :normalization:runtime (not using graph features)
    // Removed :interceptors (using OkHttp interceptors instead)
}
```

---

### From Individual Modules → Bundle

**When:** Managing dependencies becomes tedious

**Strategy:**
1. Identify bundle that covers your modules
2. Replace individual deps with bundle
3. Add any extra modules needed
4. Simplify configuration

**Example:**
```kotlin
// Before
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:mutations:1.0.0")
    implementation("dev.mattramotar.storex:normalization-runtime:1.0.0")
    implementation("dev.mattramotar.storex:interceptors:1.0.0")
}

// After (simplified)
dependencies {
    implementation("dev.mattramotar.storex:bundle-graphql:1.0.0")
    // All above modules included!
}
```

---

## FAQ

### Q: Can I use multiple bundles?

**A:** Technically yes, but **not recommended**.

❌ **Bad:**
```kotlin
dependencies {
    implementation("dev.mattramotar.storex:bundle-graphql:1.0.0")
    implementation("dev.mattramotar.storex:bundle-rest:1.0.0")  // Redundant!
}
```

Bundles overlap significantly. Use individual modules instead.

---

### Q: Does using a bundle increase method count?

**A:** Yes, but usually not significantly.

- `:bundle-graphql`: ~2000 methods
- `:bundle-rest`: ~1500 methods
- `:bundle-android`: ~1800 methods

For comparison, a typical Android app has 64K method limit (or unlimited with multidex).

---

### Q: Will ProGuard/R8 remove unused modules from bundles?

**A:** Partially. R8 can remove unused **code**, but not entire **modules**.

If you include `:bundle-graphql` but don't use normalization:
- ✅ Unused classes/methods removed by R8
- ❌ Module still referenced (some overhead remains)

**Recommendation:** If size matters, use individual modules.

---

### Q: Can I publish my own bundle to Maven?

**A:** Yes! Create a bundle module and publish it.

```kotlin
// my-bundle/build.gradle.kts
plugins {
    kotlin("jvm")
    `maven-publish`
}

dependencies {
    api("dev.mattramotar.storex:core:1.0.0")
    api("dev.mattramotar.storex:mutations:1.0.0")
    // Your custom set
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "com.mycompany"
            artifactId = "storex-bundle-custom"
            version = "1.0.0"
        }
    }
}
```

---

## Summary

### Quick Reference

| Situation | Recommendation |
|-----------|---------------|
| **New GraphQL project** | `:bundle-graphql` |
| **New REST project** | `:bundle-rest` |
| **New Android project** | `:bundle-android` |
| **Read-only cache** | `:core` only |
| **App size critical** | Individual modules |
| **Custom needs** | Individual modules or custom bundle |
| **Prototype/MVP** | Bundle |
| **Production (known reqs)** | Individual modules |

---

## Related Documentation

- [CHOOSING_MODULES.md](CHOOSING_MODULES.md) - Module selection guide
- [MODULES.md](MODULES.md) - Complete module reference
- [ARCHITECTURE.md](ARCHITECTURE.md) - Architecture overview

---

**Last Updated**: 2025-10-05
**Version**: 1.0.0
