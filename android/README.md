# StoreX Android

**Android-specific extensions and integrations for StoreX**

The `:android` module provides Android platform integrations for StoreX, including lifecycle-aware components, WorkManager integration, ConnectivityManager support, and Android-specific caching strategies.

> **Status**: 🚧 **Placeholder Implementation** - Full implementation planned for future release

## 📦 What's Included

This module will provide:

- **Lifecycle Integration** - Automatic cleanup with Android lifecycles
- **WorkManager Support** - Background sync for offline mutations
- **Connectivity Monitoring** - Automatic sync when network available
- **Room Integration** - Pre-configured Room SourceOfTruth
- **DataStore Integration** - DataStore-backed SourceOfTruth
- **LiveData/Flow Bridges** - Convert between Store.stream() and LiveData
- **ViewMod

el Extensions** - viewModelScope integration

## 🎯 When to Use

Use this module for:

- **Android apps** using StoreX
- **Lifecycle-aware** data loading
- **Background sync** with WorkManager
- **Room database** integration
- **Connectivity-aware** caching
- **ViewModel** integration

## 🚀 Planned Usage

```kotlin
import dev.mattramotar.storex.android.*
import dev.mattramotar.storex.core.*

class UserViewModel(
    private val userStore: Store<ByIdKey, User>
) : ViewModel() {

    // Automatically scoped to viewModelScope
    val user: StateFlow<User?> = userStore
        .stream(userKey)
        .stateInViewModel(initialValue = null)

    // Load with lifecycle awareness
    fun loadUser(userId: String) {
        viewModelScope.launch {
            val key = ByIdKey(
                namespace = StoreNamespace("users"),
                entity = EntityId("User", userId)
            )
            val user = userStore.get(key)
            // Use user
        }
    }
}

// Room integration
val userStore = store<ByIdKey, User> {
    sourceOfTruth(
        room = database.userDao(),
        keyToId = { it.entity.id },
        idToKey = { ByIdKey(StoreNamespace("users"), EntityId("User", it)) }
    )
}

// WorkManager background sync
val syncWorker = StoreSyncWorker(context, userStore) {
    constraints {
        requiresNetwork = true
        requiresBatteryNotLow = true
    }
}
```

## 📚 Planned Features

### Lifecycle Integration

```kotlin
class UserFragment : Fragment() {
    private val userStore: Store<ByIdKey, User> by inject()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Automatically cancels when view destroyed
        userStore.stream(userKey)
            .onEach { result -> updateUI(result) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }
}
```

### Room Integration

```kotlin
@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :userId")
    fun getUserFlow(userId: String): Flow<User?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)
}

val userStore = store<ByIdKey, User> {
    sourceOfTruth(
        reader = { key -> database.userDao().getUserFlow(key.entity.id) },
        writer = { key, user -> database.userDao().insertUser(user) }
    )
}

// Or use helper
val userStore = store<ByIdKey, User> {
    roomSourceOfTruth(
        dao = database.userDao(),
        keyMapper = RoomKeyMapper(
            keyToId = { it.entity.id },
            idToKey = { ByIdKey(StoreNamespace("users"), EntityId("User", it)) }
        )
    )
}
```

### DataStore Integration

```kotlin
val dataStore = context.createDataStore("user_cache")

val userStore = store<ByIdKey, User> {
    dataStoreSourceOfTruth(
        dataStore = dataStore,
        serializer = UserSerializer
    )
}
```

### WorkManager Background Sync

```kotlin
class StoreSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val mutationStore: MutationStore<*, *, *, *>
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            mutationStore.syncPendingMutations()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

// Schedule periodic sync
val syncRequest = PeriodicWorkRequestBuilder<StoreSyncWorker>(15, TimeUnit.MINUTES)
    .setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
    )
    .build()

WorkManager.getInstance(context).enqueue(syncRequest)
```

### Connectivity Monitoring

```kotlin
val connectivityMonitor = ConnectivityMonitor(context)

connectivityMonitor.isConnected.collect { connected ->
    if (connected) {
        // Sync pending mutations
        mutationStore.syncPendingMutations()
    }
}
```

## 🏗️ Architecture

### Module Dependencies

```
android
├── core (API dependency)
│   └── Store interface
├── mutations (optional)
│   └── MutationStore interface
├── androidx.lifecycle
├── androidx.work (optional)
├── androidx.room (optional)
└── androidx.datastore (optional)
```

### Package Structure

```
dev.mattramotar.storex.android
├── AndroidExtensions.kt         # Main extensions (placeholder)
├── lifecycle/                    # Lifecycle integration (planned)
│   ├── LifecycleStore.kt
│   └── ViewModelExtensions.kt
├── work/                         # WorkManager (planned)
│   ├── StoreSyncWorker.kt
│   └── SyncManager.kt
├── connectivity/                 # Network monitoring (planned)
│   └── ConnectivityMonitor.kt
├── room/                         # Room integration (planned)
│   ├── RoomSourceOfTruth.kt
│   └── RoomKeyMapper.kt
└── datastore/                    # DataStore integration (planned)
    └── DataStoreSourceOfTruth.kt
```

## 🔗 Related Modules

- **`:core`** - Base Store functionality (required dependency)
- **`:mutations`** - Mutation operations (for sync)
- **`:compose`** - Jetpack Compose helpers
- **`:bundle-android`** - Pre-configured bundle (includes `:android`)

## 💡 Planned Best Practices

1. **Use viewModelScope** - Automatic cleanup
2. **Room for persistence** - Type-safe, SQLite-backed
3. **WorkManager for sync** - Reliable background processing
4. **Monitor connectivity** - Sync when network available
5. **Lifecycle-aware** - Prevent memory leaks
6. **Use StateFlow** - Better than LiveData for Store
7. **Handle configuration changes** - ViewModel survives rotations

## 📊 Roadmap

### v1.1 (Planned)
- [ ] Lifecycle integration (viewModelScope, lifecycleScope)
- [ ] Room SourceOfTruth helper
- [ ] Basic WorkManager integration
- [ ] StateFlow/LiveData bridges

### v1.2 (Planned)
- [ ] DataStore SourceOfTruth helper
- [ ] Connectivity monitoring
- [ ] Advanced WorkManager (batching, backoff)
- [ ] Android-specific caching strategies

### v2.0 (Future)
- [ ] Hilt/Koin integration
- [ ] Notification support for sync
- [ ] Battery-aware sync
- [ ] App Startup integration

## 📄 License

Apache 2.0 - See [LICENSE](../LICENSE) for details.
