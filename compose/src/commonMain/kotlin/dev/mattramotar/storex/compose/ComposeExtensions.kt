package dev.mattramotar.storex.compose

import dev.mattramotar.storex.core.Store
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.StoreResult

/**
 * Jetpack Compose extensions for StoreX.
 *
 * Provides Composable functions and extensions for seamless integration
 * with Jetpack Compose, including state management and optimistic UI updates.
 *
 * **Planned Features** (to be implemented):
 * - `rememberStore()`: Composition-scoped Store instances
 * - `Store.collectAsState()`: Convert Store Flow to Compose State
 * - `LaunchedStoreEffect`: Side effects for Store operations
 * - Optimistic UI state management
 * - Automatic recomposition on Store updates
 * - Error handling composables
 *
 * Example usage (future):
 * ```kotlin
 * @Composable
 * fun UserScreen(userId: String) {
 *     val userStore = rememberStore<UserKey, User> {
 *         fetcher { key -> api.getUser(key.id) }
 *         // ... other configuration
 *     }
 *
 *     val userState by userStore.stream(UserKey(userId))
 *         .collectAsStateWithLifecycle()
 *
 *     when (userState) {
 *         is StoreResult.Data -> UserContent(userState.requireData())
 *         is StoreResult.Loading -> LoadingIndicator()
 *         is StoreResult.Error -> ErrorMessage(userState.error)
 *     }
 * }
 *
 * @Composable
 * fun UserEditor(userId: String) {
 *     val store = rememberMutationStore<UserKey, User, UserPatch, UserDraft>()
 *     var optimisticName by remember { mutableStateOf("") }
 *
 *     LaunchedStoreEffect(store, UserKey(userId)) { result ->
 *         optimisticName = result.dataOrNull()?.name ?: ""
 *     }
 *
 *     TextField(
 *         value = optimisticName,
 *         onValueChange = { newName ->
 *             optimisticName = newName
 *             scope.launch {
 *                 store.update(UserKey(userId), UserPatch(name = newName))
 *             }
 *         }
 *     )
 * }
 * ```
 */

/**
 * State holder for Store operations in Compose.
 *
 * @param K The store key type
 * @param V The domain value type
 */
interface ComposeStoreState<K : StoreKey, V> {
    /**
     * The current result from the Store.
     */
    val result: StoreResult<V>

    /**
     * Whether the Store is currently fetching.
     */
    val isLoading: Boolean

    /**
     * The error, if any.
     */
    val error: Throwable?

    /**
     * The data, if available.
     */
    val data: V?
}

// TODO: Implement the following in future phases:
// - rememberStore(): Composition-scoped Store instances
// - Store.collectAsStateWithLifecycle(): Lifecycle-aware state collection
// - LaunchedStoreEffect(): Side effect composable for Store operations
// - OptimisticUpdate: Helper for optimistic UI updates
// - StoreLoadingIndicator: Pre-built loading UI component
// - StoreErrorBoundary: Error handling composable
// - rememberMutationStore(): Composition-scoped MutationStore
// - Store.invalidate(): Composable-friendly invalidation
