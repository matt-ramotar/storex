# StoreX Bundle: Android

**All-in-one bundle for Android applications**

This bundle aggregates all modules needed for building Android applications with StoreX, including Jetpack Compose integration, Android platform APIs, and mutation support.

## 📦 What's Included

This bundle includes the following modules:

- **`:core`** - Core Store functionality (read-only operations, caching, persistence)
- **`:mutations`** - Mutation support (create, update, delete, upsert)
- **`:android`** - Android platform integrations (Room, DataStore, WorkManager, Lifecycle)
- **`:compose`** - Jetpack Compose extensions (`rememberStore()`, state management)

## 🎯 When to Use

Use this bundle when building applications that:

- Are **Android apps** using Jetpack Compose
- Need **Android platform integrations** (Room, DataStore, WorkManager)
- Require **lifecycle-aware** data fetching and caching
- Want **Compose-friendly** state management
- Need **offline-first** capabilities on Android

Perfect for:
- Modern Android apps with Jetpack Compose
- Apps requiring offline-first with Room persistence
- Apps with complex state management needs
- Apps using AndroidX Lifecycle components

## 🚀 Getting Started

### Installation

Add the bundle to your project:

```kotlin
// build.gradle.kts (app module)
dependencies {
    implementation("dev.mattramotar.storex:bundle-android:1.0.0")
}
```

This single dependency brings in all the modules you need for Android applications.

### Basic Usage

#### With Jetpack Compose

```kotlin
import androidx.compose.runtime.*
import dev.mattramotar.storex.core.*
import dev.mattramotar.storex.mutations.*
import dev.mattramotar.storex.compose.*
import dev.mattramotar.storex.android.*

@Composable
fun UserScreen(userId: String) {
    // Create a composition-scoped store
    val userStore = rememberStore<UserKey, User> {
        // Room-backed persistence
        sourceOfTruth = roomSourceOfTruth(userDao)

        // Network fetcher
        fetcher { key ->
            api.getUser(key.id)
        }
    }

    // Collect as Compose State with lifecycle awareness
    val userState by userStore.stream(UserKey(userId))
        .collectAsStateWithLifecycle()

    when (userState) {
        is StoreResult.Data -> {
            UserContent(userState.requireData())
        }
        is StoreResult.Loading -> {
            LoadingIndicator()
        }
        is StoreResult.Error -> {
            ErrorMessage(userState.error)
        }
    }
}

@Composable
fun UserEditor(userId: String) {
    val scope = rememberCoroutineScope()
    val store = rememberMutationStore<UserKey, User, UserPatch, UserDraft>()

    var name by remember { mutableStateOf("") }

    LaunchedStoreEffect(store, UserKey(userId)) { result ->
        name = result.dataOrNull()?.name ?: ""
    }

    Column {
        TextField(
            value = name,
            onValueChange = { newName ->
                name = newName
                scope.launch {
                    // Optimistic update
                    store.update(
                        UserKey(userId),
                        UserPatch(name = newName)
                    )
                }
            }
        )
    }
}
```

#### With Room Persistence

```kotlin
@Entity
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val cachedAt: Long
)

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id")
    fun observeUser(id: String): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUser(id: String)
}

val userStore = store<UserKey, User> {
    sourceOfTruth = roomSourceOfTruth(
        dao = userDao,
        reader = { key -> observeUser(key.id) },
        writer = { key, entity -> insertUser(entity) },
        deleter = { key -> deleteUser(key.id) }
    )

    fetcher { key -> api.getUser(key.id) }
}
```

#### With DataStore

```kotlin
val preferences = context.dataStore

val settingsStore = store<SettingsKey, Settings> {
    sourceOfTruth = dataStoreSourceOfTruth(
        dataStore = preferences,
        serializer = Settings.serializer()
    )

    // No fetcher needed for local-only data
}
```

#### With WorkManager Background Sync

```kotlin
// Schedule periodic background sync
userStore.scheduleSync(
    interval = 1.hours,
    constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()
)
```

## 📚 Key Features

### Jetpack Compose Integration

Seamless integration with Compose:
- **`rememberStore()`**: Composition-scoped Store instances
- **`collectAsStateWithLifecycle()`**: Lifecycle-aware state collection
- **`LaunchedStoreEffect()`**: Side effects for Store operations
- **Optimistic UI updates**: Instant feedback while mutations are in flight

### Android Platform APIs

Deep integration with Android:
- **Room**: Type-safe SQL persistence
- **DataStore**: Typed preferences storage
- **WorkManager**: Background sync scheduling
- **Lifecycle**: Automatic lifecycle awareness
- **ViewModel**: Integration with Android ViewModel

### Offline-First

Built-in offline support:
- **Local persistence**: Room or DataStore
- **Background sync**: WorkManager integration
- **Conflict resolution**: Automatic merge strategies
- **Optimistic updates**: Instant UI feedback

### Lifecycle Awareness

Respects Android component lifecycles:
- **Auto-pause**: Stop updates when app is backgrounded
- **Auto-resume**: Resume when app comes to foreground
- **Cleanup**: Automatic resource cleanup

## 🔗 Alternative Bundles

- **`bundle-graphql`**: For GraphQL applications (platform-agnostic)
- **`bundle-rest`**: For REST API applications (platform-agnostic)

## 📖 Documentation

For detailed documentation, see:
- [StoreX Documentation](../../README.md)
- [Android Guide](../android/README.md)
- [Compose Guide](../compose/README.md)
- [Mutations Guide](../mutations/README.md)

## 🏗️ Module Structure

```
bundle-android
├── core (API)
│   ├── Store interface
│   ├── Caching
│   └── Persistence
├── mutations (API)
│   ├── MutationStore
│   ├── CRUD operations
│   └── Optimistic updates
├── android (API)
│   ├── Room integration
│   ├── DataStore integration
│   ├── WorkManager sync
│   └── Lifecycle awareness
└── compose (API)
    ├── rememberStore()
    ├── collectAsStateWithLifecycle()
    ├── LaunchedStoreEffect()
    └── Optimistic UI helpers
```

## 📄 License

Apache 2.0 - See [LICENSE](../../LICENSE) for details.
