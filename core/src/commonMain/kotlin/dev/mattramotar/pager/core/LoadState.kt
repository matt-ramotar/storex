package dev.mattramotar.pager.core

/**
 * Describes the current state of a load operation in a particular pagination direction or phase (e.g., refresh, append, prepend).
 *
 * Load states include:
 * - [NotLoading]: Indicates no active load is occurring and whether the end of pagination is reached.
 * - [Loading]: Indicates an ongoing load operation.
 * - [Error]: Indicates a load operation failed, containing the encountered [Throwable].
 *
 * Use load states to update your UI accordingly (e.g., show/hide loading indicators or error messages).
 */
sealed interface LoadState {
    val endOfPaginationReached: Boolean

    data class NotLoading(
        override val endOfPaginationReached: Boolean,
    ) : LoadState {
        internal companion object {
            val Complete = NotLoading(endOfPaginationReached = true)
            val Incomplete = NotLoading(endOfPaginationReached = false)
        }
    }

    data class Loading(
        override val endOfPaginationReached: Boolean,
    ) : LoadState

    data class Error(
        val error: Throwable,
        override val endOfPaginationReached: Boolean,
    ) : LoadState
}