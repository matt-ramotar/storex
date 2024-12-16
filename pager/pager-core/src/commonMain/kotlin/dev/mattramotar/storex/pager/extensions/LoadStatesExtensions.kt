package dev.mattramotar.storex.pager.extensions

import dev.mattramotar.storex.pager.core.LoadState
import dev.mattramotar.storex.pager.core.LoadStates
import dev.mattramotar.storex.pager.extensions.LoadStateExtensions.isLoading
import dev.mattramotar.storex.pager.store.LoadDirection

/**
 * Provides convenience extension functions for [LoadStates].
 *
 * For example:
 * - `LoadStates.isLoading()` returns true if any of the internal load states is currently loading.
 * - `LoadStates.hasError()` checks if any load state represents an error.
 * - `LoadStates.isIdle()` checks if all load operations are not loading and not erred, indicating a stable state.
 *
 * These functions simplify state checks and help produce cleaner UI code.
 */
object LoadStatesExtensions {
    fun LoadStates.isLoading(): Boolean =
        this.refresh.isLoading() || this.prepend.isLoading() || this.append.isLoading()

    fun LoadStates.hasError() = refresh is LoadState.Error || append is LoadState.Error || prepend is LoadState.Error
    fun LoadStates.isIdle() =
        refresh is LoadState.NotLoading && append is LoadState.NotLoading && prepend is LoadState.NotLoading

    /**
     * Updates the [LoadState] of either the prepend or append operation within the current [LoadStates],
     * based on the provided [direction] and [newLoadState].
     *
     * @param direction The direction of the load operation (either [LoadDirection.Append] or [LoadDirection.Prepend]).
     * @param newLoadState The new [LoadState] to be applied to the specified direction.
     * @return A new [LoadStates] instance with the updated [LoadState] for the given direction.
     */
    fun LoadStates.update(direction: LoadDirection, newLoadState: LoadState): LoadStates {
        return when (direction) {
            LoadDirection.Prepend -> copy(prepend = newLoadState)
            LoadDirection.Append -> copy(append = newLoadState)
        }
    }

}