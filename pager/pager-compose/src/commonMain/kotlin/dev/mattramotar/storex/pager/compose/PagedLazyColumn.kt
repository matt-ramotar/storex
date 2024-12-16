package dev.mattramotar.storex.pager.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import dev.mattramotar.storex.pager.core.PagingState
import dev.mattramotar.storex.pager.extensions.LoadStateExtensions.isLoading
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A customizable, Compose-friendly paged list component built on [LazyColumn].
 *
 * This composable:
 * - Displays a paged list of [Value] items from [stateFlow].
 * - Automatically triggers [loadMore] when the user scrolls near the bottom.
 * - Optionally supports pull-to-refresh if [onRefresh] is provided.
 * - Allows for flexible list content via [content], which has access to a [LazyListScope].
 *
 * Example:
 * ```kotlin
 * PagedLazyColumn(
 *     stateFlow = pager.state,
 *     loadMore = { pager.loadMore() },
 *     onRefresh = { pager.refresh() },
 *     headerContent = { item { HeaderUI() } },
 *     emptyContent = { item { EmptyStateUI() } },
 *     loadingFooterContent = { item { CircularProgressIndicator() } },
 * ) { items ->
 *     items(items) { item ->
 *         Text(item.toString())
 *     }
 * }
 * ```
 *
 * @param stateFlow A [StateFlow] emitting [PagingState], providing current items and load states.
 * @param loadMore A suspend function invoked to load the next page when the user nears the end.
 * @param onRefresh An optional suspend function to refresh the data. If provided, pull-to-refresh is enabled.
 * @param loadMoreThreshold How close to the end of the list before triggering [loadMore]. Default is 5 items.
 * @param shouldLoadMore A lambda that determines if we should trigger [loadMore]. Defaults to checking if
 *                       `lastVisibleIndex >= items.size - loadMoreThreshold`.
 * @param context Optional [CoroutineContext] for collecting [stateFlow]. Default is [EmptyCoroutineContext].
 * @param headerContent Optional content displayed above the items (e.g., a header).
 * @param emptyContent Optional content displayed when the list is empty.
 * @param loadingFooterContent Optional content displayed at the bottom when the next page is loading.
 * @param content A lambda with a [LazyListScope] receiver to define how items are displayed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <Key : Any, Value : Any> PagedLazyColumn(
    stateFlow: StateFlow<PagingState<Key, Value>>,
    loadMore: suspend () -> Unit,
    onRefresh: (suspend () -> Unit)? = null,
    loadMoreThreshold: Int = 5,
    shouldLoadMore: (lastVisibleIndex: Int, items: List<Value>, threshold: Int) -> Boolean = { lastVisible, allItems, threshold ->
        allItems.isNotEmpty() && lastVisible >= allItems.size - threshold
    },
    context: CoroutineContext = EmptyCoroutineContext,
    headerContent: (LazyListScope.() -> Unit)? = null,
    emptyContent: (LazyListScope.() -> Unit)? = null,
    loadingFooterContent: (LazyListScope.() -> Unit)? = null,
    content: LazyListScope.(List<Value>) -> Unit
) {
    val pagingState by stateFlow.collectAsState(context)
    val items = pagingState.items
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Automatically load more data when nearing the bottom
    LaunchedEffect(lazyListState, items) {
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo }
            .distinctUntilChangedBy { it.lastOrNull()?.index }
            .collect { visibleItems ->
                val lastVisibleIndex = visibleItems.lastOrNull()?.index ?: 0
                if (shouldLoadMore(lastVisibleIndex, items, loadMoreThreshold)) {
                    coroutineScope.launch { loadMore() }
                }
            }
    }

    val isRefreshing = pagingState.loadStates.refresh.isLoading()
    val pullToRefreshState = rememberPullToRefreshState()

    Box(
        modifier = if (onRefresh != null) {
            Modifier.pullToRefresh(
                isRefreshing = isRefreshing,
                state = pullToRefreshState,
                onRefresh = { coroutineScope.launch { onRefresh() } }
            )
        } else {
            Modifier
        }
    ) {
        LazyColumn(state = lazyListState) {
            // Optional header
            headerContent?.invoke(this)

            if (items.isEmpty()) {
                // If empty, show empty content if provided
                emptyContent?.invoke(this)
            } else {
                // Populate list items
                content(items)
            }

            // If appending is loading, show loading footer if provided
            val appendState = pagingState.loadStates.append
            if (appendState.isLoading() && loadingFooterContent != null) {
                loadingFooterContent()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <Key : Any, Value : Any> PagedLazyColumn(
    pagingState: PagingState<Key, Value>,
    loadMore: suspend () -> Unit,
    onRefresh: (suspend () -> Unit)? = null,
    loadMoreThreshold: Int = 5,
    shouldLoadMore: (lastVisibleIndex: Int, items: List<Value>, threshold: Int) -> Boolean = { lastVisible, allItems, threshold ->
        allItems.isNotEmpty() && lastVisible >= allItems.size - threshold
    },
    headerContent: (LazyListScope.() -> Unit)? = null,
    emptyContent: (LazyListScope.() -> Unit)? = null,
    loadingFooterContent: (LazyListScope.() -> Unit)? = null,
    content: LazyListScope.(List<Value>) -> Unit
) {
    val items = pagingState.items
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Automatically load more data when nearing the bottom
    LaunchedEffect(lazyListState, items) {
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo }
            .distinctUntilChangedBy { it.lastOrNull()?.index }
            .collect { visibleItems ->
                val lastVisibleIndex = visibleItems.lastOrNull()?.index ?: 0
                if (shouldLoadMore(lastVisibleIndex, items, loadMoreThreshold)) {
                    coroutineScope.launch { loadMore() }
                }
            }
    }

    val isRefreshing = pagingState.loadStates.refresh.isLoading()
    val pullToRefreshState = rememberPullToRefreshState()

    Box(
        modifier = if (onRefresh != null) {
            Modifier.pullToRefresh(
                isRefreshing = isRefreshing,
                state = pullToRefreshState,
                onRefresh = { coroutineScope.launch { onRefresh() } }
            )
        } else {
            Modifier
        }
    ) {
        LazyColumn(state = lazyListState) {
            // Optional header
            headerContent?.invoke(this)

            if (items.isEmpty()) {
                // If empty, show empty content if provided
                emptyContent?.invoke(this)
            } else {
                // Populate list items
                content(items)
            }

            // If appending is loading, show loading footer if provided
            val appendState = pagingState.loadStates.append
            if (appendState.isLoading() && loadingFooterContent != null) {
                loadingFooterContent()
            }
        }
    }
}