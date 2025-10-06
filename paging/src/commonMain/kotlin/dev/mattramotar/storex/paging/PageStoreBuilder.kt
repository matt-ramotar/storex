package dev.mattramotar.storex.paging

import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.TimeSource
import dev.mattramotar.storex.paging.internal.RealPageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * DSL builder for creating [PageStore] instances.
 *
 * Example:
 * ```kotlin
 * val pageStore = pageStore<SearchKey, Result> {
 *     fetcher { key, token ->
 *         val offset = (token as? OffsetToken)?.offset ?: 0
 *         val response = api.search(key.query, offset, 20)
 *         Page(
 *             items = response.results,
 *             next = if (response.hasMore) OffsetToken(offset + 20) else null,
 *             prev = if (offset > 0) OffsetToken(offset - 20) else null
 *         )
 *     }
 * }
 * ```
 */
class PageStoreBuilder<K : StoreKey, V : Any> {

    private var fetcherFn: (suspend (key: K, token: PageToken?) -> Page<V>)? = null
    private var config: PagingConfig = PagingConfig(pageSize = 20)
    private var scope: CoroutineScope? = null
    private var timeSource: TimeSource = TimeSource.SYSTEM

    /**
     * Define how to fetch pages.
     *
     * The fetcher receives:
     * - `key`: The user's query/request key
     * - `token`: The page token (null for initial page)
     *
     * It should return a [Page] containing:
     * - `items`: The items for this page
     * - `next`: Token for next page (null if no more pages)
     * - `prev`: Token for previous page (null if at beginning)
     */
    fun fetcher(fetch: suspend (key: K, token: PageToken?) -> Page<V>) {
        this.fetcherFn = fetch
    }

    /**
     * Configure paging behavior.
     *
     * Optional - defaults to reasonable values.
     */
    fun config(block: PagingConfigBuilder.() -> Unit) {
        this.config = PagingConfigBuilder().apply(block).build()
    }

    /**
     * Provide a custom coroutine scope for background operations.
     *
     * Optional - defaults to a scope with SupervisorJob.
     */
    fun scope(scope: CoroutineScope) {
        this.scope = scope
    }

    /**
     * Provide a custom time source for timestamp tracking and freshness validation.
     *
     * Optional - defaults to TimeSource.SYSTEM.
     * Primarily useful for testing with TestTimeSource.
     */
    fun timeSource(timeSource: TimeSource) {
        this.timeSource = timeSource
    }

    internal fun build(): PageStore<K, V> {
        val fetcher = requireNotNull(fetcherFn) {
            "fetcher must be configured. Use fetcher { key, token -> ... }"
        }

        val actualScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

        return RealPageStore(
            fetcher = fetcher,
            config = config,
            scope = actualScope,
            timeSource = timeSource
        )
    }
}

/**
 * Builder for [PagingConfig].
 *
 * Note: prefetchDistance and maxSize default to null and are calculated
 * at build time based on the final pageSize value.
 */
class PagingConfigBuilder {
    var pageSize: Int = 20
    var prefetchDistance: Int? = null  // Defaults to pageSize at build time
    var maxSize: Int? = null            // Defaults to pageSize * 10 at build time
    var placeholders: Boolean = false
    var jumpThreshold: Int = Int.MAX_VALUE
    var pageTtl: kotlin.time.Duration = 5.minutes
    var retryBackoffBase: kotlin.time.Duration = 1.seconds

    fun build(): PagingConfig = PagingConfig(
        pageSize = pageSize,
        prefetchDistance = prefetchDistance ?: pageSize,
        maxSize = maxSize ?: (pageSize * 10),
        placeholders = placeholders,
        jumpThreshold = jumpThreshold,
        pageTtl = pageTtl,
        retryBackoffBase = retryBackoffBase
    )
}

/**
 * Create a [PageStore] with the given configuration.
 *
 * Example:
 * ```kotlin
 * val searchStore = pageStore<SearchKey, Result> {
 *     fetcher { key, token ->
 *         // Fetch page from API
 *         api.search(key.query, token)
 *     }
 *
 *     config {
 *         pageSize = 30
 *         prefetchDistance = 10
 *     }
 * }
 * ```
 */
fun <K : StoreKey, V : Any> pageStore(
    builder: PageStoreBuilder<K, V>.() -> Unit
): PageStore<K, V> {
    return PageStoreBuilder<K, V>().apply(builder).build()
}
