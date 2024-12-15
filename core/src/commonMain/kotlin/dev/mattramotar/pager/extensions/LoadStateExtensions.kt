package dev.mattramotar.pager.extensions

import dev.mattramotar.pager.core.LoadState

/**
 * Provides convenience extension functions for [LoadState].
 *
 * For example, `LoadState.isLoading()` returns true if the current state represents a loading operation.
 *
 * Use these extensions to streamline logic when checking load states in your UI.
 */
object LoadStateExtensions {
    fun LoadState.isLoading(): Boolean =
        this is LoadState.Loading
}