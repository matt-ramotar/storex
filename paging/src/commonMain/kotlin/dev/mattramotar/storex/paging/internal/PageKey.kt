package dev.mattramotar.storex.paging.internal

import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.paging.PageToken

/**
 * Internal key that combines user's key with a page token.
 *
 * For internal use only - PageStore doesn't actually use Store<PageKey, Page>,
 * it manages pages directly in PagingState.
 *
 * @param userKey The user's original key (e.g., SearchKey, FeedKey)
 * @param token The page token (cursor or offset), null for initial page
 */
internal data class PageKey<K : StoreKey>(
    val userKey: K,
    val token: PageToken?
) {
    override fun toString(): String {
        return "PageKey(userKey=$userKey, token=$token)"
    }
}
