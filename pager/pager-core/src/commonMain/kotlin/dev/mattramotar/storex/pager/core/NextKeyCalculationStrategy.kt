package dev.mattramotar.storex.pager.core


/**
 * Represents a strategy to compute the next key for pagination given the current key
 * and the items returned from the last load.
 */
interface NextKeyCalculationStrategy<Key : Any, Value : Any> {
    /**
     * Compute the next key to load based on the current key and the loaded items.
     */
    fun computeNextKey(currentKey: Key, loadedItems: List<Value>): Key
}