package dev.mattramotar.storex.store

/**
 * A contract that defines the ability to load a page of data given a [StorePageLoadRequest].
 *
 * Implementations of [PageLoad] should handle the specifics of fetching data (e.g., from network or database)
 * and return a [StorePageLoadResponse] indicating success or failure.
 *
 * @param Key The type representing page keys.
 * @param Output The type of items loaded for each page.
 */
interface PageLoad<Key: Any, Output: Any> {
    suspend fun loadPage(request: StorePageLoadRequest<Key>): StorePageLoadResponse<Output>
}