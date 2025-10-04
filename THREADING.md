# StoreX Threading & Concurrency

**Last Updated**: 2025-10-04
**Version**: 1.0

## Table of Contents
1. [Overview](#overview)
2. [Concurrency Model](#concurrency-model)
3. [Thread Safety Guarantees](#thread-safety-guarantees)
4. [Structured Concurrency](#structured-concurrency)
5. [Platform-Specific Considerations](#platform-specific-considerations)
6. [Common Patterns](#common-patterns)
7. [Common Pitfalls](#common-pitfalls)
8. [Testing Concurrency](#testing-concurrency)

---

## Overview

StoreX is built on **Kotlin Coroutines** and follows **structured concurrency** principles. All public APIs are designed to be **thread-safe** and **concurrent-safe**, but understanding the threading model is essential for optimal usage.

### Key Principles

1. **Structured Concurrency**: All async operations scoped to parent Job
2. **Cancellation Propagation**: CancellationException always propagates
3. **Flow-Based Reactivity**: Backpressure-aware reactive streams
4. **Fine-Grained Locking**: Per-key mutexes, not global locks
5. **Dispatcher Isolation**: Clear separation between IO, Default, and Main

---

## Concurrency Model

### Dispatcher Strategy

```kotlin
// Default configuration (changed from Dispatchers.Default in TASK-008)
val store = RealStore(
    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),  // Database operations
    // ...
)
```

**Rationale:**
- `Dispatchers.IO`: Database reads/writes, network I/O
- `Dispatchers.Default`: CPU-intensive operations (normalization, hashing)
- `Dispatchers.Main`: UI updates (user's responsibility to switch)

**Before/After (TASK-008):**
```kotlin
// ❌ Before: Wrong dispatcher for DB operations
scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

// ✅ After: Correct dispatcher
scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

### SupervisorJob Usage

```kotlin
CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

**Why SupervisorJob?**
- Child failures don't cancel siblings
- Store remains operational even if individual operations fail
- Enables graceful degradation

**Example:**
```kotlin
// Fetch for key1 fails, but key2 continues
launch { store.get(key1) } // Throws exception
launch { store.get(key2) } // Continues normally
```

---

## Thread Safety Guarantees

### Public API Thread Safety

**All public APIs are thread-safe:**

```kotlin
// ✅ Safe: Concurrent calls from multiple threads
launch(Dispatchers.IO) { store.get(key1) }
launch(Dispatchers.Default) { store.get(key2) }
launch(Dispatchers.Main) { store.stream(key3).collect { } }
```

### Internal Synchronization Mechanisms

#### 1. MemoryCache (TASK-002 Fix)

**Problem:** `ConcurrentModificationException` on `accessOrder.first()`

**Solution:**
```kotlin
class MemoryCacheImpl<Key, Value>(private val maxSize: Int) : MemoryCache<Key, Value> {
    private val cache = mutableMapOf<Key, CacheEntry<Value>>()
    private val accessOrder = LinkedHashSet<Key>()
    private val mutex = Mutex()

    override suspend fun put(key: Key, value: Value): Boolean = mutex.withLock {
        if (cache.size >= maxSize && key !in cache) {
            // ✅ Bounds check before accessing first()
            if (accessOrder.isNotEmpty()) {
                val oldest = accessOrder.first()
                cache.remove(oldest)
                accessOrder.remove(oldest)
            }
        }

        val previous = cache.put(key, CacheEntry(value, Clock.System.now()))
        accessOrder.remove(key)
        accessOrder.add(key)

        return@withLock previous == null  // ✅ Correct return type
    }
}
```

**Key Points:**
- **Mutex-protected**: All operations on `cache` and `accessOrder` are atomic
- **Bounds checking**: `if (accessOrder.isNotEmpty())` prevents crashes
- **LRU ordering**: `LinkedHashSet` maintains insertion order

#### 2. SingleFlight (TASK-003 Fix)

**Problem:** Double-check lock anti-pattern causing race conditions

**Solution:**
```kotlin
internal class SingleFlight<K, R> {
    private val inFlight = hashMapOf<K, CompletableDeferred<R>>()
    private val mutex = Mutex()

    suspend fun launch(scope: CoroutineScope, key: K, block: suspend () -> R): CompletableDeferred<R> {
        // ✅ Atomic get-or-create with mutex
        val deferred = mutex.withLock {
            inFlight.getOrPut(key) {
                CompletableDeferred<R>().also { newDeferred ->
                    scope.launch {
                        try {
                            val result = block()
                            newDeferred.complete(result)
                        } catch (e: CancellationException) {
                            // ✅ Never catch CancellationException
                            newDeferred.cancel(e)
                            throw e
                        } catch (t: Throwable) {
                            newDeferred.completeExceptionally(t)
                        } finally {
                            // ✅ Identity check prevents premature removal
                            mutex.withLock {
                                if (inFlight[key] === newDeferred) {
                                    inFlight.remove(key)
                                }
                            }
                        }
                    }
                }
            }
        }
        return deferred
    }
}
```

**Key Points:**
- **Atomic get-or-create**: `mutex.withLock { inFlight.getOrPut(...) }`
- **Identity check**: `if (inFlight[key] === newDeferred)` prevents race
- **CancellationException handling**: Always rethrow

**Why identity check?**
```kotlin
// Thread 1: Starts long-running operation for key "A"
val deferred1 = singleFlight.launch(scope, "A") { delay(10.seconds) }

// Thread 2: Joins the in-flight operation
val deferred2 = singleFlight.launch(scope, "A") { /* won't execute */ }

// deferred1 completes
// Without identity check: Thread 1 removes key "A" from map

// Thread 3: Starts new operation for key "A"
val deferred3 = singleFlight.launch(scope, "A") { "new operation" }

// Thread 2's finally block executes
// Without identity check: Would incorrectly remove deferred3!
// With identity check: Only removes if inFlight[key] === deferred1
```

#### 3. KeyMutex (TASK-011 Fix)

**Problem:** Unbounded growth causing memory leaks

**Solution:**
```kotlin
internal class KeyMutex<K>(private val maxSize: Int = 1000) {
    private val map = object : LinkedHashMap<K, Mutex>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Mutex>?): Boolean {
            return size > maxSize
        }
    }
    private val mapMutex = Mutex()

    suspend fun forKey(key: K): Mutex = mapMutex.withLock {
        map.getOrPut(key) { Mutex() }
    }
}
```

**Key Points:**
- **LRU eviction**: `LinkedHashMap` with `removeEldestEntry` override
- **Bounded size**: Maximum 1000 mutexes (configurable)
- **Access-order**: `true` in constructor enables LRU

**Usage:**
```kotlin
val keyMutex = KeyMutex<StoreKey>()

// ✅ Per-key locking for fine-grained concurrency
val mutex = keyMutex.forKey(key)
mutex.withLock {
    sot.write(key, value)
}
```

---

## Structured Concurrency

### Scope Management

**Store Scope:**
```kotlin
class RealStore(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val storeScope = scope
}
```

**Lifecycle:**
- Store scope lives as long as the Store instance
- Typically scoped to ViewModel/Presenter lifetime
- Cancel store scope when screen/feature is destroyed

**Best Practice:**
```kotlin
class MyViewModel : ViewModel() {
    private val storeScope = viewModelScope + Dispatchers.IO
    private val store = RealStore(scope = storeScope, ...)

    override fun onCleared() {
        super.onCleared()
        store.close()  // Cancels storeScope
    }
}
```

### Flow Cancellation (TASK-001 Fix)

**Problem:** Background fetches continue after Flow collector cancels

**Solution:**
```kotlin
override fun stream(key: K, freshness: Freshness): Flow<StoreResult<V>> = channelFlow {
    // ...
    when (freshness) {
        Freshness.MustBeFresh -> {
            // Blocking fetch
            if (plan !is FetchPlan.Skip) doFetch()
        }
        else -> {
            // ✅ Launch in channelFlow scope, NOT storeScope
            if (plan !is FetchPlan.Skip) launch { doFetch() }
        }
    }
    // ...
}
```

**Before/After:**
```kotlin
// ❌ Before: Zombie coroutines
else -> if (plan !is FetchPlan.Skip) storeScope.launch { doFetch() }

// ✅ After: Proper cancellation
else -> if (plan !is FetchPlan.Skip) launch { doFetch() }
```

**Why?**
- `launch` inside `channelFlow` creates a child of the flow's scope
- When collector cancels, the flow scope is cancelled
- All child jobs (including background fetch) are cancelled
- No zombie coroutines

**Test Validation:**
```kotlin
@Test
fun stream_whenFlowCancelled_thenBackgroundFetchIsCancelled() = runTest {
    val fetchCancelled = CompletableDeferred<Unit>()

    val fetcher = mock<Fetcher<K, V>>()
    everySuspend { fetcher.fetch(key, any()) } returns flow {
        try {
            delay(10.seconds)
        } catch (e: CancellationException) {
            fetchCancelled.complete(Unit)  // ✅ Fetch was cancelled
            throw e
        }
    }

    val job = launch {
        store.stream(key).test {
            awaitItem()
            cancel()  // Cancel collection
        }
    }

    job.join()
    fetchCancelled.await()  // Verify fetch was cancelled
}
```

### CancellationException Handling (TASK-009)

**Golden Rule:** NEVER catch `CancellationException`

**Before/After:**
```kotlin
// ❌ Before: Swallows cancellation
try {
    // operation
} catch (t: Throwable) {  // Catches CancellationException!
    // log error
}

// ✅ After: Always rethrow
try {
    // operation
} catch (e: CancellationException) {
    throw e  // Always rethrow
} catch (t: Throwable) {
    // log error
}

// ✅ Better: Catch Exception (doesn't catch CancellationException)
try {
    // operation
} catch (e: Exception) {  // CancellationException is not an Exception
    // log error
}
```

**Why?**
- `CancellationException` is how coroutines communicate cancellation
- Catching it breaks structured concurrency
- Parent cannot cancel children if exceptions are swallowed

**Pattern used throughout RealStore:**
```kotlin
try {
    sot.reader(key).firstOrNull()
} catch (e: Exception) {
    // CancellationException propagates automatically
    null
}
```

---

## Platform-Specific Considerations

### JVM

**Threading Model:**
- Efficient thread pools
- Native thread support
- No GIL restrictions

**Best Practices:**
```kotlin
// ✅ Use Dispatchers.IO for blocking calls
withContext(Dispatchers.IO) {
    database.query(...)
}

// ✅ Use Dispatchers.Default for CPU work
withContext(Dispatchers.Default) {
    complexNormalization(...)
}
```

### Native (iOS, Android NDK)

**Thread Affinity Considerations:**
- Some native libraries require specific threads
- Use `newSingleThreadContext()` for thread-affine operations

**Freeze/Mutability:**
```kotlin
// Kotlin/Native memory model
// Objects crossing thread boundaries must be frozen (legacy model)
// New memory model (1.7+) removes this restriction
```

**Best Practices:**
```kotlin
// ✅ Use Dispatchers.Main for UI updates on iOS
withContext(Dispatchers.Main) {
    updateUI(...)
}

// ✅ Avoid capturing mutable state in coroutines
val immutableData = data.toList()  // Copy before launching
launch {
    process(immutableData)
}
```

### JavaScript (Browser/Node)

**Single-Threaded Event Loop:**
- All coroutines run on the event loop
- No true parallelism
- Dispatchers.Default == Dispatchers.Main

**Best Practices:**
```kotlin
// ✅ Yield periodically in long-running operations
repeat(10000) { i ->
    process(i)
    if (i % 100 == 0) yield()  // Let event loop breathe
}

// ✅ Use suspend functions for async browser APIs
suspend fun fetchData(): Response = suspendCancellableCoroutine { cont ->
    fetch(url).then { response ->
        cont.resume(response)
    }.catch { error ->
        cont.resumeWithException(error)
    }
}
```

---

## Common Patterns

### 1. Concurrent Collectors

```kotlin
// ✅ Multiple collectors can observe the same key
launch { store.stream(key).collect { println("Collector 1: $it") } }
launch { store.stream(key).collect { println("Collector 2: $it") } }
launch { store.stream(key).collect { println("Collector 3: $it") } }

// All three will receive the same updates
// SingleFlight ensures only one network fetch
```

### 2. Scoped Collection

```kotlin
class MyScreen : ViewModel() {
    init {
        viewModelScope.launch {
            store.stream(key).collect { result ->
                when (result) {
                    is StoreResult.Data -> updateUI(result.value)
                    is StoreResult.Loading -> showLoading()
                    is StoreResult.Error -> showError(result.throwable)
                }
            }
        }
    }
}
```

### 3. Backpressure Handling (TASK-007)

```kotlin
// ✅ Conflate: Drop intermediate emissions
backend.rootInvalidations
    .conflate()  // Prevent overwhelming downstream
    .collect { invalidation ->
        recompose()
    }

// ✅ Buffer: Allow bursty updates
backend.rootInvalidations
    .buffer(capacity = 1)
    .collect { invalidation ->
        recompose()
    }
```

**When to use:**
- **conflate()**: UI updates (only care about latest)
- **buffer()**: Event processing (don't want to miss events)

### 4. Parallel Fetches

```kotlin
// ✅ Fetch multiple keys in parallel
coroutineScope {
    val user = async { store.get(userKey) }
    val posts = async { store.get(postsKey) }
    val comments = async { store.get(commentsKey) }

    UserProfile(
        user = user.await(),
        posts = posts.await(),
        comments = comments.await()
    )
}
```

---

## Common Pitfalls

### 1. ❌ Using GlobalScope

```kotlin
// ❌ Never use GlobalScope
GlobalScope.launch {
    store.get(key)
}

// ✅ Always use scoped launch
viewModelScope.launch {
    store.get(key)
}
```

**Why?**
- GlobalScope launches never get cancelled
- Memory leaks and zombie operations
- Violates structured concurrency

### 2. ❌ Blocking Main Thread

```kotlin
// ❌ Don't use runBlocking on Main
runBlocking {  // Freezes UI!
    store.get(key)
}

// ✅ Use suspend functions in coroutines
viewModelScope.launch {
    val result = store.get(key)
    updateUI(result)
}
```

### 3. ❌ Catching CancellationException

```kotlin
// ❌ Swallows cancellation
try {
    store.get(key)
} catch (e: Throwable) {  // Catches CancellationException
    // ...
}

// ✅ Let it propagate
try {
    store.get(key)
} catch (e: Exception) {  // Doesn't catch CancellationException
    // ...
}
```

### 4. ❌ Sharing Mutable State

```kotlin
// ❌ Race condition
var counter = 0
repeat(1000) {
    launch {
        counter++  // Not atomic!
    }
}

// ✅ Use atomic or mutex
val counter = AtomicInt(0)
repeat(1000) {
    launch {
        counter.incrementAndGet()
    }
}

// Or use mutex
var counter = 0
val mutex = Mutex()
repeat(1000) {
    launch {
        mutex.withLock {
            counter++
        }
    }
}
```

### 5. ❌ Long-Running Operations Without Yield

```kotlin
// ❌ Blocks event loop (JS) or thread pool (JVM)
fun process(list: List<Int>) {
    list.forEach { item ->
        // CPU-intensive work
        heavyComputation(item)
    }
}

// ✅ Yield periodically
suspend fun process(list: List<Int>) {
    list.forEachIndexed { index, item ->
        heavyComputation(item)
        if (index % 100 == 0) yield()
    }
}
```

---

## Testing Concurrency

### Test Utilities

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class MyConcurrencyTest {
    @Test
    fun testConcurrentAccess() = runTest {
        val store = createStore()

        // Launch 100 concurrent operations
        val jobs = (1..100).map {
            launch(Dispatchers.Default) {
                store.get(key)
            }
        }

        jobs.joinAll()  // Wait for all

        // Verify single fetch via SingleFlight
        verifySuspend(exactly = 1) { fetcher.fetch(key, any()) }
    }

    @Test
    fun testCancellation() = runTest {
        val cancelled = CompletableDeferred<Unit>()

        val job = launch {
            try {
                store.stream(key).collect { }
            } catch (e: CancellationException) {
                cancelled.complete(Unit)
                throw e
            }
        }

        advanceUntilIdle()
        job.cancel()
        job.join()

        cancelled.await()  // ✅ Cancellation propagated
    }
}
```

### Testing Strategies

1. **Stress Tests**: 100+ concurrent operations
2. **Cancellation Tests**: Verify proper cleanup
3. **Race Condition Tests**: Shared state, concurrent writes
4. **Deadlock Tests**: Mutex ordering, circular waits
5. **Memory Leak Tests**: Verify no zombie coroutines

---

## Summary

### Thread Safety Checklist

- [x] All public APIs are thread-safe
- [x] Internal state protected by Mutex or atomic operations
- [x] CancellationException always propagates
- [x] Flow cancellation properly handled
- [x] No GlobalScope usage
- [x] Proper dispatcher selection (IO for DB, Default for CPU)
- [x] Structured concurrency with SupervisorJob
- [x] Per-key locking for fine-grained concurrency
- [x] Backpressure handling in reactive flows
- [x] Comprehensive concurrency tests

---

## See Also

- [ARCHITECTURE.md](./ARCHITECTURE.md) - Overall architecture
- [PERFORMANCE.md](./PERFORMANCE.md) - Performance optimization
- [Test Suite](./store/src/commonTest/kotlin/dev/mattramotar/storex/store/concurrency/) - Concurrency tests

---

**Last Updated**: 2025-10-04
