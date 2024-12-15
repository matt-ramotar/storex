package dev.mattramotar.storex.pager

/**
 * Configuration parameters for a [Pager].
 *
 * Controls aspects like:
 * - [initialKey]: The key to start loading from.
 * - [pageSize]: The number of items to load per page.
 * - [prefetchDistance]: How far ahead in the data should be loaded (not currently used in `DefaultPager`).
 * - [enablePlaceholders]: Indicates whether placeholders for unloaded items are enabled (not currently used in `DefaultPager`).
 *
 * Adjust these settings based on your data source and UI needs.
 *
 * @param Key The type representing page keys.
 */
data class PagingConfig<Key : Any>(
    val initialKey: Key,
    val pageSize: Int = 20,
    val prefetchDistance: Int = pageSize,
    val enablePlaceholders: Boolean = false
)