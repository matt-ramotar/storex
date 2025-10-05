# StoreX Compose

**Jetpack Compose helpers and integrations for StoreX**

The `:compose` module provides seamless integration between StoreX and Jetpack Compose, including composable functions for reactive data loading, state management, and UI patterns optimized for declarative UIs.

> **Status**: 🚧 **Placeholder Implementation** - Full implementation planned for future release

## 📦 What's Included

This module will provide:

- **`collectAsState()`** - Collect Store.stream() as Compose State
- **`StoreLoadingState`** - Composable loading states (Loading, Success, Error)
- **`rememberStore()`** - Remember store instances in composition
- **`PagingComposables`** - LazyColumn integration for PageStore
- **Error Boundaries** - Composable error handling
- **Refresh Indicators** - Pull-to-refresh integration
- **State Hoisting** - Patterns for lifting state

## 🎯 When to Use

Use this module for:

- **Jetpack Compose** Android apps
- **Declarative UI** patterns with StoreX
- **State management** in Compose
- **Reactive data loading** with recomposition
- **Infinite scroll** with LazyColumn
- **Pull-to-refresh** patterns

## 🚀 Planned Usage

```kotlin
import androidx.compose.runtime.*
import dev.mattramotar.storex.compose.*

@Composable
fun UserScreen(
    userId: String,
    userStore: Store<ByIdKey, User>,
    viewModel: UserViewModel = viewModel()
) {
    val userKey = remember(userId) {
        ByIdKey(
            namespace = StoreNamespace("users"),
            entity = EntityId("User", userId)
        )
    }

    // Collect store stream as Compose state
    val userState by userStore.stream(userKey).collectAsState()

    // Render based on state
    when (val state = userState) {
        is StoreResult.Loading -> LoadingIndicator()
        is StoreResult.Data -> UserContent(user = state.value)
        is StoreResult.Error -> ErrorView(error = state.throwable)
    }
}

// Or use helper composable
@Composable
fun UserScreen2(userId: String, userStore: Store<ByIdKey, User>) {
    StoreLoadingState(
        store = userStore,
        key = ByIdKey(StoreNamespace("users"), EntityId("User", userId)),
        loading = { LoadingIndicator() },
        error = { throwable -> ErrorView(throwable) },
        content = { user -> UserContent(user) }
    )
}

// Paging with LazyColumn
@Composable
fun PostsList(pageStore: PageStore<PostsKey, Post>) {
    val posts by pageStore.stream(PostsKey()).collectAsPagedList()

    LazyColumn {
        items(posts) { post ->
            PostItem(post)
        }

        if (posts.hasMore) {
            item {
                LoadMoreButton(onClick = { posts.loadMore() })
            }
        }
    }
}
```

## 📚 Planned Features

### collectAsState()

```kotlin
@Composable
fun UserName(userId: String, store: Store<ByIdKey, User>): String {
    val key = remember(userId) {
        ByIdKey(StoreNamespace("users"), EntityId("User", userId))
    }

    val userState by store.stream(key).collectAsState(
        initial = StoreResult.Loading()
    )

    return when (val state = userState) {
        is StoreResult.Data -> state.value.name
        is StoreResult.Loading -> "Loading..."
        is StoreResult.Error -> "Error"
    }
}
```

### StoreLoadingState Composable

```kotlin
@Composable
fun <K : StoreKey, V> StoreLoadingState(
    store: Store<K, V>,
    key: K,
    freshness: Freshness = Freshness.CachedOrFetch,
    loading: @Composable () -> Unit = { CircularProgressIndicator() },
    error: @Composable (Throwable) -> Unit = { ErrorText(it.message) },
    content: @Composable (V) -> Unit
) {
    val state by store.stream(key, freshness).collectAsState()

    when (val s = state) {
        is StoreResult.Loading -> loading()
        is StoreResult.Data -> content(s.value)
        is StoreResult.Error -> error(s.throwable)
    }
}

// Usage
StoreLoadingState(
    store = userStore,
    key = userKey,
    loading = { LoadingScreen() },
    error = { ErrorScreen(it) }
) { user ->
    UserProfile(user)
}
```

### Paging Integration

```kotlin
@Composable
fun InfiniteList(pageStore: PageStore<SearchKey, SearchResult>) {
    val lazyPagingItems = pageStore.collectAsLazyPagingItems(
        key = SearchKey(query = "kotlin")
    )

    LazyColumn {
        items(lazyPagingItems) { item ->
            SearchResultItem(item)
        }

        when (lazyPagingItems.loadState.append) {
            is LoadState.Loading -> item { LoadingItem() }
            is LoadState.Error -> item { RetryButton { lazyPagingItems.retry() } }
            else -> {}
        }
    }
}
```

### Pull-to-Refresh

```kotlin
@Composable
fun PostsFeed(store: Store<FeedKey, List<Post>>) {
    var isRefreshing by remember { mutableStateOf(false) }
    val posts by store.stream(FeedKey()).collectAsState()

    SwipeRefresh(
        state = rememberSwipeRefreshState(isRefreshing),
        onRefresh = {
            isRefreshing = true
            scope.launch {
                store.invalidate(FeedKey())
                store.get(FeedKey(), Freshness.MustBeFresh)
                isRefreshing = false
            }
        }
    ) {
        when (val state = posts) {
            is StoreResult.Data -> PostsList(state.value)
            is StoreResult.Loading -> LoadingView()
            is StoreResult.Error -> ErrorView(state.throwable)
        }
    }
}
```

### Remember Store

```kotlin
@Composable
fun rememberUserStore(): Store<ByIdKey, User> {
    val context = LocalContext.current
    return remember {
        store<ByIdKey, User> {
            fetcher { key ->
                flow {
                    val user = api.getUser(key.entity.id)
                    emit(FetcherResult.Success(user))
                }
            }
            sourceOfTruth(
                reader = { key -> database.getUserFlow(key.entity.id) },
                writer = { key, user -> database.saveUser(user) }
            )
        }
    }
}
```

## 🏗️ Architecture

### Module Dependencies

```
compose
├── core (API dependency)
│   └── Store interface
├── paging (optional)
│   └── PageStore interface
├── androidx.compose.runtime
├── androidx.compose.foundation
└── androidx.compose.material (optional)
```

### Package Structure

```
dev.mattramotar.storex.compose
├── ComposeExtensions.kt         # Main extensions (placeholder)
├── state/                        # State management (planned)
│   ├── collectAsState.kt
│   └── StoreLoadingState.kt
├── paging/                       # Paging integration (planned)
│   ├── collectAsLazyPagingItems.kt
│   └── PagingComposables.kt
├── refresh/                      # Pull-to-refresh (planned)
│   └── SwipeRefreshStore.kt
└── remember/                     # Remember helpers (planned)
    └── rememberStore.kt
```

## 🔗 Related Modules

- **`:core`** - Base Store functionality (required dependency)
- **`:paging`** - Pagination support
- **`:android`** - Android platform integration
- **`:bundle-android`** - Pre-configured bundle (includes `:compose`)

## 💡 Planned Best Practices

1. **Use collectAsState()** - Automatic recomposition
2. **Remember keys** - Prevent unnecessary recreations
3. **Hoist state** - Keep business logic in ViewModel
4. **Handle all states** - Loading, Success, Error
5. **Use derivedStateOf** - Optimize recomposition
6. **Avoid side effects in composition** - Use LaunchedEffect
7. **Test composables** - Use ComposeTestRule

## 📊 Roadmap

### v1.1 (Planned)
- [ ] `collectAsState()` extension
- [ ] `StoreLoadingState` composable
- [ ] Basic paging integration
- [ ] `rememberStore()` helper

### v1.2 (Planned)
- [ ] Advanced paging (LazyColumn/LazyGrid)
- [ ] Pull-to-refresh integration
- [ ] Error boundaries
- [ ] Loading skeleton screens

### v2.0 (Future)
- [ ] Compose Multiplatform support
- [ ] Desktop/Web composables
- [ ] Accessibility helpers
- [ ] Animation utilities

## 🎨 Example Patterns

### Loading Skeleton

```kotlin
@Composable
fun UserProfileSkeleton() {
    Column(modifier = Modifier.padding(16.dp)) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Color.Gray.copy(alpha = 0.3f))
                .shimmer()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .background(Color.Gray.copy(alpha = 0.3f))
                .shimmer()
        )
    }
}

StoreLoadingState(
    store = userStore,
    key = userKey,
    loading = { UserProfileSkeleton() }
) { user ->
    UserProfile(user)
}
```

## 📄 License

Apache 2.0 - See [LICENSE](../LICENSE) for details.
