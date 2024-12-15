package dev.mattramotar.clerk.core

/**
 * Represents the complete state of a paginated list at a given point in time.
 *
 * Includes:
 * - [items]: The list of currently loaded items.
 * - [loadStates]: The combined [LoadStates] representing ongoing, completed, or failed load operations.
 * - [currentAppendKey] and [currentPrependKey]: The keys used for upcoming append/prepend loads.
 *
 * The [PagingState] can be observed to update UI components as new pages are loaded or errors occur.
 *
 * @param Key The type representing page keys.
 * @param Value The type of the items in the loaded pages.
 */
data class PagingState<Key : Any, Value : Any>(
    val items: List<Value> = emptyList(),
    val loadStates: LoadStates = LoadStates.initial,
    val currentAppendKey: Key? = null,
    val currentPrependKey: Key? = null,
)