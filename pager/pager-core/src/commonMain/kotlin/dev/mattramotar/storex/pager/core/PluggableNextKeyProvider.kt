package dev.mattramotar.storex.pager.core


import dev.mattramotar.storex.pager.store.LoadDirection

/**
 * A NextKeyProvider implementation that holds separate strategies for prepend and append directions.
 * Both strategies can be changed at runtime.
 */
class PluggableNextKeyProvider<Key : Any, Value : Any>(
    private var appendStrategy: NextKeyCalculationStrategy<Key, Value>,
    private var prependStrategy: NextKeyCalculationStrategy<Key, Value>
) : NextKeyProvider<Key, Value> {

    fun setAppendStrategy(strategy: NextKeyCalculationStrategy<Key, Value>) {
        this.appendStrategy = strategy
    }

    fun setPrependStrategy(strategy: NextKeyCalculationStrategy<Key, Value>) {
        this.prependStrategy = strategy
    }

    override fun computeNextKey(currentKey: Key, direction: LoadDirection, loadedItems: List<Value>): Key {
        return when (direction) {
            LoadDirection.Append -> appendStrategy.computeNextKey(currentKey, loadedItems)
            LoadDirection.Prepend -> prependStrategy.computeNextKey(currentKey, loadedItems)
        }
    }
}
