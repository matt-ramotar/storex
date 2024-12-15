package dev.mattramotar.clerk.extensions

import dev.mattramotar.clerk.core.PagingState
import dev.mattramotar.clerk.extensions.LoadStatesExtensions.isLoading

/**
 * Provides convenience extension functions for [PagingState].
 *
 * For example:
 * - `PagingState.isLoading()` indicates if the pager is currently loading any additional data.
 * - `PagingState.endOfPaginationReached()` checks if there are no more items to load in the append direction.
 *
 * These utilities help quickly assess the paging state when rendering UI or deciding next actions.
 */
object PagingStateExtensions {
    fun <Key : Any, Value : Any> PagingState<Key, Value>.isLoading(): Boolean =
        this.loadStates.isLoading()

    fun <Key : Any, Value : Any> PagingState<Key, Value>.endOfPaginationReached(): Boolean =
        this.loadStates.append.endOfPaginationReached

}