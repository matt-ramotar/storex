package dev.mattramotar.storex.pager.store

/**
 * A request object detailing what page to load.
 *
 * Specifies the [key], the desired [pageSize], the [direction] of loading (append or prepend),
 * and the [strategy] indicating how to fetch the data (e.g., skip cache).
 *
 * @param Key The type representing page keys.
 */

data class StorePageLoadRequest<Key : Any>(
    val key: Key,
    val pageSize: Int,
    val direction: LoadDirection,
    val strategy: LoadStrategy
)