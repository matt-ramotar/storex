# StoreX Paging

**Bidirectional pagination support for reactive stores**

The `:paging` module provides sophisticated pagination capabilities for StoreX, enabling efficient loading of large datasets with both forward and backward navigation. It builds on `:core` to add paginated data loading with cursor-based or offset-based strategies.

## 📦 What's Included

This module provides:

- **`PageStore<Key, Item>`** - Store interface for paginated data
- **`Page<Item>`** - Container for page data with metadata
- **`PageToken`** - Cursor for navigation (next/previous)
- **`PagingConfig`** - Configuration for page size and prefetch
- **`LoadState`** - Loading, error, and end-of-list states
- **`PagingSnapshot`** - Immutable view of paging state
- **`PagingEvent`** - Events for state changes
- **Page Freshness Validator** - TTL and staleness detection for pages

## 🎯 When to Use

Use this module when you need:

- **Infinite scroll** lists (social feeds, search results)
- **Bidirectional pagination** (prev/next navigation)
- **Large datasets** that shouldn't be loaded at once
- **Cursor-based pagination** (GraphQL Relay-style)
- **Offset-based pagination** (traditional REST APIs)
- **Prefetching** for smooth scrolling

**Perfect for:**
- Social media feeds (posts, comments, messages)
- Search results with "load more"
- Chat/message histories
- Product catalogs

**Requires:**
- `:core` module (base Store functionality)

## 🚀 Getting Started

### Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("dev.mattramotar.storex:core:1.0.0")
    implementation("dev.mattramotar.storex:paging:1.0.0")
}
```

### Basic Usage (Cursor-Based)

```kotlin
import dev.mattramotar.storex.paging.*

// Domain model
data class Post(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Instant
)

// Cursor token from API
data class PostsCursor(
    val after: String?,  // Cursor for next page
    val before: String?  // Cursor for previous page
) : PageToken

// Create a page store
val postsPageStore = pageStore<PostsKey, Post> {
    // Fetch a page
    fetcher { key, pageToken ->
        flow {
            val cursor = pageToken as? PostsCursor
            val response = api.getPosts(
                userId = key.userId,
                after = cursor?.after,
                before = cursor?.before,
                limit = 20
            )

            emit(Page(
                items = response.posts,
                nextToken = response.hasNext?.let { PostsCursor(after = response.endCursor, before = null) },
                prevToken = response.hasPrev?.let { PostsCursor(after = null, before = response.startCursor) }
            ))
        }
    }

    pagingConfig {
        pageSize = 20
        prefetchDistance = 5  // Start loading next page when 5 items from end
        initialLoadSize = 40  // Load 2 pages initially
    }
}

// Load initial page
val initialPage = postsPageStore.loadInitial(PostsKey(userId = "user-123"))

// Load next page
val nextPage = postsPageStore.loadNext(initialPage.nextToken!!)

// Load previous page
val prevPage = postsPageStore.loadPrev(initialPage.prevToken!!)

// Stream all pages
postsPageStore.stream(PostsKey(userId = "user-123")).collect { snapshot ->
    println("Total items: ${snapshot.items.size}")
    println("Loading: ${snapshot.loadState.isLoading}")
    println("Has more: ${snapshot.loadState.hasNext}")
}
```

### Offset-Based Pagination

```kotlin
// Offset token
data class OffsetToken(
    val offset: Int,
    val limit: Int
) : PageToken

val pageStore = pageStore<SearchKey, SearchResult> {
    fetcher { key, pageToken ->
        flow {
            val offset = (pageToken as? OffsetToken)?.offset ?: 0
            val limit = (pageToken as? OffsetToken)?.limit ?: 20

            val response = api.search(
                query = key.query,
                offset = offset,
                limit = limit
            )

            emit(Page(
                items = response.results,
                nextToken = if (response.hasMore) {
                    OffsetToken(offset + limit, limit)
                } else null,
                prevToken = if (offset > 0) {
                    OffsetToken(maxOf(0, offset - limit), limit)
                } else null
            ))
        }
    }
}
```

## 📚 Key Concepts

### Page

Container for paginated data:

```kotlin
data class Page<Item>(
    val items: List<Item>,
    val nextToken: PageToken?,  // null = no more pages
    val prevToken: PageToken?,   // null = at beginning
    val metadata: PageMetadata = PageMetadata()
)

data class PageMetadata(
    val totalCount: Long? = null,  // Total items (if known)
    val hasNext: Boolean = true,
    val hasPrev: Boolean = false
)
```

### LoadState

Track loading progress and errors:

```kotlin
sealed class LoadState {
    object Idle : LoadState()
    object Loading : LoadState()
    data class Error(val throwable: Throwable) : LoadState()
    object EndOfList : LoadState()
}

// Usage
when (snapshot.loadState) {
    is LoadState.Idle -> showContent()
    is LoadState.Loading -> showLoadingSpinner()
    is LoadState.Error -> showError(snapshot.loadState.throwable)
    is LoadState.EndOfList -> hideLoadMoreButton()
}
```

### PagingSnapshot

Immutable view of current paging state:

```kotlin
data class PagingSnapshot<Item>(
    val items: List<Item>,              // All loaded items
    val loadState: LoadState,            // Current loading state
    val nextToken: PageToken?,           // Token for next page
    val prevToken: PageToken?,           // Token for previous page
    val totalCount: Long? = null         // Total item count (if known)
)
```

### Prefetching

Automatically load next page before reaching end:

```kotlin
pagingConfig {
    pageSize = 20
    prefetchDistance = 5  // Trigger when 5 items from bottom

    // When user scrolls to item 15 of 20, next page starts loading
}
```

## 🔧 Advanced Features

### Refresh Strategy

Refresh paginated data with freshness control:

```kotlin
// Refresh first page only
pageStore.refresh(key, RefreshStrategy.FirstPageOnly)

// Refresh all loaded pages
pageStore.refresh(key, RefreshStrategy.AllPages)

// Refresh and reset to first page
pageStore.refresh(key, RefreshStrategy.Reset)
```

### Page TTL

Configure staleness for individual pages:

```kotlin
pagingConfig {
    pageTTL = 5.minutes  // Pages older than 5 minutes are stale
}

// Store automatically refetches stale pages
```

### Error Handling

Handle page load errors gracefully:

```kotlin
fetcher { key, pageToken ->
    flow {
        try {
            val response = api.getPosts(...)
            emit(Page(items = response.posts, ...))
        } catch (e: IOException) {
            // Emit error state
            emit(Page(
                items = emptyList(),
                nextToken = pageToken,  // Retry with same token
                prevToken = null,
                error = e
            ))
        }
    }
}
```

### Append vs Prepend

Control how pages are added:

```kotlin
// Append (load more at bottom)
pageStore.loadNext(nextToken)  // Adds to end

// Prepend (load previous at top)
pageStore.loadPrev(prevToken)  // Adds to beginning

// Snapshot maintains correct order
snapshot.items  // [prev page items, current page items, next page items]
```

## 🏗️ Architecture

### Module Dependencies

```
paging
├── core (API dependency)
│   ├── Store interface
│   ├── Freshness policies
│   └── Caching primitives
├── kotlinx-coroutines-core
└── kotlinx-datetime
```

### Package Structure

```
dev.mattramotar.storex.paging
├── PageStore.kt                # Main interface
├── Page.kt                     # Page container
├── PageToken.kt                # Navigation cursors
├── PagingConfig.kt             # Configuration
├── LoadState.kt                # Loading states
├── PagingSnapshot.kt           # State snapshot
├── PagingEvent.kt              # State change events
└── internal/
    └── PageFreshnessValidator.kt  # TTL validation
```

## 🔗 Related Modules

- **`:core`** - Base Store functionality (required dependency)
- **`:mutations`** - For paginated lists with mutations
- **`:normalization:runtime`** - For normalized paginated entities
- **`:bundle-rest`** - Pre-configured bundle (includes `:paging`)
- **`:bundle-graphql`** - Pre-configured bundle (includes `:paging`)

## 📖 Documentation

For detailed information, see:

- [Core Module](../core/README.md) - Base store functionality
- [ARCHITECTURE.md](../ARCHITECTURE.md) - Paging architecture
- [PERFORMANCE.md](../PERFORMANCE.md) - Paging performance tips
- [API Documentation](../docs/api/paging/) - Complete API reference

## 💡 Best Practices

1. **Use appropriate page size** - 20-50 items for mobile, 50-100 for desktop
2. **Enable prefetching** - Set prefetchDistance to 5-10 items
3. **Handle offline gracefully** - Show cached pages when offline
4. **Set page TTL** - Balance freshness vs network usage
5. **Use cursor-based for infinite lists** - Better performance than offset
6. **Use offset-based for known totals** - Better for "page 1 of 10" UI
7. **Invalidate on mutations** - Refresh after create/update/delete

## ⚡ Performance

- **Page cache hit**: < 1ms (memory)
- **Page cache miss**: 50-500ms (network)
- **Prefetch overhead**: Minimal (background loading)
- **Memory per page**: ~pageSize × item size
- **Recommended limits**: 10-20 cached pages

### Optimization Tips

```kotlin
// Good: Reasonable page size
pagingConfig {
    pageSize = 20
}

// Bad: Too large (network overhead)
pagingConfig {
    pageSize = 1000  // Don't do this!
}

// Good: Prefetch for smooth scrolling
pagingConfig {
    prefetchDistance = 5
}

// Good: Limit cached pages
pagingConfig {
    maxCachedPages = 10  // Drop oldest pages
}
```

## 🆚 Comparison to Other Solutions

| Feature | Paging3 (Android) | Apollo GraphQL | StoreX Paging |
|---------|-------------------|----------------|---------------|
| **Platform** | Android only | Multiplatform | Kotlin Multiplatform |
| **API Style** | PagingSource | GraphQL only | Any API (REST, GraphQL) |
| **Cursor-based** | ✅ | ✅ | ✅ |
| **Offset-based** | ✅ | ❌ | ✅ |
| **Reactive** | Flow | Flow (via cache) | Flow |
| **Offline** | Limited | Via cache | Built-in offline-first |
| **Prefetching** | ✅ | ✅ | ✅ |

## 📄 License

Apache 2.0 - See [LICENSE](../LICENSE) for details.
