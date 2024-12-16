package dev.mattramotar.storex.pager.core

import dev.mattramotar.storex.pager.store.LoadDirection

/**
 * A functional interface to compute the next pagination key after a page load.
 *
 * This is useful for cases where the next key may depend on the items received from the current page load.
 * For example, if the keys are page numbers, this might simply increment the current page number. If keys
 * depend on item metadata, the implementation can extract the next key from the loaded items.
 *
 * @param Key The type representing page keys.
 * @param Value The type of the items loaded on each page.
 */
interface NextKeyProvider<Key : Any, Value : Any> {
    fun computeNextKey(currentKey: Key, direction: LoadDirection, loadedItems: List<Value>): Key
}
